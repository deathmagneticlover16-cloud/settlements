@echo off
setlocal
cd /d "%~dp0"
echo Building Settlements 2.6.0 and running regression checks...
call gradlew.bat clean build
if errorlevel 1 (
  echo BUILD FAILED. Copy the complete console output when reporting the error.
  pause
  exit /b 1
)
echo BUILD COMPLETE
echo JAR: build\libs\settlements-2.6.0.jar
pause
