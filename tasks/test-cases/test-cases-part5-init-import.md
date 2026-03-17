# Part5 项目初始化数据导入与数据核对测试用例

测试目标：验证系统从 0 到 1 部署后，老系统历史数据能正确导入，并确保全链路数据（基础数据→收货→库存→排产→发货→库存→收款→对账）前后一致、数值准确。

> **说明**：返工没有独立的录入流程，返工物料统一通过收货单处理（`receiptSource=返工`），在收货阶段一并验证，不单独设置返工导入阶段。

测试环境：
- 后端：http://localhost:8080
- 数据库：MySQL 8（Docker，端口 3307，库名 sanitary_admin）
- 登录账号：admin / admin123

**数据初始化顺序（必须严格遵守）**：
客户/工艺/物料 → 收货单(history) → 排产单(history) → 发货单(history) → 收款单 → 期初库存补录 → 重建库存 → 批量生成对账单

---

## 前置条件

| 检查项 | 验证命令 | 预期结果 |
|--------|---------|---------|
| 后端服务运行 | `curl -s http://localhost:8080/api/auth/login -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' \| python3 -c "import sys,json; d=json.load(sys.stdin); print('OK' if d['code']==200 else 'FAIL')"` | OK |
| 数据库可连接 | `mysql -h 127.0.0.1 -P 3307 -u root -proot123 sanitary_admin -e "SELECT 1"` | 1 |
| 老系统文件存在 | `ls /Users/admin/IdeaProjects/sanitary-admin/old-system-file/` | 显示客户/工艺/物料/收货单等 Excel 文件 |

获取 Token（后续所有接口均需携带）：
```bash
TOKEN=$(curl -s http://localhost:8080/api/auth/login \
  -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
echo "Token: $TOKEN"
```

---

## Stage 1：基础数据导入

### TC-INIT-CUST-001：客户数据导入

**目的**：将老系统客户数据导入新系统，验证数量与字段正确

**前置**：数据库 customer 表为空（或全量重新导入）

**步骤**：
```bash
# 导入客户 Excel
curl -s http://localhost:8080/api/customers/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/Users/admin/IdeaProjects/sanitary-admin/old-system-file/客户.xlsx" \
  | python3 -m json.tool
```

**预期响应**：
```json
{
  "code": 200,
  "data": {
    "success": "N（约500条）",
    "skip": 0,
    "fail": 0
  }
}
```

**验证 SQL**：
```sql
-- 1. 总数核对
SELECT COUNT(*) AS total_customers FROM customer WHERE deleted=0;
-- 预期: ~500

-- 2. 字段完整性抽查（客户名称不为空）
SELECT COUNT(*) AS missing_name FROM customer WHERE deleted=0 AND (customer_name IS NULL OR customer_name='');
-- 预期: 0

-- 3. 客户名称唯一性
SELECT customer_name, COUNT(*) AS cnt
FROM customer WHERE deleted=0
GROUP BY customer_name HAVING cnt > 1;
-- 预期: 无记录（不允许重名）

-- 4. 状态核对
SELECT status, COUNT(*) FROM customer WHERE deleted=0 GROUP BY status;
-- 预期: status=1 的记录数 = 总数（默认全启用）
```

**幂等性验证**：重复执行导入命令，`skip` 数 = 第一次 `success` 数，`success` = 0。

---

### TC-INIT-PROC-001：工艺数据导入

**目的**：将老系统工艺数据导入新系统

**步骤**：
```bash
curl -s http://localhost:8080/api/processes/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/Users/admin/IdeaProjects/sanitary-admin/old-system-file/工艺.xlsx" \
  | python3 -m json.tool
```

**预期响应**：
```json
{
  "code": 200,
  "data": { "success": "N（约160条）", "skip": 0, "fail": 0 }
}
```

**验证 SQL**：
```sql
-- 1. 总数核对
SELECT COUNT(*) AS total_processes FROM process WHERE deleted=0;
-- 预期: ~160

-- 2. 工艺名称唯一性
SELECT process_name, COUNT(*) AS cnt
FROM process WHERE deleted=0
GROUP BY process_name HAVING cnt > 1;
-- 预期: 无记录

-- 3. 工艺名称非空
SELECT COUNT(*) FROM process WHERE deleted=0 AND (process_name IS NULL OR process_name='');
-- 预期: 0
```

---

### TC-INIT-MAT-001：物料数据导入

**目的**：将老系统物料数据导入新系统（约 23000 条）

**注意**：物料必须在客户导入完成后导入（物料依赖 customer_id）；按 material_code 去重，存在则更新。

**步骤**：
```bash
curl -s http://localhost:8080/api/materials/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/Users/admin/IdeaProjects/sanitary-admin/old-system-file/物料.xlsx" \
  | python3 -m json.tool
```

**预期响应**：
```json
{
  "code": 200,
  "data": { "success": "N（约23000条）", "skip": 0, "fail": 0 }
}
```

**验证 SQL**：
```sql
-- 1. 总数核对
SELECT COUNT(*) AS total_materials FROM material WHERE deleted=0;
-- 预期: ~23000

-- 2. 物料编码唯一性
SELECT material_code, COUNT(*) AS cnt
FROM material WHERE deleted=0
GROUP BY material_code HAVING cnt > 1;
-- 预期: 无记录

-- 3. 物料必须关联有效客户
SELECT COUNT(*) AS orphan_materials
FROM material m
LEFT JOIN customer c ON m.customer_id = c.id AND c.deleted=0
WHERE m.deleted=0 AND c.id IS NULL;
-- 预期: 0（所有物料均有对应客户）

-- 4. 物料名称非空
SELECT COUNT(*) FROM material WHERE deleted=0 AND (material_name IS NULL OR material_name='');
-- 预期: 0

-- 5. 客户物料分布（抽查前10客户）
SELECT c.customer_name, COUNT(m.id) AS mat_count
FROM customer c
JOIN material m ON m.customer_id = c.id AND m.deleted=0
WHERE c.deleted=0
GROUP BY c.id, c.customer_name
ORDER BY mat_count DESC LIMIT 10;
```

**幂等性验证**：重复导入，`success`=0，`skip`=总条数（按 material_code 去重复用）。

---

### TC-INIT-BASIC-VERIFY-001：基础数据三表关联核对

**目的**：验证客户、工艺、物料三张表关系一致性

**验证 SQL**：
```sql
-- 无客户的物料（不应存在）
SELECT COUNT(*) AS mat_no_customer
FROM material WHERE deleted=0 AND customer_id IS NULL;
-- 预期: 0

-- 物料的客户名冗余字段与 customer 表一致性
SELECT COUNT(*) AS name_mismatch
FROM material m
JOIN customer c ON m.customer_id = c.id
WHERE m.deleted=0 AND c.deleted=0
  AND m.customer_name != c.customer_name;
-- 预期: 0（冗余字段应与主表一致）
```

