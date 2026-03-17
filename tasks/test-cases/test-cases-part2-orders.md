# 测试用例文档 Part 2 — 订单模块

> 系统：sanitary-admin 卫浴/五金电镀加工厂生产管理系统
> 后端端口：8080
> 响应格式：`{"code":200,"msg":"success","data":{}}`
> 鉴权方式：所有接口需携带 `Authorization: Bearer <token>`
> 登录获取 token：`POST http://localhost:8080/api/auth/login {"username":"admin","password":"admin123"}`

---

## 一、收货单测试（TC-RECEIPT-xxx）

| 用例编号 | 场景名称 | 优先级 | 状态 |
|----------|----------|--------|------|
| TC-RECEIPT-001 | 新增收货单含多条明细，验证单号格式 | P0 | - |
| TC-RECEIPT-002 | 修改收货单（明细先删后插） | P0 | - |
| TC-RECEIPT-003 | 删除收货单（主表和明细均逻辑删除） | P0 | - |
| TC-RECEIPT-004 | 按日期范围+客户筛选分页查询 | P1 | - |
| TC-RECEIPT-005 | 获取单条详情（含 items 明细） | P1 | - |
| TC-RECEIPT-006 | 明细未设价标红规则验证 | P1 | - |
| TC-RECEIPT-007 | 导入 Excel（mode=history）幂等性验证 | P0 | - |
| TC-RECEIPT-008 | mode=history 导入后库存不触发 | P0 | - |
| TC-RECEIPT-009 | 查询最近工艺接口 | P1 | - |

---

### TC-RECEIPT-001：新增收货单含多条明细，验证单号格式 SH+年月+4位流水

**前置条件**：系统已存在客户（customer_id=1），物料（material_id=1，material_id=2），工艺（process_id=1）

**请求**：
```
POST /api/receipts
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "receiptDate": "2026-03-17",
  "remark": "测试收货单",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "receiptSource": "正常",
      "quantity": 100,
      "unitPrice": 5.50,
      "detailRemark": "明细备注1"
    },
    {
      "materialId": 2,
      "processId": 1,
      "receiptSource": "正常",
      "quantity": 200,
      "unitPrice": 3.20,
      "detailRemark": "明细备注2"
    }
  ]
}
```

**前置步骤（记录收货前库存）**：
```
GET /api/inventory?customerId=1
记录 material_id=1 + process_id=1 的当前 quantity = Q1
记录 material_id=2 + process_id=1 的当前 quantity = Q2
（若库存记录不存在则视为 Q1=0, Q2=0）
```

**预期结果**：
- HTTP 200
- `data.receiptNo` 匹配正则 `^SH202603-\d{4}$`（当月第一条则为 SH202603-0001）
- `data.items` 长度为 2
- `data.items[0].quantity` = 100，`data.items[0].unitPrice` = 5.50
- `data.items[1].quantity` = 200，`data.items[1].unitPrice` = 3.20
- 数据库 `receipt` 主表新增一条记录，`deleted` = 0
- 数据库 `receipt_item` 新增两条记录，均 `deleted` = 0

**库存联动验证**（收货后执行）：
```
GET /api/inventory?customerId=1
```
- material_id=1 + process_id=1 的 quantity = Q1 + 100
- material_id=2 + process_id=1 的 quantity = Q2 + 200

**库存流水验证**：
```
GET /api/inventory/log?page=1&size=10
```
- 最新两条流水 `changeType` = 1（收货）
- 对应 `changeQty` 分别为 +100、+200
- `afterQty` = 对应物料变更后的库存值

---

### TC-RECEIPT-002：修改收货单（明细先删后插）

**前置条件**：TC-RECEIPT-001 已执行，获取到 receiptId（假设为 1）和原有 items

**请求**：
```
PUT /api/receipts/1
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "receiptDate": "2026-03-17",
  "receiptSource": "正常",
  "remark": "修改后的备注",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "quantity": 150,
      "unitPrice": 6.00,
      "remark": "修改后明细1"
    },
    {
      "materialId": 3,
      "processId": 1,
      "quantity": 50,
      "unitPrice": 10.00,
      "remark": "新增明细3"
    }
  ]
}
```

