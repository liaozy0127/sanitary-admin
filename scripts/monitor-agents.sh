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

log() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# 检查 jq 是否可用
if ! command -v jq &>/dev/null; then
    echo -e "${RED}错误：需要安装 jq。请运行: brew install jq${NC}"
    exit 1
fi

echo ""
echo -e "${BOLD}${CYAN}========================================${NC}"
echo -e "${BOLD}${CYAN}       Agent 集群状态监控报告${NC}"
echo -e "${BOLD}${CYAN}========================================${NC}"
echo -e "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# 查找所有任务文件
TASK_FILES=$(ls "$TASKS_DIR"/*.json 2>/dev/null || true)

if [ -z "$TASK_FILES" ]; then
    echo -e "${YELLOW}当前没有任何任务记录${NC}"
    echo ""
    exit 0
fi

# 统计计数
TOTAL=0
RUNNING=0
COMPLETED=0
FAILED=0
STOPPED=0

# 打印表头
printf "${BOLD}%-20s %-12s %-16s %-20s %-10s${NC}\n" "任务 ID" "状态" "Agent 类型" "启动时间" "PID"
echo "─────────────────────────────────────────────────────────────────────────────────"

for TASK_FILE in $TASK_FILES; do
    TASK_ID=$(jq -r '.id' "$TASK_FILE" 2>/dev/null || echo "unknown")
    STATUS=$(jq -r '.status' "$TASK_FILE" 2>/dev/null || echo "unknown")
    AGENT=$(jq -r '.agent' "$TASK_FILE" 2>/dev/null || echo "unknown")
    STARTED_AT=$(jq -r '.startedAt' "$TASK_FILE" 2>/dev/null || echo "unknown")
    PID=$(jq -r '.pid' "$TASK_FILE" 2>/dev/null || echo "0")
    WORKTREE=$(jq -r '.worktree' "$TASK_FILE" 2>/dev/null || echo "")

    TOTAL=$((TOTAL + 1))

    # 对 running 状态的任务做进程检查
    if [ "$STATUS" = "running" ]; then
        if kill -0 "$PID" 2>/dev/null; then
            # 进程仍在运行
            RUNNING=$((RUNNING + 1))
            STATUS_COLOR="${GREEN}"
            STATUS_DISPLAY="running"
        else
            # 进程已死，标记为 failed
            log "${YELLOW}任务 '$TASK_ID' 的进程 (PID: $PID) 已结束，标记为 failed${NC}"
            FAILED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
            # 更新任务状态
            TMP_FILE=$(mktemp)
            jq --arg status "failed" --arg failedAt "$FAILED_AT" \
               '.status = $status | .failedAt = $failedAt' \
               "$TASK_FILE" > "$TMP_FILE" && mv "$TMP_FILE" "$TASK_FILE"
            STATUS="failed"
            FAILED=$((FAILED + 1))
            STATUS_COLOR="${RED}"
            STATUS_DISPLAY="failed"
        fi
    else
        case "$STATUS" in
            completed) COMPLETED=$((COMPLETED + 1)); STATUS_COLOR="${BLUE}"; STATUS_DISPLAY="completed" ;;
            failed)    FAILED=$((FAILED + 1));    STATUS_COLOR="${RED}";  STATUS_DISPLAY="failed" ;;
            stopped)   STOPPED=$((STOPPED + 1));  STATUS_COLOR="${YELLOW}"; STATUS_DISPLAY="stopped" ;;
            *)         STATUS_COLOR="${NC}"; STATUS_DISPLAY="$STATUS" ;;
        esac
    fi

    # 格式化启动时间（截短）
    STARTED_SHORT=$(echo "$STARTED_AT" | cut -c1-16 | tr 'T' ' ')

    printf "${STATUS_COLOR}%-20s %-12s %-16s %-20s %-10s${NC}\n" \
        "$TASK_ID" "$STATUS_DISPLAY" "$AGENT" "$STARTED_SHORT" "$PID"
done

echo "─────────────────────────────────────────────────────────────────────────────────"
echo ""

# 打印汇总
echo -e "${BOLD}汇总：${NC}"
echo -e "  总计:      $TOTAL"
echo -e "  ${GREEN}运行中:    $RUNNING${NC}"
echo -e "  ${BLUE}已完成:    $COMPLETED${NC}"
echo -e "  ${RED}失败:      $FAILED${NC}"
echo -e "  ${YELLOW}已停止:    $STOPPED${NC}"
echo ""

# 对 running 任务显示最近 3 条 git 提交
if [ "$RUNNING" -gt 0 ]; then
    echo -e "${BOLD}${CYAN}--- 运行中任务的最近提交 ---${NC}"
    for TASK_FILE in $TASK_FILES; do
        STATUS=$(jq -r '.status' "$TASK_FILE" 2>/dev/null || echo "unknown")
        TASK_ID=$(jq -r '.id' "$TASK_FILE" 2>/dev/null || echo "unknown")
        WORKTREE=$(jq -r '.worktree' "$TASK_FILE" 2>/dev/null || echo "")

        if [ "$STATUS" = "running" ] && [ -d "$WORKTREE" ]; then
            echo -e "${GREEN}[$TASK_ID]${NC} worktree: $WORKTREE"
            COMMITS=$(git -C "$WORKTREE" log --oneline -3 2>/dev/null || echo "  (暂无提交)")
            if [ -z "$COMMITS" ]; then
                echo "  (暂无提交)"
            else
                echo "$COMMITS" | while read -r line; do echo "  $line"; done
            fi
            echo ""
        fi
    done
fi
