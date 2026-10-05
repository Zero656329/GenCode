@echo off
title GenCode-Backend
rem 后端 http://localhost:8080/api ，接口文档 /api/swagger-ui/index.html

cd /d E:\GitHub\GenCode\gencode-server
set JAR=gencode-server\target\gencode-server-1.0.0-SNAPSHOT.jar

netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo [后端] 已在运行（端口 8080 已监听），无需重复启动。
    timeout /t 5 >nul
    exit /b 0
)

if not exist "%JAR%" (
    echo [后端] 未找到 jar，开始打包（首次约 1-2 分钟，请耐心等待）...
    call mvn -DskipTests package
    if errorlevel 1 (
        echo [后端] 打包失败，请检查 Maven/JDK 21 环境（mvn -v、java -version）。
        pause
        exit /b 1
    )
)

echo [后端] 启动中，约 30 秒就绪（此窗口保持打开，关闭窗口即停止后端）...
java -jar %JAR%
pause