**预期结果**：
- HTTP 200
- `data.remark` = "修改后的备注"
- `data.items` 长度为 2，原 material_id=2 的明细不再出现
- `data.items` 中包含 material_id=1（quantity=150）和 material_id=3（quantity=50）
- 数据库中原明细记录应被逻辑删除（`deleted`=1）或物理删除，新明细正确插入

---

### TC-RECEIPT-003：删除收货单（主表和明细均逻辑删除）

**前置条件**：系统中存在 receiptId=1 的收货单，且有对应明细

**请求**：
```
DELETE /api/receipts/1
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `code` = 200，`msg` = "success"
- 数据库 `receipt` 表中 id=1 的记录 `deleted` = 1（逻辑删除）
- 数据库 `receipt_item` 表中关联 receipt_id=1 的所有明细 `deleted` = 1（逻辑删除）
- 再次 GET /api/receipts/1 返回 404 或 `data` 为 null

---

### TC-RECEIPT-004：按日期范围+客户筛选分页查询

**前置条件**：系统中存在多条收货单，包含不同日期和不同客户的数据

**请求**：
```
GET /api/receipts?customerId=1&startDate=2026-03-01&endDate=2026-03-31&page=1&size=10
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `data.records` 为数组，所有记录均满足：
  - `customerId` = 1
  - `receiptDate` 在 2026-03-01 到 2026-03-31 之间（含边界）
- `data.total` 为符合条件的总记录数
- `data.current` = 1，`data.size` = 10
- 返回记录均未被逻辑删除（`deleted`=0）
- 结果按 `receiptDate` 倒序排列（最新在前）

---

### TC-RECEIPT-005：获取单条详情（含 items 明细）

**前置条件**：系统中存在 receiptId=2 的收货单，且有 3 条明细

**请求**：
```
GET /api/receipts/2
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `data.id` = 2
- `data.receiptNo` 不为空，格式符合 `SH\d{10}`
- `data.customerName` 不为空（关联查询客户名称）
- `data.items` 为数组，长度 = 3
- 每条 item 包含：`materialId`、`materialName`、`processId`、`processName`、`quantity`、`unitPrice`
- `data.items` 中不包含已逻辑删除的明细

---

### TC-RECEIPT-006：明细未设价标红规则——receipt_source=正常 且 unit_price=0 时接口返回数据中该行可被前端识别

**前置条件**：新增一条收货单，receipt_source="正常"，其中一条明细 unit_price=0

**请求**：
```
POST /api/receipts
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "receiptDate": "2026-03-17",
  "receiptSource": "正常",
  "remark": "含未设价明细",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "quantity": 100,
      "unitPrice": 0,
      "remark": "未设价明细"
    },
    {
      "materialId": 2,
      "processId": 1,
      "quantity": 50,
      "unitPrice": 5.00,
      "remark": "已设价明细"
    }
  ]
}
```

**后续验证请求**：
```
GET /api/receipts/{newId}
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- 新增接口 HTTP 200，数据成功保存（unit_price=0 允许保存，不报错）
- 详情接口返回中，unit_price=0 的明细行：
  - `unitPrice` = 0
  - `receiptSource` = "正常"（前端根据此两字段判断是否标红）
- 或接口直接返回字段 `needPriceAlert: true`（取决于实现方式）
- unit_price>0 的明细行不应触发标红标识

---

### TC-RECEIPT-007：导入 Excel（mode=history），验证幂等：相同 receipt_no 再次导入应 skip，返回 {success,skip,fail}

**前置条件**：准备好含有 receipt_no=SH2025010001 的 Excel 文件（.xlsx 格式）

**第一次导入请求**：
```
POST /api/receipts/import?mode=history
Headers:
  Authorization: Bearer {token}
  Content-Type: multipart/form-data
Body:
  file: receipts_test.xlsx  (含 SH2025010001、SH2025010002 两条单据)
```

