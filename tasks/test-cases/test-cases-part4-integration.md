# sanitary-admin 端到端集成测试用例

## 测试环境
- 后端：http://localhost:8080
- 认证：Authorization: Bearer {token}
- MySQL：宿主机端口 3307，库名 sanitary_admin

---

## 一、核心业务流程测试：收货 → 库存 → 排产 → 发货 → 收款 → 对账

### TC-E2E-001：完整正向业务流程

**场景**：客户送货→入库→安排生产→成品发货→客户付款→月末对账

**测试步骤与验证**：

**Step 1 - 前置数据准备**
- 确保客户"联测客户"（id=X）已存在
- 确保物料"联测物料"（id=Y，关联客户X）已存在
- 确保工艺"镀铬"（id=Z）已存在

**Step 2 - 创建收货单（触发库存入库）**
```
POST /api/receipts
Body:
{
  "receiptDate": "2025-03-01",
  "customerId": X,
  "remark": "联测收货",
  "items": [{
    "materialId": Y,
    "materialName": "联测物料",
    "processId": Z,
    "processName": "镀铬",
    "receiptSource": "正常",
    "quantity": 200,
    "unitPrice": 10.00
  }]
}
```
预期：code=200，receiptNo 格式 SH202503-xxxx
验证库存：GET /api/inventory?customerId=X → 该物料库存 quantity=200（或在原基础上+200）

**Step 3 - 创建排产单**
```
POST /api/productions
Body:
{
  "productionDate": "2025-03-05",
  "customerId": X,
  "items": [{
    "materialName": "联测物料",
    "processName": "镀铬",
    "plannedQty": 200,
    "productionType": "自制"
  }]
}
```
预期：code=200，productionNo 格式 PC202503-xxxx
注意：排产单不直接影响库存

**Step 4 - 创建发货单（触发库存扣减）**
```
POST /api/shipments
Body:
{
  "shipmentDate": "2025-03-20",
  "customerId": X,
  "items": [{
    "materialName": "联测物料",
    "processName": "镀铬",
    "shipmentType": "良品",
    "quantity": 150,
    "defectiveQty": 10,
    "unitPrice": 10.00,
    "amount": 1500.00
  }]
}
```
预期：code=200，shipmentNo 格式 FH202503-xxxx
验证库存：quantity 从 200 变为 200-150-10=40（良品150+废品10均扣减）
验证流水：GET /api/inventory/log → 最新一条 changeType=2，changeQty=-160

**Step 5 - 创建收款记录**
```
POST /api/payments
Body:
{
  "paymentDate": "2025-03-25",
  "customerId": X,
  "amount": 1500.00,
  "paymentMethod": "转账",
  "referenceNo": "银行流水TEST001",
  "remark": "3月货款"
}
```
预期：code=200，paymentNo 格式 SK202503-xxxx

**Step 6 - 生成对账单**
```
POST /api/statements/generate
Body: {"customerId": X, "statementMonth": "2025-03"}
```
预期：code=200，statementNo 格式 DZ202503-xxxx
查询明细：GET /api/statement-items?statementId={id}
验证该物料明细行：
- receipt_qty = 200
- shipment_qty = 160（150+10）
- defective_qty = 10
- goods_amount = 1500.00（仅良品150×10）
- curr_balance_qty = prev_balance_qty + 200 - 160（等式成立且≥0）

---

### TC-E2E-002：返工流程测试

**场景**：发货后客户发现质量问题 → 退回货物 → 工厂在下一批收货单中以「返工」来源录入 → 触发库存增加 → 重新排产 → 再次发货

> ⚠️ **业务说明**：系统中没有独立的"返工处理"操作。返工货物退回工厂后，通过**新增收货单、将 `receiptSource` 设为「返工」**来录入，库存联动逻辑与正常收货相同（+quantity）。返工单（/api/reworks）用于记录返工事件档案，不直接影响库存。

