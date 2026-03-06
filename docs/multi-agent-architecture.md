# 多模型 Agent 协同开发架构：从 0 到生产环境

> 作者：王大将军 | 日期：2026-03-06 | 项目：sanitary-admin

---

## 1. 背景与痛点

### 传统单 LLM 开发辅助的局限

大多数团队接入 AI 辅助开发的方式都是：打开 ChatGPT / Copilot，粘贴代码，让它帮你改。这种方式的问题在于：

- **单轮对话，无记忆**：每次对话都要重新解释上下文
- **没有质量门控**：AI 生成的代码直接用，没有审查环节
- **一个模型干所有事**：写代码、审查、测试全靠同一个模型，而不同任务对模型能力的要求完全不同
- **人工监督成本高**：开发者要手动 copy-paste，效率低

### 多 Agent 协同能解决什么问题

多 Agent 架构的核心思想：**让不同的 AI Agent 扮演不同的角色，像一个真实团队一样协作**。

```
传统方式：开发者 ←→ 一个 LLM（什么都干）

多 Agent：
  开发者
    ↓ 下达需求
  主控 Agent（项目经理）
    ├── 开发 Agent（程序员）→ 代码专项模型
    ├── 审查 Agent（Code Reviewer）→ 深度推理模型
    ├── 测试 Agent（QA）→ 快速执行模型
    └── 修复 Agent（Bug Fixer）→ 代码修改模型
```

每个角色专注自己的职责，用最合适的模型，主控 Agent 协调整个流水线。

---

## 2. 整体架构设计

### 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     开发者（你）                              │
│                  通过飞书 / 终端对话                          │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│               主控 Agent（OpenClaw）                          │
│               模型：强推理模型（如 Sonnet）                    │
│  职责：理解需求、任务拆解、Sub-Agent 调度、结果汇总             │
└───┬───────────┬───────────┬───────────┬──────────────────────┘
    │           │           │           │
    ▼           ▼           ▼           ▼
┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐
│开发    │  │审查    │  │测试    │  │修复    │
│Agent  │  │Agent  │  │Agent  │  │Agent  │
│代码模型│  │推理模型│  │快速模型│  │代码模型│
└───┬───┘  └───┬───┘  └───┬───┘  └───┬───┘
    │           │           │           │
    ▼           ▼           ▼           ▼
┌─────────────────────────────────────────┐
│            共享代码仓库（Git）             │
│         /IdeaProjects/sanitary-admin    │
└─────────────────────────────────────────┘
```

### 主控 Agent vs Sub-Agent 分工

| 角色 | 职责 | 特点 |
|------|------|------|
| 主控 Agent | 接收用户需求、拆解任务、调度 Sub-Agent、汇报结果 | **必须始终保持响应**，不做耗时操作 |
| Sub-Agent | 执行具体任务（写代码、审查、测试、修复） | 后台运行，完成后自动推送结果 |

**关键原则：主控 Agent 不能被阻塞。** 任何超过 10 秒的任务都要交给 Sub-Agent。

---

## 3. 核心组件介绍

### OpenClaw：主控层

[OpenClaw](https://docs.openclaw.ai) 是整套架构的神经中枢，提供：
- 多渠道接入（飞书、Telegram、Discord 等）
- Agent 生命周期管理
- Sub-Agent 调度（`sessions_spawn`）
- 工具调用（exec、read、write、web_search 等）
- 记忆系统（MEMORY.md、memory/*.md）

安装：
```bash
npm install -g openclaw
openclaw onboard
```

### sessions_spawn：Agent 调度原语

这是方案 B 的核心 API，主控 Agent 通过它启动 Sub-Agent：

```python
sessions_spawn(
    task="你的任务描述...",
    model="your-code-model",  # 指定模型
    mode="run",       # run=一次性任务，session=持久会话
    runtime="subagent",
    cleanup="keep"    # 保留会话历史
)
```

Sub-Agent 完成后会**自动推送结果**回主控 Agent，无需轮询。

监控命令：
```python
subagents(action="list")   # 查看所有运行中的 Sub-Agent
subagents(action="kill", target="run-id")  # 强制终止
subagents(action="steer", target="run-id", message="...")  # 引导方向
```

---

## 4. 方案演进：从 claude CLI 到 sessions_spawn

### 方案 A：claude CLI + tmux（快速起步）

最初参考的架构方案，适合快速验证：

```bash
# launch-agent.sh 核心逻辑
PROMPT_FILE=$(mktemp /tmp/agent-prompt-XXXXXX.txt)
printf '%s' "$TASK_DESC" > "$PROMPT_FILE"

