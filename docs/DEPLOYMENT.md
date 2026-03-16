# 卫浴管理系统 — 部署与数据初始化指南

> 本文档适用于在新机器上完整部署系统，包括环境准备、构建启动、数据导入全流程。

---

## 一、前置要求

### 1.1 必须安装的软件

| 软件 | 最低版本 | 说明 |
|------|---------|------|
| Docker Desktop | 4.x | 包含 docker compose |
| Git | 任意 | 克隆代码 |
| Python 3 | 3.8+ | 运行数据导入脚本 |

Python 依赖库：
```bash
pip3 install xlrd requests openpyxl
```

> **注意**：不需要在本机安装 Java、Maven、Node.js，全部在 Docker 容器内完成编译。

### 1.2 端口占用检查

系统默认占用以下端口，确保未被其他程序使用：

| 端口 | 用途 |
|------|------|
| 80 | 前端页面 |
| 8080 | 后端 API |
| 3307 | MySQL（宿主机访问用） |

如需修改端口，编辑根目录 `.env` 文件。

---

## 二、获取代码

```bash
git clone <仓库地址> sanitary-admin
cd sanitary-admin
```

目录结构说明：

```
sanitary-admin/
├── backend/          # Spring Boot 后端
├── frontend/         # Vue 3 前端
├── old-system-file/  # 老系统 Excel 数据（需手动放入）
├── scripts/          # 数据导入脚本
├── docs/             # 文档
├── docker-compose.yml
└── .env              # 端口等环境变量
```

---

## 三、放入历史数据文件

将以下 Excel 文件放入 `old-system-file/` 目录（文件名必须完全一致）：

```
old-system-file/
├── 客户档案.xls
├── 工艺数据.xls
├── 物料档案.xls
├── 收货单.xls
├── 排产单.xls
├── 发货单.xls
├── 收款单.xls
└── price-init/           ← 单价初始化专用目录（见下方说明）
    ├── README.txt
    └── *.xls             ← 手动放入历史收货单文件
```

