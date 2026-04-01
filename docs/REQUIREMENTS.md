# sanitary-admin 需求文档

> **重要提示（面向 Agent）**：在对本项目进行任何开发或修改之前，请先阅读本文档和 [DESIGN.md](./DESIGN.md)，了解系统整体功能和设计，避免重复开发或破坏现有逻辑。

---

## 一、项目背景

sanitary-admin 是一个面向卫浴/五金电镀加工厂的**生产管理系统**，用于替代老系统（Excel 人工管理）。

主要业务流程：
1. 客户送来物料（含电镀工艺要求） → 工厂**收货**
2. 工厂安排生产 → **排产**
3. 生产完成 → 向客户**发货**
4. 有质量问题的货物 → **返工**
5. 每月汇总对账 → 生成**对账单**
6. 客户付款 → 记录**收款**
7. 全程追踪**库存**（在库数量）

---

## 二、用户角色

| 角色 | 权限说明 |
|------|---------|
| 超级管理员 | 全部权限，含用户/角色/菜单管理 |
| 普通用户 | 业务操作权限，不含系统管理 |

---

## 三、功能模块

### 3.1 基础档案管理

#### 3.1.1 客户管理（/api/customers）

**功能**：维护客户基本信息档案

**字段**：
- 客户编码（自动生成）
- 客户名称（必填，唯一）
- 客户类型（正式/试样等）
- 地址、联系人、联系电话
- 业务员
- 开户银行、银行账号、税号
- 状态（启用/禁用）

**接口**：
- `GET /api/customers` — 分页查询（支持 keyword、status 筛选）
- `GET /api/customers/all` — 获取全部客户（下拉框用）
- `POST /api/customers` — 新增
- `PUT /api/customers/{id}` — 修改
- `DELETE /api/customers/{id}` — 删除（逻辑删除）
- `PUT /api/customers/{id}/status` — 启用/禁用
- `POST /api/customers/import` — Excel 导入（兼容老系统格式）

**导入格式**（老系统 Excel）：
| 列 | 含义 |
|----|------|
| A(0) | 选择（忽略）|
| B(1) | 客户编号 |
| C(2) | 客户名称 |
| D(3) | 联系人 |
| E(4) | 电话 |
| F(5) | 地址 |
| G(6) | 业务员 |
| H(7) | 客户类型 |

---

#### 3.1.2 工艺管理（/api/processes）

**功能**：维护电镀工艺数据

**字段**：
- 工艺编码（自动生成）
- 工艺名称（必填，唯一）
- 工艺类别（电镀/喷粉/氧化等）
- 工艺性质
- 厚度要求
- 是否默认报价
- 优先级排序
- 状态（启用/禁用）

**接口**：
- `GET /api/processes` — 分页查询
- `GET /api/processes/all` — 获取全部工艺（下拉框用）
- `POST /api/processes` — 新增
- `PUT /api/processes/{id}` — 修改
- `DELETE /api/processes/{id}` — 删除
- `PUT /api/processes/{id}/status` — 启用/禁用
- `POST /api/processes/import` — Excel 导入

---

#### 3.1.3 物料管理（/api/materials）

**功能**：维护物料（产品）档案，物料与客户关联（同名物料不同客户各自独立）

**字段**：
- 物料编码（必填，唯一）
- 物料名称（必填）
- 型号规格
- 所属客户（必填）
- 默认单价
- 计量单位
- 状态

**接口**：
- `GET /api/materials` — 分页查询（支持 keyword、customerId、status 筛选）
- `GET /api/materials/search` — 按名称/编码搜索（下拉框用）；无关键词时返回前 100 条，有关键词时匹配返回前 100 条
- `POST /api/materials` — 新增
- `PUT /api/materials/{id}` — 修改
- `DELETE /api/materials/{id}` — 删除
- `PUT /api/materials/{id}/status` — 启用/禁用
- `POST /api/materials/import` — Excel 导入（按 material_code 去重，存在则更新）

**导入格式**（老系统 Excel，23166行）：
| 列 | 含义 |
|----|------|
| A(0) | 选择（忽略）|
| B(1) | 物料编码 |
| C(2) | 物料名称 |
| D(3) | 规格 |
| E(4) | 客户名称 |
| F(5) | 单价 |
| G(6) | 计量单位 |

---

### 3.2 业务单据管理

> **核心设计**：收货单、排产单、发货单均采用**主表+明细表**设计，一张单据可包含多个物料明细行。

#### 3.2.1 收货单（/api/receipts + /api/receipt-items）

**业务说明**：记录客户送货到工厂的事件，一单可包含多种物料。

**主表（receipt）字段**：
- 收货单号（自动生成，格式 SH+年月+流水号）
- 收货日期（必填）
- 客户（必填）
- 备注

