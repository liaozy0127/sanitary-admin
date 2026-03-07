#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""修复物料的 customerId 关联"""

import requests

BASE_URL = "http://localhost:8080"

def login():
    resp = requests.post(f"{BASE_URL}/api/auth/login", json={"username": "admin", "password": "admin123"})
    token = resp.json().get("data", {}).get("token", "")
    print(f"✅ 登录成功")
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

def fix_material_customer_ids(headers):
    # 加载客户映射（字段名是 name，不是 customerName）
    resp = requests.get(f"{BASE_URL}/api/customers/all", headers=headers, timeout=30)
    customer_map = {}
    if resp.status_code == 200:
        cdata = resp.json().get("data", [])
        if isinstance(cdata, list):
            for c in cdata:
                # 接口返回 name 字段
                name = c.get("name") or c.get("customerName", "")
                cid = c.get("id")
                if name and cid:
                    customer_map[name] = cid
        elif isinstance(cdata, dict):
            for c in cdata.get("records", []):
                name = c.get("name") or c.get("customerName", "")
                cid = c.get("id")
                if name and cid:
                    customer_map[name] = cid
    print(f"已加载 {len(customer_map)} 个客户映射")
    
    # 分页遍历所有物料，找到有 customerName 但无 customerId 的记录
    page = 1
    size = 500
    total_fixed = 0
    total_skip = 0
    
    while True:
        resp = requests.get(f"{BASE_URL}/api/materials?page={page}&size={size}", headers=headers, timeout=30)
        data = resp.json().get("data", {})
        records = data.get("records", [])
        total = data.get("total", 0)
        
        if not records:
            break
        
        for mat in records:
            mat_id = mat.get("id")
            customer_name = mat.get("customerName", "")
            customer_id = mat.get("customerId")
            
            if customer_name and not customer_id:
                mapped_id = customer_map.get(customer_name)
                if mapped_id:
                    # 更新 customerId
                    update_resp = requests.put(
                        f"{BASE_URL}/api/materials/{mat_id}",
                        headers=headers,
                        json={"customerId": mapped_id},
                        timeout=10
                    )
                    if update_resp.status_code == 200 and update_resp.json().get("code") == 200:
                        total_fixed += 1
                    else:
                        print(f"  更新失败 id={mat_id}: {update_resp.text[:100]}")
                else:
                    total_skip += 1  # 找不到对应客户
        
        print(f"  第{page}页处理完 | 已修复:{total_fixed} 无匹配:{total_skip} | 总进度:{page*size}/{total}")
        
        if page * size >= total:
            break
        page += 1
    
    print(f"\n修复完成：共修复 {total_fixed} 条，无客户匹配跳过 {total_skip} 条")
    return total_fixed, total_skip

if __name__ == "__main__":
    headers = login()
    fix_material_customer_ids(headers)
