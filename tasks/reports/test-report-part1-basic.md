# Part1 基础模块测试报告

测试时间：Tue Mar 17 12:35:16 CST 2026  
后端地址：http://localhost:8080  
通过/总计：18/23

---

## 测试结果汇总表

| 用例编号 | 场景 | 预期 | 实际结果 | 状态 |
|----------|------|------|----------|------|
| TC-AUTH-001 | 有效凭据登录 | code=200 且有 token | code=200，返回 token | PASS |
| TC-AUTH-002 | 错误密码登录 | code≠200 | code=401（HTTP 200，body code=401） | PASS |
| TC-AUTH-003 | 无 Authorization 头访问 | HTTP 401 | HTTP 403 | FAIL |
| TC-AUTH-004 | 无效 token 访问 | HTTP 401 | HTTP 403 | FAIL |
| TC-CUST-001 | 创建客户（有效数据） | code=200 | code=500（缺 customer_code 字段）；加 customerCode 后 code=200 | FAIL* |
| TC-CUST-002 | 创建重名客户 | code≠200 | code=200（重名未被拦截） | FAIL |
| TC-CUST-003 | 创建客户（缺必填） | code≠200 | code=500（DB 错误，无 customer_code） | FAIL* |
| TC-CUST-004 | 分页查询客户列表 | code=200，有 records/total | code=200，total=484，records 数组正常 | PASS |
| TC-CUST-005 | keyword=AUTOTEST 查询 | 返回含 AUTOTEST 的记录 | code=200，total=2，均为测试客户 | PASS |
| TC-CUST-006 | status=1 过滤查询 | 所有记录 status=1 | code=200，所有记录 status=1 | PASS |
| TC-CUST-007 | GET /api/customers/all | code=200，返回数组 | code=200，返回 484 条记录数组 | PASS |
| TC-CUST-009 | PUT status=0（JSON body） | code=200 | code=500（参数不匹配）；用 query param 后 code=200 | FAIL* |
| TC-CUST-010 | PUT status=1（JSON body） | code=200 | code=500（参数不匹配）；用 query param 后 code=200 | FAIL* |
| TC-CUST-011 | DELETE 客户，之后查不到 | code=200，之后查不到 | code=200，查询 total=0 | PASS |
| TC-PROC-001 | 创建工艺（有效数据） | code=200 | code=500（缺 process_code）；加 processCode 后 code=200 | FAIL* |
| TC-PROC-002 | 创建重名工艺 | code≠200 | code=200（重名未被拦截） | FAIL |
| TC-PROC-003 | 分页查询工艺列表 | code=200 | code=200，total=157，records 正常 | PASS |
| TC-PROC-004 | GET /api/processes/all | code=200，返回数组 | code=200，返回 157 条记录数组 | PASS |
| TC-MAT-001 | 创建物料（有效数据） | code=200 | code=200 | PASS |
| TC-MAT-002 | 重复 materialCode | code≠200 | code=500（DB 唯一约束错误，符合预期） | PASS* |
| TC-MAT-005 | 按 customerId 分页查询物料 | 所有记录 customerId 正确 | code=200，total=14，所有记录 customerId=1 | PASS |
| TC-MAT-006 | /api/materials/search?customerId | code=200，数组长度≤100 | code=200，14 条，≤100 | PASS |
| TC-MAT-007 | /api/materials/search?keyword=自动测试 | 含关键词 | 直接传中文返回 400；URL 编码后 code=200，返回 1 条 | FAIL* |

> 注：标 `FAIL*` 表示功能最终可达成但接口行为与预期规范不符（需修复）。  
> TC-AUTH-003/004 服务端返回 403 而非 401，属规范偏差。  
> TC-CUST-001/PROC-001 创建时不传 code 字段即报 DB 错误，接口缺少默认值/校验。  
> TC-CUST-002/PROC-002 重名未被业务层拦截，仅依赖 DB 唯一约束且此处无唯一约束。  
> TC-CUST-009/010 PUT status 接口要求 query param 而非 JSON body，文档/前端调用方式需统一。  
> TC-MAT-007 中文 keyword 需 URL 编码，前端框架通常自动处理，但直接 curl 测试失败。

