#!/bin/bash
# run-stage.sh - 按 pipeline 配置的模型动态切换并执行 Stage
# 用法：./scripts/run-stage.sh <pipeline-json> <stage-number>

set -e

PIPELINE_FILE="$1"
STAGE_NUM="$2"
SETTINGS_FILE="$HOME/.claude/settings.json"
SETTINGS_BACKUP="$HOME/.claude/settings.json.stage-bak"

if [ -z "$PIPELINE_FILE" ] || [ -z "$STAGE_NUM" ]; then
  echo "用法: $0 <pipeline-json> <stage-number>"
  exit 1
fi

# 从 pipeline 读取 model 和 prompt
MODEL=$(python3 -c "
import json, sys
with open('$PIPELINE_FILE') as f:
    d = json.load(f)
stages = d.get('stages', [])
for s in stages:
    if s.get('stage') == $STAGE_NUM:
        print(s.get('model', 'anthropic/claude-4.5-sonnet'))
        break
")

PROMPT=$(python3 -c "
import json, sys
with open('$PIPELINE_FILE') as f:
    d = json.load(f)
stages = d.get('stages', [])
for s in stages:
    if s.get('stage') == $STAGE_NUM:
        print(s.get('prompt', ''))
        break
")

# 提取纯 model id（去掉 provider 前缀）
MODEL_ID=$(echo "$MODEL" | sed 's|.*/||')

echo "🚀 Stage $STAGE_NUM 启动，模型：$MODEL ($MODEL_ID)"

# 备份 settings
cp "$SETTINGS_FILE" "$SETTINGS_BACKUP"

# 动态修改 settings.json 里的所有模型指向
python3 << EOF
import json

with open('$SETTINGS_FILE') as f:
    d = json.load(f)

env = d.get('env', {})
env['ANTHROPIC_MODEL'] = '$MODEL_ID'
env['ANTHROPIC_DEFAULT_SONNET_MODEL'] = '$MODEL_ID'
env['ANTHROPIC_DEFAULT_HAIKU_MODEL'] = '$MODEL_ID'
env['ANTHROPIC_SMALL_FAST_MODEL'] = '$MODEL_ID'
env['ANTHROPIC_DEFAULT_OPUS_MODEL'] = '$MODEL_ID'
d['env'] = env

with open('$SETTINGS_FILE', 'w') as f:
    json.dump(d, f, indent=2)

print(f"✅ 已切换所有模型到：$MODEL_ID")
EOF

# 执行 Stage（传入 prompt）
STAGE_OUTPUT=$(claude -p "$PROMPT" --dangerously-skip-permissions 2>&1)
EXIT_CODE=$?

# 恢复 settings
cp "$SETTINGS_BACKUP" "$SETTINGS_FILE"
echo "✅ settings.json 已恢复"

echo "$STAGE_OUTPUT"
exit $EXIT_CODE