**Step 1 - 发货（含废品/原件退回）**
```
POST /api/shipments
Body:
{
  "shipmentDate": "2025-03-20",
  "customerId": X,
  "items": [{
    "materialName": "联测物料",
    "processName": "镀铬",
    "shipmentType": "良品",
    "quantity": 50,
    "defectiveQty": 20,
    "unitPrice": 10.00,
    "amount": 500.00
  }]
}
```
预期：库存扣减 70（良品50 + 废品20）
记录当前库存 Q1

**Step 2 - 客户退回返工货物，以「返工」来源录入新收货单**
```
POST /api/receipts
Body:
{
  "receiptDate": "2025-03-25",
  "customerId": X,
  "remark": "客户退回返工批次",
  "items": [{
    "materialId": Y,
    "materialName": "联测物料",
    "processId": Z,
    "processName": "镀铬",
    "receiptSource": "返工",
    "quantity": 20,
    "unitPrice": 10.00
  }]
}
```
预期：code=200，receiptNo 格式 SH202503-xxxx
验证库存：quantity = Q1 + 20（返工收货触发库存入库）

**Step 3 - 记录返工单（档案用途，不影响库存）**
```
POST /api/reworks
Body:
{
  "reworkDate": "2025-03-25",
  "customerId": X,
  "reworkStatus": "返工中",
  "remark": "电镀厚度不足，退回重镀",
  "items": [{
    "materialName": "联测物料",
    "processName": "镀铬",
    "quantity": 20,
    "reworkReason": "电镀厚度不足"
  }]
}
```
预期：code=200，reworkNo 格式 FG202503-xxxx
验证：此操作**不再改变库存**（库存已在 Step 2 收货时更新）

**Step 4 - 重新排产（针对返工批次）**
```
POST /api/productions
Body:
{
  "productionDate": "2025-03-26",
  "customerId": X,
  "items": [{
    "materialName": "联测物料",
    "processName": "镀铬",
    "plannedQty": 20,
    "receiptType": "返工",
    "productionType": "自制"
  }]
}
```
预期：code=200，productionNo 格式 PC202503-xxxx

**Step 5 - 返工完成后再次发货**
```
POST /api/shipments
Body:
{
  "shipmentDate": "2025-03-28",
  "customerId": X,
  "items": [{
    "materialName": "联测物料",
    "processName": "镀铬",
    "shipmentType": "返工品",
    "quantity": 20,
    "defective_qty": 0,
    "unitPrice": 10.00,
    "amount": 200.00
  }]
}
```
预期：库存再扣减 20；流水新增 changeType=2，changeQty=-20

**Step 6 - 对账单验证（含返工批次）**
生成当月对账单，验证该物料明细：
- `receipt_qty` = 正常收货量 + 返工收货量（Step 2 的 20 也计入收货合计）
- `shipment_qty` = Step 1 的 70 + Step 5 的 20 = 90
- `curr_balance_qty = prev_balance_qty + receipt_qty - shipment_qty`（等式成立）

---

### TC-E2E-003：历史数据初始化完整流程测试

**场景**：全量历史数据导入后，验证库存和对账单数据一致性

**Step 1 - 数据导入顺序验证**
```
1. POST /api/customers/import（Excel）→ 验证客户导入数量
2. POST /api/processes/import（Excel）→ 验证工艺导入数量
3. POST /api/materials/import（Excel）→ 验证物料导入数量
4. POST /api/receipts/import?mode=history（Excel，分批）→ 验证收货单
5. POST /api/productions/import?mode=history（Excel）→ 验证排产单
```

**Step 2 - 期初库存补录**
```
执行：python3 scripts/init_opening_stock.py
验证：GET /api/receipts?keyword=RH-INIT → 存在期初收货单（receipt_date=2024-12-31）
```

**Step 3 - 全量重建库存**
```
POST /api/inventory/rebuild
验证：
- code=200
- data.inventoryRecords > 0
- 负库存条数 ≤ 5
```

**Step 4 - 批量生成对账单**
```
POST /api/statements/generate-all
验证：
- code=200
- generated + skipped > 0
```