> **`price-init/` 目录说明**：该目录存放用于初始化物料默认单价的历史收货单文件。
> 这些文件**仅供单价初始化脚本读取**，**严禁**通过系统的「收货单导入」功能导入，否则会产生大量重复历史数据。
> 详见 [5.3 步骤](#53-第三步物料单价初始化)。

> **文件格式说明**：支持 `.xls` 格式，文件头可能有损坏警告（`WARNING *** file size not 512 + multiple of sector size`），属正常现象，不影响导入。

---

## 四、构建并启动系统

### 4.1 首次启动（全量构建）

```bash
cd sanitary-admin

# macOS Docker 路径（如不在 PATH 中则使用完整路径）
docker compose build --no-cache
docker compose up -d
```

> macOS 上 Docker 命令完整路径：`/Applications/Docker.app/Contents/Resources/bin/docker`

构建过程说明：
- **backend**：在容器内执行 `mvn package`，首次下载依赖较慢
- **frontend**：在容器内执行 `npm install && npm run build`

### 4.2 等待服务就绪

```bash
# 查看所有容器状态
docker compose ps

# 等待 backend 健康检查通过（显示 healthy）
docker compose ps backend
```

或者用命令轮询：

```bash
until curl -sf http://localhost:8080/actuator/health > /dev/null; do
  echo "等待后端启动..."; sleep 5
done
echo "后端已就绪"
```

### 4.3 验证启动成功

| 检查项 | 方法 |
|--------|------|
| 前端页面 | 浏览器打开 http://localhost |
| 后端健康 | `curl http://localhost:8080/actuator/health` 返回 `{"status":"UP"}` |
| 登录测试 | 账号 `admin` / 密码 `admin123` |

---

## 五、数据初始化导入

数据导入严格按以下顺序执行，**顺序不可颠倒**。

```
客户/工艺/物料 → 物料单价初始化 → 收货单 → 排产单 → 发货单 → 收款单
     → 期初库存补录 → 重建库存 → 批量生成对账单
```

### 5.1 第一步：转换 Excel 格式

老系统文件为 `.xls` 格式且文件头有损坏，需先转为 `.xlsx`：

```bash
cd sanitary-admin

python3 << 'EOF'
import xlrd, openpyxl, os, warnings
warnings.filterwarnings('ignore')

def xls_to_xlsx(src, dst):
    print(f'转换 {src}...')
    wb = xlrd.open_workbook(src)
    ws = wb.sheets()[0]
    wb2 = openpyxl.Workbook()
    ws2 = wb2.active
    for row in range(ws.nrows):
        ws2.append(ws.row_values(row))
    wb2.save(dst)
    print(f'  完成，共 {ws.nrows} 行')

os.makedirs('/tmp/import-xlsx', exist_ok=True)
xls_to_xlsx('old-system-file/收货单.xls',  '/tmp/import-xlsx/收货单.xlsx')
xls_to_xlsx('old-system-file/排产单.xls',  '/tmp/import-xlsx/排产单.xlsx')
xls_to_xlsx('old-system-file/发货单.xls',  '/tmp/import-xlsx/发货单.xlsx')
xls_to_xlsx('old-system-file/收款单.xls',  '/tmp/import-xlsx/收款单.xlsx')
print('全部转换完成')
EOF
```

### 5.2 第二步：导入客户、工艺、物料

```bash
python3 scripts/import_data.py
```

预期输出：
```
✅ 登录成功
客户导入完成：成功 ~482，跳过 ~6，失败 0
工艺导入完成：成功 ~155，跳过 0，失败 0
物料导入完成：成功 ~23968，跳过 ~1，失败 0
```

> 脚本内置幂等检查，重复执行不会产生重复数据。

### 5.3 第三步：物料单价初始化

> ⚠️ **重要——请仔细阅读后再操作**
>
> - 本步骤目的是**用历史收货单里的单价填充物料档案的默认单价**，让后续新建收货单时能自动带出正确单价。
> - `price-init/` 目录下的文件是**单价数据来源**，脚本只会更新 `material.default_price` 字段，**不会写入任何收货单记录**。
> - **严禁**将 `price-init/` 目录下的文件通过系统「收货单导入」功能导入，否则会产生大量重复历史单据，导致库存和对账单数据错乱。

**操作步骤：**

1. 将老系统历史收货单 `.xls` 文件放入 `old-system-file/price-init/` 目录（可放多个文件，脚本会合并处理）
2. 运行初始化脚本：

```bash
python3 scripts/init_material_price.py
```

预期输出：
```
找到 1 个文件：
  收货单.xls

✅ 登录成功

[1/3] 加载物料档案...
  已加载物料档案：~23971 条

[2/3] 读取历史收货单单价...
  读取 收货单.xls ...
    → 提取有效单价 ~8000 条

  合并后共 ~8000 个（物料名+客户）组合

[3/3] 更新物料默认单价...
  需更新单价：~6000 条
  单价未变化（跳过）：~1500 条
  物料档案中未找到（跳过）：~500 条

✅ 完成！更新：~6000 条，失败：0 条
```

> 脚本幂等，可安全重复执行。跳过的「物料档案中未找到」记录是收货单中用了别名或已停用的物料，不影响正常物料的单价。

### 5.4 第四步：导入收货单（历史数据）

```bash
python3 << 'EOF'
import requests, warnings
warnings.filterwarnings('ignore')

token = requests.post('http://localhost:8080/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
headers = {'Authorization': f'Bearer {token}'}

print('导入收货单...')
with open('/tmp/import-xlsx/收货单.xlsx', 'rb') as f:
    r = requests.post('http://localhost:8080/api/receipts/import?mode=history',
        headers=headers, files={'file': ('收货单.xlsx', f)}, timeout=600)
d = r.json()['data']
print(f'成功: {d["success"]}, 跳过: {d["skip"]}, 失败: {d["fail"]}')
if d['errors']: print('错误(前3):', d['errors'][:3])
EOF
```

预期：成功约 2260 张收货单，失败 1 条（XLS 末行日期为空，可忽略）。

### 5.5 第五步：导入排产单（历史数据）

```bash
python3 << 'EOF'
import requests, warnings
warnings.filterwarnings('ignore')

token = requests.post('http://localhost:8080/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
headers = {'Authorization': f'Bearer {token}'}

print('导入排产单...')
with open('/tmp/import-xlsx/排产单.xlsx', 'rb') as f:
    r = requests.post('http://localhost:8080/api/productions/import?mode=history',
        headers=headers, files={'file': ('排产单.xlsx', f)}, timeout=600)
d = r.json()['data']
print(f'成功: {d["success"]}, 跳过: {d["skip"]}, 失败: {d["fail"]}')
EOF
```

预期：成功约 1232 张排产单。

### 5.6 第六步：导入发货单（历史数据）

```bash
python3 << 'EOF'
import requests, warnings
warnings.filterwarnings('ignore')

token = requests.post('http://localhost:8080/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
headers = {'Authorization': f'Bearer {token}'}

print('导入发货单...')
with open('/tmp/import-xlsx/发货单.xlsx', 'rb') as f:
    r = requests.post('http://localhost:8080/api/shipments/import?mode=history',
        headers=headers, files={'file': ('发货单.xlsx', f)}, timeout=600)
d = r.json()['data']
print(f'成功: {d["success"]}, 跳过: {d["skip"]}, 失败: {d["fail"]}')
EOF
```

预期：成功约 2536 张发货单（FG- 前缀行为非发货数据，自动跳过约 2704 行）。

### 5.7 第七步：导入收款单

```bash
python3 << 'EOF'
import requests, warnings
warnings.filterwarnings('ignore')

token = requests.post('http://localhost:8080/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
headers = {'Authorization': f'Bearer {token}'}

print('导入收款单...')
with open('/tmp/import-xlsx/收款单.xlsx', 'rb') as f:
    r = requests.post('http://localhost:8080/api/payments/import',
        headers=headers, files={'file': ('收款单.xlsx', f)}, timeout=120)
d = r.json()['data']
print(f'成功: {d["success"]}, 跳过: {d["skip"]}, 失败: {d["fail"]}')
EOF
```

预期：成功约 380 条收款记录。

### 5.8 第八步：期初库存补录

**背景**：老系统仅迁移了 2025 年及以后的数据，但 2025 年初已有在途存量。
若不补录，月度对账结余会出现负数。

本步骤通过计算每个（物料、客户、工艺）组合所有月份的最大累计缺口，
在 2024-12-31 日期插入一张期初收货单（单号 `RH-INIT-{客户ID}`），
保证所有月份结余 ≥ 0。

```bash
python3 scripts/init_opening_stock.py
```

预期输出：
```
Step 1: 计算期初库存需求（基于最大累计缺口）...
  需补录物料组合: ~856 个
Step 2: 加载物料 / 工艺 / 客户信息...
Step 3: 插入期初收货单（日期 2024-12-31）...
  已跳过（已存在）: 0 张
  新增期初收货单:   ~35 张
  新增明细条数:     ~852 条
完成！请在此步骤后重新执行「重建库存」接口。
```

> 脚本幂等：已存在 `RH-INIT-{客户ID}` 的客户自动跳过，可安全重复执行。

### 5.9 第九步：重建库存

收货单（含期初）和发货单全部导入后，重建全量库存：

> **注意**：必须在期初库存补录完成后再执行，否则期初数据不会计入。

```bash
python3 << 'EOF'
import requests, warnings
warnings.filterwarnings('ignore')

token = requests.post('http://localhost:8080/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
headers = {'Authorization': f'Bearer {token}'}

print('重建库存...')
r = requests.post('http://localhost:8080/api/inventory/rebuild', headers=headers, timeout=60)
d = r.json()['data']
print(f'收货分组: {d["receiptGroups"]}, 发货分组: {d["shipmentGroups"]}, 库存记录: {d["inventoryRecords"]}')
EOF
```

预期：
- 收货分组 ~3650 个（含期初收货）
- 发货分组 ~3522 个
- 生成库存记录 ~3667 条
- 负库存 ≤ 5 条（均为原始数据录入误差，非系统问题）

### 5.10 第十步：批量生成对账单

库存重建完成后，根据所有收发货数据批量生成历史对账单：

```bash
python3 << 'EOF'
import requests, warnings
warnings.filterwarnings('ignore')

token = requests.post('http://localhost:8080/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
headers = {'Authorization': f'Bearer {token}'}

print('批量生成对账单...')
r = requests.post('http://localhost:8080/api/statements/generate-all', headers=headers, timeout=300)
d = r.json()['data']
print(f'生成: {d["success"]} 条，跳过: {d["skip"]} 条（已存在），失败: {d["fail"]} 条')
if d.get('errors'):
    print('错误(前3):', d['errors'][:3])
EOF
```

预期：生成约 412 张对账单（按客户×月份汇总），共约 10604 条明细行。
已存在的对账单自动跳过，接口幂等可重复执行。

---

## 六、验证导入结果

```bash
python3 << 'EOF'
import requests, warnings
warnings.filterwarnings('ignore')

token = requests.post('http://localhost:8080/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
headers = {'Authorization': f'Bearer {token}'}

checks = [
    ('客户',   'http://localhost:8080/api/customers?page=1&size=1'),
    ('工艺',   'http://localhost:8080/api/processes?page=1&size=1'),
    ('物料',   'http://localhost:8080/api/materials?page=1&size=1'),
    ('收货单', 'http://localhost:8080/api/receipts?page=1&size=1'),
    ('排产单', 'http://localhost:8080/api/productions?page=1&size=1'),
    ('发货单', 'http://localhost:8080/api/shipments?page=1&size=1'),
    ('收款单', 'http://localhost:8080/api/payments?page=1&size=1'),
    ('库存',   'http://localhost:8080/api/inventory?page=1&size=1'),
    ('对账单', 'http://localhost:8080/api/statements?page=1&size=1'),
]

print('=== 数据验证 ===')
for name, url in checks:
    total = requests.get(url, headers=headers).json().get('data', {}).get('total', '?')
    print(f'  {name}: {total} 条')
EOF
```

预期结果：

| 模块 | 预期数量 |
|------|---------|
| 客户 | ~482 |
| 工艺 | ~155 |
| 物料 | ~23,971 |
| 收货单 | ~2,295（含 35 张期初收货单） |
| 排产单 | ~1,232 |
| 发货单 | ~2,536 |
| 收款单 | ~380 |
| 库存记录总数 | ~3,667 |
| 对账单 | ~412 |

---

## 七、日常运维

### 7.1 启动 / 停止

```bash
# 启动所有服务
docker compose up -d

# 停止所有服务（保留数据）
docker compose down

# 完全重置（删除数据库数据，慎用！）
docker compose down -v
```

### 7.2 查看日志

```bash
# 实时查看后端日志
docker compose logs -f backend

# 查看最近 100 行
docker compose logs --tail=100 backend
```

### 7.3 升级代码后重新部署

```bash
git pull origin main

# 只重建后端（最常见）
docker compose stop backend
docker compose rm -f backend
docker rmi sanitary-admin-backend -f
docker compose build --no-cache backend
docker compose up -d backend

# 只重建前端
docker compose stop frontend
docker compose rm -f frontend
docker rmi sanitary-admin-frontend -f
docker compose build --no-cache frontend
docker compose up -d frontend
```

> **重要**：代码升级不会丢失数据库数据，数据保存在 Docker Volume（`mysql_data`）中。

### 7.4 数据库直连（调试用）

```bash
# 进入 MySQL 容器
docker exec -it sanitary-mysql mysql -uroot -proot123 sanitary_admin

# 或通过宿主机端口连接（需要 MySQL 客户端）
mysql -h 127.0.0.1 -P 3307 -uroot -proot123 sanitary_admin
```

### 7.5 重建库存

当收发货数据发生变化或数据导入出现问题时，可随时调用重建接口重算全量库存：

```bash
curl -s -X POST http://localhost:8080/api/inventory/rebuild \
  -H "Authorization: Bearer $(curl -s -X POST http://localhost:8080/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])')"
```

### 7.6 重新生成对账单

当收发货数据变化后，可对指定月份重新生成对账单（幂等，已存在则重建明细）：

```bash
# 批量重新生成全部对账单
curl -s -X POST http://localhost:8080/api/statements/generate-all \
  -H "Authorization: Bearer <token>"
```

---

## 八、已知问题与注意事项

### 8.1 首次部署的坑（已修复，记录在案）

| 问题 | 原因 | 状态 |
|------|------|------|
| `init.sql` 缺少明细表 | 历史遗留，init.sql 未跟随代码更新 | ✅ 已修复 |
| 发货单发货类型全为"返工" | 导入时列映射错误 | ✅ 已修复 |
| 导入历史数据后库存为零 | 历史导入绕过业务层，不触发库存更新 | ✅ 已修复（增加 `/rebuild` 接口） |
| 发货单数量/单价精度丢失 | `getCellString` 强转截断小数 | ✅ 已修复（改用 BigDecimal 读取） |
| 对账单流水号重复导致批量生成失败 | `generateMonthly` LIKE 模式多写了 2 个下划线（8位匹配 vs 6位年月） | ✅ 已修复 |
| 对账单上月结余全为 0 | generate 依赖链式查上月对账单，批量时顺序不保证 | ✅ 已修复（改为直接聚合历史收发记录） |
| 对账单结余出现负数 | 迁移仅含 2025 年数据，期初库存未补录 | ✅ 已修复（新增期初库存补录步骤） |
| 库存重建结果错误（负数） | `aggregateReceiptQty` GROUP BY 含字符串字段，同 key 多行被覆盖；`aggregateShipmentQty` 未计 `defective_qty` 且缺 `status=1` 过滤 | ✅ 已修复 |
| 对账单明细缺少退回数量/良品金额列 | `statement_item` 表缺字段，`generate()` 未生成明细行 | ✅ 已修复（新增 `defective_qty`、`goods_amount` 列及明细生成逻辑） |

### 8.2 导入说明

- **幂等性**：所有导入接口均检查单号是否已存在，重复执行只会跳过，不会产生重复数据。
- **最后一行失败**：收货单、发货单、收款单各有 1 条失败，原因是 XLS 最后一行日期为空，属正常现象，可忽略。
- **发货单 FG- 前缀行跳过**：发货单 XLS 中 FG- 开头的行为物料存档行（非发货数据），导入时自动跳过，约 2704 行；实际导入约 2536 张发货单。
- **期初库存幂等**：`scripts/init_opening_stock.py` 以 `RH-INIT-{customerId}` 为唯一键检查，已存在则跳过，可安全重复执行。
- **对账单批量生成**：通过「对账单 → 批量初始化」按钮或调用 `POST /api/statements/generate-all` 接口，将自动遍历所有收发货数据聚合出客户×月份组合，逐一生成对账单汇总，已存在的月份自动跳过。

### 8.3 XLS 文件说明

老系统导出的 `.xls` 文件存在文件头损坏问题（`WARNING *** file size not 512 + multiple of sector size`），`xlrd` 库仍能正常读取，无需处理。

通过 API 上传导入前需用 `openpyxl` 转为 `.xlsx`，因为后端使用 Apache POI 读取，不支持损坏头的 `.xls`。

### 8.4 负库存说明

数据初始化完成后，可能存在 ≤ 5 条库存为负的记录，原因是老系统原始数据中该物料的发货量略超出收货量（1~3 件误差），属历史数据录入问题，非系统 Bug。

---

## 九、完整一键部署脚本

以下脚本适合在新机器上全自动完成部署（需要已放好 Excel 文件）：

```bash
#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "=== [1/5] 构建并启动 Docker 服务 ==="
docker compose build --no-cache
docker compose up -d

echo "=== [2/5] 等待后端就绪 ==="
until curl -sf http://localhost:8080/actuator/health > /dev/null; do
  echo "  等待中..."; sleep 5
done
echo "后端已就绪"

echo "=== [3/5] 转换 Excel 格式 ==="
python3 - << 'PYEOF'
import xlrd, openpyxl, os, warnings
warnings.filterwarnings('ignore')
def xls_to_xlsx(src, dst):
    wb = xlrd.open_workbook(src)
    ws = wb.sheets()[0]
    wb2 = openpyxl.Workbook()
    ws2 = wb2.active
    for r in range(ws.nrows): ws2.append(ws.row_values(r))
    wb2.save(dst)
    print(f'  {src} -> {ws.nrows} 行')
os.makedirs('/tmp/import-xlsx', exist_ok=True)
for name in ['收货单', '排产单', '发货单', '收款单']:
    xls_to_xlsx(f'old-system-file/{name}.xls', f'/tmp/import-xlsx/{name}.xlsx')
PYEOF

echo "=== [4/6] 导入基础数据及单据 ==="
python3 scripts/import_data.py

# ⚠️  单价初始化：从 price-init/ 目录读取历史收货单，仅更新物料单价
# 请勿将 price-init/ 下的文件通过「收货单导入」功能导入系统
echo "=== [5/6] 物料单价初始化（price-init/ 目录） ==="
python3 scripts/init_material_price.py

python3 - << 'PYEOF'
import requests, warnings
warnings.filterwarnings('ignore')
BASE = 'http://localhost:8080'
token = requests.post(f'{BASE}/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
h = {'Authorization': f'Bearer {token}'}

for name, path, param in [
    ('收货单', '/api/receipts/import',    '?mode=history'),
    ('排产单', '/api/productions/import', '?mode=history'),
    ('发货单', '/api/shipments/import',   '?mode=history'),
    ('收款单', '/api/payments/import',    ''),
]:
    fname = name + '.xlsx'
    with open(f'/tmp/import-xlsx/{fname}', 'rb') as f:
        r = requests.post(f'{BASE}{path}{param}', headers=h,
            files={'file': (fname, f)}, timeout=600).json()['data']
    print(f'{name}: 成功 {r["success"]}, 跳过 {r["skip"]}, 失败 {r["fail"]}')
PYEOF

echo "=== [6/6] 期初库存 + 重建库存 + 生成对账单 ==="
python3 scripts/init_opening_stock.py

python3 - << 'PYEOF'
import requests, warnings
warnings.filterwarnings('ignore')
BASE = 'http://localhost:8080'
token = requests.post(f'{BASE}/api/auth/login',
    json={'username':'admin','password':'admin123'}).json()['data']['token']
h = {'Authorization': f'Bearer {token}'}

print('重建库存...')
r = requests.post(f'{BASE}/api/inventory/rebuild', headers=h, timeout=60).json()['data']
print(f'  库存记录: {r["inventoryRecords"]} 条')

print('批量生成对账单...')
r = requests.post(f'{BASE}/api/statements/generate-all', headers=h, timeout=300).json()['data']
print(f'  对账单: 生成 {r["success"]} 条，跳过 {r["skip"]} 条，失败 {r["fail"]} 条')
print('=== 部署完成 ===')
PYEOF
```

保存为 `scripts/deploy_and_import.sh`，执行：

```bash
chmod +x scripts/deploy_and_import.sh
./scripts/deploy_and_import.sh
```

---

## 十、系统账号

| 账号 | 密码 | 权限 |
|------|------|------|
| admin | admin123 | 超级管理员 |

首次登录后建议在【系统管理 → 用户管理】中修改密码。
