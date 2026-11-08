param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME to your JDK folder first.' }
if (-not $env:VIDEOQUIZ_TLS_PASSWORD -or $env:VIDEOQUIZ_TLS_PASSWORD.Length -lt 12) { throw 'Set VIDEOQUIZ_TLS_PASSWORD to a strong password of at least 12 characters.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$configDir = Join-Path $projectRoot 'config'
New-Item -ItemType Directory -Path $configDir -Force | Out-Null
$keystore = Join-Path $configDir 'localhost.p12'
if (Test-Path -LiteralPath $keystore) { throw 'A keystore already exists. No existing certificate was replaced.' }
$oldPreference=$ErrorActionPreference
Push-Location -LiteralPath $projectRoot
try {
$ErrorActionPreference='Continue'
& (Join-Path $JavaHome 'bin/keytool.exe') -genkeypair -alias videoquiz -keyalg RSA -keysize 3072 -storetype PKCS12 -keystore 'config/localhost.p12' -storepass:env VIDEOQUIZ_TLS_PASSWORD -validity 365 -dname 'CN=localhost,OU=Local Development,O=VideoQuiz' -ext 'SAN=dns:localhost,ip:127.0.0.1' *> $null
$ErrorActionPreference=$oldPreference
if ($LASTEXITCODE -ne 0) { throw 'Certificate generation failed.' }
$ErrorActionPreference='Continue'
& (Join-Path $JavaHome 'bin/keytool.exe') -exportcert -rfc -alias videoquiz -keystore 'config/localhost.p12' -storepass:env VIDEOQUIZ_TLS_PASSWORD -file 'config/localhost.crt' *> $null
$ErrorActionPreference=$oldPreference
if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
} finally { Pop-Location; $ErrorActionPreference=$oldPreference }
Write-Output 'Local certificate created. Browser trust is not configured by this script. Use a trusted certificate for deployment.'