---

## Stage 2：历史收货单导入

### TC-INIT-RECV-001：收货单历史数据导入（mode=history）

**目的**：导入老系统所有历史收货单，验证导入后不触发库存更新

**重要约束**：
- 必须使用 `mode=history`，否则会触发库存增加，导致期初数据错误
- 收货单 Excel 约 65535 行，需分批上传（每批 ≤3000 行），否则会 OOM
- 按 receipt_no 去重（已存在整单跳过）

**步骤**（以第1批为例，实际需上传全部分批文件）：
```bash
# 上传分批文件（示例：receipts-split/ 目录下所有文件逐一上传）
for f in /tmp/import-xlsx/receipts-split/*.xlsx; do
  echo "=== 上传: $f ==="
  curl -s http://localhost:8080/api/receipts/import?mode=history \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$f" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'success={d[\"data\"][\"success\"]} skip={d[\"data\"][\"skip\"]} fail={d[\"data\"][\"fail\"]}')"
done
```

**预期结果（每批）**：
- `code: 200`
- `fail: 0`（无失败记录）
- `success + skip = 本批总数`

**验证 SQL（全部批次上传完成后执行）**：
```sql
-- 1. 收货单主表数量
SELECT COUNT(*) AS total_receipts FROM receipt WHERE deleted=0;
-- 预期: ~10000单

-- 2. 收货单明细数量
SELECT COUNT(*) AS total_receipt_items FROM receipt_item WHERE deleted=0;
-- 预期: ~65000条

-- 3. 关键验证：mode=history 不应触发库存，库存表此时应为空
SELECT COUNT(*) AS inventory_count FROM inventory;
-- 预期: 0（history 模式不触发库存更新）

-- 4. 收货单状态分布
SELECT status, COUNT(*) FROM receipt WHERE deleted=0 GROUP BY status;
-- 预期: status=1 的数量 = 大部分（已确认状态导入）

-- 5. 按年月统计收货单分布（验证历史数据覆盖范围）
SELECT DATE_FORMAT(receipt_date, '%Y-%m') AS ym, COUNT(*) AS cnt
FROM receipt WHERE deleted=0
GROUP BY ym ORDER BY ym;

-- 6. 物料关联完整性（收货单明细中 material_id 均有效）
SELECT COUNT(*) AS items_no_material
FROM receipt_item ri
LEFT JOIN material m ON ri.material_id = m.id AND m.deleted=0
WHERE ri.deleted=0 AND m.id IS NULL AND ri.material_id IS NOT NULL;
-- 预期: 0（所有明细均能关联到物料）

-- 7. 客户关联完整性
SELECT COUNT(*) AS receipts_no_customer
FROM receipt r
LEFT JOIN customer c ON r.customer_id = c.id AND c.deleted=0
WHERE r.deleted=0 AND c.id IS NULL;
-- 预期: 0

-- 8. 数量字段非负核对
SELECT COUNT(*) AS negative_qty
FROM receipt_item WHERE deleted=0 AND quantity < 0;
-- 预期: 0
```

**幂等性验证**：重复上传同一批文件，`success=0`，`skip=N`（整单跳过），库存表仍为空。

---

### TC-INIT-RECV-002：收货单单价回填验证

**目的**：验证导入收货单时，系统自动将单价 ≤10000 的值回填到 material.default_price

**验证 SQL**：
```sql
-- 导入后，物料 default_price 应有值（非全0）
SELECT COUNT(*) AS has_price
FROM material WHERE deleted=0 AND default_price > 0;
-- 预期: 有相当比例的物料已回填单价

-- 抽查某物料的单价是否与收货单明细一致
SELECT m.material_code, m.material_name, m.default_price,
       ri.unit_price AS last_receipt_price
FROM material m
JOIN receipt_item ri ON ri.material_id = m.id AND ri.deleted=0
WHERE m.deleted=0 AND m.default_price > 0
ORDER BY ri.id DESC LIMIT 10;
```

---

## Stage 3：历史排产单导入

### TC-INIT-PROD-001：排产单历史数据导入（mode=history）

**目的**：导入老系统历史排产单，验证数量及字段正确

**步骤**：
```bash
curl -s "http://localhost:8080/api/productions/import?mode=history" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/Users/admin/IdeaProjects/sanitary-admin/old-system-file/排产单.xlsx" \
  | python3 -m json.tool
```

**预期响应**：
```json
{
  "code": 200,
  "data": { "success": "N（约600单）", "skip": 0, "fail": 0 }
}
```

**验证 SQL**：
```sql
-- 1. 排产单主表数量
SELECT COUNT(*) AS total_productions FROM production WHERE deleted=0;
-- 预期: ~600单

-- 2. 排产明细数量
SELECT COUNT(*) AS total_production_items FROM production_item WHERE deleted=0;
-- 预期: ~4500条

-- 3. 排产单按年月分布
SELECT DATE_FORMAT(production_date, '%Y-%m') AS ym, COUNT(*) AS cnt
FROM production WHERE deleted=0
GROUP BY ym ORDER BY ym;

-- 4. 排产数量非负
SELECT COUNT(*) FROM production_item WHERE deleted=0 AND planned_qty < 0;
-- 预期: 0

-- 5. 排产单不影响库存（库存仍为0）
SELECT COUNT(*) FROM inventory;
-- 预期: 0（排产不触发库存变动）
```

---

## Stage 4：历史发货单导入

### TC-INIT-SHIP-001：发货单历史数据导入

**目的**：导入老系统历史发货单数据（通过 Excel 导入接口）

**重要约束**：
- 必须使用 `mode=history`，否则发货会触发库存扣减，导致负库存
- 发货单导入后库存仍应为 0

**步骤**：
```bash
curl -s "http://localhost:8080/api/shipments/import?mode=history" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/Users/admin/IdeaProjects/sanitary-admin/old-system-file/发货单.xlsx" \
  | python3 -m json.tool
```

**预期响应**：
```json
{
  "code": 200,
  "data": { "success": "N", "skip": 0, "fail": 0 }
}
```

