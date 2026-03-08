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
- `GET /api/materials/search` — 按名称/编码搜索（下拉框用）
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
- `POST /api/receipts/import?mode=history` — Excel 批量导入
- `GET /api/receipt-items?receiptId=xxx` — 查询某单的明细列表

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

#### 3.2.4 返工单（/api/reworks）

**业务说明**：记录产品返工处理，返工完成后归还库存。

> ⚠️ **注意**：返工单目前仍为**单表设计**（一单一行），尚未改造为主从表。

**字段**：
- 返工单号、返工日期、客户
- 物料（直接关联）
- 工艺
- 返工数量、单价、金额
- 返工原因
- 返工状态（待返工/返工中/已完成）

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

**库存变更触发点**：
| 单据 | 触发动作 | 库存变化 |
|------|---------|---------|
| 收货单（正常模式）| 保存 | +quantity |
| 发货单 | 保存 | -quantity |
| 返工单 | 完成 | 视情况 |

> ⚠️ **历史导入不触发库存**：导入时使用 `mode=history` 参数，跳过库存更新

**接口**：
- `GET /api/inventory` — 查询库存列表（支持 keyword、customerId 筛选）
- `GET /api/inventory/log` — 查询库存流水
- `POST /api/inventory/init-from-statement` — 从对账单初始化库存（上线时用）

**库存初始化**（上线时执行一次）：
1. 从对账单 Excel 读取「上月结余」作为初始库存
2. 按物料+客户+工艺维度写入 inventory 表
3. 收货单/排产单历史数据用 mode=history 导入，不影响库存

---

### 3.4 对账单（/api/statements）

**业务说明**：每月末生成月度对账汇总，供客户确认。

**字段**：
- 对账单号、对账月份（YYYY-MM）、客户
- 收货数量、收货金额
- 发货数量、发货金额
- 状态（草稿/已确认）

**接口**：
- `GET /api/statements` — 分页查询
- `GET /api/statements/{id}` — 获取详情
- `POST /api/statements/generate` — 按月份+客户自动生成（汇总当月收货/发货数据）
- `PUT /api/statements/{id}/confirm` — 确认对账单
- `DELETE /api/statements/{id}` — 删除

---

### 3.5 报表（/api/reports）

- `GET /api/reports/monthly` — 月度汇总报表（按客户/物料统计收发数量金额）

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
3. **单号自动生成**：格式 `前缀+年月+4位流水号`，如 `SH202507-0001`
4. **逻辑删除**：所有业务表均用 `deleted` 字段标记删除，不物理删除
5. **时间字段**：create_time/update_time 由 MyBatis-Plus 自动填充

---

*文档版本：v1.0 | 最后更新：2026-03-08*
