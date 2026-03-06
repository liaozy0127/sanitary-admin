# 方案B：OpenClaw sessions_spawn 多 Agent 调度架构说明

## 1. 背景

原方案（方案A）使用 `tmux + claude CLI` 驱动多 Agent，存在以下问题：

- 中文 prompt 通过 shell 展开时容易导致 claude CLI 卡死（BashTool pre-flight 问题）
- tmux 会话管理复杂，难以感知 Agent 完成状态
- 所有 Agent 使用同一模型，无法针对不同角色做最优选型
- 主进程需要轮询日志文件来判断进度

方案B 切换为 **OpenClaw 原生 `sessions_spawn`**，彻底解决上述问题。

---

## 2. 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      用户 / Feishu / CLI                        │
└────────────────────────────┬────────────────────────────────────┘
                             │ 任务请求
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│               主控 Agent（OpenClaw Main Session）                │
│               模型：anthropic/claude-4.5-sonnet                  │
│               职责：意图理解、任务拆解、流水线调度                │
└──────┬──────────┬──────────┬──────────┬───────────────────────-─┘
       │          │          │          │  sessions_spawn
       ▼          ▼          ▼          ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│  开发    │ │  审查    │ │  测试    │ │  修复    │
│  Agent   │ │  Agent   │ │  Agent   │ │  Agent   │
│          │ │          │ │          │ │          │
│ qwen3-   │ │ claude-  │ │ claude-  │ │ qwen3-   │
│ coder-   │ │ 4.5-     │ │ 4.5-     │ │ coder-   │
│ plus     │ │ sonnet   │ │ haiku    │ │ plus     │
└──────────┘ └──────────┘ └──────────┘ └──────────┘
       │          │          │          │
       └──────────┴──────────┴──────────┘
                             │ 结果自动推送（push-based）
                             ▼
                      主控 Agent 汇总
                             │
                             ▼
                    git commit & 通知用户
```

### 流水线阶段

```
Stage 1        Stage 2        Stage 3        Stage 4        Stage 5
开发 ──────▶  审查 ──────▶  测试 ──────▶  修复 ──────▶  部署
(Qwen3)      (Sonnet)      (Haiku)       (Qwen3)       (Sonnet)
```

---

## 3. 各阶段模型选择理由

| 阶段 | 角色 | 模型 | 理由 |
|------|------|------|------|
| Stage 1 | 开发 Agent | `anthropic/qwen3-coder-plus` | 代码生成专项优化，对 Java/Spring Boot 理解深，输出稳定 |
| Stage 2 | 审查 Agent | `anthropic/claude-4.5-sonnet` | 深度推理能力强，能发现逻辑漏洞和安全问题，适合高质量 review |
| Stage 3 | 测试 Agent | `anthropic/claude-4.5-haiku` | 测试用例重复度高、结构简单，Haiku 速度最快、成本最低 |
| Stage 4 | 修复 Agent | `anthropic/qwen3-coder-plus` | 修复与开发同类任务，保持模型一致性，理解 Qwen3 生成的代码 |
| Stage 5 | 主控 Agent | `anthropic/claude-4.5-sonnet` | 意图理解和任务调度需要强推理，Sonnet 是调度层的最优解 |

---

## 4. sessions_spawn 调用方式说明

### 4.1 基本调用形式

在 OpenClaw 主 Agent 中，通过 `subagents` 工具触发 Sub-Agent：

```
# 调度开发 Agent
subagents(action=spawn, 
  task="你是一个专业的 Java 后端开发工程师。
        项目：sanitary-admin（Spring Boot 3.2.x + MyBatis-Plus）
        任务：实现部门管理模块，包括：
        1. SysDept 实体类（继承 BaseEntity）
        2. SysDeptMapper（继承 BaseMapper）
        3. SysDeptService + SysDeptServiceImpl
        4. SysDeptController（RESTful API）
        完成后输出：[DEV_COMPLETE] + 修改的文件列表",
  model="anthropic/qwen3-coder-plus"
)
```

### 4.2 与 agent-roles.json 结合使用

主控 Agent 读取 `scripts/agent-roles.json`，动态构建每个阶段的 prompt：

```javascript
// 伪代码：主控 Agent 调度逻辑
const roles = require('./agent-roles.json');
const pipeline = roles.pipeline;

