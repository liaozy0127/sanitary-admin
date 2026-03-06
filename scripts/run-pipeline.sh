#!/bin/bash
# run-pipeline.sh - 方案B 流水线启动器（循环版）
# 流程：开发 → [审查 → 测试 → 修复] 循环，直到无高危/中等问题且测试全通过
# 用法: ./scripts/run-pipeline.sh <pipeline-name> <feature-description>

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
  "status": "pending",
  "maxFixIterations": 3,
  "passThreshold": {
    "minScore": 85,
    "allowedSeverities": ["低"]
  }
}
JSONEOF

echo "✅ 配置已生成: $PROJECT_ROOT/tasks/pipeline-${PIPELINE_NAME}.json"
echo "📋 请告诉主 Agent 启动 Orchestrator 流水线（循环模式）"
