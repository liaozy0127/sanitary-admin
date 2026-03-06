#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TASKS_DIR="$PROJECT_ROOT/tasks"
LOGS_DIR="$PROJECT_ROOT/logs"
CLUSTER_CONFIG="$PROJECT_ROOT/config/cluster-config.json"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

usage() {
    echo "用法: $0 <task-id> <task-description> [agent-type]"
    echo ""
    echo "参数："
    echo "  task-id          任务唯一标识符（字母、数字、连字符）"
    echo "  task-description 任务描述"
    echo "  agent-type       Agent 类型（可选，默认: claude-sonnet）"
    echo "                   可选值: claude-sonnet | claude-haiku | claude-qwen"
    echo ""
    echo "示例："
    echo "  $0 task-001 '实现用户登录功能' claude-sonnet"
    echo "  $0 task-002 '修复按钮样式 bug' claude-haiku"
    exit 1
}

# 参数检查
if [ $# -lt 2 ]; then
    echo -e "${RED}错误：参数不足${NC}"
    usage
fi

TASK_ID="$1"
TASK_DESC="$2"
AGENT_TYPE="${3:-claude-sonnet}"

# 验证 agent-type
case "$AGENT_TYPE" in
    claude-sonnet|claude-haiku|claude-qwen) ;;
    *)
        echo -e "${RED}错误：无效的 agent-type '$AGENT_TYPE'${NC}"
        echo "可选值: claude-sonnet | claude-haiku | claude-qwen"
        exit 1
        ;;
esac

# 检查任务是否已存在
TASK_FILE="$TASKS_DIR/${TASK_ID}.json"
if [ -f "$TASK_FILE" ]; then
    EXISTING_STATUS=$(jq -r '.status' "$TASK_FILE" 2>/dev/null || echo "unknown")
    if [ "$EXISTING_STATUS" = "running" ]; then
        echo -e "${RED}错误：任务 '$TASK_ID' 已在运行中${NC}"
        exit 1
    fi
fi

# 读取集群配置
WORKTREE_BASE_DIR=$(jq -r '.worktreeBaseDir' "$CLUSTER_CONFIG" 2>/dev/null || echo "../agent-cluster-worktrees")
MAX_CONCURRENT=$(jq -r '.maxConcurrentAgents' "$CLUSTER_CONFIG" 2>/dev/null || echo "4")

# 检查并发数限制
RUNNING_COUNT=$(ls "$TASKS_DIR"/*.json 2>/dev/null | xargs -I{} jq -r '.status' {} 2>/dev/null | grep -c "running" || echo "0")
if [ "$RUNNING_COUNT" -ge "$MAX_CONCURRENT" ]; then
    echo -e "${RED}错误：已达到最大并发 Agent 数量 ($MAX_CONCURRENT)${NC}"
    echo "请等待现有 Agent 完成或使用 stop-agent.sh 停止某个 Agent"
    exit 1
fi

# 解析 worktree 路径
if [[ "$WORKTREE_BASE_DIR" == ../* ]]; then
    WORKTREE_BASE_ABS="$(cd "$PROJECT_ROOT" && cd "$WORKTREE_BASE_DIR" 2>/dev/null || echo "$PROJECT_ROOT/$WORKTREE_BASE_DIR")"
    # 如果目录不存在则创建
    WORKTREE_BASE_ABS="$(cd "$PROJECT_ROOT" && realpath "$WORKTREE_BASE_DIR" 2>/dev/null || echo "$(dirname "$PROJECT_ROOT")/agent-cluster-worktrees")"
else
    WORKTREE_BASE_ABS="$WORKTREE_BASE_DIR"
fi

WORKTREE_PATH="$WORKTREE_BASE_ABS/$TASK_ID"
BRANCH_NAME="agent/$TASK_ID"
LOG_FILE="$LOGS_DIR/${TASK_ID}.log"

log "${GREEN}正在启动 Agent...${NC}"
log "任务 ID:   $TASK_ID"
log "描述:      $TASK_DESC"
log "Agent 类型: $AGENT_TYPE"
log "Worktree:  $WORKTREE_PATH"
log "日志文件:  $LOG_FILE"

# 创建 worktree 基目录
mkdir -p "$WORKTREE_BASE_ABS"

# 创建 git worktree
cd "$PROJECT_ROOT"
if git worktree list | grep -q "$BRANCH_NAME"; then
    echo -e "${YELLOW}警告：分支 '$BRANCH_NAME' 的 worktree 已存在，将复用${NC}"
else
    log "创建 git worktree: $WORKTREE_PATH (分支: $BRANCH_NAME)"
    git worktree add "$WORKTREE_PATH" -b "$BRANCH_NAME" 2>&1 | tee -a "$LOG_FILE"
fi

# 根据 agent-type 选择模型
case "$AGENT_TYPE" in
    claude-sonnet)
        MODEL_FLAG="--model claude-sonnet-4-5"
        ;;
    claude-haiku)
        MODEL_FLAG="--model claude-haiku-4-5"
        ;;
    claude-qwen)
        # qwen 通过 openrouter 或自定义配置使用，此处用 sonnet 作为 fallback
        MODEL_FLAG="--model claude-sonnet-4-5"
        ;;
esac

# 将任务描述写入临时文件，避免特殊字符转义问题
PROMPT_FILE=$(mktemp /tmp/agent-prompt-XXXXXX.txt)
echo "$TASK_DESC" > "$PROMPT_FILE"

# 使用 tmux 启动（提供完整伪终端，解决 BashTool pre-flight 卡死问题）
TMUX_SESSION="agent-${TASK_ID}"
log "在 tmux 会话中启动 claude CLI（session: $TMUX_SESSION）..."
tmux new-session -d -s "$TMUX_SESSION" \
    -c "$WORKTREE_PATH" \
    "claude $MODEL_FLAG --dangerously-skip-permissions --print \"\$(cat $PROMPT_FILE)\" 2>&1 | tee -a '$LOG_FILE'; rm -f '$PROMPT_FILE'"
AGENT_PID=$(tmux list-panes -t "$TMUX_SESSION" -F "#{pane_pid}" 2>/dev/null | head -1)

log "${GREEN}Agent 已启动，PID: $AGENT_PID${NC}"

# 记录任务信息到 JSON
STARTED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
cat > "$TASK_FILE" << EOF
{
  "id": "$TASK_ID",
  "description": "$TASK_DESC",
  "agent": "$AGENT_TYPE",
  "worktree": "$WORKTREE_PATH",
  "branch": "$BRANCH_NAME",
  "logFile": "$LOG_FILE",
  "startedAt": "$STARTED_AT",
  "status": "running",
  "pid": $AGENT_PID
}
EOF

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Agent 启动成功！${NC}"
echo -e "${GREEN}========================================${NC}"
echo "  任务 ID:    $TASK_ID"
echo "  PID:        $AGENT_PID"
echo "  日志:       tail -f $LOG_FILE"
echo "  监控:       ./scripts/monitor-agents.sh"
echo "  列表:       ./scripts/list-agents.sh"
echo -e "${GREEN}========================================${NC}"
