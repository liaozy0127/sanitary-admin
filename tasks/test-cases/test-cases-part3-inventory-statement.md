# sanitary-admin 库存与对账单测试用例

## 测试环境
- 后端地址：http://localhost:8080
- 认证：Authorization: Bearer {token}
- 响应格式：{"code":200,"msg":"success","data":{}}

---

## 一、库存管理测试（TC-INV）

库存表唯一键：(material_id, customer_id, process_id)，无工艺时 process_id=0

### TC-INV-001：查询库存列表
请求：GET /api/inventory?page=1&size=10
预期：code=200，data.records 包含 materialName、customerName、processName、quantity 字段

### TC-INV-002：按关键词筛选库存
> ⚠️ **已知问题（DESIGN.md 记录）**：inventory 查询接口带 keyword 参数时可能返回 400，执行前确认该 Bug 已修复。

请求：GET /api/inventory?page=1&size=10&keyword=铜件
预期：所有返回记录 materialName 或 customerName 包含"铜件"；若返回 400 则记录为已知 Bug 待修复

### TC-INV-003：按客户筛选库存
请求：GET /api/inventory?page=1&size=10&customerId=1
预期：所有返回记录 customerId=1

### TC-INV-004：收货后库存增加验证
步骤：
1. 记录当前 GET /api/inventory 中某物料库存 Q1
2. POST /api/receipts（mode 非 history），body 中该物料 quantity=100
3. GET /api/inventory 查询该物料库存 Q2
预期：Q2 = Q1 + 100

### TC-INV-005：发货后库存扣减验证
步骤：
1. 记录库存 Q1（某物料）
2. POST /api/shipments，body 中该物料 quantity=30，defective_qty=5
3. GET /api/inventory 查库存 Q2
预期：Q2 = Q1 - 35（良品30+废品5均扣减）

### TC-INV-006：mode=history 导入不触发库存
步骤：
1. 记录 inventory 表记录数 N1（可通过 GET /api/inventory 中 total 判断）
2. POST /api/receipts/import?mode=history（导入收货单）
3. 查询 N2
预期：N2 = N1（history模式不新增库存记录）

### TC-INV-007：查询库存流水
请求：GET /api/inventory/log?page=1&size=20
预期：code=200，data.records 包含 changeType、changeQty、beforeQty、afterQty、orderNo 字段

### TC-INV-008：发货流水记录
步骤：
1. 执行发货操作 POST /api/shipments（quantity=50）
2. GET /api/inventory/log?page=1&size=10
预期：最新一条流水 changeType=2（发货），changeQty=-50，afterQty = beforeQty - 50

### TC-INV-009：全量重建库存
请求：POST /api/inventory/rebuild
预期：code=200，data 包含 receiptGroups、shipmentGroups、inventoryRecords 统计数字

### TC-INV-010：重建后负库存验证
步骤：
1. POST /api/inventory/rebuild
2. 查询所有库存记录：GET /api/inventory?size=1000
3. 统计 quantity < 0 的记录数
预期：quantity < 0 的记录数 ≤ 5（超过说明期初库存未补录或数据有误）

### TC-INV-011：重建结果幂等性
步骤：
1. POST /api/inventory/rebuild，记录 inventoryRecords=N1
2. 再次 POST /api/inventory/rebuild，记录 N2
预期：N1 = N2（重复重建结果一致）

### TC-INV-012：库存三维唯一键验证
步骤：
1. 收货单明细行：物料A + 客户1 + 工艺"镀铬" → 触发库存
2. 收货单明细行：物料A + 客户1 + 工艺"喷粉" → 触发库存
3. 查询库存
预期：物料A在客户1下有两条记录（按工艺区分）

---

## 二、对账单测试（TC-STMT）

对账单约束：curr_balance_qty = prev_balance_qty + receipt_qty - shipment_qty（等式必须成立且 ≥ 0）

### TC-STMT-001：生成单月对账单
请求：POST /api/statements/generate
Body: {"customerId":1,"statementMonth":"2025-01"}
预期：code=200，返回对账单 id 和 statementNo（DZ+年月+流水），状态为"草稿"