tmux new-session -d -s "agent-${TASK_ID}" \
  -c "$WORKTREE_PATH" \
  "cat '$PROMPT_FILE' | claude --model claude-sonnet-4-5 \
   --dangerously-skip-permissions --print - 2>&1 | tee -a '$LOG_FILE'"
```

**优点：** 简单直接，5 分钟能跑起来  
**缺点：**
- 只支持 `claude-*` 模型，其他模型用不了
- tmux 会话不稳定，可能被系统清理
- 监控靠轮询日志文件，不优雅
- 中文 prompt 会触发 shell 转义 bug（`$(cat file)` 方式）

**中文 prompt 卡死 bug 的修复：**
```bash
# ❌ 错误方式（中文字符在 tmux 里展开会截断）
claude --print "$(cat $PROMPT_FILE)"

# ✅ 正确方式（stdin 管道，完全避免 shell 转义）
cat "$PROMPT_FILE" | claude --print -
```

### 方案 B：sessions_spawn（生产级方案）

用 OpenClaw 原生能力替代 claude CLI：

```python
# 主控 Agent 直接调用
sessions_spawn(
    task=f"""你是开发工程师，请完成以下任务：
    {task_description}
    完成后输出：TASK_COMPLETE
    """,
    model="your-code-model",
    mode="run",
    runtime="subagent"
)
# 主控 Agent 立刻返回，Sub-Agent 在后台运行
# 完成后自动推送结果，无需轮询
```

### 两种方案对比

| 维度 | 方案 A（claude CLI） | 方案 B（sessions_spawn） |
|------|------------------|----------------------|
| 支持模型 | 只支持 claude-* | 所有公司配置的模型 |
| 启动方式 | shell 脚本 + tmux | OpenClaw 原生 API |
| 稳定性 | tmux 可能被清理 | OpenClaw 托管，稳定 |
| 状态追踪 | 手动轮询日志文件 | `subagents(list)` 实时查 |
| 结果获取 | 轮询日志 | 自动推送回主 Agent |
| 主 Agent 响应 | ❌ 等待时被阻塞 | ✅ 立刻释放，随时响应 |
| 中断/重试 | 手动 kill tmux | `subagents(kill/steer)` |
| 并发控制 | 手写计数逻辑 | OpenClaw 内置（默认 8 个）|

### 4.5 Orchestrator 模式：真正的全自动流水线

方案 B 存在一个问题：每个阶段的 Sub-Agent 完成后，需要主 Agent 人工触发下一阶段。这对于复杂的多阶段流水线来说，仍然有改进空间。

真正的解法是 **Orchestrator 模式**：把整条流水线封装进一个 Sub-Agent，由它内部顺序执行所有阶段。

```
❌ 接力模式（需要主 Agent 中转）：
主 Agent → Stage1完成 → 主 Agent → Stage2完成 → 主 Agent → Stage3...

