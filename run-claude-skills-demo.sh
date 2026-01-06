#!/bin/bash

# Claude Skills Demo Runner
# This script demonstrates real Claude API calls with tool/function calling

echo "=== Claude Skills Demo Runner ==="
echo ""

# Check if API key is set
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "Error: ANTHROPIC_API_KEY environment variable not set"
    echo ""
    echo "Please set your Claude API key:"
    echo "  export ANTHROPIC_API_KEY='sk-ant-...'"
    echo ""
    echo "Or run this script with:"
    echo "  ANTHROPIC_API_KEY='sk-ant-...' ./run-claude-skills-demo.sh"
    exit 1
fi

echo "✓ API key found"
echo ""

cd agent-kernel

# Compile if needed
echo "📦 Compiling..."
mvn compile -q

# Run the demo
echo ""
echo "🚀 Running Claude Skills Demo..."
echo ""

mvn exec:java \
    -Dexec.mainClass="com.lightweightai.kernel.demo.ClaudeSkillsDemo" \
    -Dexec.classpathScope=test \
    -q

echo ""
echo "✓ Demo complete"
