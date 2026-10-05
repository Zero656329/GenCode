@echo off
title GenCode 停止全部
rem 按端口/进程名精确停止 GenCode 相关服务，不影响其他程序

echo [停止] 前端 Vite（端口 5173）...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":5173" ^| findstr "LISTENING"') do taskkill /F /PID %%p >nul 2>&1

echo [停止] 后端（端口 8080）...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do taskkill /F /PID %%p >nul 2>&1

echo [停止] MinIO...
taskkill /F /IM minio.exe >nul 2>&1

echo [停止] Redis...
taskkill /F /IM redis-server.exe >nul 2>&1

echo [停止] MySQL（安全关闭）...
"D:\mysql-8.0.28-winx64\bin\mysqladmin.exe" -uroot -proot123456 shutdown >nul 2>&1

echo.
echo GenCode 全部服务已停止。
pause
