# sanitary-admin 设计文档

> **重要提示（面向 Agent）**：在对本项目进行任何开发或修改之前，请先阅读本文档和 [REQUIREMENTS.md](./REQUIREMENTS.md)，了解系统整体设计，避免重复开发或破坏现有逻辑。

---

## 一、系统架构

### 1.1 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + Element Plus + Axios |
| 后端 | Spring Boot 3 + MyBatis-Plus + Spring Security |
| 数据库 | MySQL 8 |
| 缓存 | Redis |
| 认证 | JWT（无状态） |
| 部署 | Docker Compose |

### 1.2 部署结构

```
docker-compose.yml
├── sanitary-mysql    — MySQL 8，端口 3307（宿主机）
├── sanitary-redis    — Redis，内部使用
├── sanitary-backend  — Spring Boot，端口 8080
└── sanitary-frontend — Nginx 静态资源，端口 80
```

### 1.3 目录结构

```
sanitary-admin/
├── backend/                    # Spring Boot 后端
│   └── src/main/java/com/sanitary/admin/
│       ├── controller/         # REST Controller
│       ├── service/            # Service 接口
│       │   └── impl/           # Service 实现
│       ├── mapper/             # MyBatis-Plus Mapper
│       ├── entity/             # 数据库实体类
│       ├── security/           # JWT 认证
│       └── util/               # 工具类（单号生成等）
├── frontend/                   # Vue 3 前端
│   └── src/
│       ├── views/              # 页面组件
│       ├── api/                # Axios 接口封装
│       └── router/             # 路由配置
├── docs/                       # 文档目录
│   ├── REQUIREMENTS.md         # 需求文档（本文档配套）
│   └── DESIGN.md               # 设计文档（本文档）
├── old-system-file/            # 老系统历史数据（Excel）
├── CLAUDE.md                   # Agent 开发规范（必读）
└── docker-compose.yml
```

---

## 二、数据库设计

### 2.1 表关系概览

```
sys_user ──── sys_role ──── sys_menu
                             
customer ◄──── material         (物料属于客户)
customer ◄──── receipt          (收货单属于客户)
receipt  ◄──── receipt_item     (一单多明细)
customer ◄──── production       (排产单属于客户)
production ◄── production_item  (一单多明细)
customer ◄──── shipment         (发货单属于客户)
shipment ◄──── shipment_item    (一单多明细)
customer ◄──── rework           (返工单主表，✅已改造)
rework   ◄──── rework_item      (返工单明细，✅已建表)
customer ◄──── payment          (收款记录)
customer ◄──── statement        (对账单主表，✅已改造主从表)
statement ◄─── statement_item   (对账单明细，✅已建表)
material ◄──── inventory        (库存，三维唯一)
inventory ◄─── inventory_log    (库存流水)
```

### 2.2 各表详细设计

#### customer（客户表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| customer_code | VARCHAR(20) | 客户编码，唯一 |
| customer_name | VARCHAR(100) | 客户名称，唯一，NOT NULL |
| customer_type | VARCHAR(10) | 客户类型 |
| address | VARCHAR(200) | 地址 |
| contact_person | VARCHAR(50) | 联系人 |
| contact_phone | VARCHAR(20) | 联系电话 |
| salesperson | VARCHAR(50) | 业务员 |
| bank_name | VARCHAR(100) | 开户银行 |
| bank_account | VARCHAR(50) | 银行账号 |
| tax_no | VARCHAR(50) | 税号 |
| remark | VARCHAR(500) | 备注 |
| status | TINYINT | 状态：1启用 0禁用 |
| deleted | TINYINT | 逻辑删除：0正常 1删除 |
| create_time | DATETIME | 创建时间（自动填充）|
| update_time | DATETIME | 更新时间（自动填充）|

#### material（物料表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| material_code | VARCHAR(50) | 物料编码，唯一，NOT NULL |
| material_name | VARCHAR(200) | 物料名称，NOT NULL |
| spec | VARCHAR(200) | 型号规格 |
| customer_id | BIGINT | 所属客户 ID（重要！）|
| customer_name | VARCHAR(100) | 所属客户名称（冗余）|
| default_price | DECIMAL(10,4) | 默认单价 |
| unit | VARCHAR(20) | 计量单位 |
| status | TINYINT | 状态 |
| deleted | TINYINT | 逻辑删除 |

> ⚠️ **重要**：物料必须关联客户（customer_id），查询物料时须按 customer_id 过滤，否则同名物料会返回多条。

#### process（工艺表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| process_code | VARCHAR(20) | 工艺编码 |
| process_name | VARCHAR(100) | 工艺名称，唯一 |
| process_category | VARCHAR(50) | 工艺类别 |
| process_nature | VARCHAR(50) | 工艺性质 |
| thickness_req | VARCHAR(100) | 厚度要求 |
| default_quote | TINYINT | 是否默认报价 |
| priority_no | INT | 优先级 |
| status | TINYINT | 状态 |
| deleted | TINYINT | 逻辑删除 |

#### receipt（收货单主表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| receipt_no | VARCHAR(30) | 收货单号，唯一，格式 SH+年月+流水 |
| receipt_date | DATE | 收货日期 |
| customer_id | BIGINT | 客户 ID |
| customer_name | VARCHAR(100) | 客户名称（冗余）|
| remark | VARCHAR(500) | 备注 |
| status | TINYINT | 状态 |
| deleted | TINYINT | 逻辑删除 |

#### receipt_item（收货单明细表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| receipt_id | BIGINT | 所属收货单 ID，NOT NULL |
| receipt_no | VARCHAR(30) | 收货单号（冗余，方便查询）|
| material_id | BIGINT | 物料 ID |
| material_name | VARCHAR(200) | 物料名称，NOT NULL |
| material_code | VARCHAR(50) | 物料编码 |
| spec | VARCHAR(200) | 型号规格 |
| process_id | BIGINT | 工艺 ID |
| process_name | VARCHAR(100) | 工艺名称 |
| receipt_source | VARCHAR(50) | 收货来源（正常/返工/样品）|
| quantity | DECIMAL(12,2) | 收货数量 |
| shipped_qty | DECIMAL(12,2) | 发货数量 |
| unshipped_qty | DECIMAL(12,2) | 未发货数量 |
| planned_qty | DECIMAL(12,2) | 排产数量 |
| ware_housed_qty | DECIMAL(12,2) | 入库数量 |
| unware_housed_qty | DECIMAL(12,2) | 未入库数量 |
| unit_price | DECIMAL(10,4) | 单价 |
| amount | DECIMAL(12,2) | 金额（= 数量 × 单价）|
| customer_order_no | VARCHAR(100) | 客户单号 |
| detail_remark | VARCHAR(500) | 明细备注 |
| deleted | TINYINT | 逻辑删除 |

#### production（排产单主表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| production_no | VARCHAR(30) | 排产单号，唯一，格式 PC+年月+流水 |
| production_date | DATE | 排产日期 |
| customer_id | BIGINT | 客户 ID |
| customer_name | VARCHAR(100) | 客户名称（冗余）|
| remark | VARCHAR(500) | 备注 |
| deleted | TINYINT | 逻辑删除 |

