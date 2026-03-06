# scripts/ — Agent 调度脚本说明

本目录包含两套 Agent 调度方案，适用于不同的运行环境。

---

## 方案A：`launch-agent.sh`（tmux + claude CLI）

**适用场景：** 本地开发环境，已安装 `tmux` 和 `claude` CLI。

**工作原理：**
1. 直接在 tmux session 中启动 `claude` 命令行
2. 将任务 prompt 传入，Claude 在 shell 环境中执行开发/测试等操作
3. 各阶段串行执行，通过检测 `STAGE_COMPLETE` 输出推进

**使用方式：**
```bash
./launch-agent.sh <pipeline-name> <feature-description>
# 示例：
./launch-agent.sh feat-role "实现角色管理模块，包含角色CRUD"
```

**依赖：**
- `tmux`（终端复用器）
- `claude` CLI（Anthropic 官方命令行工具）

---

## 方案B：`pipeline-v2.sh`（任务配置生成器 → sessions_spawn）

**适用场景：** 通过 OpenClaw 主 Agent 调度，使用 `sessions_spawn` 启动 Sub-Agent。

**工作原理：**
1. 脚本接收任务参数，生成标准化的 JSON 配置文件（`tasks/pipeline-<name>.json`）
2. **不直接执行任何 Agent**，只负责生成配置
3. 主 Agent 读取配置文件后，调用 `sessions_spawn` 依次启动各阶段的 Sub-Agent
4. Sub-Agent 完成后自动回报结果，主 Agent 推进到下一阶段

**使用方式：**
```bash
./pipeline-v2.sh <pipeline-name> <feature-description>
# 示例：
./pipeline-v2.sh feat-role "实现角色管理模块，包含角色CRUD"
```

生成后，告知主 Agent：
> "读取 `tasks/pipeline-<name>.json` 并启动流水线"

**生成的 JSON 结构：**
```json
{
  "pipelineName": "feat-role",
  "featureDescription": "...",
  "createdAt": "2026-03-06T07:47:00Z",
  "status": "pending",
  "stages": [
    { "stage": 1, "name": "开发",    "role": "developer", "model": "anthropic/qwen3-coder-plus", ... },
    { "stage": 2, "name": "代码审查", "role": "reviewer",  "model": "anthropic/claude-4.5-sonnet", ... },
    { "stage": 3, "name": "接口测试", "role": "tester",    "model": "anthropic/claude-4.5-haiku", ... },
    { "stage": 4, "name": "问题修复", "role": "fixer",     "model": "anthropic/qwen3-coder-plus", ... }
  ]
}
```

**优势：**
- 不依赖 tmux / claude CLI
- 主 Agent 可按需选择模型和并发策略
- 任务配置持久化，便于重试和审计

---

## 流水线阶段说明

| 阶段 | 角色 | 模型 | 职责 |
|------|------|------|------|
| 1 | developer | qwen3-coder-plus | 编写代码并提交 |
| 2 | reviewer  | claude-4.5-sonnet | 代码审查，输出报告 |
| 3 | tester    | claude-4.5-haiku  | 接口测试，记录结果 |
| 4 | fixer     | qwen3-coder-plus | 修复问题并重新提交 |