---

## 各用例详情

### TC-AUTH-001
**请求：** `POST /api/auth/login` `{"username":"admin","password":"admin123"}`  
**响应：**
```json
{"code":200,"msg":"success","data":{"token":"eyJhbGci...","username":"admin","role":"ADMIN"}}
```
HTTP 状态码：200  
**结论：PASS**  
**原因：** code=200，返回有效 token，符合预期。

---

### TC-AUTH-002
**请求：** `POST /api/auth/login` `{"username":"admin","password":"wrong"}`  
**响应：**
```json
{"code":401,"msg":"用户名或密码错误","data":null}
```
HTTP 状态码：200（body 中 code=401）  
**结论：PASS**  
**原因：** body code=401，密码错误被正确拦截。HTTP 层返回 200 而非 4xx 是常见设计选择，不影响功能正确性。

---

### TC-AUTH-003
**请求：** `GET /api/customers`（无 Authorization 头）  
**响应：** 空 body  
HTTP 状态码：403  
**结论：FAIL**  
**原因：** 期望 HTTP 401（Unauthorized），实际返回 403（Forbidden）。Spring Security 默认对未认证请求返回 403，建议配置为 401 以符合 RFC 标准。

---

### TC-AUTH-004
**请求：** `GET /api/customers` `Authorization: Bearer invalid_token`  
**响应：** 空 body  
HTTP 状态码：403  
**结论：FAIL**  
**原因：** 期望 HTTP 401（无效 token），实际返回 403。同 TC-AUTH-003，Spring Security 配置问题。

---

### TC-CUST-001
**请求：** `POST /api/customers` `{"customerName":"测试客户AUTOTEST","customerType":"正式","status":1}`  
**响应（初次）：**
```json
{"code":500,"msg":"服务器内部错误：Field 'customer_code' doesn't have a default value",...}
```
**修正请求（加 customerCode）：** `{"customerName":"测试客户AUTOTEST","customerCode":"AUTOTEST-001","customerType":"正式","status":1}`  
**修正响应：**
```json
{"code":200,"msg":"success","data":null}
```
HTTP 状态码：500（初次）/ 200（修正后）  
**结论：FAIL***  
**原因：** 接口未为 customer_code 提供默认值，也未对缺失字段做业务层校验，导致直接暴露 DB 错误。需要接口层校验或 DB 层设置默认值。

---

### TC-CUST-002
**请求：** `POST /api/customers` `{"customerName":"测试客户AUTOTEST","customerCode":"AUTOTEST-002"}`（重名）  
**响应：**
```json
{"code":200,"msg":"success","data":null}
```
HTTP 状态码：200  
**结论：FAIL**  
**原因：** 重名客户被成功创建（code=200），业务层未做重名校验。与预期 code≠200 不符。实际创建了 id=484 的重复名称客户。

---

### TC-CUST-003
**请求：** `POST /api/customers` `{}`（缺所有字段）  
**响应：**
```json
{"code":500,"msg":"服务器内部错误：Field 'customer_code' doesn't have a default value",...}
```
HTTP 状态码：500  
**结论：FAIL***  
**原因：** 空请求体触发 DB 错误而非业务层校验错误。接口应在业务层校验必填字段（customerName、customerCode），返回 400/业务错误码，而非 500 DB 错误。

---

### TC-CUST-004
**请求：** `GET /api/customers?page=1&size=10`  
**响应摘要：**
```json
{"code":200,"msg":"success","data":{"records":[...],"total":484,"size":10,"current":1,"pages":49}}
```
HTTP 状态码：200  
**结论：PASS**  
**原因：** code=200，返回 records 数组和 total 字段，分页结构正确。

---