**明细表（receipt_item）字段**：
- 所属收货单 ID + 单号（冗余）
- 物料（必填）
- 型号规格
- 工艺
- 收货来源（正常/返工/样品等）
- 收货数量（必填）
- 发货数量（关联发货，统计字段）
- 未发货数量
- 排产数量
- 入库数量
- 未入库数量
- 单价
- 金额（= 数量 × 单价）
- 客户单号
- 明细备注

**接口**：
- `GET /api/receipts` — 分页查询（支持 keyword、customerId、startDate、endDate 筛选）
- `GET /api/receipts/{id}` — 获取单条（含 items 明细）
- `POST /api/receipts` — 新增（body 包含 items 数组）
- `PUT /api/receipts/{id}` — 修改（body 包含 items 数组，先删后插）
- `DELETE /api/receipts/{id}` — 删除（同时删明细）
- `POST /api/receipts/import?mode=history` — Excel 批量导入（**同时按单价回填物料 default_price，≤10000 过滤**）
- `GET /api/receipt-items?receiptId=xxx` — 查询某单的明细列表
- `GET /api/receipt-items/latest-process?customerId=xxx&materialId=xxx` — 查询该客户+物料最近收货单里的工艺（前端新建单据时自动带出工艺用）

**导入格式**（老系统 Excel，65535行，一单多行）：
| 列 | 含义 | 映射 |
|----|------|------|
| B(1) | 收货单号 | 主单，空则延续上一行 |
| C(2) | 日期 | 主单（同单号取第一行）|
| D(3) | 客户名称 | 主单（同单号取第一行）|
| E(4) | 产品名称 | item.materialName |
| F(5) | 型号规格 | item.spec |
| G(6) | 工艺名称 | item.processName |
| H(7) | 收货来源 | item.receiptSource |
| I(8) | 收货数量 | item.quantity |
| J(9) | 发货数量 | item.shippedQty |
| K(10) | 未发货数量 | item.unshippedQty |
| L(11) | 单价 | item.unitPrice |
| M(12) | 客户单号 | item.customerOrderNo |
| N(13) | 备注 | receipt.remark（第一行）|
| O(14) | 明细备注 | item.detailRemark |
| P(15) | 排产数量 | item.plannedQty |
| Q(16) | 入库数量 | item.wareHousedQty |
| S(18) | 未入库数量 | item.unwareHousedQty |

**幂等性**：按 receipt_no 去重，已存在则 skip 整单。

**未设单价提醒**：收货单列表展开明细时，「收货来源=正常」且「单价=0」的明细行整行标红，单价列显示「未设价」提示，提醒用户补录价格。

---

#### 3.2.2 排产单（/api/productions + /api/production-items）

**业务说明**：工厂根据收货安排生产，记录排产计划。

**主表（production）字段**：
- 排产单号（自动生成，格式 PC+年月+流水号）
- 排产日期
- 客户
- 备注

**明细表（production_item）字段**：
- 产品名称（必填）
- 型号规格、工艺
- 收货类型（正常/返工等）
- 计量单位
- 排产数量（必填）
- 入库数量
- 未入库数量
- 委外单价、电镀单价、电镀金额
- 客户单号
- 排产方式（自制/委外）
- 明细备注

**接口**：
- `GET /api/productions` — 分页查询
- `GET /api/productions/{id}` — 获取单条（含 items）
- `POST /api/productions` — 新增
- `PUT /api/productions/{id}` — 修改
- `DELETE /api/productions/{id}` — 删除
- `POST /api/productions/import?mode=history` — Excel 批量导入
- `GET /api/production-items?productionId=xxx` — 查询明细

**导入格式**（老系统 Excel，4479行）：
| 列 | 含义 |
|----|------|
| B(1) | 排产单号（空延续）|
| C(2) | 日期（主单）|
| E(4) | 客户名称（主单）|
| F(5) | 产品名称 → item |
| G(6) | 工艺名称 → item |
| H(7) | 收货类型 → item |
| I(8) | 计量单位 → item |
| J(9) | 排产数量 → item.plannedQty |
| K(10) | 入库数量 → item.actualQty |
| L(11) | 未入库数量 → item.unwareHousedQty |
| M(12) | 委外单价 → item.outsourcePrice |
| N(13) | 电镀金额 → item.platingAmount |
| O(14) | 电镀单价 → item.platingPrice |
| P(15) | 明细备注 → item |
| Q(16) | 客户单号 → item |
| R(17) | 排产方式 → item.productionType |

---

#### 3.2.3 发货单（/api/shipments + /api/shipment-items）

**业务说明**：记录工厂向客户发货的事件。

**主表（shipment）字段**：
- 发货单号（自动生成）
- 发货日期
- 客户
- 状态（草稿/已确认）
- 备注

**明细表（shipment_item）字段**：
- 产品名称（必填）
- 型号规格、工艺
- 发货类型（良品/次品/返工品）
- 发货数量（必填）
- 单价、金额
- 客户单号、明细备注

