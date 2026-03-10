#!/bin/bash

# Geelato MCP 生产环境启动脚本
# 仅启动核心服务 (不启动测试站点和 FMS)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 自动检测 JAVA_HOME（忽略环境中已设置的值，强制重新检测）
unset JAVA_HOME 2>/dev/null

# 优先检查标准安装路径
if [ -x "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java" ]; then
    JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
elif [ -x "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java" ]; then
    JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home"
elif [ -x "/usr/libexec/java_home" ]; then
    # 尝试使用 java_home，但需要验证路径是否有效
    JAVA_HOME_CANDIDATE=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -x "$JAVA_HOME_CANDIDATE/bin/java" ]; then
        JAVA_HOME="$JAVA_HOME_CANDIDATE"
    else
        # java_home 返回的路径无效，使用默认路径
        JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
    fi
else
    JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
fi
export JAVA_HOME

# 配置变量 (可通过环境变量覆盖)
MCP_PLATFORM_PORT=${MCP_PLATFORM_PORT:-8081}
MCP_API_KEY=${MCP_API_KEY:-prod-api-key-secure-123}
JWT_SIGN_KEY=${JWT_SIGN_KEY:-5A1332068BA9FD17}
AUTH_TYPE=${AUTH_TYPE:-hybrid}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    Geelato MCP 生产环境启动${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "项目目录：$PROJECT_ROOT"
echo "Java 路径：$JAVA_HOME"
if [ -x "$JAVA_HOME/bin/java" ]; then
    echo "Java 版本：$($JAVA_HOME/bin/java -version 2>&1 | head -1)"
else
    echo -e "${RED}错误：Java 未找到：$JAVA_HOME${NC}"
    exit 1
fi
echo ""
echo -e "${YELLOW}配置信息:${NC}"
echo "  Platform 端口：$MCP_PLATFORM_PORT"
echo "  认证类型：$AUTH_TYPE"
echo ""

# 函数：检查端口是否被占用
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 0  # 端口被占用
    else
        return 1  # 端口可用
    fi
}

# 函数：启动服务
start_service() {
    local service_name=$1
    local service_path=$2
    local port=$3
    local extra_args=$4
    
    echo -e "${YELLOW}启动服务：$service_name (端口：$port)${NC}"
    
    # 检查端口
    if check_port $port; then
        echo -e "${RED}错误：端口 $port 已被占用${NC}"
        return 1
    fi
    
    # 检查 jar 文件是否存在
    local jar_file="$service_path/target/${service_name}-1.0.0-SNAPSHOT.jar"
    if [ ! -f "$jar_file" ]; then
        echo -e "${RED}错误：JAR 文件不存在：$jar_file${NC}"
        echo "请先执行：mvn clean package -DskipTests"
        return 1
    else
        local jar_size=$(du -h "$jar_file" | cut -f1)
        echo "JAR 文件：$jar_file (大小：$jar_size)"
    fi
    
    # 启动服务
    cd "$service_path"
    echo "启动命令：$JAVA_HOME/bin/java -jar $jar_file --server.port=$port"
    
    nohup $JAVA_HOME/bin/java -jar \
        -Xms512m -Xmx2g \
        -XX:+UseG1GC \
        $jar_file \
        --server.port=$port \
        --spring.profiles.active=prod \
        --geelato.mcp.security.auth-type=$AUTH_TYPE \
        --geelato.mcp.security.api-key.keys=$MCP_API_KEY \
        --geelato.mcp.security.jwt.sign-key="$JWT_SIGN_KEY" \
        $extra_args \
        > /var/log/${service_name}.log 2>&1 &
    
    local pid=$!
    echo $pid > /var/run/${service_name}.pid
    
    echo -n "等待服务启动"
    local count=0
    while [ $count -lt 120 ]; do
        # 每 10 秒显示一个点
        if [ $((count % 10)) -eq 0 ]; then
            echo -n "."
        fi
        
        # 检查端口
        if check_port $port; then
            echo -e "${GREEN} ✓ 已启动 (PID: $pid, 耗时：$((count + 1))秒)${NC}"
            return 0
        fi
        
        # 检查进程是否还在运行
        if ! ps -p $pid > /dev/null 2>&1; then
            echo -e "${RED} ✗ 进程已退出${NC}"
            echo "错误日志:"
            tail -n 10 /var/log/${service_name}.log 2>/dev/null | sed 's/^/  /'
            return 1
        fi
        
        sleep 2
        count=$((count + 1))
    done
    
    echo -e "${RED} ✗ 启动超时 (120 秒)${NC}"
    echo "最近日志:"
    tail -n 10 /var/log/${service_name}.log 2>/dev/null | sed 's/^/  /'
    return 1
}

# 启动 MCP Platform 服务
echo -e "${BLUE}启动 MCP Platform 服务${NC}"
start_service "geelato-mcp-platform" "$PROJECT_ROOT/geelato-mcp-platform" $MCP_PLATFORM_PORT ""
echo ""

# 显示启动信息
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    生产环境服务启动完成${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${GREEN}服务信息:${NC}"
echo "  MCP Platform: http://localhost:$MCP_PLATFORM_PORT"
echo ""
echo -e "${YELLOW}日志文件:${NC}"
echo "  - /var/log/geelato-mcp-platform.log"
echo ""
echo -e "${YELLOW}停止服务:${NC}"
echo "  sudo systemctl stop geelato-mcp-platform"
echo "  或：kill \$(cat /var/run/geelato-mcp-platform.pid)"
echo ""
echo -e "${YELLOW}查看日志:${NC}"
echo "  tail -f /var/log/geelato-mcp-platform.log"
echo ""
