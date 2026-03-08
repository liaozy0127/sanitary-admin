# 数据导入设计方案

> 本文档描述 sanitary-admin 系统上线时的历史数据导入方案，基于老系统 Excel 文件格式设计。

---

## 一、导入范围和顺序

上线时必须按以下顺序导入，后面的数据依赖前面的数据已存在：

```
1. 客户档案  →  2. 工艺数据  →  3. 物料档案
          ↓
4. 收货单（历史，mode=history）
5. 排产单（历史，mode=history）
          ↓
6. 初始库存（从对账单的「上月结余」）
```

---

## 二、各文件字段映射

### 2.1 客户档案.xls

| 老系统列 | 新系统字段 | 说明 |
|---------|-----------|------|
| 客户代码 | customer_code | 唯一键，用于去重 |
| 客户名称 | customer_name | 必填，其他表关联用 |
| 客户类型 | customer_type | 月结/现金 |
| 业务员 | salesperson | |
| 联系人 | contact_person | |
| 联系电话 | contact_phone | |
| 地址 | address | |
| 开户银行 | bank_name | |
| 银行帐号 | bank_account | |
| 税号 | tax_no | |
| 停用 | status | True→0(停用) False→1(启用) |
| 备注 | remark | |

**去重策略**：按 `customer_code` 判断是否已存在，存在则跳过（不覆盖）。

---

### 2.2 工艺数据.xls

| 老系统列 | 新系统字段 | 说明 |
|---------|-----------|------|
| 工艺代码 | process_code | 唯一键 |
| 工艺名称 | process_name | 必填 |
| 工艺类别 | process_category | |
| 工艺性质 | process_nature | |
| 备注 | remark | |
| 禁用 | status | True→0(停用) False→1(启用) |

**去重策略**：按 `process_code` 判断是否已存在，存在则跳过。

---

### 2.3 物料档案.xls

| 老系统列 | 新系统字段 | 说明 |
|---------|-----------|------|
| 物料代码 | material_code | 唯一键 |
| 物料名称 | material_name | 必填 |
| 规格型号 | spec | |
| 客户名称 | customer_name + customer_id | 通过名称关联 customer 表 |
| 单价 | default_price | |
| 创建时间 | create_time | Excel 数值需转换 |

**去重策略**：按 `material_code` 判断是否已存在，存在则更新（价格/规格可能变化）。

⚠️ **关联注意**：导入时通过 `customer_name` 查找 `customer_id`，找不到则记录到失败列表，不自动创建客户。

---

### 2.4 收货单.xls

老系统列顺序（共19列）：
`选择 | 收货单号 | 日期 | 客户名称 | 产品名称 | 型号规格 | 工艺名称 | 收货来源 | 收货数量 | 发货数量 | 未发货数量 | 单价 | 客户单号 | 备注 | 明细备注 | 排产数量 | 入库数量 | 未排产数量 | 未入库数量`

| 老系统列 | 列索引(0-based) | 新系统字段 | 说明 |
|---------|----------------|-----------|------|
| 收货单号 | 1 | receipt_no | 保留老单号，用于去重 |
| 日期 | 2 | receipt_date | Excel 数值转日期 |
| 客户名称 | 3 | customer_name / customer_id | 关联查找 |
| 产品名称 | 4 | material_name / material_id | 关联查找 |
| 型号规格 | 5 | spec | |
| 工艺名称 | 6 | process_name / process_id | 关联查找 |
| 收货来源 | 7 | receipt_source | 正常/返工等 |
| 收货数量 | 8 | quantity | |
| 单价 | 11 | unit_price | |
| 客户单号 | 12 | customer_order_no | |
| 备注 | 13 | remark | |
| 明细备注 | 14 | detail_remark | |

⚠️ **收货单号为空的处理**：老系统中同一单号下有多行（合并单元格），导入时需延续上一行的单号。

**去重策略**：按 `receipt_no` 判断是否已存在，存在则跳过（保证幂等，可重复导入）。

⚠️ **库存联动**：历史收货单导入时使用 `mode=history`，**不触发库存更新**，库存从对账单单独初始化。

---

### 2.5 排产单.xls

老系统列顺序（共18列）：
`选择 | 排产单号 | 日期 | 部门名称 | 客户名称 | 产品名称 | 工艺名称 | 收货类型 | 计量单位 | 排产数量 | 入库数量 | 未入库数量 | 委外单价 | 电镀金额 | 电镀单价 | 明细备注 | 客户单号 | 排产方式`

| 老系统列 | 列索引(0-based) | 新系统字段 | 说明 |
|---------|----------------|-----------|------|
| 排产单号 | 1 | production_no | 保留老单号 |
| 日期 | 2 | production_date | Excel 数值转日期 |
| 客户名称 | 4 | customer_name / customer_id | |
| 产品名称 | 5 | material_name / material_id | |
| 工艺名称 | 6 | process_name / process_id | |
| 收货类型 | 7 | receipt_type | 正常/返工 |
| 计量单位 | 8 | unit | |
| 排产数量 | 9 | quantity | |
| 委外单价 | 12 | outsource_price | |
| 电镀单价 | 14 | plating_price | |
| 客户单号 | 16 | customer_order_no | |
| 排产方式 | 17 | production_type | 自制/委外 |

**去重策略**：按 `production_no` 判断是否已存在，存在则跳过。

---

### 2.6 初始库存（对账单）

对账单用于**初始化库存**，前2行是表头，从第3行开始是数据：

| 对账单列 | 列索引(0-based) | 含义 |
|---------|----------------|------|
| 产品代码 | 0 | material_code，关联 material 表 |
| 产品名称 | 1 | material_name（备用匹配） |
| 工艺要求 | 2 | process_name，关联 process 表 |
| 结余数量（上月）| 3 | **初始库存数量** |

