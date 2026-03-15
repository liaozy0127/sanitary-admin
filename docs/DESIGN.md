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
| prev_balance_qty | DECIMAL(12,2) | 上月结余数量（col3）|
| receipt_qty | DECIMAL(12,2) | 本月收货合计（col5）|
| shipment_qty | DECIMAL(12,2) | 本月发货合计（col8，良品+退回）|
| defective_qty | DECIMAL(12,2) | 原件退回数量（col7）|
| curr_balance_qty | DECIMAL(12,2) | 本月结余数量（col9）|
| unit_price | DECIMAL(10,4) | 单价（col10）|
| goods_amount | DECIMAL(12,2) | 良品金额（col11）|
| shipment_amount | DECIMAL(12,2) | 发货合计金额（col12）|
| remark | VARCHAR(500) | 备注（col13）|
| deleted | TINYINT | 逻辑删除 |
| create_time | DATETIME | |
| update_time | DATETIME | |

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
| quantity | DECIMAL(12,2) | 当前库存数量 |
| last_receive_time | DATETIME | 最后收货时间 |
| last_ship_time | DATETIME | 最后发货时间 |

> **唯一键**：(material_id, customer_id, process_id)

> **并发安全**：库存更新使用原子 SQL `UPDATE inventory SET quantity = quantity + ? WHERE ...`，不使用 SELECT + UPDATE 模式。

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
├── views/rework/         — 返工单管理
├── views/payment/        — 收款管理
├── views/statement/      — 对账单管理
├── views/inventory/      — 库存管理
├── views/report/         — 报表
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
2. aggregateReceiptQty()：
   SELECT material_id, customer_id, COALESCE(process_id,0), SUM(quantity)
   FROM receipt_item JOIN receipt
   WHERE deleted=0 AND status=1
   GROUP BY (material_id, customer_id, COALESCE(process_id,0))
   -- 字符串维度字段用 MAX() 取一个值（不参与 GROUP BY）

3. aggregateShipmentQty()：
   SELECT material_id, customer_id, COALESCE(process_id,0),
          SUM(quantity + COALESCE(defective_qty,0))   -- 良品+退回均扣库存
   FROM shipment_item JOIN shipment
   WHERE deleted=0 AND status=1
   GROUP BY (material_id, customer_id, COALESCE(process_id,0))

4. 合并两个 Map：inventory.quantity = receipt_qty - ship_qty
5. 批量 INSERT inventory（upsert）
6. 返回统计：receiptGroups / shipmentGroups / inventoryRecords

注意：重建后负库存 ≤ 5 条（历史数据录入误差，非系统问题）
```

### 5.5 对账单生成流程（`POST /api/statements/generate`）

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
| 收货单分批上传（前端按3000行拆分）| 中 | 待开发 |
| inventory 查询接口带 keyword 参数时返回 400 | 中 | 待修复 |

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

---

*文档版本：v1.3 | 最后更新：2026-03-15*