#### production_item（排产单明细表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| production_id | BIGINT | 所属排产单 ID |
| production_no | VARCHAR(30) | 排产单号（冗余）|
| material_id | BIGINT | 物料 ID |
| material_name | VARCHAR(200) | 物料名称 |
| material_code | VARCHAR(50) | 物料编码 |
| spec | VARCHAR(200) | 规格 |
| process_id | BIGINT | 工艺 ID |
| process_name | VARCHAR(100) | 工艺名称 |
| receipt_type | VARCHAR(50) | 收货类型 |
| unit | VARCHAR(20) | 计量单位 |
| planned_qty | DECIMAL(12,2) | 排产数量 |
| actual_qty | DECIMAL(12,2) | 入库数量 |
| unware_housed_qty | DECIMAL(12,2) | 未入库数量 |
| outsource_price | DECIMAL(10,4) | 委外单价 |
| plating_price | DECIMAL(10,4) | 电镀单价 |
| plating_amount | DECIMAL(12,2) | 电镀金额 |
| customer_order_no | VARCHAR(100) | 客户单号 |
| production_type | VARCHAR(20) | 排产方式（自制/委外）|
| detail_remark | VARCHAR(500) | 明细备注 |
| deleted | TINYINT | 逻辑删除 |

#### shipment / shipment_item（发货单，结构类似收货单）

shipment 主表同 receipt（字段对应 shipment_no/shipment_date）

shipment_item 明细表：
| 字段 | 类型 | 说明 |
|------|------|------|
| shipment_id | BIGINT | 所属发货单 ID |
| shipment_no | VARCHAR(30) | 发货单号（冗余）|
| material_id/name/code | | 物料信息 |
| spec | VARCHAR(200) | 规格 |
| process_id/name | | 工艺信息 |
| shipment_type | VARCHAR(20) | 发货类型（良品/次品/返工品），默认"良品" |
| quantity | DECIMAL(12,2) | 发货数量（良品）|
| defective_qty | DECIMAL(12,2) | 废品/原件退回数量（计入发货合计扣库存，不计金额）|
| unit_price/amount | | 单价/良品金额 |
| customer_order_no | | 客户单号 |
| detail_remark | | 明细备注 |

#### rework（返工单主表）✅ 已改造

> 已完成主从表改造（2026-03-08）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| rework_no | VARCHAR(30) | 返工单号，唯一，格式 FG+年月+流水 |
| rework_date | DATE | 返工日期 |
| customer_id | BIGINT | 客户 ID |
| customer_name | VARCHAR(100) | 客户名称（冗余）|
| rework_status | VARCHAR(20) | 返工状态：待返工/返工中/已完成 |
| remark | VARCHAR(500) | 备注 |
| deleted | TINYINT | 逻辑删除 |

#### rework_item（返工单明细表）✅ 已建表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| rework_id | BIGINT | 所属返工单 ID |
| rework_no | VARCHAR(30) | 返工单号（冗余）|
| material_id | BIGINT | 物料 ID |
| material_name | VARCHAR(200) | 物料名称 |
| material_code | VARCHAR(50) | 物料编码 |
| spec | VARCHAR(200) | 规格 |
| process_id | BIGINT | 工艺 ID |
| process_name | VARCHAR(100) | 工艺名称 |
| quantity | DECIMAL(12,2) | 返工数量 |
| unit_price | DECIMAL(10,4) | 单价 |
| amount | DECIMAL(12,2) | 金额 |
| rework_reason | VARCHAR(500) | 返工原因 |
| detail_remark | VARCHAR(500) | 明细备注 |
| deleted | TINYINT | 逻辑删除 |

#### statement（对账单主表）

> 📌 对账单采用**主从表设计**，与收货单/排产单/发货单一致。主表记录汇总数据，明细表按物料展示每行数据（与老系统 Excel 格式对应）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| statement_no | VARCHAR(30) | 对账单号，格式 DZ+年月+流水 |
| statement_month | VARCHAR(7) | 对账月份，格式 YYYY-MM |
| customer_id | BIGINT | 客户 ID |
| customer_name | VARCHAR(100) | 客户名称（冗余）|
| receipt_qty | DECIMAL(12,2) | 本月收货合计数量（明细汇总）|
| shipment_qty | DECIMAL(12,2) | 本月发货合计数量（明细汇总）|
| receipt_amount | DECIMAL(12,2) | 本月收货合计金额 |
| shipment_amount | DECIMAL(12,2) | 本月发货合计金额 |
| remark | VARCHAR(500) | 备注 |
| status | VARCHAR(20) | 状态：草稿/已确认 |
| deleted | TINYINT | 逻辑删除 |

#### statement_item（对账单明细表）✅ 已建表

> 按物料维度存储每行数据，对应老系统 Excel 的每一行。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| statement_id | BIGINT | 所属对账单 ID |
| statement_no | VARCHAR(30) | 对账单号（冗余）|
| material_id | BIGINT | 物料 ID |
| material_code | VARCHAR(50) | 物料编码（冗余）|
| material_name | VARCHAR(200) | 物料名称（冗余）|
| process_id | BIGINT | 工艺 ID |
| process_name | VARCHAR(100) | 工艺名称（冗余）|
| prev_balance_qty | DECIMAL(12,2) | 上月结余数量 |
| receipt_qty | DECIMAL(12,2) | 本月收货合计 |
| shipment_qty | DECIMAL(12,2) | 本月发货合计 |
| defective_qty | DECIMAL(12,2) | 原件退回数量 |
| rework_qty | DECIMAL(12,2) | 返工数量（本月收货中返工来源的数量）|
| curr_balance_qty | DECIMAL(12,2) | 本月结余数量 |
| unit_price | DECIMAL(10,4) | 单价 |
| goods_amount | DECIMAL(12,2) | 良品金额 |
| remark | VARCHAR(500) | 备注 |
| deleted | TINYINT | 逻辑删除 |
| create_time | DATETIME | |
| update_time | DATETIME | |

> **核心计算公式**：
> - 良品数量 = 发货合计 - 退回数量 - 返工数量
> - 良品金额 = 良品数量 × 单价
> - 本月结余 = 上月结余 + 本月收货 - 本月发货
> - 返工数量 = 本月收货明细中 `receipt_source='返工'` 的数量合计

> **老系统 Excel 列映射**（用于 `POST /api/statements/import`）：
> - col0: 产品代码，col1: 产品名称，col2: 工艺要求
> - col3: 上月结余 → `prev_balance_qty`
> - col5: 本月收货合计 → `receipt_qty`
> - col7: 本月发货（原件退回）→ `defective_qty`
> - col8: 本月发货合计 → `shipment_qty`
> - col9: 本月结余 → `curr_balance_qty`
> - col10: 单价 → `unit_price`
> - col11: 良品金额 → `goods_amount`
> - col12: 合计金额 → `shipment_amount`
> - col13: 备注 → `remark`
>
> **跳过规则**：col0 = "合计" 或 "应收金额" 的行跳过；前2行为表头跳过。

