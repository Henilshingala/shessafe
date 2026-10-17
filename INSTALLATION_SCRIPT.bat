@echo off
REM Women Safety App - Installation Script
REM This script helps install the APK via ADB (Android Debug Bridge)

echo.
echo ========================================
echo Women Safety App - ADB Installation
echo ========================================
echo.

REM Check if ADB is available
adb version >nul 2>&1
if errorlevel 1 (
    echo ERROR: ADB not found!
    echo.
    echo Please install Android SDK Platform Tools:
    echo https://developer.android.com/studio/releases/platform-tools
    echo.
    pause
    exit /b 1
)

echo Checking for connected devices...
adb devices
echo.

REM Check if device is connected
for /f "tokens=2" %%a in ('adb devices ^| findstr /v "List"') do (
    if not "%%a"=="" (
        echo Found device: %%a
        echo.
        echo Installing app-release.apk...
        adb install -r app\build\outputs\apk\release\app-release.apk
        
        if errorlevel 1 (
            echo.
            echo ERROR: Installation failed!
            echo Please check:
            echo - Device is connected via USB
            echo - USB debugging is enabled
            echo - APK file exists at: app\build\outputs\apk\release\app-release.apk
            pause
            exit /b 1
        )
        
        echo.
        echo SUCCESS: App installed!
        echo.
        echo Launching app...
        adb shell am start -n com.womensafety.app/.MainActivity
        
        echo.
        echo Installation complete!
        pause
        exit /b 0
    )
)

echo ERROR: No Android device connected!
echo.
echo Please:
echo 1. Connect Android device via USB
echo 2. Enable USB debugging on device
echo 3. Run this script again
echo.
pause
exit /b 1
