@echo off
chcp 65001 >nul
echo ==========================================
echo    停止 hm-dianping 集群
echo ==========================================
echo.

echo 正在停止所有节点...

REM 停止节点1
taskkill /FI "WINDOWTITLE eq hm-dianping-node1*" /F >nul 2>&1
if errorlevel 1 (
    echo 节点1未运行或已停止
) else (
    echo 节点1已停止
)

REM 停止节点2
taskkill /FI "WINDOWTITLE eq hm-dianping-node2*" /F >nul 2>&1
if errorlevel 1 (
    echo 节点2未运行或已停止
) else (
    echo 节点2已停止
)

REM 停止节点3
taskkill /FI "WINDOWTITLE eq hm-dianping-node3*" /F >nul 2>&1
if errorlevel 1 (
    echo 节点3未运行或已停止
) else (
    echo 节点3已停止
)

echo.
echo ==========================================
echo    集群已停止
echo ==========================================
pause
