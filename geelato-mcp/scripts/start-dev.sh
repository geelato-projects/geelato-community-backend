#!/bin/bash

# Geelato MCP 开发环境启动脚本
# 启动所有开发环境所需的服务

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

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    Geelato MCP 开发环境启动${NC}"
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
        echo -e "${YELLOW}警告：端口 $port 已被占用，跳过启动 $service_name${NC}"
        return 0
    fi
    
    # 检查 jar 文件是否存在
    local jar_file="$service_path/target/${service_name}-1.0.0-SNAPSHOT.jar"
    if [ ! -f "$jar_file" ]; then
        echo -e "${YELLOW}JAR 文件不存在，先执行构建：$service_name${NC}"
        cd "$service_path"
        mvn clean package -DskipTests
        if [ $? -ne 0 ]; then
            echo -e "${RED}构建失败：$service_name${NC}"
            return 1
        fi
        echo -e "${GREEN}构建完成：$service_name${NC}"
    else
        local jar_size=$(du -h "$jar_file" | cut -f1)
        echo "JAR 文件：$jar_file (大小：$jar_size)"
    fi
    
    # 启动服务
    cd "$service_path"
    echo "启动命令：$JAVA_HOME/bin/java -jar $jar_file --server.port=$port"
    
    # 使用 Java 系统属性传递数据库配置 (比环境变量更可靠)
    nohup $JAVA_HOME/bin/java -jar \
        -Xms256m -Xmx1g \
        -Dspring.datasource.primary.jdbc-url="$GEELATO_PRIMARY_JDBCURL" \
        -Dspring.datasource.primary.username="$GEELATO_PRIMARY_JDBCUSER" \
        -Dspring.datasource.primary.password="$GEELATO_PRIMARY_JDBCPASSWORD" \
        $jar_file \
        --server.port=$port \
        $extra_args \
        > /tmp/${service_name}.log 2>&1 &
    
    local pid=$!
    echo $pid > /tmp/${service_name}.pid
    
    echo -n "等待服务启动"
    local count=0
    local dots=""
    while [ $count -lt 60 ]; do
        # 每 5 秒显示一个点
        if [ $((count % 5)) -eq 0 ]; then
            echo -n "."
        fi
        
        # 检查端口
        if check_port $port; then
            echo -e "${GREEN} ✓ 已启动 (PID: $pid, 耗时：$((count + 1))秒)${NC}"
            
            # 显示启动日志最后 3 行
            echo "最近日志:"
            tail -n 3 /tmp/${service_name}.log 2>/dev/null | sed 's/^/  /'
            return 0
        fi
        
        # 检查进程是否还在运行
        if ! ps -p $pid > /dev/null 2>&1; then
            echo -e "${RED} ✗ 进程已退出${NC}"
            echo "错误日志:"
            tail -n 10 /tmp/${service_name}.log 2>/dev/null | sed 's/^/  /'
            return 1
        fi
        
        sleep 1
        count=$((count + 1))
    done
    
    echo -e "${RED} ✗ 启动超时 (60 秒)${NC}"
    echo "最近日志:"
    tail -n 10 /tmp/${service_name}.log 2>/dev/null | sed 's/^/  /'
    return 1
}

# 函数：启动前端服务
start_frontend() {
    local service_name=$1
    local service_path=$2
    local port=$3
    
    echo -e "${YELLOW}启动前端服务：$service_name (端口：$port)${NC}"
    
    cd "$service_path"
    
    # 检查是否已安装依赖
    if [ ! -d "node_modules" ]; then
        echo "安装依赖..."
        pnpm install
    fi
    
    # 启动开发服务器
    nohup pnpm dev > /tmp/${service_name}.log 2>&1 &
    local pid=$!
    echo $pid > /tmp/${service_name}.pid
    
    # 等待服务启动
    echo -n "等待服务启动..."
    local count=0
    while [ $count -lt 30 ]; do
        if check_port $port; then
            echo -e "${GREEN} ✓ 已启动 (PID: $pid)${NC}"
            return 0
        fi
        sleep 1
        count=$((count + 1))
        echo -n "."
    done
    
    echo -e "${RED} ✗ 启动超时${NC}"
    return 1
}

# 1. 启动 geelato-mcp-platform 服务
echo -e "${BLUE}[1/2] 启动 MCP Platform 服务${NC}"

# 数据库配置 (从环境变量读取，如未设置则使用默认值)
# 生产环境请设置以下环境变量：
# export GEELATO_PRIMARY_JDBCURL="jdbc:mysql://your-host:3306/your-db"
# export GEELATO_PRIMARY_JDBCUSER="your-username"
# export GEELATO_PRIMARY_JDBCPASSWORD="your-password"
# 使用 127.0.0.1 连接 Docker 暴露的 MySQL 端口，增加超时时间提高稳定性
: ${GEELATO_PRIMARY_JDBCURL:="jdbc:mysql://127.0.0.1:3306/geelato?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&connectTimeout=60000&socketTimeout=60000"}
: ${GEELATO_PRIMARY_JDBCUSER:="root"}
: ${GEELATO_PRIMARY_JDBCPASSWORD:="root123"}
export GEELATO_PRIMARY_JDBCURL GEELATO_PRIMARY_JDBCUSER GEELATO_PRIMARY_JDBCPASSWORD

# MCP 安全配置 (生产环境请设置环境变量 MCP_JWT_SIGN_KEY)
JWT_SIGN_KEY="${MCP_JWT_SIGN_KEY:-change-me-in-production-at-least-32-chars}"
PLATFORM_ARGS="--geelato.mcp.security.auth-type=hybrid \
    --geelato.mcp.security.api-key.keys=${MCP_API_KEYS:-test-api-key-123456,geelato-mcp-key-789} \
    --geelato.mcp.security.jwt.sign-key=\"$JWT_SIGN_KEY\""

start_service "geelato-mcp-platform" "$PROJECT_ROOT/geelato-mcp-platform" 8081 "$PLATFORM_ARGS"
echo ""

# 2. 启动前端服务
echo -e "${BLUE}[2/2] 启动 MCP 测试站点${NC}"

# 3. 启动 mcp-test-site 前端 (如果存在)
if [ -d "$PROJECT_ROOT/mcp-test-site" ]; then
    echo -e "${BLUE}[3/3] 启动 MCP 测试站点${NC}"
    start_frontend "mcp-test-site" "$PROJECT_ROOT/mcp-test-site" 3050
    echo ""
else
    echo -e "${YELLOW}跳过：mcp-test-site 目录不存在${NC}"
    echo ""
fi

# 显示启动信息
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    所有服务启动完成${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${GREEN}服务列表:${NC}"
echo "  1. MCP Platform: http://localhost:8081"
echo "  2. MCP FMS:      http://localhost:8084"
echo "  3. Test Site:    http://localhost:3000"
echo ""
echo -e "${YELLOW}日志文件:${NC}"
echo "  - /tmp/geelato-mcp-platform.log"
echo "  - /tmp/geelato-mcp-fms.log"
echo "  - /tmp/mcp-test-site.log"
echo ""
echo -e "${YELLOW}停止服务:${NC}"
echo "  kill \$(cat /tmp/geelato-mcp-platform.pid)"
echo "  kill \$(cat /tmp/geelato-mcp-fms.pid)"
echo "  kill \$(cat /tmp/mcp-test-site.pid)"
echo ""
echo -e "${YELLOW}或使用：${NC}./scripts/stop-dev.sh"
echo ""
