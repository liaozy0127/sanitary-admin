# 代码审查报告 - feat: menu management module

**审查时间：** 2026-03-06  
**审查文件：** SysMenu.java / SysMenuMapper.java / SysMenuService.java / SysMenuServiceImpl.java / MenuController.java / V4__add_menu_table.sql  
**综合评分：** 72 / 100

---

## 一、问题汇总

| # | 文件 | 问题描述 | 严重级别 |
|---|------|----------|---------|
| 1 | SysMenuServiceImpl.java | `getMenuTree()` 和 `getChildren()` 使用 `Stream.peek()` 来执行副作用（setChildren），这是反模式。`peek()` 设计用于调试，不保证在所有终止操作下执行；应改用 `map()` 并在 map 内部设置 children 后返回 | 🔴 高危 |
| 2 | SysMenuServiceImpl.java | 递归构建树时没有循环引用保护，若数据库存在 parent_id 自引用或环形引用，将导致无限递归和 StackOverflowError | 🔴 高危 |
| 3 | MenuController.java | `getById` 中 `Result.error(404, ...)` 使用业务码 404 表示资源不存在，但 HTTP 状态码仍返回 200。应考虑使用 `@ResponseStatus(HttpStatus.NOT_FOUND)` 或统一异常处理器返回正确 HTTP 状态码 | 🟡 中等 |
| 4 | MenuController.java | 缺少输入校验（`@Valid` / `@Validated`）。`add()` 和 `update()` 接收 `@RequestBody SysMenu` 但未做 Bean Validation，`menuName` 为必填但未校验 | 🟡 中等 |
| 5 | MenuController.java | `delete()` 接口没有检查该菜单是否存在子菜单，直接删除父菜单会导致子菜单变成"孤儿节点" | 🟡 中等 |
| 6 | SysMenu.java | 使用 `@Data` 在有自引用（`children`）字段时，Lombok 生成的 `toString()` / `hashCode()` 可能导致无限递归（`toString` 序列化时）；建议加 `@ToString(exclude="children")` 和 `@EqualsAndHashCode(exclude="children")` | 🟡 中等 |
| 7 | SysMenu.java | `menuType`、`status` 字段使用 `Integer` 表示枚举值，缺少枚举类定义或常量类，可读性差，维护困难 | 🟢 低 |
| 8 | V4__add_menu_table.sql | 初始化数据硬编码 `parent_id=1`，假设了第一条记录 id=1，但 AUTO_INCREMENT 在某些情况下不保证从 1 开始；建议使用子查询或分步插入 | 🟢 低 |
| 9 | SysMenuMapper.java | 文件本身无问题，但未提供自定义 XML mapper 支持复杂查询（如按权限过滤菜单），后续扩展需要时补充 | 🟢 低 |
| 10 | MenuController.java | 缺少权限注解（如 `@PreAuthorize`），所有接口对任何已登录用户开放，存在越权风险 | 🟡 中等 |

---

## 二、各维度评分

| 维度 | 得分 | 说明 |
|------|------|------|
| 代码规范 | 18/25 | 命名规范，但缺少 JavaDoc 注释，@Data 自引用风险 |
| 安全性 | 14/25 | 缺少权限注解、输入校验 |
| 性能 | 18/25 | 树形查询采用全量加载内存构建，对小数据量合理；大数据量建议加缓存 |
| 逻辑完整性 | 22/25 | peek 反模式 + 无限递归风险 + 删除无孤儿保护 |

---

## 三、修复建议优先级

### 🔴 必须修复（高危）
1. 将 `peek()` 替换为 `map()`，保证树构建行为正确
2. 在递归 `getChildren` 中加深度或访问集合保护，防止环形引用 StackOverflow

### 🟡 建议修复（中等）
3. `MenuController#getById` 返回正确 HTTP 404 状态码
4. `add()` / `update()` 添加 `@Valid` + Bean Validation 注解
5. `delete()` 前检查是否有子菜单
6. `SysMenu` 添加 `@ToString(exclude="children")` 和 `@EqualsAndHashCode(exclude="children")`
7. 添加接口权限控制注解

### 🟢 可选优化（低）
8. 枚举类型化 menuType / status
9. SQL 初始化数据使用更健壮的方式插入
