# 代码审查报告：库存持久化改造 + 各单据字段补充

**提交**: `39a8a46` feat: 库存持久化改造+各单据字段补充（千问Stage1）
**审查时间**: 2026-03-08
**审查范围**: Inventory实体/服务/控制器、各ServiceImpl、V2 SQL迁移脚本

---

## 问题列表

### 🔴 HIGH — 并发安全：updateInventory 无悲观/乐观锁，高并发下库存计算错误

**位置**: `InventoryServiceImpl.java:33-84`

**描述**:
`updateInventory` 方法先 `SELECT`（`getOne`）再 `UPDATE`（`updateById`），两步操作之间无任何锁保护。高并发（如多线程同时创建收货单）场景下，两个线程可能同时读到相同的 `beforeQty`，各自在内存中相加后分别写回，导致后写入者覆盖先写入者，**最终库存数量丢失**（"lost update"）。

```java
// 当前代码（有竞争窗口）
Inventory inventory = this.getOne(queryWrapper, false);  // Thread A & B 同时读到 qty=100
...
afterQty = beforeQty.add(changeQty);  // A: 100+10=110, B: 100+20=120（B不知道A已加过）
this.updateById(inventory);           // 最终库存=120，A的+10丢失
```

**修复建议**:
方式一（推荐，悲观锁）：在 Mapper 中用 `SELECT ... FOR UPDATE` 或在 MyBatis-Plus 中通过原生 SQL 锁行：
```java
// InventoryMapper.java 新增
@Select("SELECT * FROM inventory WHERE material_id=#{materialId} AND customer_id=#{customerId} AND (process_id=#{processId} OR (process_id IS NULL AND #{processId} IS NULL)) FOR UPDATE")
Inventory selectForUpdate(...);
```
方式二（乐观锁）：在 `Inventory` 实体添加 `@Version` 字段，配合 MyBatis-Plus 乐观锁插件，并在更新失败时重试。

---

### 🔴 HIGH — SQL 迁移：DROP COLUMN 无 IF EXISTS 保护，重复执行会报错

**位置**: `V2__inventory_and_field_updates.sql:67-73`

**描述**:
```sql
ALTER TABLE customer DROP COLUMN area_name;
ALTER TABLE customer DROP COLUMN industry;
-- ... 共 7 条 DROP COLUMN
```
MySQL 8.0 的 `ALTER TABLE DROP COLUMN` **不支持 `IF EXISTS`** 语法（MySQL 8.0.28 之前版本完全不支持，8.0.28+ 才支持）。如果迁移脚本因任何原因被重复执行、或列已不存在，会导致 `Unknown column` 报错使整个迁移失败。
此外，这些 `ALTER TABLE` 语句无 `IF EXISTS`，与 `CREATE TABLE IF NOT EXISTS` 的防御性风格不一致，存在幂等性隐患。

**修复建议**:
确认 MySQL 版本 ≥ 8.0.28 并使用：
```sql
ALTER TABLE customer DROP COLUMN IF EXISTS area_name;
```
若版本不满足，用 Flyway 的 `beforeMigrate` 回调或存储过程检查列存在性后再 DROP。

---

### 🔴 HIGH — 前端 Customer 表单仍引用已删除字段，前后端数据不一致

**位置**: `frontend/src/views/customer/index.vue:113,173,178,183,188,225-228,275-278`

**描述**:
后端 `Customer.java` 已删除 `areaName`、`financeContact`、`financePhone`、`priceAdjustRate`、`shipWarningDays` 5 个字段，数据库也通过 V2 SQL 删除了对应列；但前端 Vue 组件的表单 `formData` 初始化、`v-model` 绑定、以及编辑时的重置逻辑仍在使用这些字段：
```js
// customer/index.vue:225
areaName: '', financeContact: '', financePhone: '', priceAdjustRate: 0, shipWarningDays: 0
```
影响：用户看到 5 个表单字段（区域名称、财务联系人等）实际无效；数据提交后后端忽略这些字段，但前端不报错，造成静默数据丢失的误解。若后端 DTO 有严格校验则可能报错。

**修复建议**:
删除 `customer/index.vue` 中 `areaName`、`financeContact`、`financePhone`、`priceAdjustRate`、`shipWarningDays` 相关的所有模板绑定与 JS 引用，同步更新表格列定义（若有显示）。

---

### 🟡 MEDIUM — 库存唯一键含 NULL 列，MySQL 唯一索引允许多个 NULL，可能产生重复行

**位置**: `V2__inventory_and_field_updates.sql:19`

```sql
UNIQUE KEY uk_material_customer_process (material_id, customer_id, process_id)
```