#### inventory（库存表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| material_id | BIGINT | 物料 ID |
| customer_id | BIGINT | 客户 ID |
| process_id | BIGINT | 工艺 ID（无工艺用 0 哨兵值）|
| material_code | VARCHAR(100) | 冗余字段 |
| material_name | VARCHAR(200) | 冗余字段 |
| customer_name | VARCHAR(200) | 冗余字段 |
| spec | VARCHAR(200) | 冗余字段 |
| process_name | VARCHAR(100) | 冗余字段 |
| quantity | DECIMAL(12,2) | 当前库存总数 |
| rework_qty | DECIMAL(12,2) | 其中返工库存数量 |
| last_receive_time | DATETIME | 最后收货时间 |
| last_ship_time | DATETIME | 最后发货时间 |

> **唯一键**：(material_id, customer_id, process_id)

> **并发安全**：库存更新使用原子 SQL `UPDATE inventory SET quantity = quantity + ? WHERE ...`，不使用 SELECT + UPDATE 模式。

> **返工库存追踪**：`rework_qty` 记录当前库存中返工品的数量。返工收货时增加，发货时优先消耗返工库存（扣到0为止）。

#### inventory_log（库存流水表）
| 字段 | 类型 | 说明 |
|------|------|------|
| change_type | INT | 变动类型：1收货 2发货 3返工 |
| change_qty | DECIMAL(12,2) | 变动数量（正/负）|
| before_qty | DECIMAL(12,2) | 变动前数量 |
| after_qty | DECIMAL(12,2) | 变动后数量 |
| order_type | VARCHAR(50) | 关联单据类型 |
| order_id | BIGINT | 关联单据 ID（可空）|
| order_no | VARCHAR(100) | 关联单据号 |

---

## 三、后端设计

### 3.1 统一响应格式

```json
{
  "code": 200,        // 200成功，其他失败
  "msg": "success",
  "data": {}          // 具体数据
}
```

### 3.2 分页参数

所有分页接口统一参数：`page`（页码，从1开始）、`size`（每页条数，默认10）

### 3.3 认证机制

- 登录接口返回 JWT Token
- 所有业务接口需在 Header 携带 `Authorization: Bearer <token>`
- Token 有效期：86400秒（1天）
- 密钥：配置在 `application.yml` 的 `jwt.secret`

### 3.4 MyBatis-Plus 使用规范

```java
// 逻辑删除：Entity 中配置
@TableLogic
private Integer deleted;

// 自动填充：Entity 中配置
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;

// 查询时防多条报错（物料/工艺查询必须加）
materialMapper.selectOne(wrapper.last("LIMIT 1"));

// 库存原子更新（不要用 SELECT + UPDATE）
inventoryMapper.incrementQuantity(materialId, customerId, processId, delta);
```

### 3.5 事务规范

> ⚠️ **重要**：批量导入接口中，`save()` 是 MyBatis-Plus 带 `@Transactional` 的方法，在 catch 块里捕获异常后会导致外层事务被标记为 rollback-only，后续所有操作失败。

**批量导入必须使用 `getBaseMapper().insert()` 而不是 `save()`**：

```java
// ❌ 错误写法（会导致整批回滚）
for (Receipt r : list) {
    try {
        save(r);  // @Transactional 方法
    } catch (Exception e) {
        // 捕获后，外层事务已被标记 rollback-only！
    }
}

// ✅ 正确写法
for (Receipt r : list) {
    try {
        getBaseMapper().insert(r);  // 直接操作，无独立事务
    } catch (Exception e) {
        // 只影响这一条，不影响后续
    }
}
```

### 3.6 单号生成规则

格式：`前缀 + 年月(YYYYMM) + "-" + 4位流水号`

| 单据 | 前缀 | 示例 |
|------|------|------|
| 收货单 | SH | SH202507-0001 |
| 排产单 | PC | PC202507-0001 |
| 发货单 | FH | FH202507-0001 |
| 返工单 | FG | FG202507-0001 |
| 收款单 | SK | SK202507-0001 |
| 对账单 | DZ | DZ202507-0001 |
| 期初收货单 | RH-INIT | RH-INIT-{customerId}（固定格式，非流水）|

> ⚠️ **`GenerateNoUtil` LIKE 查询**：对账单流水号查询使用 `DZ + YYYYMM`（6位年月）构建 LIKE 模式，`LIKE 'DZ______%'`（6个下划线）。历史 Bug：曾错误使用 8 个下划线导致流水号重复冲突，已修复。

---

## 四、前端设计

### 4.1 页面结构

```
layout/index.vue          — 主布局（左侧菜单 + 右侧内容）
├── views/dashboard/      — 首页（数据概览）
├── views/customer/       — 客户管理
├── views/process/        — 工艺管理
├── views/material/       — 物料管理
├── views/receipt/        — 收货单管理（主从表）
├── views/production/     — 排产单管理（主从表）
├── views/shipment/       — 发货单管理（主从表）
├── views/payment/        — 收款管理
├── views/statement/      — 对账单管理
├── views/inventory/      — 库存管理（位于生产管理菜单下）
└── views/system/         — 系统管理（用户/角色/菜单）
```

### 4.2 主从表页面设计规范

收货单/排产单/发货单均采用以下统一设计：

**列表页**：
```
┌─────────────────────────────────┐
│ 筛选栏（单号/客户/日期范围）        │
├─────────────────────────────────┤
│ 操作栏（新增/导入/导出按钮）        │
├────┬──────┬──────┬──────┬──────┤
│ ▶  │ 单号  │ 日期  │ 客户  │ 操作 │
├────┼──────┴──────┴──────┴──────┤
│ ▼  │ 明细子表格（展开后显示）        │
│    │ 产品名|规格|工艺|数量|单价|金额│
└────┴─────────────────────────── ┘
```

**新增/编辑对话框**：
```
┌──────────────────────────────────┐
│ 主单信息（单号/日期/客户/备注）       │
├──────────────────────────────────┤
│ 明细列表                           │
│ ┌──┬────┬──┬──┬──┬──┬──┬──┐    │
│ │序│产品│规│工│来│数│单│金│    │
│ │号│名称│格│艺│源│量│价│额│    │
│ └──┴────┴──┴──┴──┴──┴──┴──┘    │
│ [+ 添加明细行]                     │
│                                  │
│           [取消]  [保存]           │
└──────────────────────────────────┘
```

### 4.3 样式规范

- 主题色：Element Plus 默认主题
- 表格横向滚动：`el-table` 直接设置 `overflow-x: auto`，**不要**嵌套 `.table-scroll-wrap` div
- 侧边菜单滚动：`.layout-aside` 需设置 `height: 100vh; overflow-y: auto`
- 操作列按钮并排：所有页面 `<style scoped>` 中加 `:deep(.el-table .cell) { white-space: nowrap; }`，操作列宽度按按钮数量计算（每按钮 ~75px）
- 状态列：使用 `el-tag` 点击切换，不用 `el-switch`（避免文字被截断）

### 4.4 物料下拉搜索规范

> 适用于：收货单/排产单/发货单/返工单 新增/编辑弹窗中的物料选择

