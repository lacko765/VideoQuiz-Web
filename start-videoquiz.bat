@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-videoquiz.ps1"
set "result=%errorlevel%"
if not "%result%"=="0" echo Startup failed. Read the message above and HOW_TO_RUN_IN_NETBEANS.md.
pause
exit /b %result%