**验证 SQL**：
```sql
-- 1. 发货单主表数量
SELECT COUNT(*) AS total_shipments FROM shipment WHERE deleted=0;

-- 2. 发货明细数量
SELECT COUNT(*) AS total_shipment_items FROM shipment_item WHERE deleted=0;

-- 3. 关键验证：发货不触发库存（库存仍为0）
SELECT COUNT(*) FROM inventory;
-- 预期: 0（history 模式跳过库存更新）

-- 4. 发货数量非负
SELECT COUNT(*) FROM shipment_item WHERE deleted=0 AND quantity < 0;
-- 预期: 0

-- 5. 废品数量非负
SELECT COUNT(*) FROM shipment_item WHERE deleted=0 AND defective_qty < 0;
-- 预期: 0

-- 6. 发货单按年月分布
SELECT DATE_FORMAT(shipment_date, '%Y-%m') AS ym, COUNT(*) AS cnt
FROM shipment WHERE deleted=0
GROUP BY ym ORDER BY ym;

-- 7. 发货单客户关联完整性
SELECT COUNT(*) AS orphan_shipments
FROM shipment s
LEFT JOIN customer c ON s.customer_id = c.id AND c.deleted=0
WHERE s.deleted=0 AND c.id IS NULL;
-- 预期: 0
```

### TC-INIT-SHIP-002：收发货数据一致性预检

**目的**：在重建库存前，预检收发货数据的合理性

**验证 SQL**：
```sql
-- 按客户统计收货总量 vs 发货总量（history导入后，发货不应大幅超过收货）
SELECT
    c.customer_name,
    COALESCE(SUM(ri.quantity), 0) AS total_recv_qty,
    COALESCE(SUM(si.quantity + COALESCE(si.defective_qty,0)), 0) AS total_ship_qty,
    COALESCE(SUM(ri.quantity), 0) - COALESCE(SUM(si.quantity + COALESCE(si.defective_qty,0)), 0) AS balance
FROM customer c
LEFT JOIN receipt r ON r.customer_id = c.id AND r.deleted=0 AND r.status=1
LEFT JOIN receipt_item ri ON ri.receipt_id = r.id AND ri.deleted=0
LEFT JOIN shipment s ON s.customer_id = c.id AND s.deleted=0 AND s.status=1
LEFT JOIN shipment_item si ON si.shipment_id = s.id AND si.deleted=0
WHERE c.deleted=0
GROUP BY c.id, c.customer_name
ORDER BY balance ASC
LIMIT 20;
-- 说明: balance<0 的客户需要补录期初库存（正常现象，由 init_opening_stock.py 处理）
```

---

## Stage 5：收款记录导入

### TC-INIT-PAY-001：收款记录创建验证

**目的**：将老系统收款记录录入新系统（收款接口无批量导入，逐条或通过前端录入）

**步骤（逐条创建示例）**：
```bash
curl -s http://localhost:8080/api/payments \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentDate": "2025-01-15",
    "customerId": 1,
    "amount": 50000.00,
    "paymentMethod": "转账",
    "referenceNo": "银行流水-20250115",
    "remark": "历史收款补录"
  }' | python3 -m json.tool
```

**预期响应**：
```json
{ "code": 200, "data": { "id": "N", "paymentNo": "SK..." } }
```

**验证 SQL**：
```sql
-- 1. 收款记录总数
SELECT COUNT(*) AS total_payments FROM payment WHERE deleted=0;

-- 2. 按客户统计收款总金额
SELECT c.customer_name, COUNT(p.id) AS pay_count, SUM(p.amount) AS total_amount
FROM payment p
JOIN customer c ON p.customer_id = c.id AND c.deleted=0
WHERE p.deleted=0
GROUP BY c.id, c.customer_name
ORDER BY total_amount DESC LIMIT 10;

-- 3. 收款单号格式验证
SELECT COUNT(*) AS invalid_no FROM payment
WHERE deleted=0 AND payment_no NOT LIKE 'SK%';
-- 预期: 0
```

---

## Stage 6：期初库存补录

### TC-INIT-OPENING-001：运行期初库存补录脚本

**目的**：补录 2025 年前已有在途库存，防止对账单结余出现负数

**前置条件**：收货单、发货单历史数据已全部导入（Stage 2、4 完成）

**步骤**：
```bash
cd /Users/admin/IdeaProjects/sanitary-admin
python3 scripts/init_opening_stock.py
```

**预期输出**：
```
已插入/复用 RH-INIT 收货单：约35张
插入期初明细行：约853条（已存在的跳过）
```

**验证 SQL**：
```sql
-- 1. 期初收货单数量
SELECT COUNT(*) AS init_receipts
FROM receipt WHERE deleted=0 AND receipt_no LIKE 'RH-INIT-%';
-- 预期: ~35张

-- 2. 期初收货单明细数量
SELECT COUNT(*) AS init_items
FROM receipt_item ri
JOIN receipt r ON ri.receipt_id = r.id AND r.deleted=0
WHERE ri.deleted=0 AND r.receipt_no LIKE 'RH-INIT-%';
-- 预期: ~853条

-- 3. 期初收货单日期必须为 2024-12-31
SELECT COUNT(*) AS wrong_date
FROM receipt
WHERE deleted=0 AND receipt_no LIKE 'RH-INIT-%'
  AND receipt_date != '2024-12-31';
-- 预期: 0

-- 4. 期初收货单状态必须为 1（已确认）
SELECT COUNT(*) AS not_confirmed
FROM receipt
WHERE deleted=0 AND receipt_no LIKE 'RH-INIT-%' AND status != 1;
-- 预期: 0

-- 5. 期初补录后，按客户重新检查收发差值（应无大幅负值）
SELECT
    c.customer_name,
    COALESCE(SUM(ri.quantity), 0) AS total_recv,
    COALESCE(SUM(si.quantity + COALESCE(si.defective_qty,0)), 0) AS total_ship,
    COALESCE(SUM(ri.quantity), 0) - COALESCE(SUM(si.quantity + COALESCE(si.defective_qty,0)), 0) AS balance
FROM customer c
LEFT JOIN receipt r ON r.customer_id = c.id AND r.deleted=0 AND r.status=1
LEFT JOIN receipt_item ri ON ri.receipt_id = r.id AND ri.deleted=0
LEFT JOIN shipment s ON s.customer_id = c.id AND s.deleted=0 AND s.status=1
LEFT JOIN shipment_item si ON si.shipment_id = s.id AND si.deleted=0
WHERE c.deleted=0
GROUP BY c.id, c.customer_name
HAVING balance < 0
ORDER BY balance ASC;
-- 目标: 无负余额（期初补录后所有客户总收 >= 总发）
```

**幂等性验证**：重复执行脚本，已存在的收货单和明细行跳过，输出无新增记录。

---

### TC-INIT-OPENING-002：期初收货单与物料对应关系验证

