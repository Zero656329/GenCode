@echo off
title GenCode-MinIO
rem API 9000 / 控制台 9001，账号 minio/minio123456，数据 D:\minio\data

netstat -ano | findstr ":9000" | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo [MinIO] 已在运行（端口 9000 已监听），无需重复启动。
    timeout /t 5 >nul
    exit /b 0
)

echo [MinIO] 启动中（API 9000，控制台 http://localhost:9001）...
set MINIO_ROOT_USER=minio
set MINIO_ROOT_PASSWORD=minio123456
D:\minio\minio.exe server D:\minio\data --address ":9000" --console-address ":9001"
pause
