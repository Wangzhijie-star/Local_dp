@echo off
chcp 65001 >nul
setlocal

set "NGINX_PATH=D:\redis_resources\nginx-1.18.0\nginx-1.18.0"
set "PROJECT_PATH=D:\redis_resources\hm-dianping"
set "JAR_FILE=%PROJECT_PATH%\target\hm-dianping-0.0.1-SNAPSHOT.jar"
set "MAVEN_CMD=mvn"

where %MAVEN_CMD% >nul 2>&1
if errorlevel 1 (
    if exist "D:\develop\apache-maven-3.9.11\bin\mvn.cmd" (
        set "MAVEN_CMD=D:\develop\apache-maven-3.9.11\bin\mvn.cmd"
    )
)

echo ==========================================
echo   hm-dianping cluster + Nginx startup
echo ==========================================
echo.

echo [1/5] Checking Nginx config...
if not exist "%NGINX_PATH%\nginx.exe" (
    echo [ERROR] nginx.exe not found: %NGINX_PATH%\nginx.exe
    pause
    exit /b 1
)
pushd "%NGINX_PATH%"
nginx.exe -t
if errorlevel 1 (
    popd
    echo [ERROR] Nginx config test failed.
    pause
    exit /b 1
)
popd
echo.

echo [2/5] Building backend jar...
pushd "%PROJECT_PATH%"
where "%MAVEN_CMD%" >nul 2>&1
if errorlevel 1 (
    if exist "%JAR_FILE%" (
        echo Maven was not found. Reusing existing jar:
        echo %JAR_FILE%
    ) else (
        echo [ERROR] Maven was not found and jar does not exist:
        echo %JAR_FILE%
        echo Please install Maven or build the jar in your IDE first.
        popd
        pause
        exit /b 1
    )
) else (
    call "%MAVEN_CMD%" clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Maven package failed.
        popd
        pause
        exit /b 1
    )
)
if not exist logs mkdir logs
popd
echo.

echo [3/5] Starting Nginx on http://localhost:8080 ...
pushd "%NGINX_PATH%"
nginx.exe -s reload >nul 2>&1
if errorlevel 1 (
    start "hmdp-nginx" nginx.exe
)
popd
timeout /t 2 /nobreak >nul
echo.

echo [4/5] Starting backend nodes...
pushd "%PROJECT_PATH%"
start "hm-dianping-node1" cmd /c "java -jar %JAR_FILE% --server.port=8081 > logs\node1.log 2>&1"
timeout /t 3 /nobreak >nul
start "hm-dianping-node2" cmd /c "java -jar %JAR_FILE% --server.port=8082 > logs\node2.log 2>&1"
timeout /t 3 /nobreak >nul
start "hm-dianping-node3" cmd /c "java -jar %JAR_FILE% --server.port=8083 > logs\node3.log 2>&1"
timeout /t 5 /nobreak >nul
popd
echo.

echo [5/5] Health check...
call "%PROJECT_PATH%\scripts\test-cluster.bat" nopause

echo.
echo Frontend: http://localhost:8080
echo API through Nginx: http://localhost:8080/api/shop/1
echo Backend nodes: http://localhost:8081, http://localhost:8082, http://localhost:8083
echo Logs: %PROJECT_PATH%\logs\node1.log, node2.log, node3.log
echo.
pause
