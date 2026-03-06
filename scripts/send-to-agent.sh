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
    echo "用法: $0 <task-id> <message>"
    echo ""
    echo "参数："
    echo "  task-id   目标任务的 ID"
    echo "  message   要发送给 Agent 的消息"
    echo ""
    echo "示例："
    echo "  $0 task-001 '请优先完成登录接口的单元测试'"
    echo "  $0 task-002 '注意：数据库字段名用下划线命名'"
    exit 1
}

# 参数检查
if [ $# -lt 2 ]; then
    echo -e "${RED}错误：参数不足${NC}"
    usage
fi

TASK_ID="$1"
MESSAGE="$2"

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
LOG_FILE=$(jq -r '.logFile' "$TASK_FILE")

# 检查任务状态
if [ "$STATUS" != "running" ]; then
    echo -e "${YELLOW}警告：任务 '$TASK_ID' 的状态为 '$STATUS'，不是 running${NC}"
    echo "只能向运行中的 Agent 发送消息"
    exit 1
fi

# 确认进程存活
if ! kill -0 "$PID" 2>/dev/null; then
    echo -e "${RED}错误：任务 '$TASK_ID' 的进程 (PID: $PID) 已不存在${NC}"
    echo "请运行 ./scripts/monitor-agents.sh 更新状态"
    exit 1
fi

log "向 Agent (PID: $PID) 发送消息..."
log "消息内容: $MESSAGE"

# claude CLI 以 --print 模式启动时是单次执行，无法接收 stdin 追加消息。
# 此处将追加消息写入任务日志，并尝试通过 SIGUSR1 通知进程（如进程支持信号处理）。
# 对于完整的交互式 claude 会话，可改用 FIFO 或 tmux send-keys。

MESSAGES_DIR="$PROJECT_ROOT/tasks/messages"
mkdir -p "$MESSAGES_DIR"
MSG_FILE="$MESSAGES_DIR/${TASK_ID}.msg"

SENT_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
cat >> "$MSG_FILE" << EOF
---
sentAt: $SENT_AT
message: $MESSAGE
EOF

# 追加到日志
echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ORCHESTRATOR MESSAGE] $MESSAGE" >> "$LOG_FILE"

# 尝试发送 SIGUSR1 信号通知进程（如 Agent 支持）
if kill -USR1 "$PID" 2>/dev/null; then
    log "${GREEN}信号已发送至进程 $PID${NC}"
else
    log "${YELLOW}注意：进程不支持 SIGUSR1，消息已写入日志和消息队列${NC}"
fi

echo ""
echo -e "${GREEN}消息已发送成功${NC}"
echo "  目标任务:  $TASK_ID"
echo "  目标 PID:  $PID"
echo "  消息文件:  $MSG_FILE"
echo "  任务日志:  $LOG_FILE"
echo ""
echo -e "提示：可运行 ${CYAN}tail -f $LOG_FILE${NC} 查看 Agent 输出"
