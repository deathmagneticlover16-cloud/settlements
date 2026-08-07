@echo off
setlocal
cd /d "%~dp0"
echo Building Last Pagans Settlements 2.4.0...
call gradlew.bat clean build
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the complete console output when reporting the error.
  pause
  exit /b 1
)
echo.
echo BUILD COMPLETE
echo JAR: build\libs\settlementprototype-2.4.0.jar
pause
