@echo off
title GenCode-Frontend
rem 前端 http://localhost:5173 ，账号 admin / admin123

set PATH=D:\nvm\v20.10.0;%PATH%
cd /d E:\GitHub\GenCode\gencode-ui

netstat -ano | findstr ":5173" | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo [前端] 已在运行（端口 5173 已监听），无需重复启动。
    timeout /t 5 >nul
    exit /b 0
)

if not exist "node_modules" (
    echo [前端] 首次运行，安装依赖（约 2 分钟）...
    call npm install --registry=https://registry.npmmirror.com
    if errorlevel 1 (
        echo [前端] 依赖安装失败，请检查 Node 20 环境。
        pause
        exit /b 1
    )
)

echo [前端] 启动中：http://localhost:5173 （此窗口保持打开，关闭窗口即停止前端）...
call npm run dev
pause