**接口**：
- `GET /api/shipments` — 分页查询
- `GET /api/shipments/{id}` — 获取单条（含 items）
- `POST /api/shipments` — 新增（同时更新库存）
- `PUT /api/shipments/{id}` — 修改
- `DELETE /api/shipments/{id}` — 删除
- `GET /api/shipment-items?shipmentId=xxx` — 查询明细

> ⚠️ **库存联动**：发货时自动扣减库存（inventory 表），并记录库存流水（inventory_log）

---

#### 3.2.4 返工单（/api/reworks + /api/rework-items）

**业务说明**：记录产品返工处理，一单可包含多种物料，返工完成后归还库存。

> ✅ **已完成（2026-03-08）**：返工单已改造为主从表模式（rework + rework_item）

**主表（rework）字段**：
- 返工单号（自动生成，格式 FG+年月+流水号）
- 返工日期
- 客户
- 返工状态（待返工/返工中/已完成）
- 备注（remark）

**明细表（rework_item）字段（待建表）**：
- 所属返工单 ID + 单号（冗余）
- 物料（material_id/name/code）
- 型号规格
- 工艺（process_id/name）
- 返工数量
- 单价、金额
- 返工原因
- 明细备注

**接口**：
- `GET /api/reworks` — 分页查询
- `GET /api/reworks/{id}` — 获取单条（含 items 明细）
- `POST /api/reworks` — 新增（body 包含 items 数组）
- `PUT /api/reworks/{id}` — 修改
- `DELETE /api/reworks/{id}` — 删除
- `GET /api/rework-items?reworkId=xxx` — 查询明细

> ⚠️ **库存联动**：返工单完成时更新库存和流水

---

#### 3.2.5 收款记录（/api/payments）

**业务说明**：记录客户付款事件。

**字段**：
- 收款单号、收款日期、客户
- 金额
- 付款方式（转账/现金等）
- 参考单号（银行流水号等）
- 备注

---

### 3.3 库存管理（/api/inventory）

**业务说明**：持久化存储库存，不再实时计算。

**库存维度**：`material_id + customer_id + process_id`（三维唯一键）

**库存字段**：
- `quantity`：库存总数（当前在库总量）
- `rework_qty`：其中返工（当前库存中返工件数量）

**库存变更触发点**：
| 单据 | 触发动作 | 库存变化 | 返工库存变化 |
|------|---------|---------|-------------|
| 收货单（正常）| 保存 | +quantity | 不变 |
| 收货单（返工）| 保存 | +quantity | +quantity |
| 发货单 | 保存 | -quantity | 优先消耗（扣到0为止）|
| 返工单完成 | 状态变更 | 视类型增减 | 视类型增减 |

> ⚠️ **历史导入不触发库存**：导入时使用 `mode=history` 参数，跳过库存更新

**返工库存处理流程**：

```
业务场景：客户送来返工件，工厂处理后再发货

1. 返工收货（receipt_source = '返工'）：
   - 库存总数 +quantity
   - 返工库存 +quantity（标记为返工件）
   - 单价应为 0（不计费）

2. 发货出库：
   - 库存总数 -(quantity + defective_qty)
   - 返工库存优先消耗（扣到0为止）
   - 注：发货时不区分返工品还是良品，按 FIFO 先进先出原则优先消耗返工库存

3. 库存展示：
   - 库存总数：当前在库总量
   - 其中返工：当前库存中返工件的数量

4. 对账单返工扣减：
   - 本月收货中 receipt_source='返工' 的数量计入 rework_qty
   - 良品数量 = 发货合计 - 退回数量 - 返工数量
   - 良品金额 = 良品数量 × 单价
```

**接口**：
- `GET /api/inventory` — 查询库存列表（支持 keyword、customerId 筛选）
- `GET /api/inventory/log` — 查询库存流水
- `PUT /api/inventory/{id}` — **手动调整库存**（修改 quantity 和 rework_qty，记录 changeType=5 调整流水）
- `POST /api/inventory/rebuild` — 全量重建库存（从所有收货单/发货单重新计算，同时重建返工库存）

**库存重建规则（`POST /api/inventory/rebuild`）**：
- 清空 inventory 表，按时间顺序遍历所有收货单/发货单明细重新计算
- 收货时：累加 quantity 到库存总数；若为返工收货，同时累加 rework_qty
- 发货时：扣减 quantity 和 rework_qty（优先消耗返工库存）
- 最终库存 = Σ收货 - Σ发货
- **每次重建结果应无负库存**（≤5条为历史数据录入误差，可接受）

**期初库存补录（`scripts/init_opening_stock.py`）**：

背景：老系统仅迁移了 2025 年及之后的收货/发货数据，但 2025 年初部分物料已有在途存量。若不补录期初库存，月度对账结余会出现负数。

