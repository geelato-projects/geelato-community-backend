#!/bin/bash

# Geelato MCP 生产环境停止脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    停止 Geelato MCP 生产环境服务${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 函数：停止服务
stop_service() {
    local service_name=$1
    local pid_file="/var/run/${service_name}.pid"
    
    if [ -f "$pid_file" ]; then
        local pid=$(cat $pid_file)
        if ps -p $pid > /dev/null 2>&1; then
            echo -n "停止 $service_name (PID: $pid) ... "
            kill $pid
            sleep 2
            
            # 强制停止
            if ps -p $pid > /dev/null 2>&1; then
                kill -9 $pid
                echo -e "${RED}强制停止${NC}"
            else
                echo -e "${GREEN}已停止${NC}"
            fi
        else
            echo -e "${YELLOW}$service_name 未运行${NC}"
        fi
        rm -f $pid_file
    else
        echo -e "${YELLOW}$service_name PID 文件不存在${NC}"
    fi
}

# 停止服务
echo -e "${YELLOW}正在停止服务...${NC}"
echo ""

stop_service "geelato-mcp-platform"

echo ""
echo -e "${GREEN}所有服务已停止${NC}"
echo ""