✅ Orchestrator 模式（全自动）：
主 Agent → Orchestrator（内部：Stage1 → Stage2 → Stage3 → Stage4）→ 主 Agent（最终报告）
```

**Orchestrator 模式的优势：**

| 优势 | 说明 |
|------|------|
| 主 Agent 全程空闲 | 随时响应用户的新需求，不会被流水线占用 |
| 无需人工触发 | 每个 Sub-Agent 完成后自动执行下一阶段 |
| 上下文传递 | 审查结果可以直接传给修复 Agent，测试失败可以自动触发修复 |
| 统一汇报 | 只需在流水线结束时汇报一次最终结果 |

**实现方式：**

Orchestrator 本质上是一个"总控 Sub-Agent"，它的 prompt 包含了整个流水线的逻辑：

```python
sessions_spawn(
    task="""
    你是流水线 Orchestrator，请顺序执行以下阶段：
    
    ## Stage 1: 开发
    创建部门管理模块的所有文件...
    
    ## Stage 2: 审查
    审查 Stage 1 生成的代码，输出问题列表...
    
    ## Stage 3: 修复
    根据 Stage 2 的审查结果修复代码...
    
    ## Stage 4: 测试
    执行接口测试，验证所有修复生效...
    
    完成后输出：PIPELINE_COMPLETE + 最终报告
    """,
    model="strong-reasoning-model",  # 需要 Orchestrator 做复杂调度
    mode="run",
    runtime="subagent"
)
```

**适用场景：**

- ✅ 适合：标准化的多阶段流水线（开发→审查→修复→测试）
- ✅ 适合：需要阶段间传递上下文的场景（审查结果→修复）
- ❌ 不适合：需要人工决策的流水线（比如审查后需要人确认再修）
- ❌ 不适合：高度定制化的任务（每次流水线都不同）

---

## 5. 实战演示：部门管理功能全流程

### 背景

项目：`sanitary-admin` 卫浴管理系统（Spring Boot 3 + Vue3）  
任务：新增「部门管理」模块，走完完整的 开发→审查→测试→修复→部署 流水线

### Stage 1：开发 Agent 写代码

**Prompt 示例：**
```
你是一个后端开发工程师，请严格按照以下要求完成任务，不要询问确认，直接执行。
工作目录：/Users/admin/IdeaProjects/agent-cluster-worktrees/feat-dept

[任务] 新增"部门管理"模块：
1. 创建 entity/SysDept.java（含 @TableName、@TableLogic、@TableField(fill=...)）
2. 创建 mapper/SysDeptMapper.java（继承 BaseMapper）
3. 创建 service/SysDeptService.java（含 listDepts 方法）
4. 创建 service/impl/SysDeptServiceImpl.java（LambdaQueryWrapper 实现）
5. 创建 controller/DeptController.java（CRUD 接口 /api/depts）
6. 创建 db/migration/V2__add_dept_table.sql

完成后执行：git add -A && git commit -m "feat: add department management module"
```

**结果：** 约 70 秒，5 个文件全部创建完成，自动提交。

### Stage 2：审查 Agent 发现 3 个严重问题

**Prompt 示例：**
```
你是一个资深代码审查工程师。请审查以下文件：
- entity/SysDept.java
- controller/DeptController.java
- service/impl/SysDeptServiceImpl.java

审查维度：代码规范、安全性、性能、逻辑完整性
输出：逐文件问题列表 + 严重级别 + 改进建议 + 总体评分（0-100）
```

**审查结果（评分：42/100）：**

| # | 问题 | 严重级别 |
|---|------|---------|
| 1 | `getById` 返回 null 时直接 success(null)，前端误判为成功 | 🔴 严重 |
| 2 | POST/PUT 无 `@Validated` 参数校验，空 deptName 可入库 | 🔴 严重 |
| 3 | 删除前未校验是否有子部门，可破坏树结构 | 🔴 严重 |

### Stage 3：测试 Agent 执行接口测试

**Prompt 示例：**
```
你是测试工程师。后端在 http://localhost:8080 运行。
请对部门管理 API 进行接口测试：
1. 先登录获取 Token（POST /api/auth/login）
2. 测试 CRUD 的每个接口，用 curl 执行
3. 记录每个接口的实际返回值，标注 PASS/FAIL
4. 输出测试总结
```

**初始测试：5/5 PASS（基础 CRUD 正常）**

### Stage 4：修复 Agent 修复所有问题

根据审查报告，修复 3 个严重问题：

```java
// 修复1：getById null 检查
@GetMapping("/{id}")
public Result<SysDept> getById(@PathVariable Long id) {
    SysDept dept = sysDeptService.getById(id);
    if (dept == null) {
        return Result.error(404, "部门不存在");
    }
    return Result.success(dept);
}

// 修复2：SysDept.java 加参数校验
@NotBlank(message = "部门名称不能为空")
private String deptName;

// Controller 加 @Validated
public Result<Void> add(@Validated @RequestBody SysDept dept) { ... }

