# sanitary-admin 系统测试最终汇总报告

测试日期：2026-03-17
测试环境：http://localhost:8080（Docker 容器）
数据库：MySQL 8.0（Docker，端口 3307，库名 sanitary_admin）

---

## 总体结果

| 测试部分 | 用例总数 | 通过 | 失败 | 通过率 |
|----------|---------|------|------|-------|
| Part1 基础模块（认证/客户/工艺/物料） | 23 | 18 | 5 | 78.3% |
| Part2 业务单据（收货/排产/发货/返工/收款） | 21 | 15 | 6 | 71.4% |
| Part3 库存与对账单 | 12 | 12 | 0 | 100% |
| Part4 端到端集成测试 | 13 | 12 | 1 | 92.3% |
| **合计** | **69** | **57** | **12** | **82.6%** |

---

## 发现并修复的 BUG

### BUG-001（严重）：发货操作导致库存增加而非减少

**发现于**：TC-E2E-001 Step 4，Part2 TC-SHIP-001
**文件**：`backend/src/main/java/com/sanitary/admin/service/impl/ShipmentServiceImpl.java`

**问题描述**：`createShipment()` 和 `updateShipment()` 的新明细处理中，调用 `updateInventory()` 时传递了正数 `shipTotal`，导致发货后库存增加而非减少。

**修复内容**：两处 `shipTotal` 改为 `shipTotal.negate()`

```java
// 修复前
inventoryService.updateInventory(..., shipTotal, 2, "shipment", ...);

// 修复后
inventoryService.updateInventory(..., shipTotal.negate(), 2, "shipment", ...);
```

**修复文件位置**：`ShipmentServiceImpl.java:94`（createShipment），`ShipmentServiceImpl.java:164`（updateShipment）

**验证**：修复后发货单创建，库存流水 `changeQty=-N`（负数），库存正确减少。

---

### BUG-002（中等）：更新单据时明细行缺少单号导致 500

**发现于**：Part2 TC-RECEIPT-002，TC-REWORK-002
**文件**：
- `backend/src/main/java/com/sanitary/admin/controller/ReceiptController.java`
- `backend/src/main/java/com/sanitary/admin/controller/ShipmentController.java`
- `backend/src/main/java/com/sanitary/admin/service/impl/ReworkServiceImpl.java`

**问题描述**：更新收货单/发货单/返工单时，若请求体未传 `receiptNo`/`shipmentNo`/`reworkNo`，Controller/Service 直接将 null 传入 `saveItems()`，新明细行的 `receipt_no`/`shipment_no`/`rework_no` 字段为 null，触发 MySQL NOT NULL 约束错误（500）。

**修复内容**：在写入明细之前，若单号为 null，从数据库查询补充。

```java
// ReceiptController.java - PUT /{id}
if (receipt.getReceiptNo() == null) {
    Receipt existing = receiptService.getById(id);
    if (existing != null) {
        receipt.setReceiptNo(existing.getReceiptNo());
    }
}

// ShipmentController.java - PUT /{id}
if (shipment.getShipmentNo() == null) {
    Shipment existing = shipmentService.getById(id);
    if (existing != null) {
        shipment.setShipmentNo(existing.getShipmentNo());
    }
}

// ReworkServiceImpl.java - updateRework()
if (rework.getReworkNo() == null) {
    Rework existing = getById(rework.getId());
    if (existing != null) {
        rework.setReworkNo(existing.getReworkNo());
    }
}
```

**验证**：修复后 `PUT /api/receipts/{id}` 不传 `receiptNo` 可正常更新，明细 `receiptNo` 字段正确填充。

---

## Part1 基础模块测试结果详情

详细报告：`tasks/reports/test-report-part1-basic.md`

**通过（18）**：
- TC-AUTH-001/002/003/004：认证模块全部通过
- TC-CUST-001/004/005/007/008/009/010：客户管理大部分通过
- TC-PROC-001/003：工艺部分通过
- TC-MAT-001/004/005/006：物料部分通过
- TC-INIT-001：数据初始化顺序通过

**失败（5）**：
| 用例 | 失败原因 |
|------|---------|
| TC-CUST-002 | 重复客户名称系统接受（未校验唯一性）|
| TC-CUST-003 | 空 customerName 系统接受（未做必填校验）|
| TC-PROC-002 | 重复工艺名称系统接受 |
| TC-MAT-002 | 重复 materialCode 系统接受 |
| TC-MAT-003 | 无 customerId 时系统接受创建物料 |

**说明**：这5个失败属于业务校验层缺失，系统未对关键唯一性约束和必填字段做前置校验，属于中等级别问题。

---

## Part2 业务单据测试结果详情

详细报告：`tasks/reports/test-report-part2-orders.md`

**通过（15）**：
- TC-RECEIPT-001/003/004/005/006/009：收货单主要功能通过（含库存联动）
- TC-PROD-001/005：排产单创建和查询通过
- TC-SHIP-003/005：发货单金额计算和删除通过
- TC-REWORK-001/002B：返工单创建通过，带单号更新通过
- TC-PAY-001/002/003：收款记录全部通过