**第一次预期结果**：
- HTTP 200
- `data.success` = 2，`data.skip` = 0，`data.fail` = 0

**第二次导入（幂等测试）请求**：
```
POST /api/receipts/import?mode=history
Headers:
  Authorization: Bearer {token}
  Content-Type: multipart/form-data
Body:
  file: receipts_test.xlsx  (相同文件，包含已存在的 SH2025010001、SH2025010002)
```

**第二次预期结果**：
- HTTP 200
- `data.success` = 0，`data.skip` = 2，`data.fail` = 0
- 数据库中 SH2025010001 记录只存在一条（未重复插入）
- 不抛出异常，系统稳定运行

---

### TC-RECEIPT-008：mode=history 导入后，库存表不应有新增记录（不触发库存）

**前置条件**：记录导入前 inventory 表的记录数 N

**请求**：
```
POST /api/receipts/import?mode=history
Headers:
  Authorization: Bearer {token}
  Content-Type: multipart/form-data
Body:
  file: receipts_history_new.xlsx  (含全新单号，未在系统中存在)
```

**预期结果**：
- HTTP 200
- `data.success` >= 1
- 查询 inventory 表：`SELECT COUNT(*) FROM inventory` 结果仍为 N（未新增记录）
- 或虽有记录但 quantity 未发生变化（历史导入不影响当前库存）
- 验证方式：导入后调用 `GET /api/inventory` 检查库存列表与导入前一致

---

### TC-RECEIPT-009：查询最近工艺接口

**前置条件**：系统中 customer_id=1、material_id=1 有历史收货记录，最新一条使用 process_id=3（工艺名"镀镍"）

**请求**：
```
GET /api/receipt-items/latest-process?customerId=1&materialId=1
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `data.processId` = 3
- `data.processName` = "镀镍"（或对应工艺名称）
- 若该客户+物料无历史记录，返回 `data` = null 或空对象，不报 500

**边界测试**：
```
GET /api/receipt-items/latest-process?customerId=9999&materialId=9999
Headers:
  Authorization: Bearer {token}
```
- HTTP 200
- `data` = null 或 `{}`，不抛出异常

---

## 二、排产单测试（TC-PROD-xxx）

| 用例编号 | 场景名称 | 优先级 | 状态 |
|----------|----------|--------|------|
| TC-PROD-001 | 新增排产单含多条明细，验证单号格式 | P0 | - |
| TC-PROD-002 | 修改排产单 | P0 | - |
| TC-PROD-003 | 删除排产单 | P0 | - |
| TC-PROD-004 | 历史导入幂等性（mode=history） | P0 | - |
| TC-PROD-005 | 查询明细列表 | P1 | - |

---

### TC-PROD-001：新增排产单含多条明细，单号格式 PC+年月+4位流水

**前置条件**：系统中存在客户（customer_id=1），物料（material_id=1，material_id=2），工艺（process_id=1）

**请求**：
```
POST /api/productions
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "productionDate": "2026-03-17",
  "remark": "测试排产单",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "plannedQty": 500,
      "detailRemark": "排产明细1"
    },
    {
      "materialId": 2,
      "processId": 1,
      "plannedQty": 300,
      "detailRemark": "排产明细2"
    }
  ]
}
```

**预期结果**：
- HTTP 200
- `data.productionNo` 匹配正则 `^PC202603\d{4}$`（格式为 PC+年月+4位流水，如 PC2026030001）
- `data.items` 长度为 2
- `data.items[0].materialId` = 1，`data.items[0].plannedQty` = 500
- `data.items[1].materialId` = 2，`data.items[1].plannedQty` = 300
- 数据库 `production` 主表新增一条记录，`deleted` = 0
- 数据库 `production_item` 新增两条记录，均 `deleted` = 0

---

### TC-PROD-002：修改排产单

**前置条件**：TC-PROD-001 已执行，获取到 productionId=1

**请求**：
```
PUT /api/productions/1
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "productionDate": "2026-03-18",
  "remark": "修改后的排产备注",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "plannedQuantity": 600,
      "remark": "修改后明细1，数量从500改为600"
    }
  ]
}
```

**预期结果**：
- HTTP 200
- `data.productionDate` = "2026-03-18"
- `data.remark` = "修改后的排产备注"
- `data.items` 长度为 1（原 material_id=2 明细被移除）
- `data.items[0].plannedQuantity` = 600
- 原有明细记录被正确处理（逻辑删除或物理删除）

---

### TC-PROD-003：删除排产单

**前置条件**：系统中存在 productionId=1 的排产单，且有对应明细

**请求**：
```
DELETE /api/productions/1
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `code` = 200，`msg` = "success"
- 数据库 `production` 表中 id=1 的记录 `deleted` = 1
- 数据库 `production_item` 表中关联 production_id=1 的所有明细 `deleted` = 1
- 再次请求 `GET /api/productions/1` 返回 404 或 `data` 为 null
- 不影响其他排产单记录