**交互设计**：
1. 选择客户后，自动预加载该客户前 100 条物料（`/api/materials/search?customerId=xxx`）
2. 物料下拉使用 `el-select` 的 `remote` + `filterable` 模式
3. 用户输入关键词时，调用 `searchMaterial(query, rowIndex)`，请求 `/api/materials/search?customerId=xxx&keyword=xxx`（返回 ≤100 条）
4. 每行明细有独立的 `_matOptions`（不共享，多行搜索互不干扰）

**选中物料后自动填充**：
- 物料名称、编码、规格
- 单价：**先清零**，再从物料的 `defaultPrice` 填入（无单价则保持 0）
- 工艺：调用 `/api/receipt-items/latest-process?customerId=xxx&materialId=xxx`，查该客户该物料最近一次收货单里的工艺，自动填入

**切换物料时**：单价和工艺都先重置为 0/null，再按新物料重新带出。

### 4.5 收货单明细未设价提醒

**规则**：收货单列表展开行中，明细行满足以下条件时标红显示：
- `receiptSource === '正常'`（正常收货，非返工/样品）
- `unitPrice` 为 0 或 null

**实现**：
- `el-table` 的 `:row-class-name="itemRowClass"` 绑定 `row-no-price` class
- 单价列额外显示 `el-tooltip` 提示「正常收货未设置单价」，文字显示「未设价」
- CSS：`:deep(.row-no-price td) { background: #fff0f0 }; :deep(.row-no-price .cell) { color: #f56c6c }`

---

## 五、关键流程

### 5.1 收货单创建流程

```
前端提交 POST /api/receipts
  body: { receiptDate, customerId, remark, items: [{materialName, quantity, ...}] }
        ↓
ReceiptController.createReceipt()
        ↓
ReceiptServiceImpl.createReceipt()
  1. 生成收货单号（generateNoUtil）
  2. getBaseMapper().insert(receipt) 保存主单
  3. receiptItemService.saveItems(id, no, items) 批量保存明细
  4. 若 mode != "history"：更新库存 + 写入库存流水
        ↓
返回保存后的 receipt（含 id）
```

### 5.2 历史数据导入流程

```
POST /api/receipts/import?mode=history
  multipart: file=收货单.xlsx（每批3000行）
        ↓
ReceiptServiceImpl.importExcel()
  1. 读取 Excel，按 receiptNo 分组
  2. 构建 Map<receiptNo, Receipt> + Map<receiptNo, List<ReceiptItem>>
  3. 同一单号第一行取：日期/客户/备注 → 主单
  4. 每行取：物料/规格/工艺/数量等 → 明细
  5. 物料 ID 查询：materialMapper.selectOne(name+customerId LIMIT 1)
  6. 工艺 ID 查询：processMapper.selectOne(name LIMIT 1)
  7. 幂等检查：receiptExists(receiptNo) → skip
  8. 批量保存：getBaseMapper().insert() + saveItems()
  9. mode=history：不触发库存更新
```

### 5.3 期初库存补录流程（历史数据迁移时执行）

```
背景：老系统仅迁移 2025 年及之后数据，但 2025 年初已有在途库存。
若不补录，月度对账结余会出现负数。

1. 确保收货单、发货单已全部导入（mode=history）
2. python3 scripts/init_opening_stock.py
   ├── SQL 窗口函数计算每个 (material_id, customer_id, process_id) 组合
   │   在所有月份中的最大累计缺口：MAX(SUM(ship) - SUM(recv) OVER 月份累计)
   ├── 向上取整后作为 2024-12-31 期初收货数量
   ├── 按客户分组，插入收货单 receipt（单号 RH-INIT-{customerId}）
   └── 插入收货明细 receipt_item，status=1，receipt_date='2024-12-31'
   幂等：已存在 RH-INIT-{customerId} 则复用其 id；
         已存在相同 (material_id, process_id) 明细行则跳过

3. 执行后必须重建库存（见 5.4）
```

### 5.4 库存重建流程（`POST /api/inventory/rebuild`）

```
1. 清空 inventory 表（DELETE ALL）
2. 按时间顺序处理所有收货单明细：
   a. 对每条收货明细，累加 quantity 到库存
   b. 若 receipt_source = '返工'，同时累加到 rework_qty

3. 按时间顺序处理所有发货单明细：
   a. 对每条发货明细，扣减 quantity + defective_qty
   b. 同时扣减 rework_qty（优先消耗返工库存，扣到0为止）

4. 批量 INSERT inventory（quantity + rework_qty）
5. 同时重建库存流水（inventory_log）
6. 返回统计：receiptLogs / inventoryRecords / shipmentLogs

注意：重建后负库存 ≤ 5 条（历史数据录入误差，非系统问题）
```

### 5.5 返工库存处理流程

> **业务背景**：客户送来返工件（之前发货后发现有质量问题退回），工厂处理后重新发货。返工件不再重复收费，需在对账单中扣减。

#### 5.5.1 数据流总览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           返工处理完整流程                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. 返工收货（receipt_item.receipt_source = '返工'）                         │
│     ┌──────────────┐      ┌──────────────┐      ┌──────────────┐          │
│     │ 库存总数 +N  │      │ 返工库存 +N  │      │ 库存流水记录  │          │
│     │ (quantity)   │      │ (rework_qty) │      │ (changeType=1)│          │
│     └──────────────┘      └──────────────┘      └──────────────┘          │
│                                                                             │
│  2. 发货出库（shipment_item）                                                │
│     ┌──────────────┐      ┌──────────────┐      ┌──────────────┐          │
│     │ 库存总数 -M  │      │ 返工库存 -M  │      │ 库存流水记录  │          │
│     │ (quantity)   │      │ (rework_qty) │      │ (changeType=2)│          │
│     │ 优先消耗返工 │      │ 扣到0为止    │      │              │          │
│     └──────────────┘      └──────────────┘      └──────────────┘          │
│                                                                             │
│  3. 对账单生成（statement_item）                                             │
│     ┌──────────────────────────────────────────────────────────────┐      │
│     │ rework_qty = 本月收货中 receipt_source='返工' 的数量合计      │      │
│     │ 良品数量 = 发货合计 - 退回数量 - 返工数量                       │      │
│     │ 良品金额 = 良品数量 × 单价                                     │      │
│     └──────────────────────────────────────────────────────────────┘      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 5.5.2 库存变更触发点

| 单据类型 | 触发动作 | 库存总数变化 | 返工库存变化 | 代码位置 |
|---------|---------|-------------|-------------|---------|
| 收货单（正常）| 新增/更新/删除 | +quantity | 不变 | `ReceiptServiceImpl:91-106` |
| 收货单（返工）| 新增/更新/删除 | +quantity | +quantity | `ReceiptServiceImpl:108-112` |
| 发货单 | 新增/更新/删除 | -quantity | 优先消耗（扣到0为止）| `ShipmentServiceImpl:91-109` |
| 库存重建 | 全量 | 重新计算 | 重新计算 | `InventoryServiceImpl:351-489` |

> **注意**：更新（`updateReceipt/updateShipment`）和删除（`deleteReceipt/deleteShipment`）操作都需要先冲销旧的 rework_qty，再应用新的变化，与新增逻辑完全对称。

