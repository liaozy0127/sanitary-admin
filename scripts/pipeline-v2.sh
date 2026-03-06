#!/bin/bash
# 用法: ./pipeline-v2.sh <pipeline-name> <feature-description>
# 示例: ./pipeline-v2.sh feat-role "实现角色管理模块，包含角色CRUD"
#
# 生成 tasks/pipeline-<name>.json，包含各阶段的 Agent 配置

PIPELINE_NAME="$1"
FEATURE_DESC="$2"

if [ -z "$PIPELINE_NAME" ] || [ -z "$FEATURE_DESC" ]; then
  echo "❌ 用法: $0 <pipeline-name> <feature-description>"
  echo "   示例: $0 feat-role \"实现角色管理模块，包含角色CRUD\""
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT="${SCRIPT_DIR}/../tasks/pipeline-${PIPELINE_NAME}.json"
mkdir -p "${SCRIPT_DIR}/../tasks"

CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

cat > "$OUTPUT" << JSONEOF
{
  "pipelineName": "${PIPELINE_NAME}",
  "featureDescription": "${FEATURE_DESC}",
  "createdAt": "${CREATED_AT}",
  "status": "pending",
  "stages": [
    {
      "stage": 1,
      "name": "开发",
      "role": "developer",
      "model": "anthropic/qwen3-coder-plus",
      "status": "pending",
      "prompt": "你是专业Java后端开发工程师。项目：sanitary-admin（Spring Boot 3.1.9 + MyBatis-Plus 3.5.7）。\n工作目录：/Users/admin/IdeaProjects/sanitary-admin\n\n任务：${FEATURE_DESC}\n\n完成后执行 git add -A && git commit，输出 STAGE_COMPLETE"
    },
    {
      "stage": 2,
      "name": "代码审查",
      "role": "reviewer",
      "model": "anthropic/claude-4.5-sonnet",
      "status": "pending",
      "prompt": "你是资深代码审查工程师。审查 /Users/admin/IdeaProjects/sanitary-admin 最新提交的代码。\n审查维度：规范、安全、性能、逻辑完整性。\n输出：问题列表（含严重级别）+ 评分（0-100）+ 改进建议。\n将报告写入 /Users/admin/IdeaProjects/sanitary-admin/logs/review-${PIPELINE_NAME}.md\n输出 STAGE_COMPLETE"
    },
    {
      "stage": 3,
      "name": "接口测试",
      "role": "tester",
      "model": "anthropic/claude-4.5-haiku",
      "status": "pending",
      "prompt": "你是测试工程师。后端在 http://localhost:8080。\n测试刚开发的功能，用 curl 执行每个接口测试，记录 PASS/FAIL。\n将结果写入 /Users/admin/IdeaProjects/sanitary-admin/logs/test-${PIPELINE_NAME}.md\n输出 STAGE_COMPLETE"
    },
    {
      "stage": 4,
      "name": "问题修复",
      "role": "fixer",
      "model": "anthropic/qwen3-coder-plus",
      "status": "pending",
      "prompt": "你是代码修复工程师。读取 /Users/admin/IdeaProjects/sanitary-admin/logs/review-${PIPELINE_NAME}.md 和 test-${PIPELINE_NAME}.md。\n修复所有严重和中等问题，重新编译验证通过后 git commit。\n输出 STAGE_COMPLETE"
    }
  ]
}
JSONEOF

echo "✅ 流水线配置已生成：$OUTPUT"
echo "📋 请告诉主 Agent：读取 $OUTPUT 并启动流水线"
