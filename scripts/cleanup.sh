#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TASKS_DIR="$PROJECT_ROOT/tasks"
ARCHIVE_DIR="$TASKS_DIR/archive"
CLUSTER_CONFIG="$PROJECT_ROOT/config/cluster-config.json"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

usage() {
    echo "用法: $0 [--dry-run] [--force]"
    echo ""
    echo "选项："
    echo "  --dry-run   只预览要清理的内容，不实际执行"
    echo "  --force     跳过确认提示，直接清理"
    echo ""
    echo "清理对象：状态为 completed / stopped / failed 的任务"
    echo "操作内容："
    echo "  1. 删除对应的 git worktree"
    echo "  2. 归档任务 JSON 到 tasks/archive/"
    exit 1
}

DRY_RUN=false
FORCE=false

for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        --force)   FORCE=true ;;
        --help|-h) usage ;;
        *) echo -e "${RED}未知参数: $arg${NC}"; usage ;;
    esac
done

# 检查 jq
if ! command -v jq &>/dev/null; then
    echo -e "${RED}错误：需要安装 jq。请运行: brew install jq${NC}"
    exit 1
fi

echo ""
echo -e "${BOLD}${CYAN}======================================${NC}"
echo -e "${BOLD}${CYAN}       Agent 集群清理工具${NC}"
echo -e "${BOLD}${CYAN}======================================${NC}"
if $DRY_RUN; then
    echo -e "${YELLOW}[预览模式] 以下操作不会实际执行${NC}"
fi
echo ""

TASK_FILES=$(ls "$TASKS_DIR"/*.json 2>/dev/null || true)

if [ -z "$TASK_FILES" ]; then
    echo -e "${YELLOW}没有找到任何任务记录${NC}"
    exit 0
fi

# 收集需要清理的任务
TO_CLEAN=()
for TASK_FILE in $TASK_FILES; do
    STATUS=$(jq -r '.status' "$TASK_FILE" 2>/dev/null || echo "unknown")
    case "$STATUS" in
        completed|stopped|failed)
            TO_CLEAN+=("$TASK_FILE")
            ;;
        running)
            TASK_ID=$(jq -r '.id' "$TASK_FILE")
            echo -e "  ${GREEN}[跳过]${NC} $TASK_ID — 状态: running（运行中任务不清理）"
            ;;
    esac
done

if [ ${#TO_CLEAN[@]} -eq 0 ]; then
    echo ""
    echo -e "${GREEN}没有需要清理的任务${NC}"
    exit 0
fi

echo -e "发现 ${#TO_CLEAN[@]} 个可清理任务："
echo ""

for TASK_FILE in "${TO_CLEAN[@]}"; do
    TASK_ID=$(jq -r '.id' "$TASK_FILE")
    STATUS=$(jq -r '.status' "$TASK_FILE")
    WORKTREE=$(jq -r '.worktree' "$TASK_FILE")
    BRANCH=$(jq -r '.branch' "$TASK_FILE")

    case "$STATUS" in
        completed) STATUS_LABEL="${CYAN}completed${NC}" ;;
        failed)    STATUS_LABEL="${RED}failed${NC}" ;;
        stopped)   STATUS_LABEL="${YELLOW}stopped${NC}" ;;
        *)         STATUS_LABEL="${NC}$STATUS${NC}" ;;
    esac

    echo -e "  • $TASK_ID [${STATUS_LABEL}]"
    echo "      Worktree: $WORKTREE"
    echo "      Branch:   $BRANCH"
done

echo ""

# 确认
if ! $DRY_RUN && ! $FORCE; then
    read -rp "确认清理以上 ${#TO_CLEAN[@]} 个任务？[y/N] " CONFIRM
    if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
        echo "已取消"
        exit 0
    fi
fi

# 执行清理
mkdir -p "$ARCHIVE_DIR"
CLEANED=0
ERRORS=0

for TASK_FILE in "${TO_CLEAN[@]}"; do
    TASK_ID=$(jq -r '.id' "$TASK_FILE")
    WORKTREE=$(jq -r '.worktree' "$TASK_FILE")
    BRANCH=$(jq -r '.branch' "$TASK_FILE")

    log "清理任务: $TASK_ID"

    # 1. 删除 git worktree
    if [ -d "$WORKTREE" ]; then
        if $DRY_RUN; then
            echo "    [预览] git worktree remove --force $WORKTREE"
        else
            if git -C "$PROJECT_ROOT" worktree remove --force "$WORKTREE" 2>/dev/null; then
                log "  ${GREEN}✓ Worktree 已删除: $WORKTREE${NC}"
            else
                log "  ${YELLOW}⚠ 无法通过 git 删除 worktree，尝试手动删除...${NC}"
                rm -rf "$WORKTREE" 2>/dev/null || true
                git -C "$PROJECT_ROOT" worktree prune 2>/dev/null || true
            fi
        fi
    else
        log "  ${YELLOW}⚠ Worktree 目录不存在（已清理？）: $WORKTREE${NC}"
    fi

    # 2. 删除本地 branch（可选，失败不中断）
    if ! $DRY_RUN; then
        git -C "$PROJECT_ROOT" branch -D "$BRANCH" 2>/dev/null && \
            log "  ${GREEN}✓ 分支已删除: $BRANCH${NC}" || \
            log "  ${YELLOW}⚠ 分支不存在或已删除: $BRANCH${NC}"
    fi

    # 3. 归档任务 JSON
    if $DRY_RUN; then
        echo "    [预览] mv $TASK_FILE $ARCHIVE_DIR/"
    else
        ARCHIVED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
        TMP_FILE=$(mktemp)
        jq --arg archivedAt "$ARCHIVED_AT" '. + {archivedAt: $archivedAt}' \
           "$TASK_FILE" > "$TMP_FILE" && mv "$TMP_FILE" "$ARCHIVE_DIR/${TASK_ID}.json"
        rm -f "$TASK_FILE"
        log "  ${GREEN}✓ 任务已归档: $ARCHIVE_DIR/${TASK_ID}.json${NC}"
        CLEANED=$((CLEANED + 1))
    fi

    echo ""
done

if ! $DRY_RUN; then
    echo -e "${GREEN}======================================${NC}"
    echo -e "${GREEN}  清理完成：成功 $CLEANED 个，失败 $ERRORS 个${NC}"
    echo -e "${GREEN}======================================${NC}"
    echo "  归档目录: $ARCHIVE_DIR"
else
    echo -e "${YELLOW}[预览模式] 实际清理请去掉 --dry-run 参数${NC}"
fi
echo ""
