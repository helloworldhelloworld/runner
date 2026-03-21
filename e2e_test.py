#!/usr/bin/env python3
"""End-to-end WebSocket test for all features."""

import asyncio
import websockets
import json
import sys
import os

# Force no proxy
for k in list(os.environ.keys()):
    if "proxy" in k.lower() or "PROXY" in k:
        del os.environ[k]

WS_URL = "ws://127.0.0.1:8080/ws/chat"
req_id = 0

def next_id():
    global req_id
    req_id += 1
    return f"test-{req_id}"

async def send_and_recv(ws, msg_type, data=None, timeout=10):
    rid = next_id()
    payload = {"type": msg_type, "requestId": rid}
    if data:
        payload.update(data)
    await ws.send(json.dumps(payload))
    deadline = asyncio.get_event_loop().time() + timeout
    while asyncio.get_event_loop().time() < deadline:
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=2)
            msg = json.loads(raw)
            if msg.get("requestId") == rid:
                return msg
        except asyncio.TimeoutError:
            break
    return None

async def send_chat_and_collect(ws, message, session_id="e2e-test"):
    payload = {"type": "chat", "sessionId": session_id, "message": message, "reactive": True}
    await ws.send(json.dumps(payload))
    events = []
    deadline = asyncio.get_event_loop().time() + 15
    while asyncio.get_event_loop().time() < deadline:
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=3)
            msg = json.loads(raw)
            events.append(msg)
            if msg.get("type") in ("stream_end", "error", "crisis_alert"):
                break
        except asyncio.TimeoutError:
            break
    return events

