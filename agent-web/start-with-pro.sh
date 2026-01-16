#!/bin/bash

# 解忧杂货铺 - 启动脚本（使用Claude Pro会员）
# Soul Comfort Store - Startup Script (with Claude Pro)

echo "🏮 解忧杂货铺 - Soul Comfort Agent (Claude Pro Mode)"
echo "========================================================"
echo ""

# 检查Session Key
if [ -z "$CLAUDE_SESSION_KEY" ]; then
    echo "⚠️  警告：未检测到 CLAUDE_SESSION_KEY 环境变量"
    echo ""
    echo "📝 如何获取Session Key："
    echo "   1. 访问 https://claude.ai 并登录你的Pro账号"
    echo "   2. 按 F12 打开浏览器开发者工具"
    echo "   3. 点击 Application (应用程序) 标签"
    echo "   4. 左侧选择 Cookies -> https://claude.ai"
    echo "   5. 查找名为 'sessionKey' 的Cookie"
    echo "   6. 复制它的值"
    echo ""
    echo "设置方法："
    echo "   export CLAUDE_SESSION_KEY='your-session-key-here'"
    echo ""
    read -p "是否继续使用Mock模式？(y/n): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
    echo ""
    echo "📝 使用Mock模式启动（无真实AI回复）"
    export PROVIDER_TYPE=mock
else
    echo "✅ 检测到Session Key：${CLAUDE_SESSION_KEY:0:30}..."
    echo "🎭 使用Claude Pro会员模式"
    export PROVIDER_TYPE=pro
fi

echo ""

# 停止现有服务
echo "🔍 检查端口8080..."
if lsof -ti:8080 > /dev/null 2>&1; then
    echo "⚠️  端口8080被占用，正在停止..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null
    sleep 2
    echo "✅ 已停止现有服务"
fi

echo ""
echo "🚀 正在启动服务..."
echo "========================================================"
echo ""
echo "💡 提示："
echo "   - 使用Pro会员额度，无需额外付费"
echo "   - Session Key可能会过期，需要定期更新"
echo "   - 遵守Claude使用条款，适度使用"
echo ""
echo "========================================================"
echo ""

# 启动Spring Boot应用
mvn spring-boot:run
