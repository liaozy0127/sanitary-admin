# 集成测试报告 — 库存与字段验证（Stage3）

**测试时间**：2026-03-08
**后端地址**：http://localhost:8080
**测试账号**：admin
**测试前修复**：`InventoryMapper.java` 缺少 `import java.math.BigDecimal`，已补充后重新构建。

---

## 测试结果汇总

| 编号 | 测试项 | 结果 |
|------|--------|------|
| 测试1 | 创建收货单（验证库存自动更新） | **PASS** |
| 测试2 | 验证库存已入账 | **PASS** |
| 测试3 | 验证库存流水 | **PASS** |
| 测试4 | 创建发货单（验证库存减少） | **PASS** |
| 测试5 | 验证发货后库存 | **PASS** |
| 测试6 | 测试负库存拦截 | **PASS** |
| 测试7 | 收货单新字段验证 | **PASS** |
| 测试8 | 排产单新字段验证 | **PASS** |
| 测试9 | 客户接口无已删字段 | **PASS** |

**总计：9/9 PASS，0 FAIL**

---

## 详细测试记录

### 测试1：创建收货单（验证库存自动更新）
- **请求**：POST /api/receipts，quantity=100，receiptSource="正常"
- **响应摘要**：
  ```json
  {"code":200,"msg":"success","data":{"id":3,"receiptNo":"RH202603080003","status":1,"receiptSource":"正常","quantity":100,...}}
  ```
- **PASS 条件**：返回成功（code=200），status=1，receiptSource 字段存在 ✓

---

### 测试2：验证库存已入账
- **请求**：GET /api/inventory?page=1&size=10
- **响应摘要**：
  ```json
  {"records":[{"materialId":23164,"materialName":"接头","customerName":"轩沣卫浴","quantity":170.00,...}]}
  ```
- **说明**：测试前已有70件库存（前两轮测试数据残留），收货100件后变为170，增量正确。
- **PASS 条件**：能查到该物料库存，且较前次增加100 ✓

---

### 测试3：验证库存流水
- **请求**：GET /api/inventory/log?page=1&size=10
- **响应摘要**：
  ```json
  {"records":[
    {"changeType":1,"changeQty":100.00,"beforeQty":70.00,"afterQty":170.00,"orderType":"RECEIPT","orderNo":"RH202603080003",...},
    ...
  ]}
  ```
- **PASS 条件**：有 changeType=1（收货）、changeQty=100 的流水记录 ✓

---

### 测试4：创建发货单（验证库存减少）
- **请求**：POST /api/shipments，quantity=30，shipmentType="良品"
- **响应摘要**：
  ```json
  {"code":200,"msg":"success","data":{"id":4,"shipmentNo":"FH202603080003","shipmentType":"良品","quantity":30,...}}
  ```
- **PASS 条件**：发货成功（code=200），shipmentType 字段正常 ✓

---

### 测试5：验证发货后库存
- **请求**：GET /api/inventory?page=1&size=10
- **响应摘要**：
  ```json
  {"records":[{"materialId":23164,"materialName":"接头","quantity":140.00,...}]}
  ```
- **说明**：170 - 30 = 140，库存正确减少。
- **PASS 条件**：库存减少30，结果 quantity=140 ✓

---

### 测试6：测试负库存拦截
- **请求**：POST /api/shipments，quantity=200（超过当前库存140）
- **响应摘要**：
  ```json
  {"code":500,"msg":"服务器内部错误：库存不足，当前库存：140.00，发货数量：200","data":null}
  ```
- **PASS 条件**：返回错误，提示库存不足 ✓

---

### 测试7：收货单新字段验证
- **请求**：GET /api/receipts/3
- **响应摘要**：
  ```json
  {"data":{"receiptSource":"正常","customerOrderNo":null,"detailRemark":null,...}}
  ```
- **PASS 条件**：返回数据包含 receiptSource、customerOrderNo、detailRemark 字段 ✓

---

### 测试8：排产单新字段验证
- **请求**：POST /api/productions，unit="个"，receiptType="正常"，productionType="自制"
- **响应摘要**：
  ```json
  {"code":200,"msg":"success","data":{"id":3,"unit":"个","receiptType":"正常","productionType":"自制","plannedQty":50,...}}
  ```
- **PASS 条件**：创建成功，unit、receiptType、productionType 字段正常保存 ✓

---

### 测试9：客户接口无已删字段
- **请求**：GET /api/customers?page=1&size=1（customerId=480）
- **响应摘要**：
  ```json
  {"data":{"records":[{"id":480,"customerCode":"轩沣卫浴","customerName":"轩沣卫浴","customerType":"现金","address":"","contactPerson":"","contactPhone":"","salesperson":"","bankName":"","bankAccount":"","taxNo":"","remark":"","status":1,...}]}}
  ```
- **注**：GET /api/customers/{id} 接口返回405（不支持该HTTP方法），改用列表接口验证。
- **PASS 条件**：返回数据中不含 areaName、industry、email 字段 ✓

---

## 问题记录

### 修复项1：编译错误 — InventoryMapper.java 缺少 BigDecimal import
- **文件**：`backend/src/main/java/com/sanitary/admin/mapper/InventoryMapper.java`
- **原因**：`incrementQuantity` 方法使用了 `BigDecimal` 类型但未 import
- **修复**：添加 `import java.math.BigDecimal;`

### 注意项：GET /api/customers/{id} 不支持
- `/api/customers/{id}` 返回 405（Method Not Allowed），该路由仅支持 PUT/DELETE，不支持 GET 单记录查询。
- 测试9 改用列表接口验证，功能本身无问题。

---

*报告生成时间：2026-03-08*
