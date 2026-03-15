#!/bin/bash
# 卫浴管理系统 — 全量部署 + 数据导入脚本
# 用法：cd sanitary-admin && bash scripts/deploy_and_import.sh

set -e
cd "$(dirname "$0")/.."

# macOS 兼容：优先使用 Docker Desktop 完整路径
DOCKER=docker
if [ -f "/Applications/Docker.app/Contents/Resources/bin/docker" ]; then
    DOCKER="/Applications/Docker.app/Contents/Resources/bin/docker"
fi

echo "================================================"
echo " 卫浴管理系统 — 一键部署 & 数据导入"
echo "================================================"

# 检查 Excel 文件
echo ""
echo "[检查] 验证 old-system-file/ 目录..."
MISSING=0
for f in 客户档案.xls 工艺数据.xls 物料档案.xls 收货单.xls 排产单.xls 发货单.xls 收款单.xls; do
    if [ ! -f "old-system-file/$f" ]; then
        echo "  ❌ 缺少文件: old-system-file/$f"
        MISSING=1
    else
        echo "  ✅ $f"
    fi
done
if [ "$MISSING" = "1" ]; then
    echo "请补充缺失的 Excel 文件后重新运行"
    exit 1
fi

# 检查 Python 依赖
echo ""
echo "[检查] 验证 Python 依赖..."
python3 -c "import xlrd, openpyxl, requests" 2>/dev/null || {
    echo "安装 Python 依赖..."
    pip3 install xlrd openpyxl requests
}
echo "  ✅ Python 依赖就绪"

# 构建并启动
echo ""
echo "[1/5] 构建并启动 Docker 服务..."
$DOCKER compose build --no-cache
$DOCKER compose up -d
echo "  ✅ 容器已启动"

# 等待后端就绪
echo ""
echo "[2/5] 等待后端就绪..."
for i in $(seq 1 40); do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "  ✅ 后端已就绪（第 ${i} 次检查）"
        break
    fi
    echo "  等待中... (${i}/40)"
    sleep 5
done

if ! curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "  ❌ 后端启动超时，请检查日志：docker compose logs backend"
    exit 1
fi

# 转换 Excel 格式
echo ""
echo "[3/5] 转换 Excel 文件格式（xls -> xlsx）..."
python3 << 'PYEOF'
import xlrd, openpyxl, os, warnings
warnings.filterwarnings('ignore')

def xls_to_xlsx(src, dst):
    wb = xlrd.open_workbook(src)
    ws = wb.sheets()[0]
    wb2 = openpyxl.Workbook()
    ws2 = wb2.active
    for r in range(ws.nrows):
        ws2.append(ws.row_values(r))
    wb2.save(dst)
    print(f'  ✅ {src}（{ws.nrows} 行）')

os.makedirs('/tmp/import-xlsx', exist_ok=True)
for name in ['收货单', '排产单', '发货单', '收款单']:
    xls_to_xlsx(f'old-system-file/{name}.xls', f'/tmp/import-xlsx/{name}.xlsx')
PYEOF

# 导入基础档案
echo ""
echo "[4/5] 导入基础档案（客户、工艺、物料）..."
python3 scripts/import_data.py

# 导入业务数据
echo ""
echo "[5/5] 导入业务数据..."
python3 << 'PYEOF'
import requests, warnings
warnings.filterwarnings('ignore')

BASE = 'http://localhost:8080'

# 登录
resp = requests.post(f'{BASE}/api/auth/login',
    json={'username': 'admin', 'password': 'admin123'})
token = resp.json()['data']['token']
h = {'Authorization': f'Bearer {token}'}

# 按顺序导入各类单据
imports = [
    ('收货单', '/api/receipts/import',    '?mode=history', 600),
    ('排产单', '/api/productions/import', '?mode=history', 600),
    ('发货单', '/api/shipments/import',   '?mode=history', 600),
    ('收款单', '/api/payments/import',    '',              120),
]

for name, path, param, timeout in imports:
    fname = name + '.xlsx'
    fpath = f'/tmp/import-xlsx/{fname}'
    print(f'  导入{name}...', end=' ', flush=True)
    with open(fpath, 'rb') as f:
        r = requests.post(f'{BASE}{path}{param}', headers=h,
            files={'file': (fname, f)}, timeout=timeout)
    d = r.json()['data']
    status = '✅' if d['fail'] == 0 else '⚠️'
    print(f'{status} 成功 {d["success"]}, 跳过 {d["skip"]}, 失败 {d["fail"]}')
    if d['errors']:
        for e in d['errors'][:3]:
            print(f'     错误: {str(e)[:120]}')

# 重建库存
print('  重建库存...', end=' ', flush=True)
r = requests.post(f'{BASE}/api/inventory/rebuild', headers=h, timeout=60)
d = r.json()['data']
print(f'✅ 库存记录 {d["inventoryRecords"]} 条')
PYEOF

echo ""
echo "================================================"
echo " ✅ 部署完成！"
echo "================================================"
echo " 前端地址：http://localhost"
echo " 后端 API：http://localhost:8080"
echo " 账号：admin / admin123"
echo "================================================"
