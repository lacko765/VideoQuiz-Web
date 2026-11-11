param([switch]$NonInteractive,[switch]$UseEnvironmentSecrets,[string]$JavaHome,[string]$Model='gemini-3.5-flash-lite',[int]$Port=8443)
. (Join-Path $PSScriptRoot 'common.ps1')
try {
    Write-Host 'VideoQuiz Easy Setup - JDK 17 or 21 recommended'
    $jdks=@(Find-Jdk)
    foreach ($jdk in $jdks) { Write-Host "Found JDK $($jdk.Major): $($jdk.Path)" }
    if (-not $JavaHome -and $jdks.Count) { $JavaHome=$jdks[0].Path }
    if (-not $JavaHome -and -not $NonInteractive) {
        Write-Host 'Select your installed JDK folder. If none is installed, install JDK 21 from adoptium.net and rerun setup.'
        Add-Type -AssemblyName System.Windows.Forms
        $picker=New-Object Windows.Forms.FolderBrowserDialog
        $picker.Description='Select JDK 17 or 21 folder (contains bin and release)'
        if ($picker.ShowDialog() -eq 'OK') { $JavaHome=$picker.SelectedPath }
        $picker.Dispose()
    }
    Use-Jdk $JavaHome
    if ((Get-JdkInfo $JavaHome).Major -notin 17,21) { Write-Host 'This is a newer JDK. JDK 17 or 21 is recommended if a build/runtime problem occurs.' }
    if (-not $NonInteractive) {
        $answer=Read-Host 'HTTPS port [8443; press Enter for default]'
        if ($answer) { $Port=[int]$answer }
        $answer=Read-Host 'Gemini video model [gemini-3.5-flash-lite; Enter for default]'
        if ($answer) { $Model=$answer.Trim() }
    }
    Assert-PortAvailable $Port
    if ($Model -notmatch '^[A-Za-z0-9._-]+$') { throw 'Use a Gemini model ID, for example gemini-3.5-flash-lite.' }
    foreach ($folder in @('config','data','.tools')) { New-Item -ItemType Directory -Path (Join-Path $projectRoot $folder) -Force | Out-Null }
    $secrets=$null
    if (Test-Path -LiteralPath (Join-Path $projectRoot 'config/secrets.xml')) {
        if ($NonInteractive -and -not $UseEnvironmentSecrets) { $secrets=Read-LocalSecrets }
        elseif (-not $NonInteractive -and (Read-Host 'Keep saved API key and HTTPS password? [Y/n]') -ne 'n') { $secrets=Read-LocalSecrets }
    }
    if (-not $secrets) {
        if ($UseEnvironmentSecrets) {
            if (-not $env:GEMINI_API_KEY -or -not $env:VIDEOQUIZ_TLS_PASSWORD) { throw 'Test/advanced mode requires both secret environment variables.' }
            $key=ConvertTo-SecureString $env:GEMINI_API_KEY -AsPlainText -Force
            $password=ConvertTo-SecureString $env:VIDEOQUIZ_TLS_PASSWORD -AsPlainText -Force
        } elseif ($NonInteractive) { throw 'No saved credentials. Run setup-videoquiz.bat interactively.' }
        else {
            Write-Host 'Create your own Gemini API key at https://aistudio.google.com/apikey. Use a free-tier project without billing. Input below is hidden.'
            $key=Read-Host 'Paste your Gemini API key' -AsSecureString
            $password=Read-Host 'HTTPS password (12+ characters; existing certificate: enter its original password)' -AsSecureString
            $confirm=Read-Host 'Repeat HTTPS password' -AsSecureString
            if ((Convert-SecretToText $password) -cne (Convert-SecretToText $confirm)) { throw 'The HTTPS passwords did not match. Run setup again.' }
        }
        if ($key.Length -eq 0) { throw 'A Gemini API key is required. Run setup again with your own key.' }
        if ($password.Length -lt 12) { throw 'HTTPS password must have at least 12 characters.' }
        $secrets=[pscustomobject]@{GeminiKey=$key;TlsPassword=$password}
    }
    if ($secrets.TlsPassword.Length -lt 12) { throw 'HTTPS password must have at least 12 characters.' }
    $env:VIDEOQUIZ_TLS_PASSWORD=Convert-SecretToText $secrets.TlsPassword
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'config/localhost.p12'))) { & (Join-Path $PSScriptRoot 'create-local-certificate.ps1') -JavaHome $JavaHome }
    Assert-Certificate $JavaHome
    # Export-Clixml uses Windows DPAPI for SecureString: current user on this computer only.
    $secrets | Export-Clixml -LiteralPath (Join-Path $projectRoot 'config/secrets.xml')
    $maven=Get-Maven -AllowDownload
    Invoke-Build $maven
    [pscustomobject]@{JavaHome=$JavaHome;Maven=$maven;GeminiModel=$Model;Port=$Port} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectRoot 'config/local-settings.json') -Encoding UTF8
    [pscustomobject]@{Version=1;CompletedUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectRoot 'config/setup-complete.json') -Encoding UTF8
    Write-Host "SETUP SUCCESSFUL. Open this project in NetBeans and click Run, or double-click start-videoquiz.bat. Website: https://localhost:$Port"
    Write-Host 'The certificate is self-signed. Browser trust is NOT installed. See HOW_TO_RUN_IN_NETBEANS.md for the first browser visit.'
    Write-Host 'The key was saved encrypted, not tested against Google. Actual analysis will report invalid keys, unavailable models or quota errors.'
} catch {
    Write-Host ('SETUP ERROR: '+$_.Exception.Message) -ForegroundColor Red
    exit 1
} finally { $env:VIDEOQUIZ_TLS_PASSWORD=$null; $env:GEMINI_API_KEY=$null }