算法：对每个 `(material_id, customer_id, process_id)` 组合，按月累计计算累计缺口，取所有月份的最大值作为期初收货数量：
```sql
SELECT material_id, customer_id, process_id, CEIL(MAX(cum_deficit)) AS needed_init_qty
FROM (
  SELECT ..., SUM(ship_qty) OVER (... ORDER BY ym) - SUM(recv_qty) OVER (... ORDER BY ym) AS cum_deficit
  ...
) cumulative
GROUP BY material_id, customer_id, process_id
HAVING needed_init_qty > 0
```

执行结果：在 `receipt_date = 2024-12-31` 插入一批收货单，单号格式为 `RH-INIT-{customerId}`，共约 35 张，853 条明细。

幂等性：
- 收货单级别：已存在 `RH-INIT-{customerId}` 的收货单则直接复用其 id（不重复插入主单）
- 明细级别：按 `(receipt_id, material_id, process_id)` 检查，已存在的明细行跳过
- 可安全重复执行

---

### 3.4 对账单（/api/statements）

**业务说明**：每月末生成月度对账汇总，供客户确认。采用**主从表设计**，与老系统 Excel 格式一致——主表记录汇总数据，明细表按物料逐行展示（上月结余、本月收发、本月结余、单价、金额等）。

**主表字段**：
- 对账单号（自动生成，格式 DZ+年月+流水）
- 对账月份（YYYY-MM）
- 客户
- 收货合计数量/金额（由明细汇总）
- 发货合计数量/金额（由明细汇总）
- 状态（草稿/已确认）

**明细表（statement_item）字段**：
- 所属对账单 ID + 单号（冗余）
- 物料（material_id/code/name）、工艺（process_id/name）
- 上月结余数量（prev_balance_qty）
- 本月收货合计（receipt_qty）
- 本月发货合计（shipment_qty）
- 原件退回数量（defective_qty）
- 返工数量（rework_qty）—— 发货中返工来源的数量
- 本月结余数量（curr_balance_qty）
- 单价（unit_price）
- 良品金额（goods_amount）—— 良品数量 × 单价
- 备注

**核心计算公式**：
- 良品数量 = 发货合计 - 退回数量 - 返工数量
- 良品金额 = 良品数量 × 单价
- 本月结余 = 上月结余 + 本月收货 - 本月发货

**接口**：
- `GET /api/statements` — 分页查询
- `GET /api/statements/{id}` — 获取详情（含 items 明细）
- `POST /api/statements/generate` — 按月份+客户自动生成（重新计算汇总和明细，**幂等：已存在则删旧明细重建**）
- `POST /api/statements/generate-all` — 批量生成所有客户×月份的对账单（**跳过已存在的**，幂等可重复执行）
- `PUT /api/statements/{id}/confirm` — 确认对账单
- `DELETE /api/statements/{id}` — 删除（含明细）
- `POST /api/statements/import` — **从老系统 Excel 导入历史对账单（含物料明细）**
- `GET /api/statements/export` — 导出对账单 Excel
- `GET /api/statement-items?statementId=xxx` — 查询某单明细

**对账单生成逻辑（`generate` 接口）**：

1. 查询该客户该月所有 `status=1` 的收货单明细 `receipt_item`
2. 查询该客户该月所有 `status=1` 的发货单明细 `shipment_item`
3. 按 `(material_id, process_id)` 分组，为每组生成一行 `statement_item`：
   - `receipt_qty` = `SUM(receipt_item.quantity)`
   - `shipment_qty` = `SUM(shipment_item.quantity + defective_qty)`（良品+退回合计）
   - `defective_qty` = `SUM(shipment_item.defective_qty)`
   - `rework_qty` = 关联收货单 `receipt_source='返工'` 的发货数量
   - `goods_amount` = 良品数量 × 单价
   - `unit_price` = 来自 receipt_item 或 shipment_item，fallback 到 material.default_price
   - `prev_balance_qty` = 月初前（`< monthStart`）所有收货合计 − 所有发货合计（直接聚合历史数据，**不依赖上个月对账单的 curr_balance_qty**）
   - `curr_balance_qty` = `prev_balance_qty + receipt_qty - shipment_qty`
4. 主表汇总 = `SUM(明细行)`
5. 若对账单已存在（customerId + statementMonth 唯一）：先软删除旧明细，再重建

> ⚠️ **prevBalanceQty 计算方式**：直接聚合月初之前的全部历史收发数据，**不**依赖前月对账单链式传递，避免批量生成时因顺序问题导致结余出错。

**业务约束（不可违背）**：
- `curr_balance_qty` 必须 ≥ 0
- `curr_balance_qty` = `prev_balance_qty + receipt_qty - shipment_qty`（等式必须成立）
- 若出现负结余，说明期初库存未补录或数据录入有误