**验证 SQL**：
```sql
-- 期初明细中 material_id 均有效
SELECT COUNT(*) AS orphan_init_items
FROM receipt_item ri
JOIN receipt r ON ri.receipt_id = r.id AND r.deleted=0
LEFT JOIN material m ON ri.material_id = m.id AND m.deleted=0
WHERE ri.deleted=0 AND r.receipt_no LIKE 'RH-INIT-%' AND m.id IS NULL;
-- 预期: 0

-- 按客户统计期初补录数量（应与各客户历史缺口对应）
SELECT r.receipt_no, c.customer_name, COUNT(ri.id) AS item_count, SUM(ri.quantity) AS total_qty
FROM receipt r
JOIN customer c ON r.customer_id = c.id
JOIN receipt_item ri ON ri.receipt_id = r.id AND ri.deleted=0
WHERE r.deleted=0 AND r.receipt_no LIKE 'RH-INIT-%'
GROUP BY r.id, r.receipt_no, c.customer_name
ORDER BY total_qty DESC;
```

---

## Stage 7：库存重建

### TC-INIT-INV-001：执行全量库存重建

**目的**：基于所有收货单（含期初）和发货单重新计算库存，确保数据准确

**前置条件**：Stage 2、3、4、6 均已完成

**步骤**：
```bash
curl -s http://localhost:8080/api/inventory/rebuild \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'code: {d[\"code\"]}')
if d['code'] == 200:
    print(f'inventoryRecords: {d[\"data\"][\"inventoryRecords\"]}')
    print(f'receiptLogs:      {d[\"data\"][\"receiptLogs\"]}')
    print(f'shipmentLogs:     {d[\"data\"][\"shipmentLogs\"]}')
" 2>/dev/null || cat /tmp/rebuild_result.json
```

**预期响应**：
```json
{
  "code": 200,
  "data": {
    "inventoryRecords": "N（≥1000，对应 material×customer×process 唯一组合数）",
    "receiptLogs": "N（≈ receipt_item 总条数）",
    "shipmentLogs": "N（≈ shipment_item 总条数）"
  }
}
```

**验证 SQL**：
```sql
-- 1. 库存记录总数
SELECT COUNT(*) AS total_inventory FROM inventory;
-- 预期: ≥1000（视实际物料×客户×工艺组合数）

-- 2. 关键验证：无负库存（或 ≤5 条历史误差可接受）
SELECT COUNT(*) AS negative_inventory FROM inventory WHERE quantity < 0;
-- 预期: 0（严格）或 ≤5（含历史数据录入误差）

-- 3. 负库存详情（如有，需排查）
SELECT i.customer_name, i.material_name, i.process_name, i.quantity
FROM inventory i
WHERE i.quantity < 0
ORDER BY i.quantity ASC;

-- 4. 库存等式核对（按客户+物料+工艺维度）
-- 库存量 = 所有收货合计 - 所有发货合计
SELECT
    inv.customer_name,
    inv.material_name,
    inv.process_name,
    inv.quantity AS inv_qty,
    COALESCE(recv.recv_qty, 0) AS recv_total,
    COALESCE(ship.ship_qty, 0) AS ship_total,
    COALESCE(recv.recv_qty, 0) - COALESCE(ship.ship_qty, 0) AS calc_qty,
    ABS(inv.quantity - (COALESCE(recv.recv_qty, 0) - COALESCE(ship.ship_qty, 0))) AS diff
FROM inventory inv
LEFT JOIN (
    SELECT ri.material_id, r.customer_id, COALESCE(ri.process_id, 0) AS process_id,
           SUM(ri.quantity) AS recv_qty
    FROM receipt_item ri
    JOIN receipt r ON ri.receipt_id = r.id AND r.deleted=0 AND r.status=1
    WHERE ri.deleted=0
    GROUP BY ri.material_id, r.customer_id, COALESCE(ri.process_id, 0)
) recv ON inv.material_id = recv.material_id
      AND inv.customer_id = recv.customer_id
      AND inv.process_id = recv.process_id
LEFT JOIN (
    SELECT si.material_id, s.customer_id, COALESCE(si.process_id, 0) AS process_id,
           SUM(si.quantity + COALESCE(si.defective_qty, 0)) AS ship_qty
    FROM shipment_item si
    JOIN shipment s ON si.shipment_id = s.id AND s.deleted=0 AND s.status=1
    WHERE si.deleted=0
    GROUP BY si.material_id, s.customer_id, COALESCE(si.process_id, 0)
) ship ON inv.material_id = ship.material_id
       AND inv.customer_id = ship.customer_id
       AND inv.process_id = ship.process_id
WHERE ABS(inv.quantity - (COALESCE(recv.recv_qty, 0) - COALESCE(ship.ship_qty, 0))) > 0.01
ORDER BY diff DESC LIMIT 20;
-- 预期: 无差异记录（diff ≤ 0.01 浮点误差可接受）

-- 5. 库存总量汇总
SELECT COUNT(*) AS records,
       SUM(quantity) AS total_qty,
       MIN(quantity) AS min_qty,
       MAX(quantity) AS max_qty
FROM inventory;
```

---

### TC-INIT-INV-002：库存流水验证

**验证 SQL**：
```sql
-- 库存流水变动类型分布（重建后应有 type=1收货、type=2发货）
SELECT change_type,
       CASE change_type WHEN 1 THEN '收货' WHEN 2 THEN '发货' WHEN 3 THEN '返工' END AS type_name,
       COUNT(*) AS cnt, SUM(change_qty) AS total_qty
FROM inventory_log
GROUP BY change_type;

-- 流水数量与明细数量应匹配
SELECT
  (SELECT COUNT(*) FROM inventory_log WHERE change_type=1) AS log_recv_count,
  (SELECT COUNT(*) FROM receipt_item WHERE deleted=0) AS receipt_item_count;
-- 预期: 两者接近（差异不超过 5%）

SELECT
  (SELECT COUNT(*) FROM inventory_log WHERE change_type=2) AS log_ship_count,
  (SELECT COUNT(*) FROM shipment_item WHERE deleted=0) AS shipment_item_count;
-- 预期: 两者接近
```

---

## Stage 8：批量生成对账单

### TC-INIT-STMT-001：批量生成所有月份对账单

**目的**：对所有客户×月份组合批量生成对账单，验证结余等式成立

**步骤**：
```bash
curl -s http://localhost:8080/api/statements/generate-all \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'code: {d[\"code\"]}')
if d['code'] == 200:
    print(f'generated: {d[\"data\"].get(\"generated\", \"N\")}')
    print(f'skipped:   {d[\"data\"].get(\"skipped\", \"N\")}')
"
```

**预期响应**：
```json
{
  "code": 200,
  "data": {
    "generated": "N（所有客户×月份组合数）",
    "skipped": 0
  }
}
```

