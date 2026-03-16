#!/bin/bash

# Geelato MCP 构建并运行开发环境脚本
# 先构建项目，然后启动所有开发环境所需的服务

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 超时设置 (秒)
BUILD_TIMEOUT=600  # 构建超时 10 分钟
START_TIMEOUT=120  # 启动超时 2 分钟

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 捕获错误和中断
cleanup() {
    echo -e "${YELLOW}脚本中断，清理中...${NC}"
    exit 1
}
trap cleanup INT TERM

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    Geelato MCP 构建并启动开发环境${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 自动检测 JAVA_HOME
unset JAVA_HOME 2>/dev/null

# 优先使用 /usr/libexec/java_home 检测 Java 21
if [ -x "/usr/libexec/java_home" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "  通过 java_home 检测到 Java 21"
    else
        # 如果 Java 21 未找到，尝试检测其他版本
        JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
    fi
fi

# 如果仍未找到，尝试常见路径
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    if [ -x "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java" ]; then
        JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
    elif [ -x "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java" ]; then
        JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home"
    elif [ -x "/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home/bin/java" ]; then
        JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home"
    fi
fi

# 如果仍然未找到，报错退出
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo -e "${RED}错误：Java 未找到，请安装 JDK 21${NC}"
    exit 1
fi

export JAVA_HOME

echo -e "${CYAN}环境检查:${NC}"
echo "  Java 路径：$JAVA_HOME"
if [ -x "$JAVA_HOME/bin/java" ]; then
    echo "  Java 版本：$($JAVA_HOME/bin/java -version 2>&1 | head -1)"
else
    echo -e "${RED}错误：Java 未找到：$JAVA_HOME${NC}"
    exit 1
fi

# 检查 Maven 是否存在
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}错误：Maven 未安装，请先安装 Maven${NC}"
    exit 1
fi

echo "  Maven 版本：$(mvn -version 2>&1 | head -1)"

# 检查磁盘空间 (至少需要 1GB)
AVAILABLE_SPACE=$(df -k "$PROJECT_ROOT" | awk 'NR==2 {print $4}')
if [ "$AVAILABLE_SPACE" -lt 1048576 ]; then
    echo -e "${RED}错误：磁盘空间不足 (需要至少 1GB 可用空间)${NC}"
    exit 1
fi
echo "  可用磁盘空间：$((AVAILABLE_SPACE / 1024))MB"
echo ""

# 步骤 1: 构建项目
echo -e "${BLUE}[步骤 1/2] 构建项目...${NC}"

cd "$PROJECT_ROOT"

# 检查是否跳过测试
SKIP_TESTS=""
if [ "$1" == "--skip-tests" ] || [ "$1" == "-s" ]; then
    SKIP_TESTS="-DskipTests"
    echo -e "  测试模式：${YELLOW}跳过测试${NC}"
else
    echo -e "  测试模式：${CYAN}运行测试${NC}"
fi

# 清理旧日志
echo "  清理旧日志文件..."
rm -f /tmp/geelato-mcp-*.log /tmp/mcp-test-site.log 2>/dev/null || true

# 执行构建
echo "  开始构建..."
if mvn clean package $SKIP_TESTS; then
    echo -e "  ${GREEN}✓ 构建完成${NC}"
else
    echo -e "${RED}错误：构建失败${NC}"
    exit 1
fi

# 验证 JAR 文件是否生成
if [ ! -f "$PROJECT_ROOT/geelato-mcp-platform/target/geelato-mcp-platform-1.0.0-SNAPSHOT.jar" ]; then
    echo -e "${RED}错误：构建产物不存在${NC}"
    exit 1
fi

echo ""

# 步骤 2: 启动开发环境
echo -e "${BLUE}[步骤 2/2] 启动开发环境...${NC}"
echo ""

# 检查端口是否被占用，如果被占用则自动停止服务
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 0  # 端口被占用
    else
        return 1  # 端口可用
    fi
}

# 如果端口被占用，自动停止现有服务
if check_port 8081 || check_port 8084 || check_port 3000; then
    echo -e "${YELLOW}警告：检测到有服务正在运行，自动停止现有服务...${NC}"
    if [ -f "$SCRIPT_DIR/stop-dev.sh" ]; then
        "$SCRIPT_DIR/stop-dev.sh"
    else
        echo -e "${RED}错误：停止脚本不存在${NC}"
        exit 1
    fi
    echo ""
fi

# 设置数据库配置（使用 127.0.0.1 连接 Docker 暴露的端口）
# 增加 connectTimeout 和 socketTimeout 以提高连接稳定性
: ${GEELATO_PRIMARY_JDBCURL:="jdbc:mysql://127.0.0.1:3306/geelato?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&connectTimeout=60000&socketTimeout=60000"}
: ${GEELATO_PRIMARY_JDBCUSER:="root"}
: ${GEELATO_PRIMARY_JDBCPASSWORD:="root123"}
export GEELATO_PRIMARY_JDBCURL GEELATO_PRIMARY_JDBCUSER GEELATO_PRIMARY_JDBCPASSWORD

# 启动开发环境
echo "  启动开发环境..."
"$SCRIPT_DIR/start-dev.sh"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}    构建并启动完成！${NC}"
echo -e "${GREEN}========================================${NC}"
