@echo off
chcp 65001 > nul
echo 🛑 停止 sanitary-admin...

docker --version > nul 2>&1
if errorlevel 1 (
    echo ❌ 未找到 docker，请先安装 Docker
    pause
    exit /b 1
)

cd /d %~dp0..

echo ⏹️  停止服务...
docker compose down

echo ✅ 停止完成！
pause