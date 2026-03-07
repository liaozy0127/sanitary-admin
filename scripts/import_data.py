#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""老系统数据导入脚本"""

import xlrd
import requests
import json
import time
from datetime import datetime

BASE_URL = "http://localhost:8080"
DATA_DIR = "/Users/admin/IdeaProjects/sanitary-admin/old-system-file"

def login():
    resp = requests.post(f"{BASE_URL}/api/auth/login", json={"username": "admin", "password": "admin123"})
    token = resp.json().get("data", {}).get("token", "")
    if not token:
        raise Exception(f"登录失败: {resp.text}")
    print(f"✅ 登录成功")
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

def import_customers(headers):
    print("\n📦 开始导入客户档案...")
    wb = xlrd.open_workbook(f"{DATA_DIR}/客户档案.xls")
    sheet = wb.sheets()[0]
    
    success, skip, fail = 0, 0, 0
    fail_details = []
    
    for i in range(1, sheet.nrows):
        row = sheet.row_values(i)
        if not row[0] or not row[1]:
            skip += 1
            continue
        
        data = {
            "customerCode": str(row[0]).strip(),
            "customerName": str(row[1]).strip(),
            "areaName": str(row[2]).strip() if row[2] else "",
            "customerType": "月结" if str(row[3]).strip() == "月结" else "现金",
            "industry": str(row[4]).strip() if row[4] else "",
            "address": str(row[5]).strip() if row[5] else "",
            "salesperson": str(row[10]).strip() if row[10] else "",
            "email": str(row[11]).strip() if row[11] else "",
            "contactPerson": str(row[12]).strip() if row[12] else "",
            "contactPhone": str(row[13]).strip() if row[13] else "",
            "bankName": str(row[7]).strip() if row[7] else "",
            "bankAccount": str(row[9]).strip() if row[9] else "",
            "taxNo": str(row[8]).strip() if row[8] else "",
            "financeContact": str(row[20]).strip() if len(row) > 20 and row[20] else "",
            "financePhone": str(row[21]).strip() if len(row) > 21 and row[21] else "",
            "remark": str(row[19]).strip() if row[19] else "",
            "status": 0 if str(row[15]).strip().lower() == "true" else 1
        }
        
        try:
            resp = requests.post(f"{BASE_URL}/api/customers", headers=headers, json=data, timeout=10)
            if resp.status_code == 200 and resp.json().get("code") == 200:
                success += 1
            else:
                fail += 1
                msg = resp.text[:150]
                if len(fail_details) < 5:
                    fail_details.append(f"第{i}行[{data['customerCode']}]: {resp.status_code} {msg}")
                    print(f"  ❌ 第{i}行失败: {resp.status_code} {msg}")
        except Exception as e:
            fail += 1
            if len(fail_details) < 5:
                fail_details.append(f"第{i}行: {e}")
    
    print(f"  客户导入完成：成功 {success}，跳过 {skip}，失败 {fail}")
    return success, skip, fail, fail_details

def import_processes(headers):
    print("\n📦 开始导入工艺数据...")
    wb = xlrd.open_workbook(f"{DATA_DIR}/工艺数据.xls")
    sheet = wb.sheets()[0]
    
    success, skip, fail = 0, 0, 0
    fail_details = []
    
    for i in range(1, sheet.nrows):
        row = sheet.row_values(i)
        if not row[0] or not row[1]:
            skip += 1
            continue
        
        data = {
            "processCode": str(row[0]).strip(),
            "processName": str(row[1]).strip(),
            "thicknessReq": str(row[2]).strip() if row[2] else "",
            "remark": str(row[3]).strip() if row[3] else "",
            "processCategory": str(row[7]).strip() if len(row) > 7 and row[7] else "",
            "processNature": str(row[8]).strip() if len(row) > 8 and row[8] else "",
            "defaultQuote": 1 if str(row[5]).strip().lower() == "true" else 0,
            "status": 0 if str(row[6]).strip().lower() == "true" else 1
        }
        
        try:
            resp = requests.post(f"{BASE_URL}/api/processes", headers=headers, json=data, timeout=10)
            if resp.status_code == 200 and resp.json().get("code") == 200:
                success += 1
            else:
                fail += 1
                msg = resp.text[:150]
                if len(fail_details) < 5:
                    fail_details.append(f"第{i}行[{data['processCode']}]: {resp.status_code} {msg}")
                    print(f"  ❌ 第{i}行失败: {resp.status_code} {msg}")
        except Exception as e:
            fail += 1
            if len(fail_details) < 5:
                fail_details.append(f"第{i}行: {e}")
    
    print(f"  工艺导入完成：成功 {success}，跳过 {skip}，失败 {fail}")
    return success, skip, fail, fail_details

