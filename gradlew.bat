@echo off
setlocal

set APP_HOME=%~dp0
set GRADLE_VERSION=9.3.1
set DIST_NAME=gradle-%GRADLE_VERSION%-bin.zip
set DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%
set WRAPPER_ROOT=%APP_HOME%\.gradle-wrapper
set ZIP_PATH=%WRAPPER_ROOT%\%DIST_NAME%
set INSTALL_DIR=%WRAPPER_ROOT%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%INSTALL_DIR%\bin\gradle.bat

if not exist "%WRAPPER_ROOT%" mkdir "%WRAPPER_ROOT%"

if not exist "%GRADLE_BIN%" (
  if not exist "%ZIP_PATH%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ZIP_PATH%'"
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%WRAPPER_ROOT%' -Force"
)

call "%GRADLE_BIN%" -p "%APP_HOME%" %*

