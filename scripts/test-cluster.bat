@echo off
chcp 65001 >nul
setlocal

set "NO_PAUSE=%~1"

echo ==========================================
echo   hm-dianping cluster health check
echo ==========================================
echo.

call :check "node1" "http://localhost:8081/shop/1"
call :check "node2" "http://localhost:8082/shop/1"
call :check "node3" "http://localhost:8083/shop/1"
call :check "nginx" "http://localhost:8080/api/shop/1"

echo.
echo ==========================================
echo   health check finished
echo ==========================================

if /i not "%NO_PAUSE%"=="nopause" pause
exit /b 0

:check
set "NAME=%~1"
set "URL=%~2"
echo Checking %NAME%: %URL%
curl.exe -s -f --max-time 5 "%URL%" >nul 2>&1
if errorlevel 1 (
    echo   [FAIL] no response
) else (
    echo   [ OK ] response received
    curl.exe -s --max-time 5 "%URL%" | findstr /i "success" >nul 2>&1
    if errorlevel 1 (
        echo   [WARN] response does not contain success
    ) else (
        echo   [ OK ] response contains success
    )
)
echo.
exit /b 0
