param([switch]$SkipBuild,[switch]$CheckOnly)
. (Join-Path $PSScriptRoot 'common.ps1')
try {
    foreach ($file in @('setup-complete.json','local-settings.json','secrets.xml')) {
        if (-not (Test-Path -LiteralPath (Join-Path $projectRoot "config/$file"))) { throw 'Setup is incomplete. Double-click setup-videoquiz.bat first.' }
    }
    $settings=Get-Content -LiteralPath (Join-Path $projectRoot 'config/local-settings.json') -Raw | ConvertFrom-Json
    Use-Jdk $settings.JavaHome
    Assert-PortAvailable $settings.Port
    $secrets=Read-LocalSecrets
    $env:VIDEOQUIZ_TLS_PASSWORD=Convert-SecretToText $secrets.TlsPassword
    Assert-Certificate $settings.JavaHome
    if ($CheckOnly) { Write-Host 'Startup checks passed (Java, saved credentials, certificate and port).'; return }
    if (-not $SkipBuild) { Invoke-Build (Get-Maven -AllowDownload) }
    $jar=Join-Path $projectRoot 'target/videoquiz-1.0.0.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Application JAR is missing. Run setup again to build it.' }
    $env:GEMINI_API_KEY=Convert-SecretToText $secrets.GeminiKey
    $env:GEMINI_MODEL=$settings.GeminiModel
    $env:VIDEOQUIZ_PORT=[string]$settings.Port
    $env:VIDEOQUIZ_BIND='127.0.0.1'
    $env:VIDEOQUIZ_TLS_KEYSTORE='file:./config/localhost.p12'
    $env:VIDEOQUIZ_DATA_DIR='./data'
    Write-Host "Starting VideoQuiz. After 'Started Application', open https://localhost:$($settings.Port)"
    Write-Host 'Stop using Ctrl+C here, or the red Stop button in the NetBeans Output panel. Keep this window open while using VideoQuiz.'
    Push-Location $projectRoot
    try { & (Join-Path $settings.JavaHome 'bin/java.exe') -jar 'target/videoquiz-1.0.0.jar'; $code=$LASTEXITCODE } finally { Pop-Location }
    if ($code -ne 0) { throw 'Spring Boot stopped with an error. Check the first error above. Rerun setup to repair Java/certificate settings; check that the port is free.' }
} catch {
    Write-Host ('STARTUP ERROR: '+$_.Exception.Message) -ForegroundColor Red
    exit 1
} finally { $env:GEMINI_API_KEY=$null; $env:VIDEOQUIZ_TLS_PASSWORD=$null }