// 修复3：删除前校验子部门
@Override
public void removeDeptById(Long id) {
    long childCount = this.count(
        new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id)
    );
    if (childCount > 0) {
        throw new RuntimeException("该部门下存在子部门，无法删除");
    }
    this.removeById(id);
}
```

### Stage 5：修复后测试（8/8 PASS）

| # | 测试用例 | 结果 |
|---|---------|------|
| 1 | GET /api/depts 查询列表 | ✅ PASS |
| 2a | POST /api/depts 正常新增 | ✅ PASS |
| 2b | POST /api/depts 空 deptName 校验拦截 | ✅ PASS |
| 3a | GET /api/depts/5 查已存在部门 | ✅ PASS |
| 3b | GET /api/depts/99999 不存在返回 404 | ✅ PASS |
| 4 | PUT /api/depts/5 修改名称 | ✅ PASS |
| 5a | DELETE 有子部门（应拒绝） | ✅ PASS |
| 5b | DELETE 无子部门（正常删除） | ✅ PASS |

---

## 6. 关键经验与踩坑记录

### 坑1：中文 prompt 卡死问题

**现象：** 启动 Agent 时，中文任务导致 Agent 永久卡死，没有任何输出。

**原因：** shell 展开多字节中文字符时可能被截断，模型收到空 prompt 后进入交互等待模式。

**修复：**
```bash
# ❌ 会卡死
claude --print "$(cat $PROMPT_FILE)"

# ✅ 用 stdin 管道
cat "$PROMPT_FILE" | claude --print -
```

### 坑2：Spring Boot 3.2.x + MyBatis-Plus 不兼容

**现象：** 启动报错 `Invalid value type for attribute 'factoryBeanObjectType': java.lang.String`

**原因：** Spring Boot 3.2.x 修改了 BeanDefinition 处理方式，MyBatis-Plus 3.5.5 未适配。

**修复：** 降级 Spring Boot 至 3.1.9，或升级 MyBatis-Plus 至 3.5.9+
```xml
<!-- pom.xml -->
<parent>
    <version>3.1.9</version>  <!-- 从 3.2.3 降级 -->
</parent>
<mybatis-plus.version>3.5.7</mybatis-plus.version>
```

### 坑3：主 Agent 被长任务阻塞

**现象：** 主 Agent 在等 mvn compile（2 分钟），这期间用户发的所有消息都堆积排队，体验极差。

**解决方案：** 长任务一律用 `sessions_spawn` 交给 Sub-Agent：
```python
# ❌ 错误：主 Agent 直接跑耗时任务
exec("mvn package -DskipTests")  # 阻塞 2 分钟

# ✅ 正确：spawn Sub-Agent，主 Agent 立刻释放
sessions_spawn(task="执行 mvn 编译并重启后端...")
# 立刻回复用户："已启动，完成后通知你"
```

### 坑4：Java 25 破坏 Lombok 注解处理

**现象：** Homebrew 默认 Java 25 编译时，Lombok 的 `@Data`、`@RequiredArgsConstructor` 等注解不生效，生成的 class 缺少 getter/setter。

**修复：** 固定使用 Java 21
```bash
export JAVA_HOME="/Users/admin/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

### 坑5：BCrypt hash 问题

**现象：** 数据库里存的 hash 和 Spring Security 的 `BCryptPasswordEncoder` 对不上，登录一直 401。

**根因：** 网上找的"万能 hash"并不可靠，不同版本的 BCrypt 实现可能有差异。

**最佳做法：** 用 Python 在本机实时生成：
```python
import bcrypt
hash = bcrypt.hashpw('admin123'.encode(), bcrypt.gensalt(10)).decode()
# 再更新到数据库
```

---

## 7. 进阶：模型分工最佳实践

### 模型选择的核心决策维度

选择合适的模型需要考虑以下四个关键维度：

| 维度 | 说明 | 如何评估 |
|------|------|---------|
| **代码能力** | 代码生成、语法理解、调试能力 | 用简单编程任务测试，看生成质量 |
| **推理深度** | 复杂逻辑分析、架构设计、问题诊断 | 给复杂业务场景，看推理链是否完整 |
| **响应速度** | 单次请求耗时、并发能力 | 对比相同任务的响应时间 |
| **语言支持** | 中文理解、中文写作流畅度 | 让模型用中文解释技术概念 |