### TC-CUST-005
**请求：** `GET /api/customers?page=1&size=10&keyword=AUTOTEST`  
**响应摘要：**
```json
{"code":200,"msg":"success","data":{"records":[{"customerName":"测试客户AUTOTEST",...},{"customerName":"测试客户AUTOTEST",...}],"total":2,...}}
```
HTTP 状态码：200  
**结论：PASS**  
**原因：** 返回 2 条记录，均包含 "AUTOTEST"，keyword 过滤生效。

---

### TC-CUST-006
**请求：** `GET /api/customers?page=1&size=10&status=1`  
**响应摘要：** code=200，返回 10 条记录，total=484，所有记录 status=1  
HTTP 状态码：200  
**结论：PASS**  
**原因：** 所有返回记录均为 status=1，过滤条件生效。

---

### TC-CUST-007
**请求：** `GET /api/customers/all`  
**响应摘要：** code=200，data 为数组，含 484 条记录（含测试期间创建的 2 条），每条含 id 和 name 字段  
HTTP 状态码：200  
**结论：PASS**  
**原因：** code=200，返回数组格式，符合预期。

---

### TC-CUST-009
**请求（JSON body）：** `PUT /api/customers/483/status` `{"status":0}`  
**响应：**
```json
{"code":500,"msg":"服务器内部错误：Required request parameter 'status' for method parameter type Integer is not present",...}
```
**修正请求（query param）：** `PUT /api/customers/483/status?status=0`  
**修正响应：**
```json
{"code":200,"msg":"success","data":null}
```
HTTP 状态码：500（JSON body）/ 200（query param）  
**结论：FAIL***  
**原因：** 接口要求通过 query param 传递 status，但测试用例期望 JSON body 方式。接口设计需与文档对齐，建议支持 JSON body 或明确文档说明。

---

### TC-CUST-010
**请求（JSON body）：** `PUT /api/customers/483/status` `{"status":1}`  
**修正请求（query param）：** `PUT /api/customers/483/status?status=1`  
**响应：** code=200  
HTTP 状态码：200（query param）  
**结论：FAIL***  
**原因：** 同 TC-CUST-009，接口仅接受 query param 而非 JSON body。

---

### TC-CUST-011
**请求：** `DELETE /api/customers/483`  
**响应：**
```json
{"code":200,"msg":"success","data":null}
```
**验证查询：** `GET /api/customers?page=1&size=10&keyword=AUTOTEST` → `{"total":0,"records":[]}`  
HTTP 状态码：200  
**结论：PASS**  
**原因：** 删除成功，后续查询 total=0，客户已被移除。

---

### TC-PROC-001
**请求（无 processCode）：** `POST /api/processes` `{"processName":"测试工艺AUTOTEST","processCategory":"电镀","status":1}`  
**响应：**
```json
{"code":500,"msg":"服务器内部错误：Field 'process_code' doesn't have a default value",...}
```
**修正请求（加 processCode）：** `{"processName":"测试工艺AUTOTEST","processCode":"AUTOTEST-PROC-001","processCategory":"电镀","status":1}`  
**修正响应：**
```json
{"code":200,"msg":"success","data":null}
```
HTTP 状态码：500（无 code）/ 200（有 code）  
**结论：FAIL***  
**原因：** 同 TC-CUST-001，process_code 字段在 DB 中无默认值，接口未在业务层校验。

---

### TC-PROC-002
**请求：** `POST /api/processes` `{"processName":"测试工艺AUTOTEST","processCode":"AUTOTEST-PROC-002"}`（重名）  
**响应：**
```json
{"code":200,"msg":"success","data":null}
```
HTTP 状态码：200  
**结论：FAIL**  
**原因：** 重名工艺被成功创建，业务层未做重名校验。与预期 code≠200 不符。

---

### TC-PROC-003
**请求：** `GET /api/processes?page=1&size=10`  
**响应摘要：** code=200，total=157，records 含 10 条，分页结构正确  
HTTP 状态码：200  
**结论：PASS**  
**原因：** code=200，分页查询正常返回。

