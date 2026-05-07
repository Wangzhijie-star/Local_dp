#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量获取用户token脚本
用于JMeter压力测试
"""

import requests
import csv
import mysql.connector
from concurrent.futures import ThreadPoolExecutor, as_completed
import time

# 配置
BASE_URL = "http://localhost:8081"
DB_CONFIG = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    "password": "1234",
    "database": "hmdp"
}

OUTPUT_FILE = "tokens.csv"
BATCH_SIZE = 100  # 每批处理数量
MAX_WORKERS = 10  # 并发线程数


def get_all_phones():
    """从数据库获取所有用户手机号"""
    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor()
    cursor.execute("SELECT phone FROM tb_user")
    phones = [row[0] for row in cursor.fetchall()]
    cursor.close()
    conn.close()
    return phones


def login_and_get_token(phone):
    """登录并获取token"""
    try:
        url = f"{BASE_URL}/test/login/{phone}"
        response = requests.post(url, timeout=5)
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return phone, data.get("data")
        return phone, None
    except Exception as e:
        print(f"登录失败 {phone}: {e}")
        return phone, None


def batch_login_batch_api():
    """使用批量登录API获取所有token"""
    print("调用批量登录接口...")
    url = f"{BASE_URL}/test/batch-login"
    response = requests.get(url, timeout=60)
    if response.status_code == 200:
        print("批量登录完成，请查看Java控制台输出，复制token列表保存为 tokens.csv")
        return True
    else:
        print(f"批量登录失败: {response.status_code}")
        return False


def batch_login_one_by_one():
    """逐个登录获取token"""
    phones = get_all_phones()
    total = len(phones)
    print(f"获取到 {total} 个用户")

    tokens = []
    success_count = 0
    fail_count = 0

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        future_to_phone = {executor.submit(login_and_get_token, phone): phone for phone in phones}

        for future in as_completed(future_to_phone):
            phone, token = future.result()
            if token:
                tokens.append((phone, token))
                success_count += 1
            else:
                fail_count += 1

            if (success_count + fail_count) % 100 == 0:
                print(f"进度: {success_count + fail_count}/{total}, 成功: {success_count}, 失败: {fail_count}")

    # 保存到CSV
    with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['phone', 'token'])
        writer.writerows(tokens)

    print(f"\n完成！")
    print(f"成功: {success_count}, 失败: {fail_count}")
    print(f"Token已保存到: {OUTPUT_FILE}")


if __name__ == "__main__":
    print("=" * 50)
    print("批量获取Token工具")
    print("=" * 50)
    print("\n请选择方式:")
    print("1. 使用批量登录API (快速，需在控制台复制)")
    print("2. 逐个登录获取 (较慢，直接保存CSV)")

    choice = input("\n请输入选项 (1/2): ").strip()

    if choice == "1":
        batch_login_batch_api()
    elif choice == "2":
        batch_login_one_by_one()
    else:
        print("无效选项")
