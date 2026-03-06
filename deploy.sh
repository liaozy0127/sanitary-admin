#!/bin/bash
# 卫浴管理系统 - 一键启动脚本

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

log()  { echo -e "[$(date '+%H:%M:%S')] $1"; }
info() { log "${BLUE}ℹ  $1${NC}"; }
ok()   { log "${GREEN}✅ $1${NC}"; }
warn() { log "${YELLOW}⚠  $1${NC}"; }
err()  { log "${RED}❌ $1${NC}"; exit 1; }

# 检查依赖
command -v docker &>/dev/null || err "未安装 Docker，请先安装 Docker Desktop"
docker info &>/dev/null       || err "Docker 未启动，请先启动 Docker Desktop"

ACTION="${1:-start}"

case "$ACTION" in
  start)
    info "启动卫浴管理系统..."
    info "第1步：拉取基础镜像..."
    docker compose pull mysql redis 2>/dev/null || true

    info "第2步：构建应用镜像（首次较慢，约3-5分钟）..."
    docker compose build --parallel

    info "第3步：启动所有服务..."
    docker compose up -d

    info "等待服务就绪..."
    sleep 5

    # 等待后端健康检查
    MAX_WAIT=120
    WAITED=0
    while [ $WAITED -lt $MAX_WAIT ]; do
      if docker compose ps backend | grep -q "healthy"; then
        break
      fi
      echo -n "."
      sleep 3
      WAITED=$((WAITED + 3))
    done
    echo ""

    ok "所有服务已启动！"
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  🚀 卫浴管理系统启动成功              ${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo -e "  前端界面: ${BLUE}http://localhost:80${NC}"
    echo -e "  后端接口: ${BLUE}http://localhost:8080${NC}"
    echo -e "  默认账号: ${YELLOW}admin / admin123${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    docker compose ps
    ;;

  stop)
    info "停止所有服务..."
    docker compose down
    ok "服务已停止"
    ;;

  restart)
    "$0" stop
    "$0" start
    ;;

  logs)
    SERVICE="${2:-}"
    if [ -n "$SERVICE" ]; then
      docker compose logs -f "$SERVICE"
    else
      docker compose logs -f
    fi
    ;;

  status)
    docker compose ps
    ;;

  clean)
    warn "将删除所有容器和数据卷（数据库数据会丢失）"
    read -p "确认删除？(y/N) " confirm
    if [[ "$confirm" =~ ^[Yy]$ ]]; then
      docker compose down -v
      ok "已清理所有容器和数据"
    fi
    ;;

  rebuild)
    info "重新构建并启动..."
    docker compose down
    docker compose build --no-cache --parallel
    docker compose up -d
    ok "重新构建完成"
    ;;

  *)
    echo "用法: $0 [start|stop|restart|logs|status|clean|rebuild]"
    echo ""
    echo "  start    启动所有服务（默认）"
    echo "  stop     停止所有服务"
    echo "  restart  重启所有服务"
    echo "  logs     查看日志（可指定服务: $0 logs backend）"
    echo "  status   查看服务状态"
    echo "  clean    清理所有容器和数据"
    echo "  rebuild  重新构建镜像并启动"
    ;;
esac
