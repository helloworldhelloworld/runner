#!/bin/bash

# 解忧杂货铺 - 启动脚本（使用Claude API）
# Soul Comfort Store - Startup Script (with Claude API)

echo "🏮 解忧杂货铺 - Soul Comfort Agent"
echo "=================================="
echo ""

# 检查API密钥
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "⚠️  警告：未检测到 ANTHROPIC_API_KEY 环境变量"
    echo ""
    echo "请设置API密钥："
    echo "  export ANTHROPIC_API_KEY='sk-ant-your-api-key-here'"
    echo ""
    echo "或者将继续使用Mock模式（3秒后）..."
    sleep 3
    echo ""
    echo "📝 使用Mock模式启动（无真实AI回复）"
else
    echo "✅ 检测到API密钥：${ANTHROPIC_API_KEY:0:20}..."
    echo "📡 将连接到Claude API"
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
echo "=================================="
echo ""

# 启动Spring Boot应用
mvn spring-boot:run
