@echo off
chcp 65001 > nul
echo 停止卫浴管理系统...
docker compose down
echo 已停止。
pause
