@echo off
setlocal
cd /d "%~dp0"
echo Building Last Pagans Settlements V1.1.0...
call gradlew.bat clean build
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the complete terminal output and send it for repair.
  pause
  exit /b 1
)
echo.
echo BUILD SUCCESSFUL.
echo JAR: build\libs\settlementprototype-1.1.0.jar
pause
