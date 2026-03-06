#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TASKS_DIR="$PROJECT_ROOT/tasks"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1"; }

usage() {
    echo "用法: $0 <task-id> <message>"
    echo "示例: $0 backend-login '先做 AuthController，暂停 UserController'"
    exit 1
}

[ $# -lt 2 ] && { echo -e "${RED}错误：参数不足${NC}"; usage; }

TASK_ID="$1"
MESSAGE="$2"

TASK_FILE="$TASKS_DIR/${TASK_ID}.json"
[ ! -f "$TASK_FILE" ] && { echo -e "${RED}错误：找不到任务 '$TASK_ID'${NC}"; exit 1; }

TMUX_SESSION="agent-${TASK_ID}"

# 检查 tmux 会话是否存在
if ! tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    echo -e "${RED}错误：tmux 会话 '$TMUX_SESSION' 不存在，Agent 可能已结束${NC}"
    exit 1
fi

log "向 tmux 会话 '$TMUX_SESSION' 发送消息..."
# 用 tmux send-keys 直接向 Agent 的终端输入
tmux send-keys -t "$TMUX_SESSION" "$MESSAGE" Enter

echo -e "${GREEN}✓ 消息已发送到 Agent${NC}"
echo "  会话: $TMUX_SESSION"
echo "  消息: $MESSAGE"
echo "  监控: tmux attach -t $TMUX_SESSION"
