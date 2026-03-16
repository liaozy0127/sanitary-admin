#!/usr/bin/env python3
"""
物料默认单价初始化脚本
=====================
从 old-system-file/price-init/ 目录下的历史收货单 Excel 文件中提取单价，
批量更新物料档案的 default_price 字段。

⚠️  重要说明：
  - 本脚本【仅更新物料单价】，不会导入收货单数据
  - 请勿将 price-init/ 目录下的文件通过「收货单导入」功能导入系统
  - 本脚本幂等，可安全重复执行

收货单列映射（老系统格式）：
  col 0:  选择
  col 1:  收货单号
  col 2:  日期
  col 3:  客户名称
  col 4:  产品名称
  col 5:  型号规格
  col 6:  工艺名称
  col 7:  收货来源
  col 8:  收货数量
  col 9:  发货数量
  col 10: 未发货数量
  col 11: 单价         ← 本脚本只用此列
  col 12: 客户单号
  ...
"""

import os
import sys
import glob
import warnings
import requests
from decimal import Decimal, InvalidOperation

warnings.filterwarnings('ignore')

# ── 配置 ────────────────────────────────────────────────────────────────────
BASE_URL   = 'http://localhost:8080'
USERNAME   = 'admin'
PASSWORD   = 'admin123'
PRICE_DIR  = os.path.join(os.path.dirname(__file__), '..', 'old-system-file', 'price-init')
# 单价有效范围（过滤明显异常值）
MIN_PRICE  = Decimal('0.01')
MAX_PRICE  = Decimal('99999')
# ────────────────────────────────────────────────────────────────────────────


def login():
    resp = requests.post(f'{BASE_URL}/api/auth/login',
                         json={'username': USERNAME, 'password': PASSWORD},
                         timeout=10)
    resp.raise_for_status()
    token = resp.json()['data']['token']
    print(f'✅ 登录成功')
    return {'Authorization': f'Bearer {token}'}


def load_all_materials(headers):
    """加载系统中所有物料，建立 (material_name, customer_name) -> (id, current_price) 索引"""
    index = {}
    page, size = 1, 500
    while True:
        resp = requests.get(f'{BASE_URL}/api/materials',
                            params={'page': page, 'size': size},
                            headers=headers, timeout=30)
        resp.raise_for_status()
        data = resp.json()['data']
        records = data.get('records', [])
        if not records:
            break
        for m in records:
            name = (m.get('materialName') or '').strip()
            cust = (m.get('customerName') or '').strip()
            if name:
                key = (name, cust)
                index[key] = {
                    'id':    m['id'],
                    'price': Decimal(str(m.get('defaultPrice') or 0)),
                }
        if page >= data.get('pages', 1):
            break
        page += 1
    print(f'  已加载物料档案：{len(index)} 条')
    return index


def read_prices_from_xls(filepath):
    """从单个 xls 文件中提取 (产品名称, 客户名称, 单价) 三元组"""
    try:
        import xlrd
    except ImportError:
        print('缺少 xlrd 库，请执行：pip3 install xlrd', file=sys.stderr)
        sys.exit(1)

    prices = {}  # (material_name, customer_name) -> max_price（取该文件中出现的最大值，忽略0）
    try:
        wb = xlrd.open_workbook(filepath, encoding_override='utf-8')
        ws = wb.sheet_by_index(0)
    except Exception as e:
        print(f'  ⚠️  无法读取文件 {os.path.basename(filepath)}: {e}')
        return prices

    row_count = 0
    for r in range(1, ws.nrows):  # 跳过表头
        row = ws.row_values(r)
        if len(row) < 12:
            continue

        material_name = str(row[4]).strip() if row[4] else ''
        customer_name = str(row[3]).strip() if row[3] else ''
        source        = str(row[7]).strip() if len(row) > 7 else ''

        # 跳过返工来源（返工件单价为 0，不应覆盖正常单价）
        if source == '返工':
            continue
        if not material_name:
            continue

        raw_price = row[11]
        try:
            price = Decimal(str(raw_price)).quantize(Decimal('0.01'))
        except (InvalidOperation, TypeError):
            continue

        if price < MIN_PRICE or price > MAX_PRICE:
            continue

        key = (material_name, customer_name)
        # 同一文件内取最新出现的单价（xls 行顺序即时间顺序），直接覆盖
        prices[key] = price
        row_count += 1

    return prices


def batch_update_prices(headers, material_index, price_map):
    """将收集到的单价与物料档案比对，通过 API 更新有变化的物料"""
    to_update = []   # [(material_id, new_price, material_name)]
    skipped_no_match = []
    skipped_same = 0

    for (mat_name, cust_name), new_price in price_map.items():
        entry = material_index.get((mat_name, cust_name))
        if entry is None:
            # 尝试只按物料名称匹配（忽略客户）
            entry = material_index.get((mat_name, ''))
        if entry is None:
            skipped_no_match.append(f'{mat_name} / {cust_name}')
            continue
        if entry['price'] == new_price:
            skipped_same += 1
            continue
        to_update.append((entry['id'], new_price, mat_name))

    print(f'  需更新单价：{len(to_update)} 条')
    print(f'  单价未变化（跳过）：{skipped_same} 条')
    print(f'  物料档案中未找到（跳过）：{len(skipped_no_match)} 条')
    if skipped_no_match:
        print(f'  未匹配样例（前5条）：{skipped_no_match[:5]}')

    updated = 0
    failed  = 0
    for mat_id, new_price, mat_name in to_update:
        try:
            resp = requests.put(
                f'{BASE_URL}/api/materials/{mat_id}',
                json={'defaultPrice': str(new_price)},
                headers=headers,
                timeout=10
            )
            resp.raise_for_status()
            updated += 1
        except Exception as e:
            failed += 1
            if failed <= 5:
                print(f'  ❌ 更新失败 [{mat_name}]: {e}')

    return updated, failed


def main():
    price_dir = os.path.normpath(PRICE_DIR)
    if not os.path.isdir(price_dir):
        print(f'❌ 目录不存在：{price_dir}')
        print('请将历史收货单 .xls 文件放入 old-system-file/price-init/ 目录后重试。')
        sys.exit(1)

    xls_files = sorted(glob.glob(os.path.join(price_dir, '*.xls')))
    if not xls_files:
        print(f'⚠️  {price_dir} 目录下没有找到任何 .xls 文件，退出。')
        print('请将历史收货单 .xls 文件放入该目录后重试。')
        sys.exit(0)

    print(f'找到 {len(xls_files)} 个文件：')
    for f in xls_files:
        print(f'  {os.path.basename(f)}')
    print()

    # 1. 登录
    headers = login()

    # 2. 加载物料档案
    print('\n[1/3] 加载物料档案...')
    material_index = load_all_materials(headers)

    # 3. 从所有 xls 提取单价（多文件合并，后读取的文件覆盖先读取的）
    print('\n[2/3] 读取历史收货单单价...')
    merged_prices = {}
    for filepath in xls_files:
        fname = os.path.basename(filepath)
        print(f'  读取 {fname} ...')
        prices = read_prices_from_xls(filepath)
        merged_prices.update(prices)
        print(f'    → 提取有效单价 {len(prices)} 条')

    print(f'\n  合并后共 {len(merged_prices)} 个（物料名+客户）组合')

    # 4. 比对并更新
    print('\n[3/3] 更新物料默认单价...')
    updated, failed = batch_update_prices(headers, material_index, merged_prices)

    print(f'\n✅ 完成！更新：{updated} 条，失败：{failed} 条')


if __name__ == '__main__':
    main()