**Step 5 - 数据一致性全量验证**
对所有对账单明细验证：
- curr_balance_qty = prev_balance_qty + receipt_qty - shipment_qty（等式成立）
- curr_balance_qty ≥ 0（无负结余）
- 相邻月份：上月 curr_balance_qty = 下月 prev_balance_qty

---

## 二、数据一致性验证

### TC-CONSIST-001：收发货与库存三表核对

**验证公式**：inventory.quantity = SUM(receipt_item.quantity) - SUM(shipment_item.quantity + defective_qty)
（仅含 status=1，deleted=0 的记录）

**SQL验证**：
```sql
-- 查收货合计
SELECT ri.material_id, ri.customer_id, COALESCE(ri.process_id,0),
       SUM(ri.quantity) AS recv_total
FROM receipt_item ri
JOIN receipt r ON r.id = ri.receipt_id
WHERE r.deleted=0 AND r.status=1 AND ri.deleted=0
GROUP BY ri.material_id, ri.customer_id, COALESCE(ri.process_id,0);

-- 查发货合计
SELECT si.material_id, si.customer_id, COALESCE(si.process_id,0),
       SUM(si.quantity + COALESCE(si.defective_qty,0)) AS ship_total
FROM shipment_item si
JOIN shipment s ON s.id = si.shipment_id
WHERE s.deleted=0 AND s.status=1 AND si.deleted=0
GROUP BY si.material_id, si.customer_id, COALESCE(si.process_id,0);
```
预期：inventory.quantity = recv_total - ship_total（偏差 ≤ 5条为历史录入误差）

### TC-CONSIST-002：对账单收发货数量与原始单据核对

**验证**：statement_item.receipt_qty 应等于该月该客户该物料的收货单明细 SUM(quantity)

```sql
-- 某月某客户某物料收货汇总（应等于对账单明细的 receipt_qty）
SELECT ri.material_id, SUM(ri.quantity) AS month_recv
FROM receipt_item ri
JOIN receipt r ON r.id = ri.receipt_id
WHERE r.customer_id = X
  AND r.receipt_date BETWEEN '2025-03-01' AND '2025-03-31'
  AND r.deleted=0 AND r.status=1 AND ri.deleted=0
GROUP BY ri.material_id;
```

### TC-CONSIST-003：对账单结余连续性验证

**验证**：对同一客户同一物料，月份连续时上月 curr_balance_qty = 下月 prev_balance_qty

（注：系统直接聚合历史数据计算 prevBalanceQty，不依赖上月对账单链式传递，所以理论上可以跳月生成也保持一致）

---

## 三、边界与异常场景测试

### TC-EDGE-001：发货数量超过库存
步骤：库存剩余 10 件时，发货单填写 quantity=100
预期：系统允许提交（库存可为负，但对账单结余等式必须成立）
注意：系统不做库存充足性校验，但重建后负库存 ≤ 5 条为正常

### TC-EDGE-002：删除有关联发货记录的收货单
步骤：
1. 先创建发货单，关联某物料
2. 尝试删除该物料对应的收货单
预期：按业务逻辑，系统应允许删除（逻辑删除），但对账单重新生成后数量会变化

### TC-EDGE-003：同一月份多次 generate 重建对账单
步骤：
1. 生成 2025-03 对账单（状态：草稿）
2. 新增一张收货单（2025-03 日期）
3. 再次调用 generate（单个生成，非 generate-all）
预期：对账单明细被重建（旧明细软删除），receipt_qty 增加，结余等式仍成立

### TC-EDGE-004：单号格式边界
验证各类单号在同一年月超过 9999 单时（流水号归零场景）：
- 测试用环境下不易触发，记录为手动验证项

### TC-EDGE-005：大批量导入性能
步骤：导入 3000 行以内的收货单 Excel（一批）
预期：接口响应时间 < 60秒，不返回 500 错误

---

## 四、系统管理测试