def import_materials(headers):
    print("\n📦 开始导入物料档案（共23165条，批量处理）...")
    
    # 先获取客户名称→ID映射
    resp = requests.get(f"{BASE_URL}/api/customers/all", headers=headers, timeout=30)
    customer_map = {}
    if resp.status_code == 200:
        cdata = resp.json().get("data", [])
        if isinstance(cdata, list):
            for c in cdata:
                customer_map[c.get("customerName", "")] = c.get("id")
        elif isinstance(cdata, dict):
            # 可能是分页结构
            for c in cdata.get("records", []):
                customer_map[c.get("customerName", "")] = c.get("id")
    print(f"  已加载 {len(customer_map)} 个客户映射")
    
    wb = xlrd.open_workbook(f"{DATA_DIR}/物料档案.xls")
    sheet = wb.sheets()[0]
    
    success, skip, fail = 0, 0, 0
    fail_details = []
    
    for i in range(1, sheet.nrows):
        row = sheet.row_values(i)
        if not row[0] or not row[1]:
            skip += 1
            continue
        
        customer_name = str(row[3]).strip() if row[3] else ""
        customer_id = customer_map.get(customer_name)
        
        # row[6] = 单价
        try:
            price = float(row[6]) if row[6] else 0.0
        except:
            price = 0.0
        
        data = {
            "materialCode": str(row[0]).strip(),
            "materialName": str(row[1]).strip(),
            "spec": str(row[2]).strip() if row[2] else "",
            "customerId": customer_id,
            "customerName": customer_name,
            "defaultPrice": round(price, 4),
            "status": 1
        }
        
        try:
            resp = requests.post(f"{BASE_URL}/api/materials", headers=headers, json=data, timeout=10)
            if resp.status_code == 200 and resp.json().get("code") == 200:
                success += 1
            else:
                fail += 1
                msg = resp.text[:120]
                if len(fail_details) < 5:
                    fail_details.append(f"第{i}行[{data['materialCode']}]: {resp.status_code} {msg}")
                    print(f"  ❌ 物料[{data['materialCode']}]失败: {msg}")
        except Exception as e:
            fail += 1
            if len(fail_details) < 5:
                fail_details.append(f"第{i}行: {e}")
        
        # 每500条打印进度
        if i % 500 == 0:
            print(f"  进度: {i}/{sheet.nrows-1} | 成功:{success} 失败:{fail} 跳过:{skip}")
    
    print(f"  物料导入完成：成功 {success}，跳过 {skip}，失败 {fail}")
    return success, skip, fail, fail_details

if __name__ == "__main__":
    start_time = datetime.now()
    print("=" * 50)
    print("卫浴管理系统 - 老系统数据导入")
    print(f"开始时间: {start_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 50)
    
    headers = login()
    
    c_ok, c_skip, c_fail, c_details = import_customers(headers)
    p_ok, p_skip, p_fail, p_details = import_processes(headers)
    m_ok, m_skip, m_fail, m_details = import_materials(headers)
    
    end_time = datetime.now()
    elapsed = (end_time - start_time).seconds
    
    print("\n" + "=" * 50)
    print(f"✅ 导入完成！耗时 {elapsed} 秒")
    print(f"  客户: {c_ok} 成功 / {c_skip} 跳过 / {c_fail} 失败")
    print(f"  工艺: {p_ok} 成功 / {p_skip} 跳过 / {p_fail} 失败")
    print(f"  物料: {m_ok} 成功 / {m_skip} 跳过 / {m_fail} 失败")
    print("=" * 50)
    
    # 输出失败详情
    if c_details:
        print("\n客户失败详情:")
        for d in c_details:
            print(f"  {d}")
    if p_details:
        print("\n工艺失败详情:")
        for d in p_details:
            print(f"  {d}")
    if m_details:
        print("\n物料失败详情(前5条):")
        for d in m_details:
            print(f"  {d}")
