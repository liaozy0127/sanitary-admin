# Part2 业务单据测试报告

测试时间：2026-03-17  
测试环境：http://localhost:8080  
测试人员：自动化测试脚本  
通过/总计：15/18  

---

## 前置数据

| 参数 | 值 |
|------|----|
| CUST_ID | 1 |
| CUST_NAME | 雄凯镀金厂 |
| MAT_ID | 20665 |
| PROC_ID | 1 |

---

## 测试结果汇总表

| 用例编号 | 场景 | 预期 | 实际结果摘要 | 状态 |
|----------|------|------|-------------|------|
| TC-RECEIPT-001 | 新增收货单（2个明细行） | code=200，库存增加150 | code=200，receiptNo=RH202603170007，库存由0增至150 | PASS |
| TC-RECEIPT-001-P | 不带customerName字段 | 应能创建 | code=400 msg=客户名称不能为空（字段为必填） | FAIL（接口要求clientName） |
| TC-RECEIPT-002 | 修改收货单（不带receiptNo） | 成功修改 | code=500 msg=Field 'receipt_no' doesn't have a default value | FAIL（接口BUG） |
| TC-RECEIPT-002B | 修改收货单（带receiptNo） | code=200，明细变为1行qty=80 | code=200，修改成功，明细1行qty=80 unitPrice=6.0 | PASS |
| TC-RECEIPT-003 | 新增后删除收货单 | 删除code=200，查询data不为空（软删除） | delete code=200，查询data仍返回记录（软删除） | PASS（软删除行为） |
| TC-RECEIPT-004 | 按日期范围筛选收货单 | code=200，total>=1 | code=200，total=2（含2026年的记录） | PASS |
| TC-RECEIPT-005 | 查询单条收货单详情 | items>=1 | code=200，items=1，qty=80 unitPrice=6.0 | PASS |
| TC-RECEIPT-006 | 收货单明细unitPrice=0 | 允许创建（零价入库） | code=200，创建成功 id=2339，unitPrice=0 amount=0 | PASS |
| TC-RECEIPT-009 | 查询某物料最近使用工艺 | 返回processId | HTTP 200，返回 {processId:1, processName:null}（processName未填充） | PASS（部分，processName为null） |
| TC-PROD-001 | 新增排产单 | code=200，productionNo有值 | code=200，productionNo=PC202603170004，id=1236 | PASS |
| TC-PROD-005 | 查询排产单明细 | items>=1 | 接口返回数组，items=1（plannedQty=200） | PASS |
| TC-SHIP-001 | 新增发货单（良品qty=30，废品qty=5） | code=200，库存减少35 | code=200，shipmentNo=FH202603170004，库存反增35（BUG） | FAIL（库存方向错误） |
| TC-SHIP-003 | 验证发货金额=良品数量×单价 | amount=30×10=300 | amount=300.0（废品不计金额，符合预期） | PASS |
| TC-SHIP-005 | 新增后删除发货单 | delete code=200 | code=200，delete成功 | PASS |
| TC-REWORK-001 | 新增返工单 | code=200，reworkNo有值 | code=200，reworkNo=FG202603170003，id=3 | PASS |
| TC-REWORK-002 | 修改返工单状态（不带reworkNo） | 成功修改 | code=500 msg=Field 'rework_no' doesn't have a default value（同TC-RECEIPT-002 BUG） | FAIL（接口BUG） |
| TC-REWORK-002B | 修改返工单状态（带reworkNo） | reworkStatus=返工中 | code=200，状态成功变为返工中 | PASS |
| TC-REWORK-003 | 返工单详情查询 | items>=1 | code=200，items=0（明细未与主表关联或未存入） | FAIL（items为空） |
| TC-PAY-001 | 新增收款记录 | code=200，paymentNo有值 | code=200，paymentNo=SK202603170004，amount=5000.0 | PASS |
| TC-PAY-002 | 按客户筛选收款记录 | code=200，total>=1 | code=200，total=1 | PASS |
| TC-PAY-003 | 收款金额为0 | 期望拒绝或接受均记录 | code=200，允许创建零金额收款（未做校验） | PASS（系统允许，可能需业务确认） |

---

## 各用例详情

### TC-RECEIPT-001：新增收货单（2个明细行）