### TC-STMT-002：对账单结余等式验证
步骤：
1. POST /api/statements/generate（某客户某月）
2. GET /api/statements/{id}（含 items 明细）
3. 对每条 statement_item 验证：curr_balance_qty = prev_balance_qty + receipt_qty - shipment_qty
预期：所有明细行等式成立，且 curr_balance_qty ≥ 0

### TC-STMT-003：对账单生成幂等性（generate 重建）
步骤：
1. 第一次：POST /api/statements/generate，Body: {"customerId":1,"statementMonth":"2025-01"}，记录 statementId=A
2. 修改数据（新增一条收货单）
3. 第二次：POST /api/statements/generate，相同参数
预期：对账单 id 相同（A），明细被重建（旧明细软删除，新明细插入），结余等式依然成立

### TC-STMT-004：批量生成所有对账单
请求：POST /api/statements/generate-all
预期：code=200，返回统计 {generated:N, skipped:M}
注意：N+M = 系统中**有收发货记录的**客户×月份组合数（不是所有客户×所有月份，而是实际存在收发货数据的组合）

### TC-STMT-005：generate-all 跳过已存在对账单
步骤：
1. POST /api/statements/generate（客户1，2025-01）
2. POST /api/statements/generate-all
3. 查询客户1 2025-01 的对账单
预期：步骤2不会重建步骤1生成的对账单（skipped 中包含该对账单）

### TC-STMT-006：确认对账单
步骤：
1. 生成对账单，状态为"草稿"
2. PUT /api/statements/{id}/confirm
3. GET /api/statements/{id}
预期：status 变为"已确认"

### TC-STMT-007：查询对账单明细
请求：GET /api/statement-items?statementId={id}
预期：code=200，data 包含各物料行的 prevBalanceQty、receiptQty、shipmentQty、defectiveQty、currBalanceQty、unitPrice、goodsAmount

### TC-STMT-008：上月结余等于当月期初验证
步骤：
1. 生成 2025-01 对账单，记录某物料的 currBalanceQty=X
2. 生成 2025-02 对账单，查询同一物料的 prevBalanceQty=Y
预期：Y = X（上月结余 = 下月期初）

注意：prevBalanceQty 计算方式是直接聚合历史数据（月初之前全部收发合计），不依赖上月对账单传递，所以即使跳月生成也应保持一致。

### TC-STMT-009：对账单删除
请求：DELETE /api/statements/{id}
预期：code=200；GET /api/statements 列表中不再包含该对账单（逻辑删除）

### TC-STMT-010：发货含废品时对账单金额验证
前提：发货单中某物料 quantity=100，defective_qty=10，unit_price=5.0
生成对账单后查该物料明细：
- shipment_qty 应为 110（良品100+废品10）
- defective_qty 应为 10
- goods_amount 应为 500（仅良品100×5.0，废品不计金额）
- shipment_amount = goods_amount = 500

### TC-STMT-011：历史对账单导入
请求：POST /api/statements/import（multipart/form-data）
参数：file=老系统对账单.xlsx，customerId=1，statementMonth=2024-12
预期：code=200，success>0，skip=0（首次导入）；再次导入 skip>0（幂等）

---

## 三、期初库存补录验证（TC-INIT-STOCK）

### TC-INIT-STOCK-001：无期初库存时对账单出现负结余
步骤（验证问题场景）：
1. 历史收货/发货已导入（mode=history）
2. 跳过期初库存补录
3. 生成对账单
预期（可能出现）：某些物料 curr_balance_qty < 0（说明期初缺口未补录）

### TC-INIT-STOCK-002：补录期初库存后负结余消失
步骤：
1. 执行 python3 scripts/init_opening_stock.py
2. POST /api/inventory/rebuild
3. POST /api/statements/generate-all（或重新 generate 有负结余的月份）
预期：所有对账单明细 curr_balance_qty ≥ 0

### TC-INIT-STOCK-003：期初收货单格式验证
步骤：执行期初补录脚本后查询收货单
请求：GET /api/receipts?keyword=RH-INIT
预期：存在单号格式为 RH-INIT-{customerId} 的收货单，receipt_date=2024-12-31

### TC-INIT-STOCK-004：期初补录幂等性
步骤：执行两次 python3 scripts/init_opening_stock.py
预期：第二次执行后收货单数量不变，明细行不重复（按 receipt_id+material_id+process_id 去重）