for (const stage of pipeline) {
  const role = roles.roles[stage.role];
  const task = `${role.promptTemplate}\n\n当前任务：${taskDescription}`;
  
  // sessions_spawn 触发 Sub-Agent
  await spawnSubAgent({
    task: task,
    model: role.model,
  });
  
  // Sub-Agent 完成后自动 push 结果，无需轮询
}
```

### 4.3 结果感知（push-based）

方案B 的核心优势：**Sub-Agent 完成时自动将结果推送给父 Agent**，父 Agent 无需轮询。

```
# 方案A（轮询模式）
while ! grep -q "\[AGENT DONE\]" "$LOG_FILE"; do
    sleep 5   # 每5秒轮询一次，浪费资源
done

# 方案B（push 模式）
# 主 Agent spawn 后立刻释放，Sub-Agent 完成后自动通知
# 主 Agent 在此期间可以处理其他用户请求
```

---

## 5. 与方案A（claude CLI + tmux）的详细对比

| 维度 | 方案A（tmux + claude CLI） | 方案B（sessions_spawn） |
|------|--------------------------|------------------------|
| **中文 Prompt** | ❌ shell 展开易卡死，需用临时文件 workaround | ✅ 原生支持，无转义问题 |
| **多模型支持** | ⚠️ 需手动传 `--model` 参数，qwen3 需要 fallback | ✅ 每个 Agent 独立配置 model 字段 |
| **完成通知** | ❌ 轮询日志文件（grep `[AGENT DONE]`） | ✅ push-based，自动回调 |
| **主线程阻塞** | ❌ 等待 Agent 完成时主线程被占用 | ✅ spawn 后立即释放，主线程保持响应 |
| **并发控制** | ⚠️ 手动维护 `tasks/*.json` 计数 | ✅ OpenClaw 框架自动管理 |
| **错误处理** | ❌ tmux 会话崩溃难以感知 | ✅ Sub-Agent 异常自动上报父 Agent |
| **日志管理** | ⚠️ 需手动创建 `logs/` 目录和文件 | ✅ 框架统一管理 |
| **部署复杂度** | ❌ 依赖 tmux、jq、claude CLI 多个工具 | ✅ 仅依赖 OpenClaw |
| **可移植性** | ❌ macOS/Linux 差异，Windows 不支持 | ✅ 跨平台 |
| **调试难度** | ❌ 需 attach tmux 会话查看 | ✅ 结构化日志，可直接查看 |

### 方案A 的遗留问题及修复记录

1. **中文 Prompt 卡死问题**：`$(cat file)` 在 tmux shell 中展开多字节字符时触发 BashTool pre-flight 超时
   - 修复：改用 `cat file | claude --print -`（stdin 管道），完全绕过 shell 展开
   
2. **qwen3 模型不支持**：`--model` 参数仅支持 claude 系列，qwen3 需要 openrouter 中转
   - 修复：方案B 通过 sessions_spawn 的 model 字段原生支持

3. **worktree 路径竞争**：多 Agent 同时操作时 git worktree 可能冲突
   - 修复：方案B 每个 Sub-Agent 独立运行，主控 Agent 串行调度流水线

---

## 6. 迁移指南

### 从方案A迁移到方案B

1. 停止所有运行中的 tmux Agent 会话：
   ```bash
   tmux list-sessions | grep agent- | awk -F: '{print $1}' | xargs -I{} tmux kill-session -t {}
   ```

2. 清理临时任务文件（可选）：
   ```bash
   rm -f tasks/*.json logs/*.log
   ```

3. 在 OpenClaw 主 Agent 中使用新的调度方式：
   ```
   # 不再需要调用 launch-agent.sh
   # 直接在 OpenClaw 对话中描述任务，主控 Agent 自动调度
   ```

4. `launch-agent.sh` 保留作为历史参考，不再主动调用。

---

## 7. 文件结构

```
sanitary-admin/
├── scripts/
│   ├── launch-agent.sh      # 方案A（已废弃，保留参考）
│   ├── pipeline.md          # 本文件：方案B架构说明
│   └── agent-roles.json     # 各 Agent 角色和模型配置
├── docs/
│   └── multi-agent-architecture.md  # 完整技术文章
└── config/
    └── cluster-config.json  # 历史集群配置（方案A使用）
```