**验证 SQL**：
```sql
-- 1. 对账单主表数量
SELECT COUNT(*) AS total_statements FROM statement WHERE deleted=0;

-- 2. 对账单明细数量
SELECT COUNT(*) AS total_statement_items FROM statement_item WHERE deleted=0;

-- 3. 关键验证：结余等式是否成立
-- curr_balance_qty = prev_balance_qty + receipt_qty - shipment_qty
SELECT COUNT(*) AS equation_violation
FROM statement_item
WHERE deleted=0
  AND ABS(curr_balance_qty - (prev_balance_qty + receipt_qty - shipment_qty)) > 0.01;
-- 预期: 0（等式必须100%成立）

-- 4. 关键验证：无负结余
SELECT COUNT(*) AS negative_balance
FROM statement_item WHERE deleted=0 AND curr_balance_qty < 0;
-- 预期: 0（不允许负结余）

-- 5. 负结余详情（如有，需排查期初补录是否完整）
SELECT s.statement_month, s.customer_name,
       si.material_name, si.process_name,
       si.prev_balance_qty, si.receipt_qty, si.shipment_qty, si.curr_balance_qty
FROM statement_item si
JOIN statement s ON si.statement_id = s.id AND s.deleted=0
WHERE si.deleted=0 AND si.curr_balance_qty < 0
ORDER BY si.curr_balance_qty ASC LIMIT 20;

-- 6. 对账单月份分布
SELECT statement_month, COUNT(*) AS stmt_count
FROM statement WHERE deleted=0
GROUP BY statement_month ORDER BY statement_month;

-- 7. 主表汇总与明细汇总一致性
SELECT
    s.id, s.statement_month, s.customer_name,
    s.receipt_qty AS hdr_recv, SUM(si.receipt_qty) AS dtl_recv,
    s.shipment_qty AS hdr_ship, SUM(si.shipment_qty) AS dtl_ship,
    ABS(s.receipt_qty - SUM(si.receipt_qty)) AS recv_diff,
    ABS(s.shipment_qty - SUM(si.shipment_qty)) AS ship_diff
FROM statement s
JOIN statement_item si ON si.statement_id = s.id AND si.deleted=0
WHERE s.deleted=0
GROUP BY s.id, s.statement_month, s.customer_name,
         s.receipt_qty, s.shipment_qty
HAVING recv_diff > 0.01 OR ship_diff > 0.01
ORDER BY recv_diff DESC LIMIT 10;
-- 预期: 无记录（主表与明细汇总完全一致）
```

---

### TC-INIT-STMT-002：对账单跨月结余链式验证

**目的**：验证相邻月份之间，上月结余 = 下月 prev_balance_qty（当月结余正确传递）

**验证 SQL**：
```sql
-- 相邻月份结余链式核对（同一客户+物料+工艺维度）
SELECT
    a.customer_name,
    a.material_name,
    a.process_name,
    a.statement_month AS prev_month,
    a.curr_balance_qty AS prev_curr_balance,
    b.statement_month AS curr_month,
    b.prev_balance_qty AS curr_prev_balance,
    ABS(a.curr_balance_qty - b.prev_balance_qty) AS diff
FROM statement_item a
JOIN statement sa ON a.statement_id = sa.id AND sa.deleted=0
JOIN statement_item b ON b.material_id = a.material_id
                      AND b.process_id = a.process_id
JOIN statement sb ON b.statement_id = sb.id AND sb.deleted=0
                  AND sb.customer_id = sa.customer_id
                  AND sb.statement_month = DATE_FORMAT(
                        DATE_ADD(STR_TO_DATE(CONCAT(sa.statement_month,'-01'),'%Y-%m-%d'),
                                 INTERVAL 1 MONTH), '%Y-%m')
WHERE a.deleted=0 AND b.deleted=0
  AND ABS(a.curr_balance_qty - b.prev_balance_qty) > 0.01
ORDER BY diff DESC LIMIT 20;
-- 预期: 无记录（上月结余 = 下月期初）
-- 注意: prevBalanceQty 是直接聚合历史数据计算，不依赖链式传递，理论上等式必然成立
```

---

## Stage 9：全链路数据一致性核对

### TC-INIT-CONSIST-001：收货→库存一致性

**目的**：验证库存收货合计与 receipt_item 总量一致

**验证 SQL**：
```sql
-- 收货汇总 vs 库存记录的收货量（按 material+customer+process）
SELECT
    recv.material_id, recv.customer_id, recv.process_id,
    recv.total_recv_qty,
    inv.quantity AS current_inv,
    COALESCE(ship.total_ship_qty, 0) AS total_ship,
    recv.total_recv_qty - COALESCE(ship.total_ship_qty, 0) AS expected_inv,
    ABS(inv.quantity - (recv.total_recv_qty - COALESCE(ship.total_ship_qty, 0))) AS diff
FROM (
    SELECT ri.material_id, r.customer_id, COALESCE(ri.process_id, 0) AS process_id,
           SUM(ri.quantity) AS total_recv_qty
    FROM receipt_item ri
    JOIN receipt r ON ri.receipt_id = r.id AND r.deleted=0 AND r.status=1
    WHERE ri.deleted=0
    GROUP BY ri.material_id, r.customer_id, COALESCE(ri.process_id, 0)
) recv
JOIN inventory inv ON inv.material_id = recv.material_id
                   AND inv.customer_id = recv.customer_id
                   AND inv.process_id = recv.process_id
LEFT JOIN (
    SELECT si.material_id, s.customer_id, COALESCE(si.process_id, 0) AS process_id,
           SUM(si.quantity + COALESCE(si.defective_qty, 0)) AS total_ship_qty
    FROM shipment_item si
    JOIN shipment s ON si.shipment_id = s.id AND s.deleted=0 AND s.status=1
    WHERE si.deleted=0
    GROUP BY si.material_id, s.customer_id, COALESCE(si.process_id, 0)
) ship ON recv.material_id = ship.material_id
       AND recv.customer_id = ship.customer_id
       AND recv.process_id = ship.process_id
WHERE ABS(inv.quantity - (recv.total_recv_qty - COALESCE(ship.total_ship_qty, 0))) > 0.01
ORDER BY diff DESC LIMIT 20;
-- 预期: 无记录（库存与收发差值完全一致）
```

---

### TC-INIT-CONSIST-002：发货→库存一致性

**目的**：验证发货（含废品）正确扣减库存

**验证 SQL**：
```sql
-- 发货合计（良品+废品）核对
SELECT
    SUM(si.quantity) AS total_good_qty,
    SUM(COALESCE(si.defective_qty, 0)) AS total_defective_qty,
    SUM(si.quantity + COALESCE(si.defective_qty, 0)) AS total_ship_qty
FROM shipment_item si
JOIN shipment s ON si.shipment_id = s.id AND s.deleted=0 AND s.status=1
WHERE si.deleted=0;

-- 库存流水中发货扣减总量（应与上面一致）
SELECT SUM(ABS(change_qty)) AS log_ship_total
FROM inventory_log WHERE change_type = 2;
-- 预期: 与 total_ship_qty 接近（允许 0.1% 误差）

-- 废品不计入金额验证（shipment_item.amount 应只含良品数量×单价）
SELECT COUNT(*) AS wrong_amount
FROM shipment_item si
JOIN shipment s ON si.shipment_id = s.id AND s.deleted=0
WHERE si.deleted=0
  AND si.defective_qty > 0
  AND ABS(si.amount - si.quantity * si.unit_price) > 0.01;
-- 预期: 0（废品数量不计入金额）
```

