@echo off
setlocal
cd /d "%~dp0"

set "JAR=%~1"

if not "%JAR%"=="" goto :run

set "JAR="
for /f "delims=" %%F in ('dir /b /a:-d "*.jar" 2^>nul') do (
  set "JAR=%%F"
  goto :run
)

echo No .jar file found in %cd%
pause
exit /b 1

:run
if not exist "%JAR%" (
  echo JAR not found: %JAR%
  pause
  exit /b 1
)

echo Starting %JAR%
java -jar "%JAR%"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo Process exited with code %EXIT_CODE%
)

pause
exit /b %EXIT_CODE%
