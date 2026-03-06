# 角色管理接口测试报告

**测试时间：** 2026-03-06 16:07 ~ 16:09 (Asia/Shanghai)  
**测试环境：** http://localhost:8080  
**测试账号：** admin / admin123  
**备注：** 原运行后端为 feat-dept 分支（不含角色模块），已重新编译主项目并启动后完成测试。

---

## 前置：登录获取 Token

```
POST /api/auth/login
Body: {"username":"admin","password":"admin123"}
响应: {"code":200,"msg":"success","data":{"token":"eyJhbGci...","username":"admin","role":"ADMIN"}}
```

✅ 登录成功，Token 已获取。

---

## 测试结果汇总

| # | 接口 | 请求摘要 | 实际返回 | 结果 |
|---|------|----------|----------|------|
| 1 | GET /api/roles | 查询角色列表 | `{"code":200,"msg":"success","data":[{"id":1,"roleName":"超级管理员","roleCode":"ADMIN",...},{"id":2,"roleName":"普通用户","roleCode":"USER",...}]}` HTTP 200 | ✅ PASS |
| 2 | POST /api/roles | 新增角色 `{"roleName":"测试角色","roleCode":"TEST","status":1}` | `{"code":200,"msg":"success","data":null}` HTTP 200 | ✅ PASS |
| 3 | GET /api/roles/3 | 查单条（新建 id=3） | `{"code":200,"msg":"success","data":{"id":3,"roleName":"测试角色","roleCode":"TEST","status":1,...}}` HTTP 200 | ✅ PASS |
| 4 | PUT /api/roles/3 | 修改 roleName 为"测试角色2" `{"roleName":"测试角色2","roleCode":"TEST","status":1}` | `{"code":200,"msg":"success","data":null}` HTTP 200；验证查询返回 `"roleName":"测试角色2"` ✓ | ✅ PASS |
| 5 | DELETE /api/roles/3 | 删除 id=3 | `{"code":200,"msg":"success","data":null}` HTTP 200；删除后查询返回 `{"code":404,"msg":"角色不存在"}` ✓ | ✅ PASS |
| 6 | GET /api/roles/99999 | 查询不存在的 id | `{"code":404,"msg":"角色不存在","data":null}` HTTP 200（业务层 404） | ✅ PASS |

---

## 说明

- **接口 6**：HTTP 状态码返回 200，但响应体 `code=404`、`msg="角色不存在"`，属于业务级 404 错误（与任务期望一致，返回了 404 错误信息）。若需 HTTP 层也返回 404，可在 Controller 中使用 `@ResponseStatus(HttpStatus.NOT_FOUND)` 或 `ResponseEntity`。

---

## 统计：6/6 PASS

所有角色管理接口均测试通过。
