param([string]$JavaHome = $env:JAVA_HOME, [string]$GsonPath)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { throw 'Set JAVA_HOME to a JDK 17 or newer, or pass -JavaHome.' }
$classes = Join-Path $projectRoot 'target/offline-checks'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$gson = if ($GsonPath) { $GsonPath } else { Join-Path $projectRoot 'lib/gson-2.13.2.jar' }
$sources = (Get-ChildItem (Join-Path $projectRoot 'src/main/java/edu/videoquiz/core/*.java')).FullName
$compileOutput = & (Join-Path $JavaHome 'bin/javac.exe') --release 17 -cp $gson -d $classes $sources (Join-Path $projectRoot 'tests/CoreChecks.java') (Join-Path $projectRoot 'tests/LiveProbe.java') 2>&1
$compileExit = $LASTEXITCODE
$compileOutput | ForEach-Object { Write-Output $_ }
if ($compileExit -ne 0 -or ($compileOutput -join "`n") -match 'exception has occurred in the compiler|AccessDeniedException') { throw 'Core compilation failed or the compiler reported a filesystem error.' }
& (Join-Path $JavaHome 'bin/java.exe') -cp "$classes;$gson" edu.videoquiz.core.CoreChecks
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed.' }
node --check (Join-Path $projectRoot 'src/main/resources/static/app.js')
if ($LASTEXITCODE -ne 0) { throw 'JavaScript syntax check failed.' }
node --test (Join-Path $projectRoot 'tests/clock.test.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Clock tests failed.' }
