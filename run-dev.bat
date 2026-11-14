@echo off
setlocal
if "%PORT%"=="" set "PORT=8081"
if not "%JAVA_HOME%"=="" (set "JAVA_EXE=%JAVA_HOME%\bin\java.exe") else (set "JAVA_EXE=java")
cd /d "%~dp0"
start "UniLodge dev" /b "%JAVA_EXE%" -jar unilodge-web\target\unilodge-web-1.0.0.jar --spring.profiles.active=dev --server.port=%PORT% > run.log 2>&1
endlocal