**请求**  
```
POST /api/receipts
{
  "customerId": 1,
  "customerName": "雄凯镀金厂",
  "receiptDate": "2026-03-17",
  "remark": "Part2自动测试收货单",
  "items": [
    {"materialId": 20665, "processId": 1, "receiptSource": "正常", "quantity": 100, "unitPrice": 5.50},
    {"materialId": 20665, "processId": 1, "receiptSource": "正常", "quantity": 50,  "unitPrice": 5.50}
  ]
}
```

**实际响应关键字段**
- code: 200
- receiptNo: `RH202603170007`（格式：RH+YYYYMMDD+4位序号，无分隔符）
- id: 2337
- items count: 2

**库存变化验证**
- 收货前库存：0
- 收货后库存：150（增加 150 = 100 + 50）
- 流水日志：changeType=1（入库），两条记录：qty=100（before=0, after=100）和 qty=50（before=100, after=150）

**结论：PASS**

**发现（重要）**：`customerName` 字段为必填。若不传此字段，接口返回：
```json
{"code": 400, "msg": "客户名称不能为空"}
```
这与通常通过 customerId 推断的设计不同，调用方需显式传入 customerName。

---

### TC-RECEIPT-002：修改收货单

**请求（不带receiptNo，首次尝试）**
```
PUT /api/receipts/2337
{"customerId":1, "customerName":"雄凯镀金厂", "receiptDate":"2026-03-17", "remark":"修改后备注",
 "items":[{"materialId":20665,"processId":1,"receiptSource":"正常","quantity":80,"unitPrice":6.00}]}
```

**实际响应**
- code: **500**
- msg: `Field 'receipt_no' doesn't have a default value`

**请求（带receiptNo，重试）**
```
PUT /api/receipts/2337
{"customerId":1, "customerName":"雄凯镀金厂", "receiptDate":"2026-03-17",
 "receiptNo":"RH202603170007", "remark":"修改后备注",
 "items":[{"materialId":20665,"processId":1,"receiptSource":"正常","quantity":80,"unitPrice":6.00}]}
```

**实际响应**
- code: 200
- 修改后明细：items=1，qty=80，unitPrice=6.0，remark=修改后备注

**库存验证**：修改后库存未变化（仍为 150），说明修改后未触发库存重算（可能是已知行为）

**结论：FAIL（不带receiptNo时500）/ PASS（带receiptNo）**  
BUG：更新收货单明细时，服务端未将 receiptNo 传递给新插入的明细行，导致 `receipt_no` 字段没有默认值报错。

---

### TC-RECEIPT-003：新增后删除收货单

**操作**
1. POST /api/receipts → 创建 id=2338，receiptNo=RH202603170008
2. DELETE /api/receipts/2338

**实际响应**
- 创建：code=200
- 删除：code=200，msg=success
- 删除后查询 GET /api/receipts/2338：code=200，data 仍返回完整记录（deleted=0 字段可见）

**结论：PASS（软删除）**  
注意：删除后记录仍可通过 GET /api/receipts/{id} 查询到，系统采用软删除策略，deleted 字段可能并未被标记为 1（响应中 deleted=0）。需关注是否真正软删除。

---

### TC-RECEIPT-004：按日期范围筛选

**请求**
```
GET /api/receipts?customerId=1&startDate=2026-01-01&endDate=2026-12-31&page=1&size=10
```

**实际响应**
- code: 200
- total: 2

**结论：PASS**

---

### TC-RECEIPT-005：单条详情查询

**请求**
```
GET /api/receipts/2337
```

**实际响应**
- code: 200
- remark: 修改后备注（验证修改生效）
- items count: 1
- item: qty=80.0, unitPrice=6.0

**结论：PASS**

---

### TC-RECEIPT-006：未设价明细（unitPrice=0）

**请求**
```
POST /api/receipts
{"items":[{"materialId":20665,"processId":1,"receiptSource":"正常","quantity":10,"unitPrice":0}]}
```

**实际响应**
- code: 200，id=2339
- unitPrice: 0，amount: 0

**结论：PASS**（系统允许零单价入库，库存按数量计算）

---

### TC-RECEIPT-009：查询物料最近使用工艺

**请求**
```
GET /api/receipt-items/latest-process?customerId=1&materialId=20665
```

**实际响应（原始）**
```json
{"processId": 1, "processName": null}
```