---

### TC-PROD-004：历史导入幂等性（mode=history，相同 production_no skip）

**前置条件**：准备含有 production_no=PC2025010001、PC2025010002 的 Excel 文件

**第一次导入请求**：
```
POST /api/productions/import?mode=history
Headers:
  Authorization: Bearer {token}
  Content-Type: multipart/form-data
Body:
  file: productions_test.xlsx
```

**第一次预期结果**：
- HTTP 200
- `data.success` = 2，`data.skip` = 0，`data.fail` = 0

**第二次导入（幂等测试）请求**：
```
POST /api/productions/import?mode=history
Headers:
  Authorization: Bearer {token}
  Content-Type: multipart/form-data
Body:
  file: productions_test.xlsx  (相同文件)
```

**第二次预期结果**：
- HTTP 200
- `data.success` = 0，`data.skip` = 2，`data.fail` = 0
- 数据库中 PC2025010001 记录只存在一条（未重复插入）

---

### TC-PROD-005：查询排产单明细列表

**前置条件**：系统中 productionId=2 的排产单有 3 条明细

**请求**：
```
GET /api/production-items?productionId=2
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `data` 为数组，长度 = 3
- 每条明细包含字段：`id`、`productionId`、`materialId`、`materialName`、`processId`、`processName`、`plannedQuantity`
- 所有明细的 `productionId` = 2
- 不返回已逻辑删除的明细

**边界测试**：
```
GET /api/production-items?productionId=9999
Headers:
  Authorization: Bearer {token}
```
- HTTP 200，`data` = `[]`（空数组），不报 500

---

## 三、发货单测试（TC-SHIP-xxx）

| 用例编号 | 场景名称 | 优先级 | 状态 |
|----------|----------|--------|------|
| TC-SHIP-001 | 新增发货单，触发库存扣减 | P0 | - |
| TC-SHIP-002 | 发货后查库存，验证扣减数量正确 | P0 | - |
| TC-SHIP-003 | 废品不计入金额，amount=quantity×unit_price | P0 | - |
| TC-SHIP-004 | 修改发货单 | P1 | - |
| TC-SHIP-005 | 删除发货单 | P1 | - |

---

### TC-SHIP-001：新增发货单，触发库存扣减（quantity+defective_qty 均扣库存）

**前置条件**：
- 系统中存在客户（customer_id=1）
- inventory 表中 customer_id=1、material_id=1 的库存 quantity = 1000
- 发货数量 quantity=80，废品数量 defective_qty=20（合计扣减 100）

**请求**：
```
POST /api/shipments
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "shipmentDate": "2026-03-17",
  "remark": "测试发货单",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "quantity": 80,
      "defectiveQty": 20,
      "unitPrice": 10.00,
      "remark": "发货明细1"
    }
  ]
}
```

**预期结果**：
- HTTP 200
- `data.shipmentNo` 不为空
- `data.items[0].quantity` = 80，`data.items[0].defectiveQty` = 20
- 数据库 `inventory` 表中 customer_id=1、material_id=1 的 quantity 变为 900（1000 - 80 - 20 = 900）
- 库存流水表 `inventory_log` 新增一条出库记录，`change_qty` = -100，`change_type` = 2

---

### TC-SHIP-002：发货后查库存，验证 inventory.quantity 减少正确数量

**前置条件**：TC-SHIP-001 已执行，库存已扣减为 900

**请求**：
```
GET /api/inventory?customerId=1
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- 在返回的 `data.records` 中找到 material_id=1 的记录，`quantity` = 900（确认库存正确扣减）
- 可结合 `keyword` 参数用物料名称筛选，如 `GET /api/inventory?customerId=1&keyword=物料名`