### 任务-模型选择决策矩阵

根据任务类型和模型特性的匹配度：

| 任务类型 | 代码能力 | 推理深度 | 响应速度 | 中文支持 | 推荐模型类型 |
|---------|:-------:|:-------:|:-------:|:-------:|-------------|
| 功能代码开发 | ⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐ | 代码专项模型 |
| 架构设计 | ⭐⭐ | ⭐⭐⭐ | ⭐ | ⭐⭐ | 强推理模型 |
| 代码审查 | ⭐⭐ | ⭐⭐⭐ | ⭐ | ⭐⭐ | 强推理模型 |
| 安全分析 | ⭐⭐ | ⭐⭐⭐ | ⭐ | ⭐⭐ | 强推理模型 |
| 单元测试生成 | ⭐⭐⭐ | ⭐ | ⭐⭐ | ⭐ | 代码专项模型 |
| 接口测试执行 | ⭐ | ⭐ | ⭐⭐⭐ | ⭐ | 快速执行模型 |
| Bug 修复 | ⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐ | 代码专项模型 |
| 中文文档写作 | ⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐⭐ | 中文优化模型 |
| 快速问答 | ⭐ | ⭐ | ⭐⭐⭐ | ⭐⭐ | 快速执行模型 |
| 复杂调试 | ⭐⭐ | ⭐⭐⭐ | ⭐ | ⭐⭐ | 强推理模型 |

### 决策树：如何选择模型

```
你的任务是什么？
│
├── 生成/修改代码？
│   ├── 简单重复（CRUD、模板代码）→ 快速模型 或 代码模型
│   └── 复杂实现（算法、架构）→ 强推理模型
│
├── 需要深度分析？
│   ├── 代码审查 / 安全审计 → 强推理模型
│   ├── 架构设计 / 技术方案 → 强推理模型
│   └── Bug 定位 / 根因分析 → 强推理模型
│
├── 简单执行类任务？
│   ├── 接口测试 / 格式转换 → 快速模型
│   └── 简单问答 / 信息检索 → 快速模型
│
└── 中文写作？
    ├── 技术文档 / 报告 → 中文优化模型
    └── 注释 / README → 任意模型
```

### 本项目实际使用的模型示例

以下是本项目的实际模型配置，**你可以替换为你公司可用的同类模型**：

| Agent 角色 | 本项目使用 | 选择理由 | 可替换为 |
|-----------|-----------|---------|---------|
| 主控 Agent | claude-4.5-sonnet | 强推理，理解复杂意图 | 任意强推理模型 |
| 开发 Agent | qwen3-coder-plus | 代码专项，Java/TS 精准 | 其他代码模型 |
| 审查 Agent | claude-4.5-sonnet | 深度理解业务逻辑 | 任意强推理模型 |
| 测试 Agent | claude-4.5-haiku | 任务明确，速度快 3-5 倍 | 其他快速模型 |
| 修复 Agent | qwen3-coder-plus | 本质是代码任务 | 其他代码模型 |
| 文档写作 Agent | glm-5 | 中文技术文档流畅自然 | 其他中文优化模型 |

> **注意**：本文本身就是由 GLM-5 文档写作 Agent 生成的，作为多模型协同架构中"文档写作"角色的实际验证。

### 如何确认你的模型白名单

不同公司/环境的模型白名单不同，在使用前建议先验证模型是否可用：

**方法一：直接测试**

```python
# 在 OpenClaw 中执行
sessions_spawn(
    task="输出 'Hello, 我可用了！'",
    model="your-model-id",
    mode="run"
)
```

如果成功返回结果，说明该模型在你的环境中可用。

**方法二：查看配置文件**

检查 `openclaw.json` 中的 `models.providers` 配置：

```json
{
  "models": {
    "providers": {
      "anthropic": {
        "models": [
          {"id": "claude-4.5-sonnet", "name": "Sonnet"},
          {"id": "claude-4.5-haiku", "name": "Haiku"}
        ]
      }
    }
  }
}
```

**方法三：询问管理员**