**对账单 Excel 导入格式**（老系统，122行，前2行表头，单一客户）：
| 列 | 含义 | 映射字段 |
|----|------|---------|
| A(0) | 产品代码 | material_code → 查 material_id |
| B(1) | 产品名称 | material_name |
| C(2) | 工艺要求 | process_name → 查 process_id |
| D(3) | 上月结余数量 | prev_balance_qty |
| E(4) | 本月收货（正常）| 忽略 |
| F(5) | 本月收货合计 | receipt_qty |
| G(6) | 本月发货（良品）| 忽略 |
| H(7) | 本月发货（原件退回）| defective_qty |
| I(8) | 本月发货合计 | shipment_qty |
| J(9) | 本月结余数量 | curr_balance_qty |
| K(10) | 单价 | unit_price |
| L(11) | 良品金额 | goods_amount |
| M(12) | 合计金额 | shipment_amount |
| N(13) | 备注 | remark |

**导入接口参数**：
- `file`：Excel 文件
- `customerId`：客户 ID（必填，因 Excel 无客户列）
- `statementMonth`：对账月份 YYYY-MM（必填）

> ⚠️ **导入幂等性**：按 customerId + statementMonth 去重，已存在则 skip 整单

> ⚠️ **跳过规则**：col0 = "合计" 或 "应收金额" 的行跳过；前2行表头跳过

---

---

### 3.6 系统管理

#### 3.6.1 用户管理（/api/users）
- 用户名、姓名、密码、角色、状态
- CRUD + 启用/禁用

#### 3.6.2 角色管理（/api/roles）
- 角色名称、关联菜单权限
- CRUD

#### 3.6.3 菜单管理（/api/menus）
- 树形结构菜单，支持多级
- CRUD

#### 3.6.4 认证（/api/auth）
- `POST /api/auth/login` — 登录，返回 JWT Token
- Token 在请求头 `Authorization: Bearer <token>` 携带

---

### 3.5 Excel 数据导出

> 所有列表查询页面均支持按当前筛选条件导出 Excel，主从表数据在同一文件中清晰展示。

#### 3.5.1 导出范围

**基础档案管理**：

| 模块 | 导出接口 | 文件名 |
|------|---------|--------|
| 客户管理 | `GET /api/customers/export` | `客户档案_YYYYMMDD.xlsx` |
| 工艺管理 | `GET /api/customers/export` → `GET /api/processes/export` | `工艺档案_YYYYMMDD.xlsx` |
| 物料管理 | `GET /api/materials/export` | `物料档案_YYYYMMDD.xlsx` |

**生产管理**：

| 模块 | 导出接口 | 文件名 |
|------|---------|--------|
| 收货单 | `GET /api/receipts/export` | `收货单_YYYYMMDD.xlsx` |
| 排产单 | `GET /api/productions/export` | `排产单_YYYYMMDD.xlsx` |
| 发货单 | `GET /api/shipments/export` | `发货单_YYYYMMDD.xlsx` |
| 返工单 | `GET /api/reworks/export` | `返工单_YYYYMMDD.xlsx` |
| 库存 | `GET /api/inventory/export` | `库存明细_YYYYMMDD.xlsx` |

**财务管理**：

| 模块 | 导出接口 | 文件名 |
|------|---------|--------|
| 收款记录 | `GET /api/payments/export` | `收款记录_YYYYMMDD.xlsx` |
| 对账单 | `GET /api/statements/export` | `对账单_YYYYMMDD.xlsx` |

#### 3.5.2 导出接口规范

- **参数**：与列表查询接口参数完全一致（`keyword`、`customerId`、`startDate`、`endDate` 等），导出当前筛选结果（不分页，导出全量）
- **响应**：二进制流，`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，`Content-Disposition: attachment;filename=<URL编码文件名>`
- **数据量限制**：单次导出最多 50000 行明细行（超出时截断并在表格末尾注明"数据已截断"）

#### 3.5.3 Excel 样式规范

**通用格式**：
- 第一行：表格标题行（合并单元格，字体加粗16号，居中，背景色 `#2F75B6`，白色字体）
- 第二行：列标题行（字体加粗11号，居中，背景色 `#D6E4F0`，深蓝色字体 `#1F3864`）
- 数据行：
  - 奇数行背景 `#FFFFFF`，偶数行背景 `#F5F9FF`（浅蓝色交替）
  - 字体：宋体11号，垂直居中
  - 数字列：右对齐，金额保留2位小数，数量保留2位小数
  - 日期列：居中，格式 `yyyy-mm-dd`
  - 文字列：左对齐
- 所有单元格加细边框（黑色 `#000000`）
- 冻结第1、2行（滚动时标题固定可见）
- 每列宽度根据内容自适应（最小8，最大40个字符宽度）

**主从表格式（收货单/排产单/发货单/返工单/对账单）**：