---

### TC-INIT-CONSIST-003：对账单→收发货三表核对

**目的**：验证对账单汇总数与 receipt_item / shipment_item 直接聚合结果一致

**验证 SQL**：
```sql
-- 对账单月度汇总 vs 直接聚合收发数据的差异
SELECT
    s.statement_month,
    s.customer_name,
    s.receipt_qty AS stmt_recv_qty,
    COALESCE(recv.actual_recv_qty, 0) AS actual_recv_qty,
    s.shipment_qty AS stmt_ship_qty,
    COALESCE(ship.actual_ship_qty, 0) AS actual_ship_qty,
    ABS(s.receipt_qty - COALESCE(recv.actual_recv_qty, 0)) AS recv_diff,
    ABS(s.shipment_qty - COALESCE(ship.actual_ship_qty, 0)) AS ship_diff
FROM statement s
LEFT JOIN (
    SELECT r.customer_id, DATE_FORMAT(r.receipt_date, '%Y-%m') AS ym,
           SUM(ri.quantity) AS actual_recv_qty
    FROM receipt r
    JOIN receipt_item ri ON ri.receipt_id = r.id AND ri.deleted=0
    WHERE r.deleted=0 AND r.status=1
    GROUP BY r.customer_id, DATE_FORMAT(r.receipt_date, '%Y-%m')
) recv ON s.customer_id = recv.customer_id AND s.statement_month = recv.ym
LEFT JOIN (
    SELECT sh.customer_id, DATE_FORMAT(sh.shipment_date, '%Y-%m') AS ym,
           SUM(si.quantity + COALESCE(si.defective_qty, 0)) AS actual_ship_qty
    FROM shipment sh
    JOIN shipment_item si ON si.shipment_id = sh.id AND si.deleted=0
    WHERE sh.deleted=0 AND sh.status=1
    GROUP BY sh.customer_id, DATE_FORMAT(sh.shipment_date, '%Y-%m')
) ship ON s.customer_id = ship.customer_id AND s.statement_month = ship.ym
WHERE s.deleted=0
  AND (ABS(s.receipt_qty - COALESCE(recv.actual_recv_qty, 0)) > 0.01
    OR ABS(s.shipment_qty - COALESCE(ship.actual_ship_qty, 0)) > 0.01)
ORDER BY recv_diff + ship_diff DESC LIMIT 20;
-- 预期: 无记录（对账单汇总与原始数据完全一致）
```

---

### TC-INIT-CONSIST-004：返工来源收货记录验证

**目的**：验证 `receiptSource=返工` 的收货明细已被正确导入，并在库存重建后计入库存

> **说明**：返工没有独立的单据录入流程，返工物料统一通过收货单（`receiptSource=返工`）处理，此用例验证该类收货记录的完整性。

**验证 SQL**：
```sql
-- 1. 返工来源的收货明细数量
SELECT COUNT(*) AS rework_recv_count
FROM receipt_item WHERE deleted=0 AND receipt_source='返工';
-- 预期: 有记录（来自历史数据中有返工场景的客户）

-- 2. 返工来源收货单分布（按客户汇总）
SELECT c.customer_name,
       COUNT(DISTINCT r.id) AS receipt_count,
       COUNT(ri.id) AS item_count,
       SUM(ri.quantity) AS total_qty
FROM receipt r
JOIN customer c ON r.customer_id = c.id AND c.deleted=0
JOIN receipt_item ri ON ri.receipt_id = r.id AND ri.deleted=0
WHERE r.deleted=0 AND ri.receipt_source = '返工'
GROUP BY c.id, c.customer_name
ORDER BY total_qty DESC LIMIT 10;

-- 3. 返工收货已纳入库存计算（库存量包含返工来源的收货数量）
-- 验证方式：库存等式中已含返工收货，无需单独核对
-- 可抽查某客户某物料：收货合计（含返工）- 发货合计 = 当前库存
SELECT
    c.customer_name, m.material_name, p.process_name,
    SUM(CASE WHEN ri.receipt_source='返工' THEN ri.quantity ELSE 0 END) AS rework_recv_qty,
    SUM(ri.quantity) AS total_recv_qty,
    inv.quantity AS current_inv
FROM receipt_item ri
JOIN receipt r ON ri.receipt_id = r.id AND r.deleted=0 AND r.status=1
JOIN customer c ON r.customer_id = c.id AND c.deleted=0
JOIN material m ON ri.material_id = m.id AND m.deleted=0
LEFT JOIN process p ON ri.process_id = p.id
LEFT JOIN inventory inv ON inv.material_id = ri.material_id
                        AND inv.customer_id = r.customer_id
                        AND inv.process_id = COALESCE(ri.process_id, 0)
WHERE ri.deleted=0 AND ri.receipt_source='返工'
GROUP BY c.id, c.customer_name, m.id, m.material_name, p.process_name, inv.quantity
ORDER BY rework_recv_qty DESC LIMIT 10;
```

---

## Stage 10：业务流转链条端到端验证

> **目的**：选取具体客户+物料+工艺组合，按月份顺序追踪完整业务链：
> 收货 → 发货 → 收款 → 对账（结余） → 下月再收货（含返工来源） → 再发货 → 再收款 → 再对账
> 验证每个月份的对账单结余与实际收发数据完全吻合，且相邻月份结余正确衔接。

---

### TC-INIT-FLOW-001：抽取代表性客户，逐月验证完整业务流水

**步骤一：找出有多月连续业务的客户（收发货跨≥3个月）**

```sql
-- 找出同时有收货、发货、收款、对账单的客户（业务最完整）
SELECT
    c.id AS customer_id,
    c.customer_name,
    COUNT(DISTINCT DATE_FORMAT(r.receipt_date,'%Y-%m')) AS recv_months,
    COUNT(DISTINCT DATE_FORMAT(s.shipment_date,'%Y-%m')) AS ship_months,
    COUNT(DISTINCT stmt.statement_month) AS stmt_months,
    COUNT(DISTINCT p.id) AS pay_count
FROM customer c
JOIN receipt r ON r.customer_id = c.id AND r.deleted=0 AND r.status=1
JOIN shipment s ON s.customer_id = c.id AND s.deleted=0 AND s.status=1
JOIN statement stmt ON stmt.customer_id = c.id AND stmt.deleted=0
LEFT JOIN payment p ON p.customer_id = c.id AND p.deleted=0
WHERE c.deleted=0
GROUP BY c.id, c.customer_name
HAVING recv_months >= 3 AND ship_months >= 3 AND stmt_months >= 3
ORDER BY stmt_months DESC, pay_count DESC
LIMIT 5;
```