#### 5.5.3 详细流程说明

**1. 返工收货入库**

```
触发条件：receipt_item.receipt_source = '返工'

执行步骤：
1. 更新库存总数：inventory.quantity += receipt_item.quantity
2. 更新返工库存：inventory.rework_qty += receipt_item.quantity
3. 记录库存流水：
   - change_type = 1 (收货)
   - change_qty = +quantity
   - order_type = 'receipt'
```

**2. 发货出库（含返工消耗）**

```
触发条件：shipment_item 保存

执行步骤：
1. 计算发货总量：totalQty = quantity + defective_qty
2. 更新库存总数：inventory.quantity -= totalQty
3. 优先消耗返工库存：
   - inventory.rework_qty = GREATEST(0, rework_qty - totalQty)
   - 注：发货时不区分是良品还是返工品，按 FIFO 先进先出原则优先消耗返工库存
4. 记录库存流水：
   - change_type = 2 (发货)
   - change_qty = -totalQty
   - order_type = 'shipment'
```

**3. 库存重建（POST /api/inventory/rebuild）**

```
执行步骤：
1. 清空 inventory 和 inventory_log 表
2. 按时间顺序处理所有收货明细：
   - 累加 quantity 到库存总数
   - 若 receipt_source = '返工'，同时累加到 rework_qty
3. 按时间顺序处理所有发货明细：
   - 扣减 quantity（含 defective_qty）
   - 优先消耗 rework_qty（扣到0为止）
4. 批量 INSERT 最终库存快照
5. 返回统计：{ inventoryRecords, receiptLogs, shipmentLogs }
```

**4. 对账单返工扣减**

```
计算逻辑（StatementServiceImpl.buildAndSaveItems）：
1. 累计本月收货中返工来源数量：
   rework_qty = SUM(receipt_item.quantity WHERE receipt_source='返工')
2. 计算良品数量：
   goodsShipQty = shipment_qty - defective_qty
   billableQty = MAX(0, goodsShipQty - rework_qty)
3. 计算良品金额：
   goods_amount = billableQty × unit_price

核心公式：
- 良品数量 = 发货合计 - 退回数量 - 返工数量
- 良品金额 = 良品数量 × 单价
```

#### 5.5.4 关键代码位置

| 功能 | 文件 | 行号 | 说明 |
|-----|------|-----|------|
| 返工收货库存更新 | `ReceiptServiceImpl.java` | 108-112 | `incrementReworkQty()` |
| 发货库存更新 | `ShipmentServiceImpl.java` | 91-109 | 发货出库+返工消耗 |
| 库存重建 | `InventoryServiceImpl.java` | 351-489 | 全量重建 inventory |
| 对账单返工扣减 | `StatementServiceImpl.java` | 204-206, 314-319 | 累计返工数量+计算良品金额 |
| 库存返工字段 | `InventoryMapper.java` | 21-22 | `incrementReworkQty()` SQL |

#### 5.5.5 数据库字段

**inventory 表**：
- `quantity`：库存总数（当前在库总量）
- `rework_qty`：其中返工库存数量（当前库存中返工件数量）

**statement_item 表**：
- `rework_qty`：本月收货中返工来源的数量（用于良品金额计算扣减，从 receipt_item.receipt_source='返工' 统计）
- `goods_amount`：良品金额 = (发货合计 - 退回 - 返工) × 单价

**statement 表**：
- `goods_amount`：所有明细行 goods_amount 的汇总（generate 方法实时计算；importExcel 导入时从 Excel col11 汇总）

#### 5.5.6 业务规则

1. **返工收货不计费**：收货来源为"返工"的明细，其单价应为 0 或不参与金额统计
2. **FIFO 消耗原则**：发货时优先消耗返工库存，确保返工件先出库
3. **返工库存不低于0**：使用 `GREATEST(0, rework_qty - delta)` 保证不会出现负数
4. **对账单幂等性**：重新生成对账单时，会删除旧明细重建，保证返工数量正确计算

### 5.6 对账单生成流程（`POST /api/statements/generate`）

```
入参：customerId, statementMonth (YYYY-MM)

1. 查询该月 [monthStart, monthEnd] 内该客户所有 receipt_item（status=1, deleted=0）
2. 查询该月内该客户所有 shipment_item（status=1, deleted=0）
3. buildAndSaveItems(stmt, customerId, ym, receiptItems, shipmentItems):
   a. 按 (materialId + "_" + processId) 分组
   b. 计算 prevBalanceQty：
      查 receipt_date < monthStart 的所有收货合计
      减去 shipment_date < monthStart 的所有发货合计（含 defective_qty）
      -- 注意：直接聚合历史数据，不依赖上月对账单的 curr_balance_qty
   c. 每组生成 StatementItem：
      receipt_qty   = SUM(receipt_item.quantity)
      shipment_qty  = SUM(ship.quantity + ship.defective_qty)
      defective_qty = SUM(ship.defective_qty)
      goods_amount  = SUM(ship.amount)
      shipment_amount = goods_amount
      curr_balance_qty = prevBalance + receipt_qty - shipment_qty
   d. 批量 INSERT statement_item

4. 若对账单已存在：
   ├── statementItemService.deleteByStatementId(existing.id)  -- 软删旧明细
   └── 重新执行 buildAndSaveItems（保证等式成立、结余 ≥ 0）

5. generate-all：遍历所有收发货记录聚合出客户×月份组合，
   对每个组合调用 generate()；已存在的**跳过**（不重建）
```

---

## 六、已知问题和待办

| 问题 | 优先级 | 状态 |
|------|--------|------|
| 返工单改造为主从表（rework + rework_item）| 高 | ✅ 已完成（2026-03-08）|
| 对账单历史数据导入接口（POST /api/statements/import）| 高 | ✅ 已完成（2026-03-08）|
| 对账单改造为主从表（statement + statement_item）| 高 | ✅ 已完成（2026-03-08）|
| 对账单前端展示物料明细（展开行）| 高 | ✅ 已完成（2026-03-08）|
| 物料下拉改为远程搜索（默认100条+关键词过滤）| 高 | ✅ 已完成（2026-03-08）|
| 选物料自动带出单价+工艺（查最近收货单）| 高 | ✅ 已完成（2026-03-08）|
| 收货单明细未设价标红提醒 | 中 | ✅ 已完成（2026-03-08）|
| 收货单导入自动回填物料 default_price | 中 | ✅ 已完成（2026-03-08）|
| 对账单流水号 LIKE 模式 8个下划线导致重复冲突 | 高 | ✅ 已修复（改为6个下划线，2026-03-15）|
| 对账单 prevBalanceQty 依赖上月链式传递导致批量生成错误 | 高 | ✅ 已修复（改为直接聚合历史数据，2026-03-15）|
| statement_item 缺少 defective_qty / goods_amount 字段 | 高 | ✅ 已修复（新增列并更新导入/生成逻辑，2026-03-15）|
| 期初库存未补录导致对账单结余为负 | 高 | ✅ 已修复（新增 scripts/init_opening_stock.py，2026-03-15）|
| inventory 重建 aggregateReceiptQty GROUP BY 含字符串字段导致同 key 多行覆盖 | 高 | ✅ 已修复（GROUP BY 只含3个 key 字段，字符串用 MAX()，2026-03-15）|
| inventory 重建 aggregateShipmentQty 未计 defective_qty 且缺 status=1 过滤 | 高 | ✅ 已修复（SUM(qty+defective_qty) + status=1，2026-03-15）|
| **发货单新增/编辑时价格变动，当月同客户+物料+工艺的收货/排产/发货明细单价联动更新** | 高 | ✅ 已完成（2026-04-01）|
| **库存手动调整功能**（PUT /api/inventory/{id}，月末对账后修正实际库存）| 高 | ✅ 已完成（2026-04-01）|
| 收货单分批上传（前端按3000行拆分）| 中 | 待开发 |
| inventory 查询接口带 keyword 参数时返回 400 | 中 | 待修复 |
| **全模块 Excel 导出功能**（基础档案/生产/财务所有列表页）| 高 | 🚧 开发中 |
| **排产单/发货单打印功能**（含打印配置管理）| 高 | 🚧 开发中 |

