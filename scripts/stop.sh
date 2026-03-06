#!/bin/bash
set -e
echo "🛑 停止 sanitary-admin..."

cd "$(dirname "$0")/.."

echo "⏹️  停止服务..."
docker compose down

echo "✅ 停止完成！"