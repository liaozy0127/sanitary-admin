@echo off
chcp 65001 > nul
title 卫浴管理系统 - 启动中

echo ================================================
echo   卫浴管理系统 一键启动脚本
echo ================================================
echo.

:: 检查 Docker
docker --version > nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Docker，请先安装 Docker Desktop for Windows
    echo 下载地址：https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

:: 检查 Docker 是否运行
docker ps > nul 2>&1
if errorlevel 1 (
    echo [错误] Docker 未启动，请先启动 Docker Desktop
    pause
    exit /b 1
)

echo [1/4] 加载后端镜像...
docker load -i sanitary-backend.tar
if errorlevel 1 ( echo [错误] 后端镜像加载失败 & pause & exit /b 1 )

echo [2/4] 加载前端镜像...
docker load -i sanitary-frontend.tar
if errorlevel 1 ( echo [错误] 前端镜像加载失败 & pause & exit /b 1 )

echo [3/4] 启动所有服务...
docker compose up -d
if errorlevel 1 ( echo [错误] 服务启动失败 & pause & exit /b 1 )

echo [4/4] 等待服务就绪（约30秒）...
timeout /t 30 /nobreak > nul

echo.
echo ================================================
echo   启动完成！
echo   访问地址：http://localhost
echo   账号密码：admin / admin123
echo ================================================
echo.
start http://localhost
pause