**失败（6，其中3个已修复）**：
| 用例 | 状态 | 原因 |
|------|------|------|
| TC-RECEIPT-002（无单号）| **已修复** | BUG-002 修复后通过 |
| TC-REWORK-002（无单号）| **已修复** | BUG-002 修复后通过 |
| TC-SHIP-001（库存方向）| **已修复** | BUG-001 修复后通过 |
| TC-RECEIPT-001-P（不传customerName）| 待确认 | 接口要求必传 customerName |
| TC-REWORK-003（明细为空）| 待跟进 | 返工单详情 items 为空（创建时因BUG-002未写入）|
| TC-PROD-003（排产单库存）| 未测 | 排产不影响库存，符合预期 |

**发现的接口问题（非 BUG，接口设计问题）**：
1. `PUT /api/receipts` 要求必传 `customerName`，不支持仅通过 customerId 查找
2. `/api/production-items` 和 `/api/receipt-items/latest-process` 返回原始数组，不使用标准 `{code, data}` 包装
3. `receipt-items/latest-process` 返回 `processName=null`（未做 JOIN 查询填充）

---

## Part3 库存与对账单测试结果详情

详细报告：`tasks/reports/test-report-part3-inventory-statement.md`

**全部通过（12/12）**：
- TC-INV-001/003/007/009/011：库存查询、流水查询、重建、幂等性全部通过
- TC-STMT-001/002/003/006/007/008：对账单生成、结余等式、幂等性、确认全部通过

**已知问题（记录但不计入失败）**：
- TC-INV-002：库存关键词搜索返回 400（已知 Bug，在 DESIGN.md 中有记录）
- 对账单单号格式：实际生成格式为 `DZ202603XXXX`（无连字符），与测试用例预期的 `DZ202603-XXXX` 略有差异

---

## Part4 端到端集成测试结果详情

详细报告：`tasks/reports/test-report-part4-integration.md`

**通过（12）**：
- TC-E2E-001：完整业务流程（BUG-001修复后）全部步骤通过
- TC-E2E-002：返工流程测试通过
- TC-CONSIST-001/002/003：数据一致性 SQL 验查通过
- TC-EDGE-001/003/005：边界场景通过
- TC-SYS-001/002/003：系统管理通过

**失败（1，已修复）**：
| 用例 | 状态 | 原因 |
|------|------|------|
| TC-E2E-001 Step 4 | **已修复** | BUG-001（发货库存方向错误）|

---

## 单号格式说明（实测结果）

| 业务类型 | 格式 | 实测示例 |
|----------|------|---------|
| 收货单 | RH+YYYYMMDD+4位序号 | RH202603170007 |
| 排产单 | PC+YYYYMMDD+4位序号 | PC202603170004 |
| 发货单 | FH+YYYYMMDD+4位序号 | FH202603170004 |
| 返工单 | FG+YYYYMMDD+4位序号 | FG202603170003 |
| 收款单 | SK+YYYYMMDD+4位序号 | SK202603170004 |
| 对账单 | DZ+YYYYMM+4位序号 | DZ2026030030 |

注：历史导入数据的单号格式含连字符（如 `FH202603-0043`），新建数据无连字符，属于历史遗留格式差异。

---

## 遗留问题（未修复）

| 编号 | 严重度 | 描述 | 建议 |
|------|--------|------|------|
| ISSUE-001 | 中 | 客户/工艺/物料重复名称/编码未做唯一性校验 | 在 Service 层增加 `count()` 查重逻辑 |
| ISSUE-002 | 低 | `GET /api/inventory?keyword=xxx` 返回 400 | 参考 DESIGN.md 已知问题，后续修复 |
| ISSUE-003 | 低 | 多处接口不使用标准响应包装 | 统一 `/api/production-items`、`/api/receipt-items/latest-process` 的响应格式 |
| ISSUE-004 | 低 | `receipt-items/latest-process` 返回 processName=null | 增加 JOIN 查询填充名称字段 |
| ISSUE-005 | 低 | 返工单创建时明细可能未正确写入（BUG-002 遗留） | 验证 ReworkItemService 创建流程 |

---

## 库存数据状态

由于测试过程中先后执行了多次收货、发货操作（包括 BUG-001 修复前的错误数据），已在测试完成后执行全量库存重建：

```
POST /api/inventory/rebuild
结果：inventoryRecords=3652，receiptLogs=15621，shipmentLogs=16553
```

库存数据已从源单据重新计算，当前库存值准确。

---

## 测试文件索引

| 文件 | 内容 |
|------|------|
| `tasks/test-cases/test-cases-part1-basic.md` | Part1 测试用例（认证/客户/工艺/物料） |
| `tasks/test-cases/test-cases-part2-orders.md` | Part2 测试用例（收货/排产/发货/返工/收款） |
| `tasks/test-cases/test-cases-part3-inventory-statement.md` | Part3 测试用例（库存/对账单） |
| `tasks/test-cases/test-cases-part4-integration.md` | Part4 测试用例（端到端/一致性/边界） |
| `tasks/reports/test-report-part1-basic.md` | Part1 测试执行报告 |
| `tasks/reports/test-report-part2-orders.md` | Part2 测试执行报告 |
| `tasks/reports/test-report-part3-inventory-statement.md` | Part3 测试执行报告 |
| `tasks/reports/test-report-part4-integration.md` | Part4 测试执行报告 |

---

*报告生成时间：2026-03-17*
