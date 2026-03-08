# sanitary-admin 开发规范

## 🔴 开发前必读（强制）

**在对本项目进行任何开发或修改之前，必须先阅读以下文档：**

1. [`docs/REQUIREMENTS.md`](./docs/REQUIREMENTS.md) — 需求文档，了解所有功能模块和业务规则
2. [`docs/DESIGN.md`](./docs/DESIGN.md) — 设计文档，了解数据库结构、接口设计、关键流程和已知坑

**每次修改后，如果涉及功能变更或新增，需同步更新上述文档。**

---

## 项目信息

- **项目路径**：/Users/admin/IdeaProjects/sanitary-admin
- **后端**：Spring Boot 3 + MyBatis-Plus + MySQL，Docker 部署，端口 8080
- **前端**：Vue 3 + Element Plus
- **MySQL 容器**：sanitary-mysql，root密码 root123，数据库 sanitary_admin，宿主机端口 3307
- **Docker 命令路径**：/Applications/Docker.app/Contents/Resources/bin/docker
- **登录接口**：POST http://localhost:8080/api/auth/login，{"username":"admin","password":"admin123"}

---

## 🔴 Multi-Agent 开发规范（必读）

### 正确做法

用 `sessions_spawn(runtime="subagent")` 启动子 Agent，通过 OpenClaw 原生工具执行任务：

```
sessions_spawn(
  task="...",
  model="anthropic/qwen3-coder-plus",  // 开发任务用千问
  runtime="subagent",
  mode="run"
)
```

子 Agent 直接使用 read/write/exec/edit 工具操作文件和 Docker，**不需要也不应该** 启动 Claude Code。

### ❌ 禁止的做法

- 禁止用 `exec` 启动 `claude --dangerously-skip-permissions` 进程
- 禁止用 Claude Code CLI 代替 sessions_spawn 做开发
- Claude Code 是交互式终端工具，难以自动化，绕一道程序且容易失控

### 模型分工

| 阶段 | 模型 | 说明 |
|------|------|------|
| 开发/修复 | `anthropic/qwen3-coder-plus` | Sub-Agent |
| 审查/测试 | `anthropic/claude-4.5-sonnet` | Sub-Agent |
| 主 Agent 调度 | `anthropic/claude-4.5-sonnet` | 主线程 |

---

## 编译 & 构建

**⚠️ 本地 Lombok 与 Java 21 不兼容，禁止在宿主机跑 `mvn compile`**

只能在 Docker 容器内编译：

```bash
DOCKER=/Applications/Docker.app/Contents/Resources/bin/docker
cd /Users/admin/IdeaProjects/sanitary-admin
$DOCKER compose stop backend && $DOCKER compose rm -f backend && $DOCKER rmi sanitary-admin-backend -f 2>/dev/null
$DOCKER compose build --no-cache backend
$DOCKER compose up -d backend
```

---

## 常见坑

1. **Java switch expression**：块内必须用 `yield` 不能用 `return`
2. **selectOne 多条报错**：查询加 `.last("LIMIT 1")` 防止 MyBatis-Plus 报 "Expected one result"
3. **MySQL DROP COLUMN IF EXISTS 不支持**：需要先查 information_schema 再决定是否 DROP
4. **文件上传大小**：application.yml 已配置 `spring.servlet.multipart.max-file-size: 100MB`
5. **xls 文件头损坏**：老系统 xls 文件头有问题，需先用 Python xlrd/openpyxl 转成 xlsx 再导入
6. **禁止执行** `openclaw gateway stop`（会导致网关崩溃）

---

## 数据导入

老系统文件目录：`/Users/admin/IdeaProjects/sanitary-admin/old-system-file/`
xlsx 转换缓存：`/tmp/import-xlsx/`（含收货单22批分割文件 receipts-split/）

导入顺序：客户 → 工艺 → 物料 → 初始库存（对账单）→ 收货单（mode=history）→ 排产单（mode=history）

---

## Git

```bash
cd /Users/admin/IdeaProjects/sanitary-admin
git add -A && git commit -m "feat/fix: ..." && git push origin main
```
