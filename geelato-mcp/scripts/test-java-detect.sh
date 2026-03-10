#!/bin/bash
# 测试 Java 检测逻辑

echo "测试 Java 检测逻辑..."
echo ""

# 模拟脚本中的检测逻辑
if [ -x "/Library/Jav trae-sandbox 'cd /Users/simon/projects/geelato-projects/geelato-community-backend/geelato-mcp/scripts && ./test-java-detect.sh'
a/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java" ]; then
    JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
    echo "✓ 检测到标准 Java 21 安装"
elif [ -x "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java" ]; then
    JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home"
    echo "✓ 检测到 Zulu Java 21"
elif [ -x "/usr/libexec/java_home" ]; then
    JAVA_HOME_CANDIDATE=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -x "$JAVA_HOME_CANDIDATE/bin/java" ]; then
        JAVA_HOME="$JAVA_HOME_CANDIDATE"
        echo "✓ java_home 返回有效路径"
    else
        JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
        echo "⚠ java_home 返回的路径无效，使用默认路径"
    fi
else
    JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
    echo "⚠ 使用默认路径"
fi

echo ""
echo "最终 JAVA_HOME: $JAVA_HOME"
echo ""

if [ -x "$JAVA_HOME/bin/java" ]; then
    echo "✅ Java 验证成功!"
    echo "Java 版本：$($JAVA_HOME/bin/java -version 2>&1 | head -1)"
else
    echo "❌ Java 验证失败！"
    echo "路径不存在：$JAVA_HOME/bin/java"
fi
