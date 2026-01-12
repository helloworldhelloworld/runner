#!/usr/bin/env python3
"""Test script for Soul Comfort Agent"""

import urllib.request
import json
import sys

BASE_URL = "http://localhost:8080/api"
SESSION_ID = "test-user-001"

def test_chat(message):
    """Send a chat message to the soul comfort agent"""
    data = json.dumps({
        "message": message,
        "sessionId": SESSION_ID,
        "soulComfortMode": True
    }).encode('utf-8')

    req = urllib.request.Request(
        f"{BASE_URL}/chat",
        data=data,
        headers={'Content-Type': 'application/json'}
    )

    with urllib.request.urlopen(req) as response:
        return json.loads(response.read().decode('utf-8'))

def get_session_summary():
    """Get session summary"""
    req = urllib.request.Request(f"{BASE_URL}/session/{SESSION_ID}/summary")
    with urllib.request.urlopen(req) as response:
        return json.loads(response.read().decode('utf-8'))

def print_response(title, data):
    """Pretty print response"""
    print(f"\n{'='*60}")
    print(f"{title}")
    print('='*60)
    print(json.dumps(data, indent=2, ensure_ascii=False))
    print()

if __name__ == "__main__":
    # Test 1: First emotional message (anxiety)
    print("\n🏮 测试 1: 工作压力（焦虑情绪）")
    result1 = test_chat("我今天被领导批评了，心里很难受...")
    print_response("回复:", result1)

    # Test 2: Second message (showing reflection)
    print("\n🏮 测试 2: 后续对话（测试记忆）")
    result2 = test_chat("是的，我觉得自己做得不够好")
    print_response("回复:", result2)

    # Test 3: Get session summary
    print("\n🏮 测试 3: 获取会话摘要")
    summary = get_session_summary()
    print_response("会话摘要:", summary)

    # Test 4: Change topic (career choice)
    print("\n🏮 测试 4: 转换话题（职业选择）")
    result3 = test_chat("我不知道该不该换工作...")
    print_response("回复:", result3)

    # Test 5: Final session summary
    print("\n🏮 测试 5: 最终会话摘要")
    final_summary = get_session_summary()
    print_response("最终会话摘要:", final_summary)