主单行与明细行在同一 Sheet 内，采用**"主单行 + 缩进明细行"**的展示方式：

```
行1: 表格总标题（合并全列）
行2: 列标题（所有列）
行3: ▶ 主单信息（单号、日期、客户...）+ 明细列空白   ← 主单行，背景蓝色 #BDD7EE
行4:   └─ 明细行1（单号冗余、物料、规格、工艺、数量...）← 缩进，背景白色/浅蓝交替
行5:   └─ 明细行2（...）
行6: ▶ 主单信息（第2张单）
行7:   └─ 明细行1
...
最后行: 合计行（合并单元格，对数字列求和，背景 #FFF2CC）
```

**主单列**（每张单号只在第一行显示，后续明细行该列空白）：
- 单号、日期、客户名称、状态、备注

**明细列**（每明细行展示）：
- 物料编码、物料名称、规格、工艺、数量相关字段、单价、金额等

#### 3.5.4 各模块导出字段详细设计

**客户档案**（单表，无主从）：

| 列 | 字段 | 说明 |
|----|------|------|
| A | 客户编码 | customer_code |
| B | 客户名称 | customer_name |
| C | 客户类型 | customer_type |
| D | 联系人 | contact_person |
| E | 联系电话 | contact_phone |
| F | 地址 | address |
| G | 业务员 | salesperson |
| H | 开户银行 | bank_name |
| I | 银行账号 | bank_account |
| J | 税号 | tax_no |
| K | 状态 | status（启用/禁用）|
| L | 创建时间 | create_time |
| M | 备注 | remark |

**工艺档案**（单表）：

| 列 | 字段 | 说明 |
|----|------|------|
| A | 工艺编码 | process_code |
| B | 工艺名称 | process_name |
| C | 工艺类别 | process_category |
| D | 工艺性质 | process_nature |
| E | 厚度要求 | thickness_req |
| F | 是否默认报价 | default_quote（是/否）|
| G | 优先级 | priority_no |
| H | 状态 | status（启用/禁用）|

**物料档案**（单表）：

| 列 | 字段 | 说明 |
|----|------|------|
| A | 物料编码 | material_code |
| B | 物料名称 | material_name |
| C | 型号规格 | spec |
| D | 所属客户 | customer_name |
| E | 默认单价 | default_price（数字右对齐）|
| F | 计量单位 | unit |
| G | 状态 | status（启用/禁用）|

**收货单**（主从表）：

主单列：收货单号、收货日期、客户名称、备注
明细列：物料编码、物料名称、型号规格、工艺名称、收货来源、收货数量、发货数量、未发货数量、排产数量、入库数量、未入库数量、单价、金额、客户单号、明细备注

| 列标题 | 字段 | 对齐 |
|--------|------|------|
| 收货单号 | receipt_no | 左 |
| 收货日期 | receipt_date | 中 |
| 客户名称 | customer_name | 左 |
| 备注 | remark | 左 |
| 物料编码 | material_code | 左 |
| 物料名称 | material_name | 左 |
| 型号规格 | spec | 左 |
| 工艺名称 | process_name | 左 |
| 收货来源 | receipt_source | 中 |
| 收货数量 | quantity | 右 |
| 发货数量 | shipped_qty | 右 |
| 未发货数量 | unshipped_qty | 右 |
| 排产数量 | planned_qty | 右 |
| 入库数量 | ware_housed_qty | 右 |
| 未入库数量 | unware_housed_qty | 右 |
| 单价 | unit_price | 右 |
| 金额 | amount | 右 |
| 客户单号 | customer_order_no | 左 |
| 明细备注 | detail_remark | 左 |

**排产单**（主从表）：

主单列：排产单号、排产日期、客户名称、备注
明细列：物料编码、物料名称、型号规格、工艺名称、收货类型、计量单位、排产数量、入库数量、未入库数量、委外单价、电镀单价、电镀金额、排产方式、客户单号、明细备注

**发货单**（主从表）：

主单列：发货单号、发货日期、客户名称、状态、备注
明细列：物料编码、物料名称、型号规格、工艺名称、发货类型、发货数量（良品）、废品/退回数量、单价、金额、客户单号、明细备注

**返工单**（主从表）：

主单列：返工单号、返工日期、客户名称、返工状态、备注
明细列：物料编码、物料名称、型号规格、工艺名称、返工数量、单价、金额、返工原因、明细备注

**收款记录**（单表）：

| 列 | 字段 | 说明 |
|----|------|------|
| A | 收款单号 | payment_no |
| B | 收款日期 | payment_date |
| C | 客户名称 | customer_name |
| D | 金额 | amount（数字，右对齐）|
| E | 收款方式 | payment_method |
| F | 参考单号 | reference_no |
| G | 备注 | remark |

最后一行增加"合计"行，对金额列求和。

**对账单**（主从表）：