**连续扣减验证**：
```
POST /api/shipments
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "shipmentDate": "2026-03-17",
  "items": [{"materialId":1,"processId":1,"quantity":50,"defectiveQty":10,"unitPrice":10.00}]
}
```
- 再次查询库存，`quantity` = 840

---

### TC-SHIP-003：废品(defective_qty)不计入金额，amount=quantity×unit_price（不含 defective_qty）

**前置条件**：系统中库存充足（quantity >= 200），新增发货单 quantity=80，defective_qty=20，unit_price=10.00

**请求**：
```
POST /api/shipments
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "shipmentDate": "2026-03-17",
  "remark": "废品金额验证",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "quantity": 80,
      "defectiveQty": 20,
      "unitPrice": 10.00,
      "remark": "含废品的发货明细"
    }
  ]
}
```

**预期结果**：
- HTTP 200
- `data.items[0].amount` = 800.00（= 80 × 10.00，不含废品 20 件）
- `data.items[0].amount` ≠ 1000.00（即 100 × 10.00 的错误计算应不出现）
- `data.totalAmount` = 800.00
- 数据库 `shipment_item` 中 `amount` = 800.00

---

### TC-SHIP-004：修改发货单

**前置条件**：系统中存在 shipmentId=1 的发货单，已有明细，库存已因该发货单扣减

**请求**：
```
PUT /api/shipments/1
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "shipmentDate": "2026-03-18",
  "remark": "修改后备注",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "quantity": 60,
      "defectiveQty": 10,
      "unitPrice": 12.00,
      "remark": "修改后明细"
    }
  ]
}
```

**预期结果**：
- HTTP 200
- `data.shipmentDate` = "2026-03-18"
- `data.items[0].quantity` = 60，`data.items[0].unitPrice` = 12.00
- `data.items[0].amount` = 720.00（= 60 × 12.00）
- 库存应根据修改前后的差值进行回滚和重新扣减（若实现了库存修正逻辑）

---

### TC-SHIP-005：删除发货单

**前置条件**：系统中存在 shipmentId=1 的发货单

