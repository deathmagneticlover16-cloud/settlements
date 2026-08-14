@echo off
setlocal
cd /d "%~dp0"
echo Building Settlements 2.5.1...
call gradlew.bat clean build
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the complete console output when reporting the error.
  pause
  exit /b 1
)
echo.
echo BUILD COMPLETE
echo JAR: build\libs\settlements-2.5.1.jar
pause