主单列：对账单号、对账月份、客户名称
明细列：物料编码、物料名称、工艺名称、上月结余、本月收货、发货合计、良品数量、返工数量、本月结余、单价、良品金额、备注

> **核心公式**：良品数量 = 发货合计 - 退回数量 - 返工数量

**库存**（单表）：

| 列 | 字段 | 说明 |
|----|------|------|
| A | 物料编码 | material_code |
| B | 物料名称 | material_name |
| C | 型号规格 | spec |
| D | 客户名称 | customer_name |
| E | 工艺名称 | process_name |
| F | 库存总数 | quantity（数字）|
| G | 其中返工 | rework_qty（数字）|
| H | 最后收货时间 | last_receive_time |
| I | 最后发货时间 | last_ship_time |

#### 3.5.5 前端导出交互

- 在各列表页的操作栏中新增"导出 Excel"按钮（与"新增"、"导入"按钮并排）
- 点击后，按**当前筛选条件**请求后端导出接口，浏览器自动下载文件
- 导出期间按钮显示 loading 状态，防止重复点击
- 文件名包含导出日期，格式：`<模块名>_YYYYMMDD.xlsx`

---

### 3.7 打印单据功能

> 支持排产单、发货单两个模块的单据打印，直接调用浏览器打印对话框输出到针式打印机。

#### 3.7.1 功能概述

- 在排产单和发货单列表页，每行操作列新增"打印"按钮
- 点击打印按钮后，用 `window.open('', '_blank')` 新开窗口，写入格式化 HTML，再调用 `win.print()` 触发打印对话框
- 单据上的**工厂名称**和**签名栏标签**等均可在系统配置中维护

#### 3.7.2 纸张规格

- **物理纸张**：241mm × 120mm（针式打印机标准连续纸，已去掉上下孔戳区域）
- **CSS 页面设置**：`@page { size: 241mm 120mm; margin: 5mm; }`
- **可用内容区域**：约 231mm × 110mm

#### 3.7.3 排产单打印格式（已实现）

**布局**：前端按行数公式将明细项预分页，每页生成一个独立 `<table>`，用 `page-break-after: always` 分隔。

**每页表格结构**：
```
┌──────────────────────────────────────────────┐
│   【docTitle：工厂名称/单据名称】（居中大字）    │  ← thead 第1行
├──────────────────────────────────────────────┤
│ 排产日期：__  单号：__  班别：__  客户：__      │  ← thead 第2行（4字段均分一行）
├─────┬──────┬─────┬──────┬──────┬──────────────┤
│客户 │品名  │规格 │工艺  │数量  │类型│1良│2良│3良│不良│备注│  ← thead 列标题（含完成情况合并列）
├─────┼──────┼─────┼──────┼──────┼──────────────┤
│     │      │     │      │      │              │  ← tbody 数据行（ROWS_PER_PAGE≈10行）
│ … 空白填充行（&nbsp;保持行高） …              │
│ 合计（colspan=4）  + totalQty    │            │  ← 末页 tbody 最后一行
├──────────────────────────────────────────────┤
│ 备注：…（来自 detail.remark）                │  ← tfoot 固定备注行
└──────────────────────────────────────────────┘
签名栏（div flex，3个签名位，位于表格下方）
```

**分页行数计算公式**（JavaScript，前端）：
- 纸张可用高度 110mm，1pt = 0.353mm，浏览器行高系数 LH = 1.2
- `titleH = 13pt × 0.353 × 1.2 + 4mm ≈ 9.5mm`
- `metaH = 9pt × 0.353 × 1.2 + 2mm ≈ 5.8mm`
- `hdrH（含2行列标题）= 8.5pt × 0.353 × 1.2 + 2.5mm ≈ 6.1mm` × 2
- `remarkH（tfoot备注）≈ 6.1mm`
- `sigH（签名div）≈ 7mm`
- `ROWS_PER_PAGE = floor((110 - overhead - 3mm缓冲) / rowH)` ≈ **10行/页**

**字段映射**：
- `docTitle`：系统配置 `printTitleProduction`
- 排产日期：`production.productionDate`
- 单号：`production.productionNo`
- 客户：`production.customerName`
- 品名/规格/工艺/数量/类型/明细备注：来自 `production_item`
- 合计数量：所有明细 `plannedQty` 之和
- tfoot 备注：`production.remark`
- 签名栏标签：系统配置 `printSignature1Label`/`printSignature2Label`/`printSignature3Label`

#### 3.7.4 发货单打印格式（已实现）

**布局**：同排产单，前端预分页，每页一个独立 `<table>`，`page-break-after: always` 分隔。