如果你使用的是公司内部的 AI 平台，直接询问 IT 或 AI 团队：
- 哪些模型可用？
- 每个模型的配额和限制？
- 是否需要特殊的 API key？

### 成本与质量的平衡原则

**核心原则：** 用最便宜能完成任务的模型，贵的模型留给真正需要推理的场景。

```
任务复杂度评估：
  明确、重复、结构化  →  快速模型（省成本）
  代码生成、代码修改  →  代码模型（专项精准）
  推理、判断、分析    →  强推理模型（高质量）
  中文语境           →  中文优化模型（更自然）
```

**成本对比参考（相对值）：**

| 模型类型 | 相对成本 | 适用场景 |
|---------|:-------:|---------|
| 快速模型 | 1x | 简单任务 |
| 代码模型 | 2-3x | 代码开发 |
| 强推理模型 | 5-10x | 复杂分析 |
| 中文优化模型 | 2-4x | 中文写作 |

---

## 8. 快速上手指南

### 环境准备

**前置要求：**
- macOS / Linux（Windows 需要 WSL2）
- Node.js 18+
- Git

**安装 OpenClaw：**
```bash
npm install -g openclaw
openclaw onboard
# 按引导配置公司 API endpoint 和飞书 Bot
```

**配置公司模型（openclaw.json）：**
```json
{
  "models": {
    "providers": {
      "anthropic": {
        "baseUrl": "https://your-company-endpoint.com",
        "api": "anthropic-messages",
        "models": [
          {"id": "your-sonnet-model", "name": "Sonnet"},
          {"id": "your-haiku-model", "name": "Haiku"}
        ]
      }
    }
  }
}
```

### 核心步骤

1. **准备项目仓库**
   ```bash
   mkdir my-project && cd my-project
   git init
   ```

2. **启动第一个 Sub-Agent**
   
   在 OpenClaw 对话中输入：
   ```
   帮我 spawn 一个 Sub-Agent，用代码模型，任务是在当前目录创建一个 Spring Boot 项目骨架
   ```

3. **监控和接收结果**
   
   Sub-Agent 完成后会自动推送结果，无需等待。

### 常见问题 FAQ

**Q: Sub-Agent 卡住了怎么办？**
```python
subagents(action="list")  # 先查状态
subagents(action="kill", target="run-id")  # 强制终止
```

**Q: 如何让 Sub-Agent 的结果传给下一个 Sub-Agent？**

通过共享文件系统：
- Sub-Agent A 把结果写入 `/tmp/review-result.md`
- Sub-Agent B 的 prompt 里说"读取 `/tmp/review-result.md`"

**Q: 主 Agent 能同时跑几个 Sub-Agent？**

默认 8 个并发，可在 openclaw.json 中调整。

**Q: Sub-Agent 能访问本地文件系统吗？**

可以，Sub-Agent 拥有和主 Agent 相同的工具权限。

---

## 附录：完整项目结构

```
sanitary-admin/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/sanitary/admin/
│   │   ├── controller/         # REST 控制器
│   │   ├── service/            # 业务逻辑
│   │   ├── mapper/             # MyBatis Mapper
│   │   └── entity/             # 数据实体
│   └── pom.xml                 # Spring Boot 3.1.9 + MyBatis-Plus 3.5.7
├── frontend/                   # Vue3 前端
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   ├── api/                # Axios 接口
│   │   └── stores/             # Pinia 状态
│   └── vite.config.js
├── scripts/
│   ├── launch-agent.sh         # 方案 A：claude CLI 启动器
│   ├── agent-roles.json        # 方案 B：角色模型配置
│   └── pipeline.md             # 方案 B：架构说明
├── docs/
│   └── multi-agent-architecture.md  # 本文章
├── logs/                       # Agent 运行日志
├── tasks/                      # 任务状态 JSON
├── docker-compose.yml          # MySQL + Redis
└── PIPELINE_REPORT.md          # 流水线运行报告

GitHub: https://github.com/liaozy0127/sanitary-admin
```

---

*本文所有代码和架构均来自实际项目的生产实践，非理论推导。*

> 本文由 GLM-5 模型生成，作为多模型协同架构中"文档写作 Agent"角色的实际验证。
