# sanitary-admin 基础模块测试用例

## 测试环境
- 后端地址：http://localhost:8080
- 登录接口：POST /api/auth/login
- 认证方式：Authorization: Bearer {token}
- 响应格式：{"code":200,"msg":"success","data":{}}

---

## 一、认证模块测试（TC-AUTH）

### TC-AUTH-001：登录成功
前置条件：系统已启动，admin账号存在
请求：POST /api/auth/login，Body: {"username":"admin","password":"admin123"}
预期：code=200，data.token 非空字符串

### TC-AUTH-002：登录失败-密码错误
请求：POST /api/auth/login，Body: {"username":"admin","password":"wrong"}
预期：code≠200，token 不返回

### TC-AUTH-003：未携带 token 访问业务接口
请求：GET /api/customers（无 Authorization 头）
预期：HTTP 401

### TC-AUTH-004：携带无效 token
请求：GET /api/customers，Authorization: Bearer invalid_token
预期：HTTP 401

---

## 二、客户管理测试（TC-CUST）

### TC-CUST-001：新增客户-正常
前置：已登录获取 token
请求：POST /api/customers
Body: {"customerName":"测试客户A","customerType":"正式","contactPerson":"张三","contactPhone":"13800138000","address":"广东省深圳市","salesperson":"李四","status":1}
预期：code=200，data.id 非空，data.customerCode 自动生成

### TC-CUST-002：新增客户-名称重复
前置：TC-CUST-001已执行，"测试客户A"已存在
请求：POST /api/customers，Body: {"customerName":"测试客户A"}
预期：code≠200，提示名称重复

### TC-CUST-003：新增客户-必填字段缺失
请求：POST /api/customers，Body: {}（customerName 为空）
预期：code≠200，提示必填字段

### TC-CUST-004：分页查询客户列表
请求：GET /api/customers?page=1&size=10
预期：code=200，data.records 数组，data.total≥0

### TC-CUST-005：按关键词筛选
请求：GET /api/customers?page=1&size=10&keyword=测试
预期：code=200，所有返回记录 customerName 包含"测试"

### TC-CUST-006：按状态筛选
请求：GET /api/customers?page=1&size=10&status=1
预期：所有返回记录 status=1

### TC-CUST-007：获取全部客户（下拉框用）
请求：GET /api/customers/all
预期：code=200，data 为数组，所有记录 status=1

### TC-CUST-008：修改客户信息
前置：已有客户 id=1
请求：PUT /api/customers/1，Body: {"customerName":"测试客户A修改","contactPhone":"13900139000"}
预期：code=200

### TC-CUST-009：禁用客户
请求：PUT /api/customers/1/status，Body: {"status":0}
预期：code=200；GET /api/customers/all 中不再包含该客户

### TC-CUST-010：启用客户
请求：PUT /api/customers/1/status，Body: {"status":1}
预期：code=200；GET /api/customers/all 中可查到该客户

### TC-CUST-011：删除客户（逻辑删除）
请求：DELETE /api/customers/1
预期：code=200；GET /api/customers?page=1&size=10 列表中不再出现该客户（deleted=1）

---

## 三、工艺管理测试（TC-PROC）

### TC-PROC-001：新增工艺-正常
请求：POST /api/processes
Body: {"processName":"镀铬","processCategory":"电镀","processNature":"表面处理","status":1}
预期：code=200，data.id 非空，processCode 自动生成

### TC-PROC-002：新增工艺-名称重复
前置："镀铬"已存在
请求：POST /api/processes，Body: {"processName":"镀铬"}
预期：code≠200，提示名称重复

### TC-PROC-003：分页查询工艺列表
请求：GET /api/processes?page=1&size=10
预期：code=200，data.records 数组

### TC-PROC-004：获取全部工艺（下拉框）
请求：GET /api/processes/all
预期：code=200，data 为数组

### TC-PROC-005：修改工艺
请求：PUT /api/processes/{id}，Body: {"processName":"镀铬-更新","thicknessReq":"8-12μm"}
预期：code=200

### TC-PROC-006：禁用/启用工艺
请求：PUT /api/processes/{id}/status，Body: {"status":0}
预期：code=200

### TC-PROC-007：删除工艺
请求：DELETE /api/processes/{id}
预期：code=200；列表查询不到该工艺

---

## 四、物料管理测试（TC-MAT）

### TC-MAT-001：新增物料-正常
前置：客户 id=1 存在
请求：POST /api/materials
Body: {"materialCode":"MAT-001","materialName":"测试物料","spec":"M8x20","customerId":1,"defaultPrice":5.50,"unit":"个","status":1}
预期：code=200，data.id 非空

### TC-MAT-002：新增物料-编码重复
前置：MAT-001 已存在
请求：POST /api/materials，Body: {"materialCode":"MAT-001","materialName":"另一物料","customerId":1}
预期：code≠200，提示编码重复

### TC-MAT-003：新增物料-必须关联客户
请求：POST /api/materials，Body: {"materialCode":"MAT-002","materialName":"无客户物料"}（customerId 为空）
预期：code≠200，提示 customerId 必填

### TC-MAT-004：不同客户同名物料各自独立
前置：客户 id=1 和 id=2 均存在
步骤1：POST /api/materials，Body: {"materialCode":"MAT-003","materialName":"铜件A","customerId":1}
步骤2：POST /api/materials，Body: {"materialCode":"MAT-004","materialName":"铜件A","customerId":2}
预期：两次均返回 code=200，两条记录各自独立

### TC-MAT-005：按客户ID过滤查询
请求：GET /api/materials?page=1&size=10&customerId=1
预期：所有返回记录 customerId=1

### TC-MAT-006：搜索接口-无关键词返回前100条
请求：GET /api/materials/search?customerId=1
预期：code=200，data 长度 ≤ 100，所有记录 customerId=1，status=1

### TC-MAT-007：搜索接口-有关键词模糊匹配
请求：GET /api/materials/search?customerId=1&keyword=铜件
预期：code=200，所有返回记录名称或编码包含"铜件"，长度 ≤ 100

### TC-MAT-008：禁用物料后搜索接口不返回
步骤1：PUT /api/materials/{id}/status，Body: {"status":0}（禁用物料）
步骤2：GET /api/materials/search?customerId=1&keyword=该物料名
预期：步骤2结果中不包含该物料（search 接口只返回 status=1 的物料）

### TC-MAT-009：删除物料（逻辑删除）
请求：DELETE /api/materials/{id}
预期：code=200；GET /api/materials 列表不再包含

---

## 五、数据初始化顺序验证（TC-INIT）

### TC-INIT-001：客户导入后工艺/物料才能关联
前置条件验证：
1. 先导入客户：POST /api/customers/import（Excel）
2. 再导入工艺：POST /api/processes/import（Excel）
3. 再导入物料：POST /api/materials/import（Excel）
预期：物料导入时可通过客户名查到 customerId，导入结果 fail=0（无客户未找到的错误）

### TC-INIT-002：Excel 导入幂等性验证
步骤：对相同文件执行两次导入

注意：不同模块幂等行为不同：
- **客户/工艺**：按名称唯一，已存在则跳过，第二次 skip > 0
- **物料**：按 material_code 去重，已存在则**更新**，第二次 success > 0（更新不是 skip）
- **收货单/排产单**：按单号（receipt_no/production_no）去重，已存在则 skip，第二次 skip > 0

预期：第二次导入不报错，不重复创建数据，返回合理的统计数字（success/skip/fail）
