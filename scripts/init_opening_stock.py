#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
期初库存补录脚本
==============
用于在历史数据迁移完成后，补录 2024 年底的期初库存。

背景：
  老系统仅迁移了 2025 年及之后的收货/发货数据。
  部分物料在 2025 年初已有存量（期初库存），若不补录，
  月度对账的结余会出现负数（发货量大于可用库存）。

算法：
  对每个（物料、客户、工艺）组合，按月累计计算：
    cum_deficit = 累计发货 - 累计收货
  取所有月份的最大 cum_deficit（即最大缺口），向上取整后
  作为 2024-12-31 期初收货数量。此数量保证每个月的结余 ≥ 0。

使用方式：
  运行前确保收货单、发货单已全部导入完毕。
  python3 scripts/init_opening_stock.py

注意：
  脚本幂等——若对应客户已存在 RH-INIT-{customerId} 的期初收货单，
  则自动跳过，不会重复插入。
"""

import subprocess
import sys
import math
from collections import defaultdict

DOCKER = '/Applications/Docker.app/Contents/Resources/bin/docker'
CONTAINER = 'sanitary-mysql'
DB = 'sanitary_admin'
MYSQL_USER = 'root'
MYSQL_PASS = 'root123'


def mysql(sql):
    """执行查询，返回行列表，每行为字符串列表。"""
    r = subprocess.run(
        [DOCKER, 'exec', CONTAINER,
         'mysql', f'-u{MYSQL_USER}', f'-p{MYSQL_PASS}', '--default-character-set=utf8mb4', '-N', '-B', DB, '-e', sql],
        capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr)
    rows = []
    for line in r.stdout.strip().split('\n'):
        if line:
            rows.append(line.split('\t'))
    return rows


def mysql_exec(sql):
    """执行写操作（INSERT / UPDATE）。"""
    r = subprocess.run(
        [DOCKER, 'exec', CONTAINER,
         'mysql', f'-u{MYSQL_USER}', f'-p{MYSQL_PASS}', '--default-character-set=utf8mb4', DB, '-e', sql],
        capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"SQL error: {r.stderr}\nSQL: {sql[:300]}")


def esc(s):
    return (s or '').replace("'", "\\'")


def main():
    # ── Step 1: 计算各组合所需的期初数量 ─────────────────────────────────
    print("Step 1: 计算期初库存需求（基于最大累计缺口）...")
    needed_sql = """