**请求**：
```
DELETE /api/shipments/1
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `code` = 200，`msg` = "success"
- 数据库 `shipment` 表中 id=1 的记录 `deleted` = 1
- 数据库 `shipment_item` 表中关联 shipment_id=1 的所有明细 `deleted` = 1
- 再次 GET /api/shipments/1 返回 404 或 `data` = null

---

## 四、返工单测试（TC-REWORK-xxx）

| 用例编号 | 场景名称 | 优先级 | 状态 |
|----------|----------|--------|------|
| TC-REWORK-001 | 新增返工单含多条明细，验证单号格式 | P0 | - |
| TC-REWORK-002 | 返工状态变更（待返工→返工中→已完成） | P0 | - |
| TC-REWORK-003 | 查询返工单详情（含 items） | P1 | - |
| TC-REWORK-004 | 删除返工单 | P1 | - |

---

### TC-REWORK-001：新增返工单含多条明细，单号格式 FG+年月+4位流水

**前置条件**：系统中存在客户（customer_id=1），物料（material_id=1，material_id=2），工艺（process_id=1）

**请求**：
```
POST /api/reworks
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "reworkDate": "2026-03-17",
  "remark": "测试返工单",
  "items": [
    {
      "materialId": 1,
      "processId": 1,
      "quantity": 50,
      "reworkReason": "表面划伤",
      "remark": "返工明细1"
    },
    {
      "materialId": 2,
      "processId": 1,
      "quantity": 30,
      "reworkReason": "电镀不均匀",
      "remark": "返工明细2"
    }
  ]
}
```

**预期结果**：
- HTTP 200
- `data.reworkNo` 匹配正则 `^FG202603\d{4}$`（格式为 FG+年月+4位流水，如 FG2026030001）
- `data.status` = "待返工"（初始状态）
- `data.items` 长度为 2
- `data.items[0].materialId` = 1，`data.items[0].quantity` = 50，`data.items[0].reworkReason` = "表面划伤"
- `data.items[1].materialId` = 2，`data.items[1].quantity` = 30
- 数据库 `rework` 主表新增一条记录，`deleted` = 0，`status` = "待返工"

---

### TC-REWORK-002：返工状态变更（待返工→返工中→已完成）

**前置条件**：TC-REWORK-001 已执行，获取到 reworkId=1，当前状态为"待返工"

**第一次状态变更（待返工→返工中）**：
```
PUT /api/reworks/1
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:（完整对象，仅修改 reworkStatus）
{
  "reworkStatus": "返工中"
}
```

**预期结果**：
- HTTP 200
- `data.reworkStatus` = "返工中"
- 数据库 `rework` 表中 id=1 的 `rework_status` = "返工中"

**第二次状态变更（返工中→已完成）**：
```
PUT /api/reworks/1
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "reworkStatus": "已完成"
}
```

**预期结果**：
- HTTP 200
- `data.reworkStatus` = "已完成"
- 数据库 `rework` 表中 id=1 的 `rework_status` = "已完成"

---

### TC-REWORK-003：查询返工单详情（含 items）

**前置条件**：系统中存在 reworkId=2 的返工单，有 2 条明细

**请求**：
```
GET /api/reworks/2
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `data.id` = 2
- `data.reworkNo` 不为空，格式符合 `FG\d{10}`
- `data.customerName` 不为空（关联查询）
- `data.status` 为有效状态值（"待返工"/"返工中"/"已完成" 之一）
- `data.items` 为数组，长度 = 2
- 每条 item 包含：`materialId`、`materialName`、`processId`、`processName`、`quantity`、`reworkReason`
- 不包含已逻辑删除的明细

---

### TC-REWORK-004：删除返工单

**前置条件**：系统中存在 reworkId=1 的返工单，状态为"待返工"（建议仅允许删除待返工状态的单据）

**请求**：
```
DELETE /api/reworks/1
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `code` = 200，`msg` = "success"
- 数据库 `rework` 表中 id=1 的记录 `deleted` = 1
- 数据库 `rework_item` 表中关联 rework_id=1 的所有明细 `deleted` = 1
- 再次 GET /api/reworks/1 返回 404 或 `data` = null

**删除已完成状态单据测试**：
```
DELETE /api/reworks/{completedReworkId}
Headers:
  Authorization: Bearer {token}
