#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TASKS_DIR="$PROJECT_ROOT/tasks"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

usage() {
    echo "用法: $0 <task-id>"
    echo ""
    echo "参数："
    echo "  task-id   要停止的任务 ID"
    echo ""
    echo "示例："
    echo "  $0 task-001"
    exit 1
}

# 参数检查
if [ $# -lt 1 ]; then
    echo -e "${RED}错误：请提供任务 ID${NC}"
    usage
fi

TASK_ID="$1"

# 检查 jq
if ! command -v jq &>/dev/null; then
    echo -e "${RED}错误：需要安装 jq。请运行: brew install jq${NC}"
    exit 1
fi

# 读取任务文件
TASK_FILE="$TASKS_DIR/${TASK_ID}.json"
if [ ! -f "$TASK_FILE" ]; then
    echo -e "${RED}错误：找不到任务 '$TASK_ID' 的记录${NC}"
    echo "请用 ./scripts/list-agents.sh 查看所有任务"
    exit 1
fi

STATUS=$(jq -r '.status' "$TASK_FILE")
PID=$(jq -r '.pid' "$TASK_FILE")

log "任务 ID: $TASK_ID"
log "当前状态: $STATUS"
log "进程 PID: $PID"

# 检查状态
if [ "$STATUS" = "stopped" ]; then
    echo -e "${YELLOW}任务 '$TASK_ID' 已经是 stopped 状态${NC}"
    exit 0
fi

if [ "$STATUS" = "completed" ]; then
    echo -e "${YELLOW}任务 '$TASK_ID' 已完成，无需停止${NC}"
    exit 0
fi

# 尝试终止进程
if kill -0 "$PID" 2>/dev/null; then
    log "发送 SIGTERM 至进程 $PID..."
    kill -TERM "$PID" 2>/dev/null || true

    # 等待最多 5 秒
    for i in {1..5}; do
        if ! kill -0 "$PID" 2>/dev/null; then
            break
        fi
        sleep 1
    done

    # 如果还在运行，强制终止
    if kill -0 "$PID" 2>/dev/null; then
        log "${YELLOW}进程未响应 SIGTERM，发送 SIGKILL...${NC}"
        kill -9 "$PID" 2>/dev/null || true
        sleep 1
    fi

    if kill -0 "$PID" 2>/dev/null; then
        echo -e "${RED}错误：无法终止进程 $PID${NC}"
        exit 1
    else
        log "${GREEN}进程 $PID 已成功终止${NC}"
    fi
else
    log "${YELLOW}进程 $PID 已不存在（可能已自然结束）${NC}"
fi

# 更新任务状态
STOPPED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
TMP_FILE=$(mktemp)
jq --arg status "stopped" --arg stoppedAt "$STOPPED_AT" \
   '.status = $status | .stoppedAt = $stoppedAt' \
   "$TASK_FILE" > "$TMP_FILE" && mv "$TMP_FILE" "$TASK_FILE"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Agent 已成功停止${NC}"
echo -e "${GREEN}========================================${NC}"
echo "  任务 ID:  $TASK_ID"
echo "  停止时间: $STOPPED_AT"
echo "  任务文件: $TASK_FILE"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "提示：运行 ./scripts/cleanup.sh 清理已停止任务的 worktree"
