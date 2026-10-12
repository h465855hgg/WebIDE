@echo off
rem ============================================================
rem  WebIDE 一键构建脚本 (Windows 双击可用)
rem
rem  用法: build.bat [debug^|release] [arm64-v8a^|armeabi-v7a^|all] [--clean]
rem     build.bat                      REM debug 两个架构
rem     build.bat release              REM release 两个架构
rem     build.bat release arm64-v8a    REM release 仅 arm64
rem
rem  产物: app\build\outputs\apk\<架构>\<debug^|release>\*.apk
rem ============================================================
setlocal enabledelayedexpansion
cd /d "%~dp0.."

set BUILD_TYPE=debug
set FLAVOR=all
set EXTRA=

:parse
if "%~1"=="" goto after
if /i "%~1"=="debug"     set BUILD_TYPE=debug
if /i "%~1"=="release"   set BUILD_TYPE=release
if /i "%~1"=="arm64-v8a"   set FLAVOR=arm64-v8a
if /i "%~1"=="armeabi-v7a" set FLAVOR=armeabi-v7a
if /i "%~1"=="all"         set FLAVOR=all
if /i "%~1"=="--clean"     set EXTRA=clean
shift
goto parse
:after

if /i "%BUILD_TYPE%"=="debug" (set TYPE_CAP=Debug) else (set TYPE_CAP=Release)

echo ==========================================
echo  WebIDE 构建
echo    类型: %BUILD_TYPE%
echo    架构: %FLAVOR%
echo ==========================================

if /i "%FLAVOR%"=="all" (
    if not "%EXTRA%"=="" ( call gradlew.bat clean --console=plain --quiet )
    call gradlew.bat :app:assembleArm64-v8a%TYPE_CAP% :app:assembleArmeabi-v7a%TYPE_CAP% --console=plain
) else (
    if not "%EXTRA%"=="" ( call gradlew.bat clean --console=plain --quiet )
    call gradlew.bat :app:assemble%FLAVOR%%TYPE_CAP% --console=plain
)

if errorlevel 1 (
    echo.
    echo [失败] 构建出错，请查看上方日志
    pause
    exit /b 1
)

echo.
echo ==========================================
echo  构建完成，产物:
echo ==========================================
if /i "%FLAVOR%"=="all" (
    dir /b "app\build\outputs\apk\arm64-v8a\%BUILD_TYPE%\*.apk" 2^nul
    dir /b "app\build\outputs\apk\armeabi-v7a\%BUILD_TYPE%\*.apk" 2^nul
) else (
    dir /b "app\build\outputs\apk\%FLAVOR%\%BUILD_TYPE%\*.apk" 2^nul
)
pause
endlocal