⚠️ 对账单没有客户名称列，通过 `material_code` 查找 `customer_id`（物料已关联客户）。

---

## 三、后端实现要求

### 3.1 通用处理规范

```java
// 1. 日期转换（兼容 Excel 数值和字符串两种格式）
private LocalDate parseExcelDate(String value) {
    if (value == null || value.trim().isEmpty()) return null;
    value = value.trim();
    try {
        // 尝试直接解析日期字符串 yyyy-MM-dd
        return LocalDate.parse(value);
    } catch (Exception e1) {
        try {
            // 处理 Excel 数值格式（如 45844.0）
            double d = Double.parseDouble(value);
            return LocalDate.of(1899, 12, 30).plusDays((long) d);
        } catch (Exception e2) {
            throw new RuntimeException("无法解析日期: " + value);
        }
    }
}

// 2. 关联查找（不自动创建，找不到报错）
private Long requireCustomerIdByName(String name) {
    if (name == null || name.trim().isEmpty()) return null;
    Customer c = customerMapper.selectOne(
        new LambdaQueryWrapper<Customer>()
            .eq(Customer::getCustomerName, name.trim()));
    if (c == null) throw new RuntimeException("客户不存在: " + name);
    return c.getId();
}

// 3. 幂等性检查（按业务单号去重）
private boolean receiptExists(String receiptNo) {
    if (receiptNo == null || receiptNo.trim().isEmpty()) return false;
    return receiptMapper.selectCount(
        new LambdaQueryWrapper<Receipt>()
            .eq(Receipt::getReceiptNo, receiptNo.trim())) > 0;
}

// 4. 空值处理
private String getCellString(Row row, int col) {
    Cell cell = row.getCell(col);
    if (cell == null) return "";
    return switch (cell.getCellType()) {
        case STRING -> cell.getStringCellValue().trim();
        case NUMERIC -> DateUtil.isCellDateFormatted(cell)
            ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
            : String.valueOf((long) cell.getNumericCellValue());
        case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
        case FORMULA -> String.valueOf(cell.getNumericCellValue());
        default -> "";
    };
}
```

### 3.2 导入接口规范

每类数据提供两个接口：

```
POST /api/{entity}/import           # 导入（兼容老系统格式）
GET  /api/{entity}/import/template  # 下载导入模板
```

请求参数：
```
?mode=history   # 历史数据导入：不触发库存更新，保留原始单号
?mode=normal    # 正常导入：触发库存更新（默认）
```

返回格式统一：
```json
{
  "success": 100,
  "fail": 2,
  "skip": 5,
  "errors": [
    "第3行: 客户「希镁乐」不存在，请先导入客户档案",
    "第8行: 日期格式错误「abc」"
  ]
}
```

### 3.3 收货单导入改造（现有接口需修改）

现有 importExcel 的问题及修复：

```java
// 问题1：列映射是简化格式，需改为适配老系统列索引
// 问题2：未做幂等性检查，重复导入会产生重复数据
// 问题3：历史导入时不应触发库存更新

@PostMapping("/import")
public Result<Map<String, Object>> importExcel(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "normal") String mode) {
    return Result.success(receiptService.importExcel(file, mode));
}
```

### 3.4 库存初始化接口（新增）

```
POST /api/inventory/init-from-statement
```

逻辑：
1. 解析对账单，读取表头后跳过2行
2. 按 material_code 查找 material，获取 customer_id
3. 按 process_name 查找 process_id
4. 直接 INSERT OR UPDATE inventory 表（不走收货单流程）
5. 写入 inventory_log，change_type=4（初始化），order_no='INIT-YYYYMMDD'
6. 已有库存记录则覆盖（重新初始化场景）

---

## 四、上线操作步骤

```
Step 1: 部署新系统（空库）

Step 2: 导入基础数据（顺序不能颠倒）
  a. 导入客户档案.xls  → 验证：客户列表能看到所有客户
  b. 导入工艺数据.xls  → 验证：工艺管理能看到所有工艺
  c. 导入物料档案.xls  → 可能有「客户不存在」报错（客户名称不匹配），记录并修复

Step 3: 初始化库存
  → POST /api/inventory/init-from-statement 上传对账单.xls
  → 验证：库存查询中各物料数量与对账单「上月结余」一致

Step 4: 导入历史单据（mode=history，不影响库存）
  a. 导入收货单.xls（可选，用于历史查询）
  b. 导入排产单.xls（可选，用于历史查询）

Step 5: 人工核对
  → 随机抽查 5-10 个客户的库存数量，与老系统对账单比对
  → 确认无误后正式切换

Step 6: 正式使用
  → 后续所有新录入的收货/发货/返工单据走正常流程
  → 自动更新库存
```

---

## 五、兼容性处理清单

| 问题场景 | 处理方式 |
|---------|---------|
| 日期是 Excel 数值（45844.0） | `LocalDate.of(1899,12,30).plusDays((long)d)` |
| 收货单号为空（多行同单号） | 延续上一行的收货单号 |
| 金额列是公式 | `cell.getNumericCellValue()` 读取计算结果 |
| 字段前后有空格 | 统一 `.trim()` |
| .xls 和 .xlsx 格式 | `WorkbookFactory.create()` 自动识别 |
| 客户名称不匹配 | 记录失败，不自动创建，操作员手动核对 |
| 物料代码重复 | 按 material_code 去重，更新价格/规格 |
| 收货单号重复（重复导入）| 跳过（skip），不报错，保证幂等 |
| 文件过大（收货单65535行）| 分批处理，每批500行，避免内存溢出 |

---

*文档版本：v1.0 | 2026-03-08*