记录其中一个 customer_id（以下用 ${CUST_ID} 代替）作为追踪对象。

---

**步骤二：查该客户所有月份的对账单明细（按月份+物料展开）**

```sql
-- 替换 ${CUST_ID} 为实际值，例如 1
SET @cust_id = ${CUST_ID};

SELECT
    stmt.statement_month,
    si.material_name,
    si.process_name,
    si.prev_balance_qty,
    si.receipt_qty,
    si.shipment_qty,
    si.defective_qty,
    si.curr_balance_qty,
    si.unit_price,
    si.goods_amount,
    -- 验证等式
    (si.prev_balance_qty + si.receipt_qty - si.shipment_qty) AS calc_curr_balance,
    ABS(si.curr_balance_qty - (si.prev_balance_qty + si.receipt_qty - si.shipment_qty)) AS equation_diff
FROM statement_item si
JOIN statement stmt ON si.statement_id = stmt.id AND stmt.deleted=0
WHERE stmt.customer_id = @cust_id AND si.deleted=0
ORDER BY stmt.statement_month, si.material_name, si.process_name;
```

**预期**：`equation_diff` 全部为 0，`curr_balance_qty` 全部 ≥ 0。

---

**步骤三：验证相邻月份结余衔接（上月结余 = 下月 prev_balance_qty）**

```sql
SET @cust_id = ${CUST_ID};

SELECT
    a.statement_month  AS month_A,
    b.statement_month  AS month_B,
    a.material_name,
    a.process_name,
    a.curr_balance_qty AS A_curr_balance,
    b.prev_balance_qty AS B_prev_balance,
    ABS(a.curr_balance_qty - b.prev_balance_qty) AS carry_diff
FROM statement_item a
JOIN statement sa ON a.statement_id = sa.id AND sa.deleted=0 AND sa.customer_id = @cust_id
JOIN statement_item b ON b.material_id = a.material_id
                      AND b.process_id = a.process_id
JOIN statement sb ON b.statement_id = sb.id AND sb.deleted=0 AND sb.customer_id = @cust_id
                  AND sb.statement_month = DATE_FORMAT(
                        DATE_ADD(STR_TO_DATE(CONCAT(sa.statement_month,'-01'),'%Y-%m-%d'),
                                 INTERVAL 1 MONTH), '%Y-%m')
WHERE a.deleted=0 AND b.deleted=0
ORDER BY sa.statement_month, a.material_name;
```

**预期**：`carry_diff` 全部为 0（上月结余与下月期初完全一致）。

---

**步骤四：将对账单收发数与原始单据逐月比对**

```sql
SET @cust_id = ${CUST_ID};

-- 对账单汇总 vs 原始收货单明细（逐月）
SELECT
    stmt.statement_month,
    stmt.receipt_qty       AS stmt_recv_qty,
    SUM(ri.quantity)       AS actual_recv_qty,
    ABS(stmt.receipt_qty - SUM(ri.quantity)) AS recv_diff,
    stmt.shipment_qty      AS stmt_ship_qty,
    SUM(si.quantity + COALESCE(si.defective_qty,0)) AS actual_ship_qty,
    ABS(stmt.shipment_qty - SUM(si.quantity + COALESCE(si.defective_qty,0))) AS ship_diff
FROM statement stmt
-- 收货明细
LEFT JOIN receipt r
    ON r.customer_id = @cust_id AND r.deleted=0 AND r.status=1
    AND DATE_FORMAT(r.receipt_date,'%Y-%m') = stmt.statement_month
LEFT JOIN receipt_item ri ON ri.receipt_id = r.id AND ri.deleted=0
-- 发货明细
LEFT JOIN shipment s
    ON s.customer_id = @cust_id AND s.deleted=0 AND s.status=1
    AND DATE_FORMAT(s.shipment_date,'%Y-%m') = stmt.statement_month
LEFT JOIN shipment_item si ON si.shipment_id = s.id AND si.deleted=0
WHERE stmt.customer_id = @cust_id AND stmt.deleted=0
GROUP BY stmt.id, stmt.statement_month, stmt.receipt_qty, stmt.shipment_qty
ORDER BY stmt.statement_month;
```

**预期**：`recv_diff` 和 `ship_diff` 全部为 0。

---

**步骤五：验证收款金额与对账单发货金额的关系**

```sql
SET @cust_id = ${CUST_ID};

-- 按月汇总：对账单发货金额 vs 当月收款金额
SELECT
    stmt.statement_month,
    stmt.shipment_amount      AS stmt_ship_amount,
    COALESCE(SUM(p.amount),0) AS actual_payment,
    stmt.shipment_amount - COALESCE(SUM(p.amount),0) AS outstanding
FROM statement stmt
LEFT JOIN payment p
    ON p.customer_id = @cust_id AND p.deleted=0
    AND DATE_FORMAT(p.payment_date,'%Y-%m') = stmt.statement_month
WHERE stmt.customer_id = @cust_id AND stmt.deleted=0
GROUP BY stmt.id, stmt.statement_month, stmt.shipment_amount
ORDER BY stmt.statement_month;
```

**说明**：`outstanding > 0` 表示当月有欠款，`outstanding < 0` 表示预付款，均为正常业务现象，此步骤只验证数据存在且金额不为空（shipment_amount > 0）。

---

### TC-INIT-FLOW-002：含返工来源收货的月份专项验证

**目的**：找到有 `receiptSource=返工` 收货记录的月份，验证该月对账单的 receipt_qty 已正确包含返工来源的收货量。

```sql
-- 找含返工收货的客户+月份
SELECT
    r.customer_id,
    c.customer_name,
    DATE_FORMAT(r.receipt_date,'%Y-%m') AS ym,
    SUM(CASE WHEN ri.receipt_source='返工' THEN ri.quantity ELSE 0 END) AS rework_recv_qty,
    SUM(ri.quantity) AS total_recv_qty
FROM receipt r
JOIN customer c ON r.customer_id = c.id AND c.deleted=0
JOIN receipt_item ri ON ri.receipt_id = r.id AND ri.deleted=0
WHERE r.deleted=0 AND r.status=1 AND ri.receipt_source='返工'
GROUP BY r.customer_id, c.customer_name, DATE_FORMAT(r.receipt_date,'%Y-%m')
ORDER BY rework_recv_qty DESC LIMIT 10;
```

记录一组 customer_id + ym，执行以下验证：