```
- 预期：若系统限制已完成状态不可删除，应返回 HTTP 400 或业务错误码

---

## 五、收款记录测试（TC-PAY-xxx）

| 用例编号 | 场景名称 | 优先级 | 状态 |
|----------|----------|--------|------|
| TC-PAY-001 | 新增收款记录，验证单号格式 | P0 | - |
| TC-PAY-002 | 按客户筛选收款记录 | P1 | - |
| TC-PAY-003 | 金额为0或负数时报错 | P0 | - |

---

### TC-PAY-001：新增收款记录，单号格式 SK+年月+4位流水

**前置条件**：系统中存在客户（customer_id=1）

**请求**：
```
POST /api/payments
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "paymentDate": "2026-03-17",
  "amount": 5000.00,
  "paymentMethod": "银行转账",
  "remark": "3月收款"
}
```

**预期结果**：
- HTTP 200
- `data.paymentNo` 匹配正则 `^SK202603\d{4}$`（格式为 SK+年月+4位流水，如 SK2026030001）
- `data.customerId` = 1
- `data.amount` = 5000.00
- `data.paymentDate` = "2026-03-17"
- `data.paymentMethod` = "银行转账"
- 数据库 `payment` 表新增一条记录，`deleted` = 0

**连续新增验证流水递增**：
```
POST /api/payments（再次提交相同客户，不同金额）
```
- 预期第二条记录单号为 SK2026030002（流水递增）

---

### TC-PAY-002：按客户筛选收款记录

**前置条件**：系统中 customer_id=1 有 3 条收款记录，customer_id=2 有 2 条收款记录

**请求**：
```
GET /api/payments?customerId=1&page=1&size=10
Headers:
  Authorization: Bearer {token}
```

**预期结果**：
- HTTP 200
- `data.records` 长度 = 3
- 所有记录的 `customerId` = 1（不包含其他客户的收款记录）
- `data.total` = 3
- 每条记录包含：`paymentNo`、`customerId`、`customerName`、`paymentDate`、`amount`、`paymentMethod`

**不传 customerId 查询全量**：
```
GET /api/payments?page=1&size=10
Headers:
  Authorization: Bearer {token}
```
- 预期返回所有未删除的收款记录（合计 5 条），`data.total` = 5

---

### TC-PAY-003：金额为0或负数时报错

**测试场景一：金额为 0**：
```
POST /api/payments
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "paymentDate": "2026-03-17",
  "amount": 0,
  "paymentMethod": "现金",
  "remark": "金额为0测试"
}
```

**预期结果**：
- HTTP 400 或 `code` != 200
- `msg` 包含金额相关错误提示，如 "金额必须大于0" 或 "amount must be positive"
- 数据库中不插入该条记录

**测试场景二：金额为负数**：
```
POST /api/payments
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "paymentDate": "2026-03-17",
  "amount": -100.00,
  "paymentMethod": "现金",
  "remark": "负数金额测试"
}
```

**预期结果**：
- HTTP 400 或 `code` != 200
- `msg` 包含金额相关错误提示
- 数据库中不插入该条记录
- 系统不崩溃，返回明确的业务错误信息

**测试场景三：金额字段缺失**：
```
POST /api/payments
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "customerId": 1,
  "paymentDate": "2026-03-17",
  "paymentMethod": "现金"
}
```

**预期结果**：
- HTTP 400 或 `code` != 200
- `msg` 包含字段缺失提示，如 "amount不能为空"
- 数据库中不插入该条记录

---

## 附录：测试执行顺序建议

```
1. 获取 token（POST /api/auth/login）
2. 执行收货单测试（TC-RECEIPT-001 ~ TC-RECEIPT-009）
3. 执行排产单测试（TC-PROD-001 ~ TC-PROD-005）
4. 执行发货单测试（TC-SHIP-001 ~ TC-SHIP-005）
   注意：发货测试依赖库存数据，建议在收货单测试后执行
5. 执行返工单测试（TC-REWORK-001 ~ TC-REWORK-004）
6. 执行收款记录测试（TC-PAY-001 ~ TC-PAY-003）
```

## 附录：通用异常场景（适用于所有模块）

| 场景 | 请求 | 预期 |
|------|------|------|
| 未携带 token | 任意接口，不带 Authorization Header | HTTP 401，`code`=401 |
| token 格式错误 | Authorization: Bearer invalid_token | HTTP 401，`code`=401 |
| 访问不存在资源 | GET /api/receipts/99999 | HTTP 404 或 `data`=null，不报 500 |
| 请求体格式错误 | POST 接口 Body 为非 JSON | HTTP 400，`code`=400 |
| 必填字段缺失 | POST 接口 Body 缺少 customerId | HTTP 400，返回字段校验错误 |
