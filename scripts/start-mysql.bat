@echo off
title GenCode-MySQL
rem 端口 3306，root/root123456，数据目录随软件目录

netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo [MySQL] 已在运行（端口 3306 已监听），无需重复启动。
    timeout /t 5 >nul
    exit /b 0
)

echo [MySQL] 启动中（端口 3306）...
"D:\mysql-8.0.28-winx64\bin\mysqld.exe" --defaults-file="D:\mysql-8.0.28-winx64\my.ini" --console
pause
