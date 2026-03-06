#!/bin/bash
set -e
echo "🚀 启动 sanitary-admin..."

# 检测 Docker
if ! command -v docker &> /dev/null; then
  echo "❌ 未找到 docker，请先安装 Docker Desktop"
  exit 1
fi

# Mac Docker Desktop 特殊路径
if [[ "$OSTYPE" == "darwin"* ]]; then
  export PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"
  export DOCKER_HOST="unix://${HOME}/.docker/run/docker.sock"
fi

cd "$(dirname "$0")/.."

echo "📦 拉取/构建镜像..."
docker compose pull --ignore-pull-failures 2>/dev/null || true
docker compose build

echo "▶️  启动服务..."
docker compose up -d

echo "⏳ 等待服务就绪（约60秒）..."
sleep 60

echo "✅ 启动完成！"
echo "   前端: http://localhost:80"
echo "   后端: http://localhost:8080"
echo "   账号: admin / admin123"