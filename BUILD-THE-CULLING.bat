@echo off
setlocal
cd /d "%~dp0"
echo Building Settlements: The Culling and running regression checks...
call gradlew.bat clean build
if errorlevel 1 (
  echo BUILD FAILED. Copy the complete console output when reporting the error.
  pause
  exit /b 1
)
echo BUILD COMPLETE
echo JAR: build\libs\settlements-4.0.0.jar
pause