---

## 七、开发注意事项

> 这些是实际开发中踩过的坑，后续 Agent 请重点关注：

1. **禁止本地 mvn 编译**：Lombok 1.18.34 与本地 Java 21.0.9（Microsoft）不兼容，必须在 Docker 容器内编译
2. **Java switch expression**：块内必须用 `yield` 返回值，不能用 `return`（会编译报"attempt to return out of a switch expression"）
3. **selectOne 多条报错**：物料表同名不同客户，查询时必须加 `.last("LIMIT 1")` 防止报 "Expected one result"
4. **批量导入事务问题**：导入循环中必须用 `getBaseMapper().insert()` 而非 `save()`（详见 3.5）
5. **MySQL DROP COLUMN IF EXISTS 不支持**：需先查 information_schema 确认列存在再 DROP
6. **xls 文件头损坏**：老系统 xls 文件 `file size not 512+multiple of sector size`，需用 Python xlrd 先转 xlsx
7. **收货单 OOM**：65535 行 xlsx 一次性加载会 OOM，需分批（每批 3000 行）上传
8. **YAML 重复 spring: 块**：修改 application.yml 时注意不要出现两个 `spring:` 顶层 key
9. **axios GET params 传参**：`request.get(url, params)` 不会把 params 附加到 URL，必须写 `request.get(url, { params })`；否则客户筛选等条件静默失效
10. **Vue 响应式数组更新**：直接赋值 `row._matOptions = [...]` 不触发 UI 更新，必须用 `row._matOptions.splice(0, length, ...newItems)` 或 `row._matOptions = reactive([...])`
11. **el-select remote 模式**：模板引用 `materialList` 改为 `row._matOptions` 后，`onItemMaterialChange` 也要从 `row._matOptions` 里查，否则找不到选中的物料信息
12. **驼峰命名与 DB 列名不一致**：字段名含连续大写（如 `wareHousedQty`）MyBatis-Plus 转下划线为 `ware_housed_qty`，与实际列名 `warehoused_qty` 不符，需显式 `@TableField("warehoused_qty")`
13. **单价回填 Excel 日期污染**：导入 Excel 时日期值会被读为数字（如 43673），需加 `unitPrice ≤ 10000` 上限过滤
14. **MySQL -N -B 空字符串列**：`docker exec ... mysql -N -B` 输出 tab 分隔行时，空字符串列不会输出 tab，导致 `split('\t')` 返回列数比预期少。Python 脚本中用 `IF(col IS NULL OR col='','',col)` 输出占位，并用 `len(row) >= N` 做防御性检查
15. **逻辑删除查询**：MyBatis-Plus `@TableLogic` 会自动在 ORM 查询加 `deleted=0`，但**原生 SQL（@Select 注解或 mapper XML）必须手动加 `AND deleted=0`**，否则会统计到已软删除记录，导致统计值偏大或出现"负库存"假象
16. **generate-all vs generate 幂等差异**：`generate-all` 遇到已存在的对账单会**跳过**；单个 `generate` 会**删旧明细重建**。需要强制更新某月对账单时，必须调用单个 `generate` 接口
17. **老系统客户档案 Excel 列映射**：`客户档案.xls` 共 22 列（col0 到 col21），正确映射如下：
    - col0=客户代码, col1=客户名称, col2=区域名称, col3=客户类型, col4=所属行业
    - col5=地址, col6=邮编, col7=开户银行, col8=税号, col9=银行帐号
    - col10=业务员, col11=电子邮箱, col12=联系人, col13=联系电话, col14=传真
    - col15=停用（True→status=0，False→status=1）
    - **历史版本错误**：原代码用 col2=customerType、col3=salesperson 等，导致字段全部错位（客户类型为空、业务员乱写等）
18. **老系统工艺数据 Excel 列映射**：`工艺数据.xls` 共 9 列（col0 到 col8），正确映射如下：
    - col0=工艺代码, col1=工艺名称, col2=厚度要求, col3=备注, col4=优先编号
    - col5=缺省报价（布尔，**不是禁用字段**）, col6=禁用（True→status=0，False→status=1）
    - col7=工艺类别, col8=工艺性质
    - **历史版本错误**：原代码用 col5 作为禁用字段，但 col5 是"缺省报价"，导致 145/155 条工艺被错误设为 status=0（禁用），前端工艺下拉列表几乎为空，选产品后工艺显示数字 ID 而非名称
19. **MySQL 字符集导致 mojibake**：通过 Python subprocess 执行 mysql CLI 时，若不加 `--default-character-set=utf8mb4`，中文会以 latin1 编码存入，导致 `SHOW HEX(col)` 显示 `C3A7...` 等，页面显示乱码。**修复 SQL**：`UPDATE customer SET customer_type = CASE WHEN HEX(customer_type) LIKE 'C3A7%' THEN '现金' WHEN HEX(customer_type) LIKE 'C3A6%' THEN '月结' ELSE customer_type END`
20. **前端 API 响应格式不统一**：部分后端接口（如 `GET /api/processes/all`）直接返回数组，不包在 `{code,msg,data}` 结构里。Axios 拦截器遇到 `res.code !== 200` 时直接返回 `res`（即原始数组）。前端取数据时必须用 `Array.isArray(res) ? res : (res.data || [])` 做兼容，否则 `processList` 为空、下拉显示数字 ID。
21. **老系统客户数据源质量问题**：部分老系统客户 `客户代码 == 客户名称`（如"轩沣卫浴"），这是老系统原始数据问题，非导入 bug。前端客户类型展示需做 `v-if="row.customerType"` 防御，避免空类型渲染空白 Tag。

---

---

## 八、Excel 导出功能设计

> 适用于：基础档案（客户/工艺/物料）、生产管理（收货单/排产单/发货单/返工单/库存）、财务管理（收款记录/对账单）共 10 个模块。

### 8.1 后端实现方案

**复用现有 Apache POI 5.2.5 依赖（无需新增依赖）。**

每个模块新增一个导出 Service 方法 + Controller 端点：

