#!/usr/bin/env python3
"""
自动获取Claude Session Key
支持从Chrome、Edge、Firefox浏览器自动提取sessionKey
"""

import sys
import os
import sqlite3
import json
import platform
from pathlib import Path
import shutil
import tempfile

def get_chrome_cookie_path():
    """获取Chrome Cookie文件路径"""
    system = platform.system()

    if system == "Darwin":  # macOS
        return Path.home() / "Library/Application Support/Google/Chrome/Default/Cookies"
    elif system == "Windows":
        return Path.home() / "AppData/Local/Google/Chrome/User Data/Default/Network/Cookies"
    elif system == "Linux":
        return Path.home() / ".config/google-chrome/Default/Cookies"

    return None

def get_edge_cookie_path():
    """获取Edge Cookie文件路径"""
    system = platform.system()

    if system == "Darwin":  # macOS
        return Path.home() / "Library/Application Support/Microsoft Edge/Default/Cookies"
    elif system == "Windows":
        return Path.home() / "AppData/Local/Microsoft/Edge/User Data/Default/Network/Cookies"
    elif system == "Linux":
        return Path.home() / ".config/microsoft-edge/Default/Cookies"

    return None

def get_firefox_cookie_path():
    """获取Firefox Cookie文件路径"""
    system = platform.system()

    if system == "Darwin":  # macOS
        firefox_dir = Path.home() / "Library/Application Support/Firefox/Profiles"
    elif system == "Windows":
        firefox_dir = Path.home() / "AppData/Roaming/Mozilla/Firefox/Profiles"
    elif system == "Linux":
        firefox_dir = Path.home() / ".mozilla/firefox"
    else:
        return None

    if not firefox_dir.exists():
        return None

    # 查找默认配置文件
    for profile_dir in firefox_dir.iterdir():
        if profile_dir.is_dir() and "default" in profile_dir.name.lower():
            cookies_file = profile_dir / "cookies.sqlite"
            if cookies_file.exists():
                return cookies_file

    return None

def extract_session_key_chromium(cookie_path):
    """从Chromium系浏览器提取Session Key"""
    if not cookie_path or not cookie_path.exists():
        return None

    try:
        # 复制Cookie文件到临时目录（避免锁定问题）
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_cookie = Path(temp_dir) / "cookies_copy"
            shutil.copy2(cookie_path, temp_cookie)

            # 连接到Cookie数据库
            conn = sqlite3.connect(f"file:{temp_cookie}?mode=ro", uri=True)
            cursor = conn.cursor()

            # 查询claude.ai的sessionKey
            cursor.execute("""
                SELECT name, value, host_key
                FROM cookies
                WHERE host_key LIKE '%claude.ai%'
                AND (name = 'sessionKey' OR name = '__Secure-sessionKey')
            """)

            rows = cursor.fetchall()
            conn.close()

            for row in rows:
                name, value, host = row
                if value and len(value) > 20:  # Session Key通常很长
                    return value

            return None

    except Exception as e:
        print(f"读取Cookie失败: {e}", file=sys.stderr)
        return None

def extract_session_key_firefox(cookie_path):
    """从Firefox提取Session Key"""
    if not cookie_path or not cookie_path.exists():
        return None

    try:
        # Firefox的Cookie数据库可以直接读取
        conn = sqlite3.connect(f"file:{cookie_path}?mode=ro", uri=True)
        cursor = conn.cursor()

        # 查询claude.ai的sessionKey
        cursor.execute("""
            SELECT name, value, host
            FROM moz_cookies
            WHERE host LIKE '%claude.ai%'
            AND (name = 'sessionKey' OR name = '__Secure-sessionKey')
        """)

        rows = cursor.fetchall()
        conn.close()

        for row in rows:
            name, value, host = row
            if value and len(value) > 20:
                return value

        return None

    except Exception as e:
        print(f"读取Firefox Cookie失败: {e}", file=sys.stderr)
        return None

