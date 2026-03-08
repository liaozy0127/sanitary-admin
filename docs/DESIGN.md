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
customer ◄──── rework           (返工单主表，待改造)
rework   ◄──── rework_item      (返工单明细，待建表)
customer ◄──── payment          (收款记录)
customer ◄──── statement        (对账单)
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
| quantity | DECIMAL(12,2) | 发货数量 |
| unit_price/amount | | 单价/金额 |
| customer_order_no | | 客户单号 |
| detail_remark | | 明细备注 |

#### rework（返工单主表）⏳ 待改造

> 当前为单表设计，待改造为主从表。改造后字段：

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

#### rework_item（返工单明细表）⏳ 待建表

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

#### statement_item（对账单明细表）⏳ 待建表

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
| prev_balance_qty | DECIMAL(12,2) | 上月结余数量（col3，库存初始化用）|
| receipt_qty | DECIMAL(12,2) | 本月收货合计（col5）|
| shipment_qty | DECIMAL(12,2) | 本月发货合计（col8）|
| curr_balance_qty | DECIMAL(12,2) | 本月结余数量（col9）|
| unit_price | DECIMAL(10,4) | 单价（col10）|
| shipment_amount | DECIMAL(12,2) | 发货合计金额（col12）|
| remark | VARCHAR(500) | 备注（col13）|
| deleted | TINYINT | 逻辑删除 |
| create_time | DATETIME | |
| update_time | DATETIME | |

> **老系统 Excel 列映射**（用于 `POST /api/statements/import`）：
> - col0: 产品代码，col1: 产品名称，col2: 工艺要求
> - col3: 上月结余 → `prev_balance_qty`（库存初始化用）
> - col5: 本月收货合计 → `receipt_qty`
> - col8: 本月发货合计 → `shipment_qty`
> - col9: 本月结余 → `curr_balance_qty`
> - col10: 单价 → `unit_price`
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

### 5.3 库存初始化流程（上线一次性操作）

```
1. 准备对账单 Excel（包含「上月结余」col3列）
2. POST /api/inventory/init-from-statement（传入 Excel 文件）
   ├── 若 inventory 表已有数据 → 拒绝执行，返回错误（防止重复初始化）
   ├── 按物料代码(col0)+工艺(col2) 查找 material_id 和 process_id
   └── 写入 inventory 表（quantity = 上月结余数量）
3. 之后收货单/排产单/对账单历史数据用 mode=history 导入，不影响库存
4. 新增的收货/发货单正常触发库存更新

注意：对账单导入（POST /api/statements/import）和库存初始化是两个独立接口，
互不触发。历史对账单数据导入不影响库存。
```

---

## 六、已知问题和待办

| 问题 | 优先级 | 状态 |
|------|--------|------|
| 返工单改造为主从表（rework + rework_item）| 高 | **待开发** |
| 对账单历史数据导入接口（POST /api/statements/import）| 高 | **待开发** |
| 库存初始化接口防重复执行（已有数据时拒绝）| 高 | **待修复** |
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

---

*文档版本：v1.0 | 最后更新：2026-03-08*
