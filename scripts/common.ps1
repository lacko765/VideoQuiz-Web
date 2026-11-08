$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Get-JdkInfo([string]$Path) {
    if (-not $Path -or -not (Test-Path -LiteralPath (Join-Path $Path 'bin/javac.exe')) -or -not (Test-Path -LiteralPath (Join-Path $Path 'release'))) { return }
    $release = Get-Content -LiteralPath (Join-Path $Path 'release') -Raw
    if ($release -match 'JAVA_VERSION="(\d+)[^"]*"') {
        $major = [int]$Matches[1]
        if ($major -ge 17 -and $major -le 26) { [pscustomobject]@{Path=(Resolve-Path -LiteralPath $Path).Path; Major=$major; Preferred=($major -in 17,21)} }
    }
}
function Find-Jdk {
    $paths = @($env:JAVA_HOME)
    $cmd = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($cmd) { $paths += Split-Path -Parent (Split-Path -Parent $cmd.Source) }
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, "$env:LOCALAPPDATA\Programs", "$env:USERPROFILE\.jdks")) {
        foreach ($pattern in @('Java\*','Eclipse Adoptium\*','Microsoft\jdk*','Amazon Corretto\*','BellSoft\*','Zulu\*','NetBeans*\jdk','Apache NetBeans*\jdk')) {
            $paths += @(Get-Item -Path (Join-Path $base $pattern) -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
        }
        if ($base -like '*.jdks') { $paths += @(Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
    }
    foreach ($drive in Get-PSDrive -PSProvider FileSystem) {
        $paths += @(Get-Item -Path (Join-Path $drive.Root '*NetBeans*\jdk') -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    }
    foreach ($key in @('HKLM:\SOFTWARE\JavaSoft\JDK','HKLM:\SOFTWARE\JavaSoft\Java Development Kit')) {
        foreach ($child in Get-ChildItem $key -ErrorAction SilentlyContinue) { $paths += (Get-ItemProperty $child.PSPath -ErrorAction SilentlyContinue).JavaHome }
    }
    @($paths | Where-Object { $_ } | Select-Object -Unique | ForEach-Object { Get-JdkInfo $_ } | Sort-Object @{Expression='Preferred';Descending=$true},@{Expression='Major';Descending=$true})
}
function Use-Jdk([string]$Path) {
    if (-not (Get-JdkInfo $Path)) { throw 'A supported JDK was not found. Install JDK 21 or 17, then run setup again.' }
    $env:JAVA_HOME=$Path
    $env:PATH=(Join-Path $Path 'bin')+';'+$env:PATH
}
function Assert-PortAvailable([int]$Port) {
    if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Choose a port between 1024 and 65535.' }
    $listeners=[Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if (@($listeners | Where-Object { $_.Port -eq $Port }).Count) { throw "Port $Port is already in use. Stop your previous VideoQuiz window with Ctrl+C, or rerun setup and choose another port. No process was stopped." }
}
function Get-Maven([switch]$AllowDownload) {
    $candidates=@((Join-Path $projectRoot '.tools/apache-maven-3.9.16/bin/mvn.cmd'))
    $cmd=Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }
    foreach ($drive in Get-PSDrive -PSProvider FileSystem) { $candidates += @(Get-Item -Path (Join-Path $drive.Root '*NetBeans*\java\maven\bin\mvn.cmd') -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
    foreach ($base in @($env:ProgramFiles,${env:ProgramFiles(x86)},"$env:LOCALAPPDATA\Programs")) { $candidates += @(Get-Item -Path (Join-Path $base '*NetBeans*\java\maven\bin\mvn.cmd') -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
            $version=(& $candidate --version 2>&1 | Out-String)
            $ok=$LASTEXITCODE -eq 0
            $ErrorActionPreference=$old
            if ($ok -and $version -match 'Apache Maven (3\.\d+\.\d+)') { if ([version]$Matches[1] -ge [version]'3.6.3') { return $candidate } }
        }
    }
    if (-not $AllowDownload) { throw 'Maven was not found. Run setup-videoquiz.bat while connected to the internet.' }
    return Install-ProjectMaven
}
function Install-ProjectMaven {
    Write-Host 'Downloading Apache Maven to this project (no administrator access needed)...'
    $toolsDir=Join-Path $projectRoot '.tools'
    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    $zip=Join-Path $toolsDir 'apache-maven-3.9.16-bin.zip'
    $base='https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip'
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $base -OutFile $zip
        $checksum=(Invoke-WebRequest -UseBasicParsing -Uri ($base+'.sha512')).Content
        if ($checksum -is [byte[]]) { $checksum=[Text.Encoding]::UTF8.GetString($checksum) }
        if ($checksum -notmatch '(?i)\b[0-9a-f]{128}\b') { throw 'Missing checksum' }
        if ((Get-FileHash -LiteralPath $zip -Algorithm SHA512).Hash -ne $Matches[0]) { throw 'Checksum mismatch' }
        Expand-Archive -LiteralPath $zip -DestinationPath $toolsDir -Force
    } catch { throw 'Maven download or checksum verification failed. Check your connection/proxy, or install Apache Maven from maven.apache.org and rerun setup.' }
    return Get-Maven
}
function Invoke-Build([string]$Maven) {
    Write-Host 'Building and testing VideoQuiz. First run downloads dependencies; please wait...'
    Push-Location $projectRoot
    try {
        & $Maven -B -ntp '-Dmaven.repo.local=.tools/m2' verify
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed. Check the first ERROR above. Check internet/proxy access to Maven Central and use JDK 17 or 21. Rerun setup after fixing it.' }
    } finally { Pop-Location }
}
function Convert-SecretToText([Security.SecureString]$Secret) {
    $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
function Read-LocalSecrets {
    try {
        $secrets=Import-Clixml -LiteralPath (Join-Path $projectRoot 'config/secrets.xml')
        if ($secrets.GeminiKey -isnot [Security.SecureString] -or $secrets.TlsPassword -isnot [Security.SecureString]) { throw 'Invalid secrets' }
        return $secrets
    } catch { throw 'Cannot unlock local settings. Run setup-videoquiz.bat using your own Windows account. Never copy another user''s config/secrets.xml.' }
}
function Assert-Certificate([string]$JavaHome) {
    $keystore=Join-Path $projectRoot 'config/localhost.p12'
    if (-not (Test-Path -LiteralPath $keystore)) { throw 'HTTPS certificate is missing. Run setup-videoquiz.bat.' }
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    Push-Location -LiteralPath $projectRoot
    try { & (Join-Path $JavaHome 'bin/keytool.exe') -list -alias videoquiz -keystore 'config/localhost.p12' -storepass:env VIDEOQUIZ_TLS_PASSWORD *> $null } finally { Pop-Location }
    $ok=$LASTEXITCODE -eq 0
    $ErrorActionPreference=$old
    if (-not $ok) { throw 'The HTTPS password does not unlock the existing certificate. Rerun setup with its original password; the certificate was not replaced.' }
}
