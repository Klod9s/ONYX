
@echo off
setlocal
title Android APK One-Click Installer
color 0A

echo ==========================================
echo       ANDROID ONE-CLICK INSTALLER
echo ==========================================
echo.

:: ADB location
set "ADB=C:\Users\Seren\AppData\Local\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo ERROR: ADB not found!
    pause
    exit /b 1
)

:: Find latest debug APK using PowerShell
echo Searching for latest debug APK...

for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "$root='%~dp0'; $f=Get-ChildItem -LiteralPath $root -Filter *.apk -File -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match '\\build\\outputs\\apk\\debug\\' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if ($f) { $f.FullName }"`) do (
    set "APK=%%A"
)

if not defined APK (
    echo.
    echo ERROR: No debug APK found!
    echo Build your APK in Android Studio first.
    pause
    exit /b 1
)

echo.
echo Found APK:
echo "%APK%"
echo.

:: Check device
echo Checking connected devices...
"%ADB%" start-server
"%ADB%" devices

set "DEVICE_COUNT=0"

for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" set /a DEVICE_COUNT+=1
)

if not "%DEVICE_COUNT%"=="1" (
    echo.
    echo ERROR: Connect exactly one authorized device.
    pause
    exit /b 1
)

:: Install APK
echo.
echo Installing APK update...
echo.

"%ADB%" install -r "%APK%"

if errorlevel 1 (
    echo.
    echo INSTALLATION FAILED!
    pause
    exit /b 1
)

echo.
echo ==========================================
echo       INSTALLATION SUCCESSFUL
echo ==========================================
echo.
echo App updated successfully!
echo Existing app data preserved.
echo.
pause
