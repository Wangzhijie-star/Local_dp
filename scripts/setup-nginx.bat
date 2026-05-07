@echo off
chcp 65001 >nul
setlocal

set "NGINX_PATH=D:\redis_resources\nginx-1.18.0\nginx-1.18.0"
set "PROJECT_PATH=D:\redis_resources\hm-dianping"
set "NGINX_CONF=%NGINX_PATH%\conf\nginx.conf"

echo ==========================================
echo   hm-dianping Nginx config setup
echo ==========================================
echo.

if not exist "%NGINX_PATH%\nginx.exe" (
    echo [ERROR] nginx.exe not found: %NGINX_PATH%\nginx.exe
    pause
    exit /b 1
)

if not exist "%NGINX_CONF%" (
    echo [ERROR] nginx.conf not found: %NGINX_CONF%
    pause
    exit /b 1
)

echo [1/3] Backing up current nginx.conf...
copy "%NGINX_CONF%" "%NGINX_CONF%.backup" >nul
if errorlevel 1 (
    echo [ERROR] Failed to create backup.
    pause
    exit /b 1
)

echo [2/3] Installing cluster nginx.conf...
copy "%PROJECT_PATH%\scripts\nginx.conf" "%NGINX_CONF%" >nul
if errorlevel 1 (
    echo [ERROR] Failed to copy nginx config.
    pause
    exit /b 1
)

echo [3/3] Testing Nginx config...
pushd "%NGINX_PATH%"
nginx.exe -t
if errorlevel 1 (
    popd
    echo [ERROR] Nginx config test failed. Restoring backup...
    copy "%NGINX_CONF%.backup" "%NGINX_CONF%" >nul
    pause
    exit /b 1
)
popd

echo.
echo [OK] Nginx is configured for:
echo   frontend: http://localhost:8080
echo   upstream: 127.0.0.1:8081, 8082, 8083
echo.
pause
