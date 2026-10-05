@echo off
title GenCode-Redis
rem 端口 6379，仅监听 127.0.0.1，数据落本目录

cd /d D:\redis-5.0.14.1
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo [Redis] 已在运行（端口 6379 已监听），无需重复启动。
    timeout /t 5 >nul
    exit /b 0
)

echo [Redis] 启动中（端口 6379）...
D:\redis-5.0.14.1\redis-server.exe D:\redis-5.0.14.1\redis.conf
pause