**描述**:
MySQL 的唯一索引对 NULL 值的处理是：**多个 NULL 视为不相等**，因此 `process_id IS NULL` 的行可以重复插入，唯一约束不生效。`InventoryServiceImpl` 用 `isNull(Inventory::getProcessId)` 查询后再 `getOne`，在并发场景下两次 `getOne` 均返回 null 后两者都走 `save`，会插入两条 `process_id=NULL` 的库存记录（唯一键无法阻止），后续查询将返回多条，`getOne` 抛出 `TooManyResultsException`。

**修复建议**:
方式一：将 `process_id` 改为用 `0` 表示"无工艺"，唯一键 `(material_id, customer_id, process_id)` 在 `process_id=0` 时正常生效。
方式二：在数据库层改用生成列或函数索引（MySQL 8.0+）。
方式三：配合悲观锁（见 HIGH#1）彻底避免并发插入。

---

### 🟡 MEDIUM — importExcel 导入收货单绕过库存更新，库存数据不一致

**位置**: `ReceiptServiceImpl.java:108-149`（`importExcel` 方法）

**描述**:
`createReceipt` 在保存后会调用 `inventoryService.updateInventory(...)`，但 `importExcel` 直接调用 `save(receipt)` 跳过了 `createReceipt`，**不触发库存更新**。批量导入的收货记录不会影响库存表，导致手工录入与批量导入的库存数据不一致。

```java
// importExcel 中只调用了 save，没有调用 createReceipt 或 updateInventory
save(receipt);  // 库存未更新
```

**修复建议**:
在 `importExcel` 的循环中将 `save(receipt)` 替换为 `createReceipt(receipt)`，或在 `save` 后显式调用 `inventoryService.updateInventory(...)`。注意 `importExcel` 中 `materialId` 和 `customerId` 被硬编码为 `0L`，需同步修正物料/客户解析逻辑。

---

### 🟡 MEDIUM — importExcel 中 materialId/customerId 硬编码为 0L，数据完整性破坏

**位置**: `ReceiptServiceImpl.java:128-129`

```java
receipt.setCustomerId(0L);
receipt.setMaterialId(0L);
```

**描述**:
Excel 导入时无法正确关联物料和客户，`customer_id=0` 和 `material_id=0` 在数据库中没有对应实体，破坏外键语义（即使无硬性外键约束，业务层查询也会出错）。这是明显的功能不完整，不仅影响当前批次，还会污染历史数据。

**修复建议**:
Excel 中应包含可识别的客户编码/物料编码列，导入时根据编码从数据库查找对应 ID；若查找失败，应记录错误行并跳过，而非填充 `0L`。

---

### 🟡 MEDIUM — 发货时未校验库存是否足够，可能产生负库存

**位置**: `InventoryServiceImpl.java:71-84`，`ShipmentServiceImpl.java:70`

**描述**:
发货调用 `updateInventory(..., shipment.getQuantity().negate(), 2, ...)` 时，`InventoryServiceImpl` 直接计算 `afterQty = beforeQty + changeQty`（changeQty 为负），未检查 `afterQty` 是否 `< 0`。业务上库存不足时不应允许发货，但当前代码会将库存减为负数。

```java
afterQty = beforeQty.add(changeQty);  // 无负数校验
inventory.setQuantity(afterQty);      // 可能写入负值
```

此外，`pageList` 用 `gt(Inventory::getQuantity, BigDecimal.ZERO)` 过滤库存，负库存记录会消失在列表中但仍在数据库中，掩盖了问题。

**修复建议**:
在 `updateInventory` 中，当 `changeType == 2`（发货）时检查 `afterQty.compareTo(BigDecimal.ZERO) < 0` 并抛出业务异常（如 `InsufficientStockException`），阻止发货。

---

### 🟡 MEDIUM — 返工（Rework）库存调整方向存在业务歧义，且无日期字段

**位置**: `ReworkServiceImpl.java:59-75`

**描述**:
1. 返工单 `createRework` 对库存做**正向增加**（`rework.getQuantity()`），注释写 "may vary based on business logic"，说明此处逻辑未确定。返工通常是把成品退回重加工，应是先从成品库存中**减出**，完成后再**增回**；当前仅做一次增加，可能与实际业务流程不符。
2. 传入 `orderDate` 使用 `LocalDate.now()`（硬编码当前日期），而非使用 `Rework` 实体中的日期字段（若有），导致日志中日期不准确。

**修复建议**:
与业务方确认返工库存流转方向；若 `Rework` 实体有 `reworkDate` 字段，应使用该字段而非 `LocalDate.now()`。

---

### 🟡 MEDIUM — ALTER TABLE 无事务包裹，部分失败导致数据库状态不一致

**位置**: `V2__inventory_and_field_updates.sql:47-73`