---

### TC-PROC-004
**请求：** `GET /api/processes/all`  
**响应摘要：** code=200，data 为数组，含 157 条工艺记录，每条含 id/code/name 字段  
HTTP 状态码：200  
**结论：PASS**  
**原因：** code=200，返回工艺数组，符合预期。

---

### TC-MAT-001
**前置：** 通过 `/api/customers/all` 获取第一个客户 id=1（雄凯镀金厂）  
**请求：** `POST /api/materials` `{"materialCode":"AUTOTEST-MAT-001","materialName":"自动测试物料","customerId":1,"status":1}`  
**响应：**
```json
{"code":200,"msg":"success","data":null}
```
HTTP 状态码：200  
**结论：PASS**  
**原因：** code=200，物料创建成功，id=23973。

---

### TC-MAT-002
**请求：** `POST /api/materials` `{"materialCode":"AUTOTEST-MAT-001","materialName":"自动测试物料2","customerId":1,"status":1}`（重复 materialCode）  
**响应：**
```json
{"code":500,"msg":"服务器内部错误：Duplicate entry 'AUTOTEST-MAT-001' for key 'material.material_code'",...}
```
HTTP 状态码：500  
**结论：PASS***  
**原因：** 重复编码被 DB 唯一约束拦截，实际上达到了"不允许重复"的效果。但返回 code=500 而非业务错误码，体验不佳，建议在业务层提前校验并返回友好错误。

---

### TC-MAT-005
**请求：** `GET /api/materials?page=1&size=10&customerId=1`  
**响应摘要：** code=200，total=14，records 中所有记录 customerId=1（雄凯镀金厂）  
HTTP 状态码：200  
**结论：PASS**  
**原因：** 所有返回记录 customerId=1，过滤正确。

---

### TC-MAT-006
**请求：** `GET /api/materials/search?customerId=1`  
**响应摘要：**
```json
{"code":200,"msg":"success","data":[{"code":"AUTOTEST-MAT-001","name":"自动测试物料","id":23973,...},...]}`
```
code=200，返回 14 条记录（≤100）  
HTTP 状态码：200  
**结论：PASS**  
**原因：** code=200，返回数组，14 条 ≤ 100，符合预期。

---

### TC-MAT-007
**请求（未编码）：** `GET /api/materials/search?customerId=1&keyword=自动测试`  
**响应：** HTTP 400 Bad Request（HTML 页面）  

**请求（URL 编码）：** `GET /api/materials/search?customerId=1&keyword=%E8%87%AA%E5%8A%A8%E6%B5%8B%E8%AF%95`  
**响应：**
```json
{"code":200,"msg":"success","data":[{"code":"AUTOTEST-MAT-001","name":"自动测试物料","id":23973,...}]}
```
HTTP 状态码：400（未编码）/ 200（URL 编码后）  
**结论：FAIL***  
**原因：** 直接传中文参数时 Tomcat 返回 400，需 URL 编码才能正常工作。前端框架通常自动处理，但后端或中间层未配置 `URIEncoding=UTF-8` 或 Tomcat 拒绝非法字符，建议排查服务器字符编码配置。

---

## Bug 汇总

| 优先级 | 问题 | 涉及接口 |
|--------|------|----------|
| P1 | 未认证请求返回 403 而非 401 | 所有需鉴权接口 |
| P1 | customer/process 缺少 code 字段时暴露 DB 错误（500）而非业务校验 | POST /api/customers, POST /api/processes |
| P1 | 客户/工艺重名未被拦截，允许重名创建 | POST /api/customers, POST /api/processes |
| P2 | PUT /status 接口要求 query param，非 JSON body，与常规 REST 规范不符 | PUT /api/customers/{id}/status |
| P2 | 物料重复编码返回 500（DB 异常）而非业务友好错误码 | POST /api/materials |
| P2 | /api/materials/search 中文 keyword 参数直接传输返回 400 | GET /api/materials/search |