```java
// Controller 层：通用模式
@GetMapping("/export")
public void export(HttpServletResponse response,
                   @RequestParam(required = false) String keyword,
                   @RequestParam(required = false) Long customerId,
                   @RequestParam(required = false) String startDate,
                   @RequestParam(required = false) String endDate) {
    xxxService.exportExcel(response, keyword, customerId, startDate, endDate);
}

// Service 层：通用模式
public void exportExcel(HttpServletResponse response, ...) {
    // 1. 按筛选条件查全量数据（不分页，limit 50000行）
    // 2. 创建 XSSFWorkbook，配置样式
    // 3. 写标题行、列标题、数据行
    // 4. 主从表：先写主单行（蓝底），再循环写明细行（交替白/浅蓝）
    // 5. 最后写合计行（黄底）
    // 6. 设置 response header，写入 OutputStream
}
```

**通用 ExcelExportUtil 工具类**（新建，避免各模块重复代码）：

```java
// 路径：util/ExcelExportUtil.java
public class ExcelExportUtil {
    // 创建工作簿和通用样式
    public static XSSFWorkbook createWorkbook() { ... }

    // 创建标题行样式（蓝色背景、白字、加粗16号）
    public static CellStyle createTitleStyle(XSSFWorkbook wb) { ... }

    // 创建列标题样式（浅蓝背景、深蓝字、加粗11号）
    public static CellStyle createHeaderStyle(XSSFWorkbook wb) { ... }

    // 创建主单行样式（#BDD7EE 背景、加粗）
    public static CellStyle createMasterRowStyle(XSSFWorkbook wb) { ... }

    // 创建数据行样式（奇行白色/偶行#F5F9FF，带细边框）
    public static CellStyle createDataRowStyle(XSSFWorkbook wb, boolean even) { ... }

    // 创建合计行样式（#FFF2CC 黄色背景、加粗）
    public static CellStyle createSummaryRowStyle(XSSFWorkbook wb) { ... }

    // 设置 response header 并写出
    public static void writeResponse(XSSFWorkbook wb, HttpServletResponse response, String filename) { ... }

    // 自适应列宽（遍历前300行估算最大内容宽度）
    public static void autoSizeColumns(Sheet sheet, int colCount) { ... }

    // 创建单元格并设置值（支持 String/Number/LocalDate）
    public static Cell createCell(Row row, int col, Object value, CellStyle style) { ... }
}
```

### 8.2 主从表导出结构

以收货单为例，Sheet 布局：

```
行 0 ：[收货单导出报表                                                    ]  ← 合并A~S列，蓝底白字
行 1 ：[收货单号][日期][客户][备注][物料码][物料名][规格][工艺]...[明细备注]  ← 列标题行
行 2 ：[SH2025-0001][2025-01-01][客户A][...][   ][   ][...][...][...][...] ← 主单行（蓝底）
行 3 ：[           ][         ][     ][   ][M001][产品A][..][..][10][0.5]   ← 明细行（白底）
行 4 ：[           ][         ][     ][   ][M002][产品B][..][..][20][0.8]   ← 明细行（浅蓝）
行 5 ：[SH2025-0002][2025-01-02][客户B][...][   ][   ][...][...][...][...] ← 主单行（蓝底）
行 6 ：[           ][         ][     ][   ][M003][产品C][..][..][15][0.6]   ← 明细行（白底）
...
最后行：[合计       ][         ][     ][   ][   ][   ][  ][  ][45][   ]     ← 合计行（黄底）
```

**主从表列顺序**（以收货单为例，共19列）：

| 列 | 归属 | 字段 | 说明 |
|----|------|------|------|
| A | 主单 | receipt_no | 主单才填，明细行空白 |
| B | 主单 | receipt_date | 主单才填 |
| C | 主单 | customer_name | 主单才填 |
| D | 主单 | remark | 主单才填 |
| E | 明细 | material_code | |
| F | 明细 | material_name | |
| G | 明细 | spec | |
| H | 明细 | process_name | |
| I | 明细 | receipt_source | |
| J | 明细 | quantity | 右对齐 |
| K | 明细 | shipped_qty | 右对齐 |
| L | 明细 | unshipped_qty | 右对齐 |
| M | 明细 | planned_qty | 右对齐 |
| N | 明细 | ware_housed_qty | 右对齐 |
| O | 明细 | unware_housed_qty | 右对齐 |
| P | 明细 | unit_price | 右对齐 |
| Q | 明细 | amount | 右对齐 |
| R | 明细 | customer_order_no | |
| S | 明细 | detail_remark | |

### 8.3 后端查询优化

主从表导出时，明细数据通过一次 JOIN 查询获取（避免 N+1 问题）：

```java
// 方案：先查主表（带筛选条件），再一次性批量查全部明细
// 1. 查主表列表（按条件过滤，不分页，limit 50000）
List<Receipt> receipts = receiptMapper.selectList(wrapper);
if (receipts.isEmpty()) { writeEmptyExcel(); return; }

// 2. 提取所有主表 ID
List<Long> receiptIds = receipts.stream().map(Receipt::getId).collect(toList());

// 3. 一次查所有明细（IN 查询）
List<ReceiptItem> allItems = receiptItemMapper.selectList(
    new LambdaQueryWrapper<ReceiptItem>()
        .in(ReceiptItem::getReceiptId, receiptIds)
        .eq(ReceiptItem::getDeleted, 0)
        .orderByAsc(ReceiptItem::getId)
);

// 4. 按 receiptId 分组
Map<Long, List<ReceiptItem>> itemsByReceiptId = allItems.stream()
    .collect(Collectors.groupingBy(ReceiptItem::getReceiptId));

// 5. 写 Excel（主单行 + 明细行）
```

### 8.4 前端实现方案

在各模块 API 文件中新增 `exportXxx` 函数：

```js
// 以 receipt.js 为例
export const exportReceipts = (params) =>
  request.get('/receipts/export', { params, responseType: 'blob' })
```

在 view 中处理 blob 下载：

```js
const handleExport = async () => {
  exporting.value = true
  try {
    const res = await exportReceipts({ keyword: keyword.value, customerId: customerId.value, ... })
    // 从 Content-Disposition header 取文件名（或使用默认名）
    const url = URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.download = `收货单_${dayjs().format('YYYYMMDD')}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}