**描述**:
SQL 迁移脚本中 `CREATE TABLE IF NOT EXISTS` 之后的 `ALTER TABLE ADD COLUMN` / `DROP COLUMN` 语句是独立执行的，MySQL DDL 语句会隐式提交（`AUTO COMMIT`），无法回滚。若其中某条 `ALTER TABLE` 失败（如列已存在），后续语句不执行，数据库处于部分迁移状态，Flyway 会将该版本标记为 FAILED，需要手动修复。

**修复建议**:
对所有 `ADD COLUMN` 同样加 `IF NOT EXISTS`（MySQL 8.0.28+）：
```sql
ALTER TABLE receipt ADD COLUMN IF NOT EXISTS receipt_source VARCHAR(100) COMMENT '收货来源';
```

---

### 🟢 LOW — InventoryLog 中 orderId/orderNo 定义为 NOT NULL，但 insertLog 可能传入 null

**位置**: `V2__inventory_and_field_updates.sql:38-39`，`InventoryServiceImpl.java:88`

**描述**:
`inventory_log` 表中 `order_id BIGINT NOT NULL` 和 `order_no VARCHAR(100) NOT NULL`，但 `insertLog` 方法的 `orderId`、`orderNo` 参数无 `@NonNull` 限制，未来若其他调用方传入 null 会在 DB 层抛出异常，错误信息不直观。

**修复建议**:
在 `insertLog` 方法参数上添加 `@NonNull` 注解，或在方法体内做 null 检查并抛出明确业务异常；或将数据库列改为可 NULL 以匹配实际场景。

---

### 🟢 LOW — pageList 日期过滤使用字符串拼接方式，存在潜在类型转换风险

**位置**: `InventoryServiceImpl.java:160-165`

```java
queryWrapper.ge(InventoryLog::getCreateTime, startDate + " 00:00:00");
queryWrapper.le(InventoryLog::getCreateTime, endDate + " 23:59:59");
```

**描述**:
直接将字符串与时间后缀拼接传入 MyBatis-Plus，依赖 MySQL 的隐式字符串转时间。若 `startDate` 格式非 `yyyy-MM-dd`（如 `2026/03/08`），MySQL 转换会得到 NULL 或静默失败，导致过滤条件不生效。

**修复建议**:
在 Service 层将字符串解析为 `LocalDateTime`：
```java
queryWrapper.ge(InventoryLog::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
queryWrapper.le(InventoryLog::getCreateTime, LocalDate.parse(endDate).atTime(23,59,59));
```

---

### 🟢 LOW — InventoryController 中 StringUtils import 未使用

**位置**: `InventoryController.java:9`

```java
import org.springframework.util.StringUtils;
```

**描述**:
`InventoryController` 中没有使用 `StringUtils`，该 import 是无效引用（可能是从旧版本保留的）。

**修复建议**:
删除该 import 语句。

---

### 🟢 LOW — 循环依赖风险分析（无问题，结论记录）

**位置**: `ReceiptServiceImpl`, `ShipmentServiceImpl`, `ReworkServiceImpl` 均注入 `InventoryService`

**结论**:
`InventoryService` 的实现 `InventoryServiceImpl` 只依赖 `InventoryMapper` 和 `InventoryLogMapper`，不依赖 `ReceiptService`、`ShipmentService`、`ReworkService`，依赖方向单向（收发货→库存），**不存在循环依赖**。
Spring 使用 `@RequiredArgsConstructor`（构造器注入），若存在循环依赖会在启动时立即报错，当前结构安全。

---

## 总评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 并发安全 | 40/100 | 核心库存操作无锁，高并发必现丢失 |
| 数据一致性 | 55/100 | importExcel 绕过库存、负库存可能、返工方向不确定 |
| SQL 迁移质量 | 60/100 | DROP COLUMN 无 IF EXISTS，幂等性不足 |
| 前后端同步 | 45/100 | 前端仍有 5 个已删除字段的表单绑定 |
| 代码结构 | 80/100 | 整体架构清晰，Service 分层合理，无循环依赖 |
| 流水完整性 | 75/100 | 正常路径有日志，但 importExcel 路径无日志 |

### 综合评分：**62 / 100**

**主要风险**:
1. 并发库存计算（HIGH）是生产环境的核心风险，需在上线前必须修复。
2. 前端客户表单字段残留（HIGH）会影响用户体验和数据质量。
3. SQL DROP COLUMN 无 IF EXISTS（HIGH）在迁移重跑或版本问题时会导致部署失败。
4. importExcel 绕过库存更新（MEDIUM）是功能性缺陷，批量导入数据会造成库存不一致。

**建议优先级**:
`HIGH#1（并发锁）` > `HIGH#3（前端字段）` > `HIGH#2（SQL幂等）` > `MEDIUM#4（importExcel）` > `MEDIUM#5（负库存校验）` > 其余 MEDIUM/LOW
