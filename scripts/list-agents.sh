#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TASKS_DIR="$PROJECT_ROOT/tasks"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# 检查 jq
if ! command -v jq &>/dev/null; then
    echo -e "${RED}错误：需要安装 jq。请运行: brew install jq${NC}"
    exit 1
fi

echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║               AI Agent 集群 - 任务列表                              ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════════════╝${NC}"
echo ""

TASK_FILES=$(ls "$TASKS_DIR"/*.json 2>/dev/null || true)

if [ -z "$TASK_FILES" ]; then
    echo -e "  ${YELLOW}暂无任务记录${NC}"
    echo ""
    echo "  使用 ./scripts/launch-agent.sh <task-id> <description> 启动新任务"
    echo ""
    exit 0
fi

# 打印表头
printf "  ${BOLD}%-3s  %-20s  %-12s  %-16s  %-8s  %s${NC}\n" \
    "#" "任务 ID" "状态" "Agent 类型" "PID" "描述"
echo "  ──────────────────────────────────────────────────────────────────────"

IDX=0
for TASK_FILE in $TASK_FILES; do
    IDX=$((IDX + 1))
    TASK_ID=$(jq -r '.id' "$TASK_FILE" 2>/dev/null || echo "unknown")
    STATUS=$(jq -r '.status' "$TASK_FILE" 2>/dev/null || echo "unknown")
    AGENT=$(jq -r '.agent' "$TASK_FILE" 2>/dev/null || echo "unknown")
    PID=$(jq -r '.pid' "$TASK_FILE" 2>/dev/null || echo "0")
    DESC=$(jq -r '.description' "$TASK_FILE" 2>/dev/null || echo "")
    STARTED_AT=$(jq -r '.startedAt' "$TASK_FILE" 2>/dev/null || echo "")

    # 截断描述
    DESC_SHORT="${DESC:0:35}"
    if [ ${#DESC} -gt 35 ]; then
        DESC_SHORT="${DESC_SHORT}..."
    fi

    # 状态颜色 + 实时进程检测
    ALIVE_MARKER=""
    case "$STATUS" in
        running)
            if kill -0 "$PID" 2>/dev/null; then
                STATUS_COLOR="${GREEN}"
                ALIVE_MARKER=" ●"
            else
                STATUS_COLOR="${YELLOW}"
                ALIVE_MARKER=" ✗"
            fi
            ;;
        completed) STATUS_COLOR="${BLUE}" ;;
        failed)    STATUS_COLOR="${RED}" ;;
        stopped)   STATUS_COLOR="${YELLOW}" ;;
        *)         STATUS_COLOR="${NC}" ;;
    esac

    printf "  %-3s  ${STATUS_COLOR}%-20s  %-12s${NC}  %-16s  %-8s  %s\n" \
        "$IDX" "$TASK_ID" "${STATUS}${ALIVE_MARKER}" "$AGENT" "$PID" "$DESC_SHORT"
done

echo "  ──────────────────────────────────────────────────────────────────────"
echo ""

# 图例
echo -e "  图例:  ${GREEN}● running（进程存活）${NC}  ${YELLOW}✗ running（进程已结束）${NC}  ${BLUE}completed${NC}  ${RED}failed${NC}  ${YELLOW}stopped${NC}"
echo ""
echo "  常用命令："
echo "    启动 Agent:  ./scripts/launch-agent.sh <id> <描述> [agent-type]"
echo "    监控状态:    ./scripts/monitor-agents.sh"
echo "    发送消息:    ./scripts/send-to-agent.sh <id> <消息>"
echo "    停止 Agent:  ./scripts/stop-agent.sh <id>"
echo "    清理任务:    ./scripts/cleanup.sh"
echo ""