```

> ⚠️ **注意**：axios 拦截器默认处理 JSON 响应，blob 下载需要设置 `responseType: 'blob'` 并**绕过**拦截器的 `res.code !== 200` 检查。需确认现有 `request.js` 拦截器对 blob 响应的处理方式，必要时在拦截器中加入 `if (response.config.responseType === 'blob') return response.data` 判断。

### 8.5 开发顺序建议

1. **先建工具类** `ExcelExportUtil.java`，包含所有公共样式和辅助方法
2. **单表模块先开发**（客户、工艺、物料、收款记录、库存）—— 结构简单，验证样式效果
3. **主从表模块后开发**（收货单、排产单、发货单、返工单、对账单）—— 结构复杂，有了工具类后开发较快
4. **前端统一模式**：每个模块的导出按钮和 blob 下载逻辑相同，可复制粘贴后改参数

### 8.6 注意事项

- **大数据量**：超过 50000 行明细时截断，表格末尾最后一行写"注：数据已截断，请缩小查询范围后重试"
- **列宽自适应**：调用 `autoSizeColumns` 前必须先写完所有数据行，否则估算不准
- **字体**：使用"宋体"（SimSun），所有中文环境可用
- **合计行**：单表直接对数字列 SUM；主从表对所有明细行的数字列求和（不对主单行求和）
- **筛选条件透传**：导出接口参数与列表查询参数完全一致，前端传什么筛选条件后端就导出什么数据
- **文件名编码**：使用 `URLEncoder.encode(filename, StandardCharsets.UTF_8)` 处理中文，避免浏览器下载时乱码

---

## 九、打印单据功能设计

> 适用于：排产单（production）、发货单（shipment）两个模块。

### 9.1 后端设计

#### 9.1.1 sys_config 表（新建）

```sql
CREATE TABLE sys_config (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key   VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
  config_value VARCHAR(500)          COMMENT '配置值',
  remark       VARCHAR(200)          COMMENT '说明',
  create_time  DATETIME,
  update_time  DATETIME
) COMMENT='系统配置表';

-- 初始化打印配置
INSERT INTO sys_config (config_key, config_value, remark) VALUES
  ('print.factory_name', '', '打印单据工厂名称'),
  ('print.maker_name',   '', '打印单据制单人');
```

#### 9.1.2 打印配置接口

```
GET  /api/config/print   → { factoryName, makerName }
PUT  /api/config/print   body: { factoryName, makerName }
```

对应后端实现：
- `SysConfigController` + `SysConfigService`
- `SysConfigMapper` — MyBatis-Plus CRUD
- `SysConfig` 实体 (`@TableName("sys_config")`)

#### 9.1.3 打印数据接口

打印预览所需数据通过已有的明细查询接口获取，**无需新增后端接口**：
- 排产单：`GET /api/productions/{id}`（已含 items）
- 发货单：`GET /api/shipments/{id}`（已含 items）
- 打印配置：`GET /api/config/print`

> ⚠️ 发货单需要客户地址：`shipment.customer_id → customer.address`，需确认 `GET /api/shipments/{id}` 是否返回 `customerAddress`，若无需在 ShipmentVO 中补充该字段（JOIN customer 表取 address）。

### 9.2 前端设计

#### 9.2.1 打印预览实现方案

使用**新窗口 + CSS @media print** 方案：
1. 点击"打印"按钮，先并行请求打印配置和单据数据
2. 用 `window.open()` 打开新窗口，动态写入 HTML（含样式和数据）
3. 在新窗口中调用 `window.print()`，打印完成后关闭窗口

```js
// 核心逻辑
const handlePrint = async (row) => {
  const [detail, config] = await Promise.all([
    getProductionById(row.id),
    getPrintConfig()
  ])
  const html = buildPrintHtml(detail, config)  // 构造打印 HTML
  const win = window.open('', '_blank', 'width=800,height=600')
  win.document.write(html)
  win.document.close()
  win.onload = () => { win.print(); win.close() }
}
```

#### 9.2.2 CSS 页面尺寸设置

```css
@page {
  size: 241mm 140mm;        /* 物理纸张尺寸 */
  margin: 10mm 5mm;         /* 上下10mm留孔戳，左右5mm */
}
body {
  width: 231mm;             /* 231 = 241 - 2*5 */
  font-family: SimSun, '宋体', serif;
  font-size: 9pt;
}
```

可用内容区域：`231mm × 120mm`（上下各10mm留给孔戳区）

#### 9.2.3 排产单 HTML 模板结构

```html
<div class="print-container">
  <div class="factory-name">{{ factoryName }}</div>
  <div class="doc-title">排 产 单</div>
  <div class="header-row">
    <span>客户：{{ customerName }}</span>
    <span>日期：{{ productionDate }}</span>
  </div>
  <div class="header-row">
    <span>单号：{{ productionNo }}</span>
  </div>
  <table class="items-table">
    <thead>
      <tr><th>序号</th><th>品名规格</th><th>数量</th><th>备注</th></tr>
    </thead>
    <tbody>
      <tr v-for="(item, i) in items">
        <td>{{ i+1 }}</td>
        <td>{{ item.materialName }}{{ item.spec ? ' '+item.spec : '' }}</td>
        <td>{{ item.plannedQty }}{{ item.unit }}</td>
        <td>{{ item.detailRemark }}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer-row">制单人：{{ makerName }}</div>
</div>
```

#### 9.2.4 发货单 HTML 模板结构

```html
<div class="print-container">
  <div class="factory-name">{{ factoryName }}</div>
  <div class="doc-title">发 货 单</div>
  <div class="header-row">
    <span>客户：{{ customerName }}</span>
    <span>日期：{{ shipmentDate }}</span>
  </div>
  <div class="header-row">
    <span>单号：{{ shipmentNo }}</span>
    <span>收货地址：{{ customerAddress }}</span>
  </div>
  <table class="items-table">
    <thead>
      <tr><th>序号</th><th>品名规格</th><th>单位</th><th>数量</th><th>备注</th></tr>
    </thead>
    <tbody>
      <tr v-for="(item, i) in items">
        <td>{{ i+1 }}</td>
        <td>{{ item.materialName }}{{ item.spec ? ' '+item.spec : '' }}</td>
        <td>{{ item.unit }}</td>
        <td>{{ item.quantity }}</td>
        <td>{{ item.detailRemark }}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer-row">
    <span>制单人：{{ makerName }}</span>
    <span>收货人：________________</span>
  </div>
</div>
```

#### 9.2.5 前端文件变更清单

| 文件 | 变更说明 |
|------|---------|
| `src/api/config.js`（新建）| `getPrintConfig()` / `updatePrintConfig()` |
| `src/views/system/PrintConfig.vue`（新建）| 打印配置设置页面 |
| `src/views/production/index.vue` | 操作列新增"打印"按钮 + `handlePrint` 函数 |
| `src/views/shipment/index.vue` | 操作列新增"打印"按钮 + `handlePrint` 函数 |
| `src/router/index.js` | 新增打印设置路由（系统管理下）|

### 9.3 打印样式规范

```css
/* 工厂名称：大字居中，加粗 */
.factory-name { text-align: center; font-size: 14pt; font-weight: bold; margin-bottom: 2mm; }

/* 单据标题：居中，加粗 */
.doc-title { text-align: center; font-size: 12pt; font-weight: bold; margin-bottom: 3mm; }

/* 表格：全宽，细边框 */
.items-table { width: 100%; border-collapse: collapse; font-size: 9pt; }
.items-table th, .items-table td { border: 0.5pt solid #000; padding: 1mm 2mm; }
.items-table th { text-align: center; background: #f0f0f0; }

/* 品名规格列宽：占约50%，数量列约20% */
.items-table th:nth-child(2), .items-table td:nth-child(2) { width: 50%; }

/* 签名行：底部，两端分布 */
.footer-row { margin-top: 3mm; display: flex; justify-content: space-between; }
```

---

*文档版本：v1.7 | 最后更新：2026-03-26*