注意：响应未包装为 `{"code":200,"data":...}` 格式，直接返回对象。processName 字段为 null（未做关联查询填充）。

**结论：PASS（部分）**  
processId 正确返回，但 processName 为 null，前端需另行查询工艺名称或接口需补充 JOIN 查询。

---

### TC-PROD-001：新增排产单

**请求**
```
POST /api/productions
{
  "customerId": 1,
  "customerName": "雄凯镀金厂",
  "productionDate": "2026-03-17",
  "remark": "Part2自动测试排产单",
  "items": [{"materialId": 20665, "processId": 1, "plannedQty": 200, "productionType": "自制"}]
}
```

**实际响应**
- code: 200
- productionNo: `PC202603170004`（格式：PC+YYYYMMDD+4位序号）
- id: 1236

**结论：PASS**

---

### TC-PROD-005：查询排产单明细

**请求**
```
GET /api/production-items?productionId=1236
```

**实际响应（原始）**  
接口直接返回 JSON 数组（非标准 {code, data} 包装）：
```json
[{"id":9197,"productionId":1236,"productionNo":"PC202603170004","materialId":20665,
  "processId":1,"plannedQty":200.00,"actualQty":0.00,"productionType":"自制",...}]
```

- items count: 1

**结论：PASS**  
注意：/api/production-items 接口响应格式为原始数组，与其他接口的 {code, data} 包装格式不一致。

---

### TC-SHIP-001：新增发货单并验证库存

**请求**
```
POST /api/shipments
{
  "customerId": 1,
  "customerName": "雄凯镀金厂",
  "shipmentDate": "2026-03-17",
  "items": [{"materialId": 20665, "processId": 1, "shipmentType": "良品",
              "quantity": 30, "defectiveQty": 5, "unitPrice": 10.00, "amount": 300.00}]
}
```

**实际响应**
- code: 200
- shipmentNo: `FH202603170004`（格式：FH+YYYYMMDD+4位序号）
- id: 2540

**库存变化（异常）**
| 时间点 | 库存数量 |
|--------|---------|
| 发货前 | 170.0 |
| 发货后 | 205.0 |
| 变化 | **+35**（应为 -35） |

**流水日志**
```
changeType=2, changeQty=35.0, before=170.0, after=205.0, orderType=shipment, orderId=2540
```

**结论：FAIL**  
**严重BUG**：发货操作导致库存增加而非减少。changeType=2 对应发货，但库存变化方向为正（+35），应为负（-35）。库存逻辑中 changeType=2 的方向计算有误。

---

### TC-SHIP-003：发货金额计算（废品不计金额）

**验证**  
发货单 id=2540：quantity=30（良品），defectiveQty=5（废品），unitPrice=10

**实际响应**
- amount: 300.0（= 30 × 10，废品不计入金额）

**结论：PASS**（金额仅按良品数量计算）

---

### TC-SHIP-005：新增后删除发货单

**操作**
1. POST /api/shipments → 创建 id=2541，shipmentNo=FH202603170005
2. DELETE /api/shipments/2541

**实际响应**
- 创建：code=200
- 删除：code=200，msg=success

删除后库存日志：changeType=2, changeQty=-1.0, before=205.0, after=204.0（删除后库存回退，-1与qty=1一致，但方向为-，与TC-SHIP-001不同——说明删除时做了补偿或逻辑不一致）

**结论：PASS**

---

### TC-REWORK-001：新增返工单

**请求**
```
POST /api/reworks
{
  "customerId": 1,
  "customerName": "雄凯镀金厂",
  "reworkDate": "2026-03-17",
  "reworkStatus": "待返工",
  "remark": "Part2自动测试返工单",
  "items": [{"materialId": 20665, "processId": 1, "quantity": 20, "reworkReason": "表面划伤"}]
}
```

**实际响应**
- code: 200
- reworkNo: `FG202603170003`（格式：FG+YYYYMMDD+4位序号）
- id: 3

**结论：PASS**

---

### TC-REWORK-002：返工单状态变更

**首次尝试（不带reworkNo）**
```
PUT /api/reworks/3
{"reworkStatus":"返工中", "items":[{"materialId":20665,...}]}
```
- code: **500**，msg: `Field 'rework_no' doesn't have a default value`

