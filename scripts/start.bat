@echo off
chcp 65001 > nul
echo 🚀 启动 sanitary-admin...

docker --version > nul 2>&1
if errorlevel 1 (
    echo ❌ 未找到 docker，请先安装 Docker Desktop for Windows
    pause
    exit /b 1
)

cd /d %~dp0..

echo 📦 构建镜像...
docker compose build
if errorlevel 1 (
    echo ❌ 构建失败
    pause
    exit /b 1
)

echo ▶️  启动服务...
docker compose up -d

echo ⏳ 等待服务就绪（约60秒）...
timeout /t 60 /nobreak > nul

echo ✅ 启动完成！
echo    前端: http://localhost:80
echo    后端: http://localhost:8080
echo    账号: admin / admin123
pause