SELECT material_id, customer_id, process_id, CEIL(MAX(cum_deficit)) AS needed_init_qty
FROM (
  SELECT material_id, customer_id, process_id, ym,
         SUM(ship_qty) OVER (PARTITION BY material_id, customer_id, process_id ORDER BY ym)
         - SUM(recv_qty) OVER (PARTITION BY material_id, customer_id, process_id ORDER BY ym)
         AS cum_deficit
  FROM (
    SELECT material_id, customer_id, process_id, ym,
           SUM(ship_qty) AS ship_qty, SUM(recv_qty) AS recv_qty
    FROM (
      SELECT si.material_id, sh.customer_id, COALESCE(si.process_id, 0) AS process_id,
             DATE_FORMAT(sh.shipment_date, '%Y-%m') AS ym,
             si.quantity + COALESCE(si.defective_qty, 0) AS ship_qty, 0 AS recv_qty
      FROM shipment_item si JOIN shipment sh ON sh.id = si.shipment_id
      WHERE sh.status = 1 AND si.deleted = 0 AND sh.deleted = 0
      UNION ALL
      SELECT ri.material_id, r.customer_id, COALESCE(ri.process_id, 0) AS process_id,
             DATE_FORMAT(r.receipt_date, '%Y-%m') AS ym,
             0 AS ship_qty, ri.quantity AS recv_qty
      FROM receipt_item ri JOIN receipt r ON r.id = ri.receipt_id
      WHERE r.status = 1 AND ri.deleted = 0 AND r.deleted = 0
    ) t
    GROUP BY material_id, customer_id, process_id, ym
  ) monthly
) cumulative
GROUP BY material_id, customer_id, process_id
HAVING needed_init_qty > 0
ORDER BY customer_id, material_id, process_id
"""
    needed_rows = mysql(needed_sql)
    print(f"  需补录物料组合: {len(needed_rows)} 个")
    if not needed_rows:
        print("  无需补录，退出。")
        return

    # ── Step 2: 预加载物料 / 工艺 / 客户信息 ────────────────────────────
    print("Step 2: 加载物料 / 工艺 / 客户信息...")

    all_mids = list(set(r[0] for r in needed_rows))
    all_pids = list(set(r[2] for r in needed_rows if r[2] != '0'))
    all_cids = list(set(r[1] for r in needed_rows))

    mat_info = {}
    batch = 200
    for i in range(0, len(all_mids), batch):
        ids = ','.join(all_mids[i:i + batch])
        for row in mysql(f"SELECT id, material_code, material_name, IF(spec IS NULL OR spec='','',spec) FROM material WHERE id IN ({ids})"):
            if len(row) >= 3:
                mat_info[row[0]] = {'code': esc(row[1]), 'name': esc(row[2]), 'spec': esc(row[3]) if len(row)>3 else ''}

    proc_info = {'0': ''}
    if all_pids:
        for row in mysql(f"SELECT id, process_name FROM process WHERE id IN ({','.join(all_pids)})"):
            proc_info[row[0]] = esc(row[1])

    cust_info = {}
    for row in mysql(f"SELECT id, customer_name FROM customer WHERE id IN ({','.join(all_cids)})"):
        cust_info[row[0]] = esc(row[1])

    # ── Step 3: 按客户分组 ────────────────────────────────────────────────
    by_customer = defaultdict(list)
    for row in needed_rows:
        material_id, customer_id, process_id, needed_qty = row
        by_customer[customer_id].append({
            'material_id': material_id,
            'process_id': process_id,
            'needed_qty': int(math.ceil(float(needed_qty))),
        })

    # ── Step 4: 插入期初收货单 ────────────────────────────────────────────
    print("Step 3: 插入期初收货单（日期 2024-12-31）...")
    receipt_count = 0
    item_count = 0
    skip_count = 0
    skip_items = 0

    for customer_id, items in by_customer.items():
        customer_name = cust_info.get(customer_id, f'客户{customer_id}')
        receipt_no = f"RH-INIT-{customer_id}"

        # 幂等：收货单按客户级别检查，已存在则直接使用其 id（允许追加明细）
        existing = mysql(f"SELECT id FROM receipt WHERE receipt_no = '{receipt_no}'")
        if existing:
            rid = existing[0][0]
            skip_count += 1  # 收货单本身跳过，但明细可能要追加
        else:
            mysql_exec(f"""
                INSERT INTO receipt
                  (receipt_no, receipt_date, customer_id, customer_name,
                   remark, status, deleted, create_time, update_time)
                VALUES
                  ('{receipt_no}', '2024-12-31', {customer_id}, '{customer_name}',
                   '期初库存补录（2024年底结存）', 1, 0, NOW(), NOW())
            """)
            rid = mysql(f"SELECT id FROM receipt WHERE receipt_no = '{receipt_no}'")[0][0]
            receipt_count += 1

        for item in items:
            mid = item['material_id']
            pid_val = item['process_id']
            qty = item['needed_qty']
            mat = mat_info.get(mid)
            if not mat:
                skip_items += 1
                continue
            # 幂等：按明细行检查（material_id + process_id），已存在则跳过
            pid_check = 'IS NULL' if pid_val == '0' else f'= {pid_val}'
            item_exists = mysql(
                f"SELECT COUNT(*) FROM receipt_item "
                f"WHERE receipt_id = {rid} AND material_id = {mid} AND process_id {pid_check}"
            )
            if item_exists and item_exists[0][0] != '0':
                skip_items += 1
                continue
            proc_name = proc_info.get(pid_val, '')
            pid_sql = 'NULL' if pid_val == '0' else pid_val

            mysql_exec(f"""
                INSERT INTO receipt_item
                  (receipt_id, receipt_no, material_id, material_name, material_code, spec,
                   process_id, process_name, quantity, unit_price, amount,
                   deleted, create_time, update_time)
                VALUES
                  ({rid}, '{receipt_no}', {mid},
                   '{mat['name']}', '{mat['code']}', '{mat['spec']}',
                   {pid_sql}, '{proc_name}', {qty}, 0, 0,
                   0, NOW(), NOW())
            """)
            item_count += 1

    print(f"  已跳过（已存在）: {skip_count} 张")
    print(f"  新增期初收货单:   {receipt_count} 张")
    print(f"  新增明细条数:     {item_count} 条")
    if skip_items:
        print(f"  跳过明细（物料不存在）: {skip_items} 条")

    print("\n完成！请在此步骤后重新执行「重建库存」接口。")


if __name__ == '__main__':
    main()