def get_session_key():
    """自动从浏览器获取Session Key"""
    print("🔍 正在搜索Claude Session Key...")
    print()

    browsers = [
        ("Chrome", get_chrome_cookie_path(), extract_session_key_chromium),
        ("Edge", get_edge_cookie_path(), extract_session_key_chromium),
        ("Firefox", get_firefox_cookie_path(), extract_session_key_firefox),
    ]

    for browser_name, cookie_path, extract_func in browsers:
        print(f"检查 {browser_name}...", end=" ")

        if not cookie_path:
            print("❌ 未找到")
            continue

        if not cookie_path.exists():
            print("❌ Cookie文件不存在")
            continue

        print("✓ 找到Cookie文件")

        session_key = extract_func(cookie_path)

        if session_key:
            print(f"✅ 成功从 {browser_name} 提取Session Key!")
            print()
            return browser_name, session_key
        else:
            print(f"   ⚠️  未找到sessionKey（可能未登录claude.ai）")

    return None, None

def save_to_env(session_key):
    """保存到.env文件"""
    env_file = Path(__file__).parent / ".env"

    # 读取现有内容
    if env_file.exists():
        with open(env_file, 'r') as f:
            lines = f.readlines()
    else:
        lines = []

    # 更新或添加CLAUDE_SESSION_KEY
    updated = False
    new_lines = []

    for line in lines:
        if line.startswith("CLAUDE_SESSION_KEY="):
            new_lines.append(f"CLAUDE_SESSION_KEY={session_key}\n")
            updated = True
        elif line.startswith("PROVIDER_TYPE="):
            new_lines.append("PROVIDER_TYPE=pro\n")
        else:
            new_lines.append(line)

    if not updated:
        new_lines.append(f"\n# 自动获取的Session Key\n")
        new_lines.append(f"PROVIDER_TYPE=pro\n")
        new_lines.append(f"CLAUDE_SESSION_KEY={session_key}\n")

    # 写入文件
    with open(env_file, 'w') as f:
        f.writelines(new_lines)

    return env_file

def main():
    print("=" * 60)
    print("Claude Session Key 自动获取工具")
    print("=" * 60)
    print()

    # 检查是否有浏览器正在运行
    print("⚠️  提示：如果浏览器正在运行，可能无法读取Cookie")
    print("   建议：先关闭浏览器，或者忽略此警告继续")
    print()

    browser, session_key = get_session_key()

    if not session_key:
        print()
        print("❌ 未能自动获取Session Key")
        print()
        print("可能的原因：")
        print("  1. 未在任何浏览器登录 claude.ai")
        print("  2. 浏览器正在运行导致Cookie被锁定")
        print("  3. 使用了其他浏览器（Arc、Brave等）")
        print()
        print("解决方法：")
        print("  1. 访问 https://claude.ai 并登录")
        print("  2. 关闭浏览器后重新运行此脚本")
        print("  3. 或手动获取：按F12 → Application → Cookies → sessionKey")
        return 1

    print()
    print("=" * 60)
    print("✅ Session Key 获取成功!")
    print("=" * 60)
    print()
    print(f"浏览器: {browser}")
    print(f"Session Key: {session_key[:30]}...")
    print()

    # 询问是否保存
    try:
        save = input("是否保存到 .env 文件？(y/n) [y]: ").strip().lower()
        if save == '' or save == 'y' or save == 'yes':
            env_file = save_to_env(session_key)
            print(f"✅ 已保存到: {env_file}")
            print()
            print("使用方法：")
            print("  1. source .env")
            print("  2. ./start-with-pro.sh")
        else:
            print()
            print("请手动设置环境变量：")
            print(f"  export PROVIDER_TYPE=pro")
            print(f"  export CLAUDE_SESSION_KEY='{session_key}'")
    except KeyboardInterrupt:
        print("\n\n已取消")
        return 1

    print()
    print("=" * 60)
    print("🏮 现在可以启动解忧杂货铺了！")
    print("=" * 60)

    return 0

if __name__ == "__main__":
    sys.exit(main())