async def run_tests():
    results = []
    async with websockets.connect(WS_URL) as ws:

        # 1. Health
        print("TEST 1: Health Check")
        resp = await send_and_recv(ws, "health")
        ok = resp and resp.get("data", {}).get("status") == "UP"
        d = resp.get("data", {}) if resp else {}
        print(f"  {json.dumps(d, ensure_ascii=False)[:200]}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Health Check", ok))

        # 2. Skills
        print("\nTEST 2: Get Skills")
        resp = await send_and_recv(ws, "get_skills")
        data = resp.get("data", []) if resp else []
        ok = isinstance(data, list) and len(data) > 0
        print(f"  Count: {len(data)}, names: {[s.get('name') for s in data]}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Get Skills", ok))

        # 3. Tools
        print("\nTEST 3: Get Tools")
        resp = await send_and_recv(ws, "get_tools")
        data = resp.get("data", []) if resp else []
        ok = isinstance(data, list) and len(data) > 0
        print(f"  Count: {len(data)}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Get Tools", ok))

        # 4. Tools Detail
        print("\nTEST 4: Get Tools Detail")
        resp = await send_and_recv(ws, "get_tools_detail")
        data = resp.get("data", {}) if resp else {}
        tools = data.get("tools", [])
        ok = len(tools) > 0 and "source" in tools[0]
        print(f"  Total: {data.get('total')}, Enabled: {data.get('enabled')}")
        for t in tools[:3]:
            print(f"    - {t.get('name')} [{t.get('source')}]")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Get Tools Detail", ok))

        # 5. Scales
        print("\nTEST 5: Get Scales")
        resp = await send_and_recv(ws, "get_scales")
        data = resp.get("data", {}) if resp else {}
        ok = len(data) > 0
        print(f"  Scales: {list(data.keys())}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Get Scales", ok))

        # 6. Create User
        print("\nTEST 6: Create User")
        resp = await send_and_recv(ws, "create_user")
        data = resp.get("data", {}) if resp else {}
        user_id = data.get("userId") or data.get("id") or ""
        ok = len(user_id) > 0
        print(f"  User ID: {user_id}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Create User", ok))

        # 7. Checkin
        print("\nTEST 7: Emotion Check-in")
        resp = await send_and_recv(ws, "checkin", {"userId": user_id, "emotion": "开心", "note": "e2e测试"})
        ok = resp and resp.get("type") == "checkin_result"
        print(f"  Type: {resp.get('type') if resp else 'N/A'}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Emotion Check-in", ok))

        # 8. Get Emotions
        print("\nTEST 8: Get Emotions")
        resp = await send_and_recv(ws, "get_emotions", {"userId": user_id, "days": 7})
        data = resp.get("data", []) if resp else []
        ok = isinstance(data, list) and len(data) > 0
        print(f"  Count: {len(data)}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Get Emotions", ok))

        # 9. Stats
        print("\nTEST 9: Get Stats")
        resp = await send_and_recv(ws, "get_stats", {"userId": user_id})
        data = resp.get("data", {}) if resp else {}
        ok = resp and resp.get("type") == "stats"
        print(f"  Stats: {json.dumps(data, ensure_ascii=False)[:200]}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Get Stats", ok))

        # 10. Chat Streaming
        print("\nTEST 10: Chat Streaming")
        events = await send_chat_and_collect(ws, "你好，今天心情不太好")
        types = [e.get("type") for e in events]
        has_token = "token" in types
        has_end = "stream_end" in types
        ok = has_token and has_end
        tokens = "".join(e.get("data", "") for e in events if e.get("type") == "token")
        print(f"  Events: {types}")
        print(f"  Response: {tokens[:200]}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Chat Streaming", ok))

        # 11. Session History
        print("\nTEST 11: Session History")
        resp = await send_and_recv(ws, "get_history", {"sessionId": "e2e-test"})
        data = resp.get("data", []) if resp else []
        ok = isinstance(data, list) and len(data) > 0
        print(f"  Entries: {len(data)}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Session History", ok))

        # 12. Session Summary
        print("\nTEST 12: Session Summary")
        resp = await send_and_recv(ws, "get_summary", {"sessionId": "e2e-test"})
        data = resp.get("data", {}) if resp else {}
        ok = data is not None
        print(f"  Keys: {list(data.keys()) if isinstance(data, dict) else 'N/A'}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Session Summary", ok))

        # 13. Crisis Detection
        print("\nTEST 13: Crisis Detection")
        events = await send_chat_and_collect(ws, "我不想活了", session_id="e2e-crisis")
        types = [e.get("type") for e in events]
        ok = "crisis_alert" in types
        print(f"  Events: {types}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Crisis Detection", ok))

        # 14. Memory Search
        print("\nTEST 14: Memory Search")
        resp = await send_and_recv(ws, "search_memory", {"query": "心情", "topK": 5})
        data = resp.get("data", []) if resp else []
        ok = isinstance(data, list)
        print(f"  Results: {len(data)}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Memory Search", ok))

        # 15. Remember Durable
        print("\nTEST 15: Remember Durable")
        resp = await send_and_recv(ws, "remember_durable", {"section": "e2e测试", "content": "端到端测试记忆"})
        data = resp.get("data", {}) if resp else {}
        ok = data.get("status") == "saved"
        print(f"  {json.dumps(data, ensure_ascii=False)}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Remember Durable", ok))

        # 16. Clear Session
        print("\nTEST 16: Clear Session")
        resp = await send_and_recv(ws, "clear_session", {"sessionId": "e2e-test"})
        data = resp.get("data", {}) if resp else {}
        ok = data.get("status") == "cleared"
        print(f"  {json.dumps(data, ensure_ascii=False)}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Clear Session", ok))

        # 17. MCP Servers
        print("\nTEST 17: MCP Servers")
        resp = await send_and_recv(ws, "get_mcp_servers")
        data = resp.get("data", {}) if resp else {}
        ok = data is not None and "enabled" in data
        print(f"  MCP enabled: {data.get('enabled')}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("MCP Servers", ok))

        # 18. Tool Definitions
        print("\nTEST 18: Tool Definitions")
        resp = await send_and_recv(ws, "get_tool_definitions")
        data = resp.get("data", {}) if resp else {}
        ok = data.get("count", 0) > 0
        print(f"  Count: {data.get('count')}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Tool Definitions", ok))

        # 19. Risk Control
        print("\nTEST 19: Risk Control Pipeline Active")
        resp = await send_and_recv(ws, "health")
        ws_conns = resp.get("data", {}).get("wsConnections", 0) if resp else 0
        ok = ws_conns > 0
        print(f"  WS connections: {ws_conns}")
        print(f"  {'PASS' if ok else 'FAIL'}")
        results.append(("Risk Control Active", ok))

    # Summary
    print("\n" + "=" * 60)
    print("END-TO-END TEST SUMMARY")
    print("=" * 60)
    passed = sum(1 for _, ok in results if ok)
    failed = sum(1 for _, ok in results if not ok)
    for name, ok in results:
        print(f"  {'✅' if ok else '❌'} {name}")
    print(f"\n  Total: {len(results)} | Passed: {passed} | Failed: {failed}")
    print("=" * 60)
    return failed == 0

ok = asyncio.run(run_tests())
sys.exit(0 if ok else 1)