### TC-SYS-001：用户管理 CRUD
```
POST /api/users，Body: {"username":"testuser","realName":"测试员","password":"Test@123","status":1}
预期：code=200
```

### TC-SYS-002：角色管理 CRUD
```
POST /api/roles，Body: {"roleName":"业务员","remark":"只有业务模块权限"}
预期：code=200
```

### TC-SYS-003：菜单树查询
```
GET /api/menus
预期：code=200，data 为树形结构（有 children 字段）
```

---

## 五、测试执行顺序建议

1. **环境准备**：确认后端服务和数据库正常启动
2. **认证**：TC-AUTH-001 获取 token
3. **基础档案**：TC-CUST → TC-PROC → TC-MAT（顺序不可颠倒）
4. **历史导入**（如需验证初始化）：按顺序导入，最后重建库存
5. **业务流程**：TC-E2E-001（完整正向流程）
6. **返工流程**：TC-E2E-002
7. **数据一致性**：TC-CONSIST-001 ~ 003（跑 SQL 验证）
8. **边界测试**：TC-EDGE-001 ~ 005

---

## 六、SQL 快速验查脚本

连接方式：docker exec -it sanitary-mysql mysql -uroot -proot123 sanitary_admin

```sql
-- 1. 查负库存
SELECT material_name, customer_name, process_name, quantity
FROM inventory WHERE quantity < 0;

-- 2. 查对账单结余不一致（等式不成立）
SELECT id, statement_no, material_name,
       prev_balance_qty, receipt_qty, shipment_qty, curr_balance_qty,
       (prev_balance_qty + receipt_qty - shipment_qty) AS calc_balance,
       ABS(curr_balance_qty - (prev_balance_qty + receipt_qty - shipment_qty)) AS diff
FROM statement_item
WHERE deleted=0
  AND ABS(curr_balance_qty - (prev_balance_qty + receipt_qty - shipment_qty)) > 0.01;

-- 3. 查期初收货单
SELECT receipt_no, receipt_date, customer_name, COUNT(*) AS item_count
FROM receipt r
JOIN receipt_item ri ON ri.receipt_id = r.id
WHERE r.receipt_no LIKE 'RH-INIT%' AND r.deleted=0 AND ri.deleted=0
GROUP BY r.id;

-- 4. 收发库存三表核对（差异大于0.01的物料）
SELECT i.material_name, i.customer_name, i.process_name,
       i.quantity AS inv_qty,
       COALESCE(recv.total,0) AS recv_total,
       COALESCE(ship.total,0) AS ship_total,
       COALESCE(recv.total,0) - COALESCE(ship.total,0) AS calc_qty,
       ABS(i.quantity - (COALESCE(recv.total,0) - COALESCE(ship.total,0))) AS diff
FROM inventory i
LEFT JOIN (
  SELECT ri.material_id, r.customer_id, COALESCE(ri.process_id,0) pid, SUM(ri.quantity) total
  FROM receipt_item ri JOIN receipt r ON r.id=ri.receipt_id
  WHERE r.deleted=0 AND r.status=1 AND ri.deleted=0
  GROUP BY ri.material_id, r.customer_id, COALESCE(ri.process_id,0)
) recv ON recv.material_id=i.material_id AND recv.customer_id=i.customer_id AND recv.pid=i.process_id
LEFT JOIN (
  SELECT si.material_id, s.customer_id, COALESCE(si.process_id,0) pid,
         SUM(si.quantity + COALESCE(si.defective_qty,0)) total
  FROM shipment_item si JOIN shipment s ON s.id=si.shipment_id
  WHERE s.deleted=0 AND s.status=1 AND si.deleted=0
  GROUP BY si.material_id, s.customer_id, COALESCE(si.process_id,0)
) ship ON ship.material_id=i.material_id AND ship.customer_id=i.customer_id AND ship.pid=i.process_id
WHERE ABS(i.quantity - (COALESCE(recv.total,0) - COALESCE(ship.total,0))) > 0.01
ORDER BY diff DESC;
```