**每页表格结构**：
```
┌──────────────────────────────────────────────┐
│   【docTitle：工厂名称/单据名称】（居中大字）    │  ← thead 第1行
├──────────────────────────────────────────────┤
│ 客户：__  发货日期：__  单号：__              │  ← thead 第2行
├──┬──────┬────┬──┬──────┬──┬───┬───┬────┬─────┤
│序│品名  │规格│单│工艺  │类│良品│不│原件│备注 合计│  ← thead 列标题
├──┼──────┼────┼──┼──────┼──┼───┼───┼────┼─────┤
│  │      │    │  │      │  │   │   │    │     │  ← tbody 数据行
│ … 空白填充行 …                              │
│ 合计行（良品合计 + 废品合计）                 │  ← 末页
├──────────────────────────────────────────────┤
│ 备注：…（来自配置 printDeliveryRemark）       │  ← tfoot
└──────────────────────────────────────────────┘
签名栏（收货单位 / 仓管 / 制单人，div flex）
```

**字段映射**：
- `docTitle`：系统配置 `printTitleDelivery`
- 客户：`shipment.customerName`
- 发货日期：`shipment.shipmentDate`
- 单号：`shipment.shipmentNo`
- 序号/品名/规格/单位/工艺/类型/良品数量/废品数量/明细备注：来自 `shipment_item`
- tfoot 备注：系统配置 `printDeliveryRemark`
- 签名栏标签：系统配置 `printSignature1Label`/`printSignature2Label`/`printMakerLabel`/`makerName`

#### 3.7.5 打印配置管理

**配置项**（存储在 `sys_config` 表，通过 `GET/PUT /api/config/print` 读写）：

| 配置键 | 说明 |
|--------|------|
| `printTitleProduction` | 排产单标题（大字抬头）|
| `printTitleDelivery` | 发货单标题（大字抬头）|
| `printCompanyName` | 公司名称 |
| `printCompanyPhone` | 公司电话/传真 |
| `printCompanyAddress` | 公司地址 |
| `printContact1` | 联系人1（发货单用）|
| `printContact2` | 联系人2（发货单用）|
| `printSignature1Label` | 签名栏1标签（排产单：生产班长；发货单：收货单位）|
| `printSignature2Label` | 签名栏2标签（排产单：仓管；发货单：仓管）|
| `printSignature3Label` | 签名栏3标签（排产单：签名）|
| `printMakerLabel` | 制单人标签 |
| `makerName` | 制单人姓名 |
| `printDeliveryRemark` | 发货单固定备注（多行文本，`\n` 分隔）|

**前端入口**：系统管理 → 打印设置

---

## 四、非功能需求

### 4.1 数据导入兼容性
- 兼容老系统 Excel 格式（.xls/.xlsx 均支持）
- 老系统 xls 文件头可能损坏，需先用 Python xlrd 转换为 xlsx
- 导入接口支持 `mode=history` 参数，跳过库存更新

### 4.2 幂等性
- 所有导入接口按业务单号去重：已存在则 skip，不报错
- 返回统计：`{success, skip, fail, errors[前20条]}`

### 4.3 数据量
- 客户：~500条
- 工艺：~160条
- 物料：~23000条
- 收货单：~10000单（明细行 ~65000条）
- 排产单：~600单（明细行 ~4500条）

### 4.4 性能
- 大文件导入（收货单 22MB）需分批（每批 3000 行），前端按批次上传
- 文件上传大小限制：100MB（已配置）

---

## 五、业务规则

1. **物料与客户强关联**：同名物料属于不同客户时视为不同物料
2. **库存三维唯一**：material_id + customer_id + process_id 组合唯一（processId=0 表示无工艺）
3. **单号自动生成**：格式 `前缀+年月(YYYYMM)+"-"+4位流水号`，如 `SH202507-0001`
4. **逻辑删除**：所有业务表均用 `deleted` 字段标记删除，不物理删除；**查询时必须加 `deleted=0` 过滤**，否则会统计到已软删除的历史数据
5. **时间字段**：create_time/update_time 由 MyBatis-Plus 自动填充
6. **对账单结余约束**：
   - `curr_balance_qty = prev_balance_qty + receipt_qty - shipment_qty`（等式必须成立）
   - `curr_balance_qty ≥ 0`（本月结余不能为负）
   - `shipment_qty ≤ prev_balance_qty + receipt_qty`（本月发货不超过可用库存）
7. **发货含废品**：发货单中 `defective_qty` 代表原件退回数量，计入发货合计（扣库存），但不计入良品金额
8. **期初库存必要性**：历史数据迁移时，若老系统 2025 年前已有在途库存，必须通过 `scripts/init_opening_stock.py` 补录期初收货（`RH-INIT-{customerId}`），否则对账单结余会出现负数
9. **数据初始化顺序**：客户/工艺/物料 → 收货单(history) → 排产单(history) → 发货单(history) → 收款单 → **期初库存补录** → **重建库存** → **批量生成对账单**

---

*文档版本：v1.7 | 最后更新：2026-03-26*