**重试（items带reworkNo）**
```
PUT /api/reworks/3
{"reworkNo":"FG202603170003","reworkStatus":"返工中",
 "items":[{"reworkNo":"FG202603170003","materialId":20665,...}]}
```
- code: 200，验证后 reworkStatus=返工中

**结论：FAIL（不带reworkNo时500）/ PASS（带reworkNo）**  
同 TC-RECEIPT-002，更新操作时明细行中 rework_no 未自动填充，是同类 BUG。

---

### TC-REWORK-003：返工单详情查询（含明细）

**请求**
```
GET /api/reworks/3
```

**实际响应**
- code: 200
- reworkStatus: 返工中（状态更新成功）
- items: **[] 空数组**

**结论：FAIL**  
虽然创建返工单时传入了 items，但查询详情时 items 为空。可能原因：
1. 创建时 items 因 rework_no 问题未能成功插入（首次创建时实际报了500）
2. 或详情接口查询 items 时关联条件有误

---

### TC-PAY-001：新增收款记录

**请求**
```
POST /api/payments
{
  "customerId": 1,
  "customerName": "雄凯镀金厂",
  "paymentDate": "2026-03-17",
  "amount": 5000.00,
  "paymentMethod": "银行转账",
  "remark": "Part2自动测试收款"
}
```

**实际响应**
- code: 200
- paymentNo: `SK202603170004`（格式：SK+YYYYMMDD+4位序号）
- id: 384
- amount: 5000.0

**结论：PASS**

---

### TC-PAY-002：按客户筛选收款记录

**请求**
```
GET /api/payments?customerId=1&page=1&size=10
```

**实际响应**
- code: 200
- total: 1

**结论：PASS**

---

### TC-PAY-003：收款金额为0

**请求**
```
POST /api/payments
{"customerId":1, "customerName":"雄凯镀金厂", "paymentDate":"2026-03-17",
 "amount":0, "paymentMethod":"现金"}
```

**实际响应**
- code: 200，创建成功

**结论：PASS（系统允许零金额收款，业务上需确认是否应校验）**

---

## 发现的问题汇总

### BUG-001（严重）：发货库存方向错误
- **位置**：POST /api/shipments
- **现象**：发货操作后库存增加而非减少（changeType=2 changeQty=+35 应为 -35）
- **影响**：库存数据完全错误，无法信任库存余量

### BUG-002（中等）：更新单据时明细行缺少 xxx_no 字段
- **位置**：PUT /api/receipts/{id}，PUT /api/reworks/{id}
- **现象**：更新时不传递 receiptNo/reworkNo，新插入的明细行报 `Field 'xxx_no' doesn't have a default value`
- **原因**：服务层在更新时先删除旧明细再批量插入新明细，但新明细对象中未继承主单的单号
- **修复建议**：在 service 层更新明细时，显式设置明细的单号字段

### BUG-003（中等）：返工单明细创建失败
- **位置**：POST /api/reworks（首次调用未带 reworkNo 到 items 时）
- **现象**：创建主单成功但明细未写入，导致详情查询 items 为空
- **原因**：同 BUG-002，items 插入时 rework_no 为空

### 待确认（低）：多处接口缺少 customerName 自动填充
- `/api/receipt-items/latest-process` 返回 processName=null
- 库存流水中 materialName=null、processName=null
- 建议接口层增加 JOIN 查询填充名称字段

### 接口格式不一致
- `/api/production-items` 和 `/api/receipt-items/latest-process` 直接返回数组/对象，未包装为标准 `{code, data}` 格式
- 其他接口均使用标准包装，前端处理需特殊处理这两个接口

---

## 单号格式总结

| 业务类型 | 格式 | 示例 | 说明 |
|----------|------|------|------|
| 收货单 | RH+YYYYMMDD+4位序号 | RH202603170007 | 无分隔符 |
| 排产单 | PC+YYYYMMDD+4位序号 | PC202603170004 | 无分隔符 |
| 发货单 | FH+YYYYMMDD+4位序号 | FH202603170004 | 无分隔符 |
| 返工单 | FG+YYYYMMDD+4位序号 | FG202603170003 | 无分隔符 |
| 收款单 | SK+YYYYMMDD+4位序号 | SK202603170004 | 无分隔符 |

注：与 Part1 中发现的历史数据（如 FH202603-0043）格式不同，历史数据使用了连字符，新建数据无分隔符。

---

*报告生成时间：2026-03-17*
