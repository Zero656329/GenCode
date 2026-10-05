@echo off
title GenCode 一键启动
rem 依次拉起 Redis -> MinIO -> MySQL -> 后端 -> 前端（各开一个窗口）
rem 已在运行的服务会自动跳过，可重复执行

start "GenCode-Redis" cmd /k call "%~dp0start-redis.bat"
start "GenCode-MinIO" cmd /k call "%~dp0start-minio.bat"
start "GenCode-MySQL" cmd /k call "%~dp0start-mysql.bat"

echo 等待 MySQL 就绪（约 10 秒）...
timeout /t 10 /nobreak >nul

start "GenCode-Backend" cmd /k call "%~dp0start-backend.bat"
start "GenCode-Frontend" cmd /k call "%~dp0start-frontend.bat"

echo.
echo 全部启动中（各服务窗口请保持打开）：
echo   Redis    localhost:6379            [Sa-Token 会话/验证码/登录锁定]
echo   MinIO    localhost:9000            [文件存储，控制台 http://localhost:9001 账号 minio/minio123456]
echo   MySQL    localhost:3306            [root/root123456, 库 gencode]
echo   后端     http://localhost:8080/api   [约 30 秒就绪]
echo   前端     http://localhost:5173       [账号 admin / admin123，test / test123456]
echo.
echo 就绪后浏览器访问 http://localhost:5173 ，停止全部服务请运行 stop-all.bat
timeout /t 15 >nul
