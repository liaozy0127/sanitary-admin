#!/bin/bash
# run-pipeline.sh - 方案B 流水线启动器
# 用法: ./scripts/run-pipeline.sh <pipeline-name> <feature-description>
# 示例: ./scripts/run-pipeline.sh feat-menu "实现菜单管理模块"
#
# 本脚本生成流水线配置，主 Agent 读取后启动 Orchestrator Sub-Agent 全自动执行

set -e
PIPELINE_NAME="$1"
FEATURE_DESC="$2"

if [ -z "$PIPELINE_NAME" ] || [ -z "$FEATURE_DESC" ]; then
  echo "用法: $0 <pipeline-name> <feature-description>"
  exit 1
fi

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$PROJECT_ROOT/tasks" "$PROJECT_ROOT/logs"

cat > "$PROJECT_ROOT/tasks/pipeline-${PIPELINE_NAME}.json" << JSONEOF
{
  "pipelineName": "$PIPELINE_NAME",
  "featureDescription": "$FEATURE_DESC",
  "projectRoot": "$PROJECT_ROOT",
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "status": "pending"
}
JSONEOF

echo "✅ 配置已生成: $PROJECT_ROOT/tasks/pipeline-${PIPELINE_NAME}.json"
echo ""
echo "📋 请复制以下内容发给主 Agent 启动流水线："
echo "---"
echo "请启动流水线：读取 $PROJECT_ROOT/tasks/pipeline-${PIPELINE_NAME}.json，用 orchestrator 模式全自动执行开发→审查→测试→修复→推送"