```sql
-- 替换实际值
SET @cust_id = ${CUST_ID};
SET @ym = '${YM}'; -- 例如 '2025-03'

-- 对账单中该月 receipt_qty 应 = 正常收货 + 返工收货
SELECT
    stmt.statement_month,
    stmt.receipt_qty AS stmt_recv_qty,
    SUM(ri.quantity) AS all_recv_qty,
    SUM(CASE WHEN ri.receipt_source='返工' THEN ri.quantity ELSE 0 END) AS rework_qty,
    SUM(CASE WHEN ri.receipt_source!='返工' THEN ri.quantity ELSE 0 END) AS normal_qty,
    ABS(stmt.receipt_qty - SUM(ri.quantity)) AS diff
FROM statement stmt
JOIN receipt r ON r.customer_id = @cust_id AND r.deleted=0 AND r.status=1
              AND DATE_FORMAT(r.receipt_date,'%Y-%m') = @ym
JOIN receipt_item ri ON ri.receipt_id = r.id AND ri.deleted=0
WHERE stmt.customer_id = @cust_id AND stmt.deleted=0 AND stmt.statement_month = @ym
GROUP BY stmt.id, stmt.statement_month, stmt.receipt_qty;
```

**预期**：`diff = 0`，即对账单收货量 = 正常收货 + 返工来源收货之和（返工统一视为收货处理，不做区分）。

---

### TC-INIT-FLOW-003：多月累计收发与库存终态核对

**目的**：对选定客户，验证"所有历史月份收货合计 - 发货合计 = 当前库存"，确保全生命周期数据闭合。

```sql
SET @cust_id = ${CUST_ID};

-- 按 material+process 维度：历史总收 - 历史总发 vs 当前库存
SELECT
    inv.material_name,
    inv.process_name,
    COALESCE(recv.total_recv, 0)  AS total_recv_qty,
    COALESCE(ship.total_ship, 0)  AS total_ship_qty,
    COALESCE(recv.total_recv, 0) - COALESCE(ship.total_ship, 0) AS expected_inv,
    inv.quantity                  AS actual_inv,
    ABS(inv.quantity - (COALESCE(recv.total_recv,0) - COALESCE(ship.total_ship,0))) AS diff
FROM inventory inv
LEFT JOIN (
    SELECT ri.material_id, COALESCE(ri.process_id,0) AS process_id, SUM(ri.quantity) AS total_recv
    FROM receipt_item ri
    JOIN receipt r ON ri.receipt_id = r.id AND r.deleted=0 AND r.status=1
                   AND r.customer_id = @cust_id
    WHERE ri.deleted=0
    GROUP BY ri.material_id, COALESCE(ri.process_id,0)
) recv ON inv.material_id = recv.material_id AND inv.process_id = recv.process_id
LEFT JOIN (
    SELECT si.material_id, COALESCE(si.process_id,0) AS process_id,
           SUM(si.quantity + COALESCE(si.defective_qty,0)) AS total_ship
    FROM shipment_item si
    JOIN shipment s ON si.shipment_id = s.id AND s.deleted=0 AND s.status=1
                    AND s.customer_id = @cust_id
    WHERE si.deleted=0
    GROUP BY si.material_id, COALESCE(si.process_id,0)
) ship ON inv.material_id = ship.material_id AND inv.process_id = ship.process_id
WHERE inv.customer_id = @cust_id
ORDER BY diff DESC, inv.material_name;
```

**预期**：`diff` 全部为 0（允许 ≤0.01 的浮点误差）。

---

## 测试执行总结表

| Stage | 用例编号 | 测试内容 | 关键验证指标 | 状态 |
|-------|---------|---------|------------|------|
| 1 | TC-INIT-CUST-001 | 客户数据导入 | 数量~500，无重名，无孤立记录 | 待执行 |
| 1 | TC-INIT-PROC-001 | 工艺数据导入 | 数量~160，名称唯一 | 待执行 |
| 1 | TC-INIT-MAT-001 | 物料数据导入 | 数量~23000，关联客户有效 | 待执行 |
| 1 | TC-INIT-BASIC-VERIFY-001 | 基础数据三表核对 | 孤立记录=0，冗余字段一致 | 待执行 |
| 2 | TC-INIT-RECV-001 | 历史收货单导入(history) | 数量~10000单，库存=0 | 待执行 |
| 2 | TC-INIT-RECV-002 | 收货单单价回填验证 | 物料 default_price 有值 | 待执行 |
| 3 | TC-INIT-PROD-001 | 历史排产单导入(history) | 数量~600单，不影响库存 | 待执行 |
| 4 | TC-INIT-SHIP-001 | 历史发货单导入(history) | 库存仍=0，废品字段非负 | 待执行 |
| 4 | TC-INIT-SHIP-002 | 收发货预检 | 负余额客户需期初补录 | 待执行 |
| 5 | TC-INIT-PAY-001 | 收款记录创建 | 单号格式SK..，金额非负 | 待执行 |
| 6 | TC-INIT-OPENING-001 | 期初库存补录脚本 | ~35单~853条，日期=2024-12-31 | 待执行 |
| 6 | TC-INIT-OPENING-002 | 期初单物料关联验证 | 孤立明细=0 | 待执行 |
| 7 | TC-INIT-INV-001 | 库存全量重建 | 负库存≤5，等式成立 | 待执行 |
| 7 | TC-INIT-INV-002 | 库存流水验证 | 流水数与明细数匹配 | 待执行 |
| 8 | TC-INIT-STMT-001 | 批量生成对账单 | 结余等式=0违反，负结余=0 | 待执行 |
| 8 | TC-INIT-STMT-002 | 对账单跨月结余链式验证 | 相邻月份结余差=0 | 待执行 |
| 9 | TC-INIT-CONSIST-001 | 收货→库存一致性 | 差异记录=0 | 待执行 |
| 9 | TC-INIT-CONSIST-002 | 发货→库存一致性 | 废品不计金额，流水匹配 | 待执行 |
| 9 | TC-INIT-CONSIST-003 | 对账单→收发货三表核对 | 汇总差异=0 | 待执行 |
| 9 | TC-INIT-CONSIST-004 | 返工来源收货记录验证 | receiptSource=返工 的明细已计入库存 | 待执行 |
| 10 | TC-INIT-FLOW-001 | 代表性客户逐月完整业务流水验证 | 等式差=0，结余≥0，相邻月衔接差=0，对账单与原始单据一致 | 待执行 |
| 10 | TC-INIT-FLOW-002 | 含返工来源收货的月份专项验证 | 对账单receipt_qty=正常收货+返工收货，diff=0 | 待执行 |
| 10 | TC-INIT-FLOW-003 | 多月累计收发与库存终态核对 | 历史总收-总发=当前库存，diff≤0.01 | 待执行 |

---

*文档版本：v1.0 | 生成日期：2026-03-17*
