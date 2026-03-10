# Geelato MCP 脚本目录

本目录包含 Geelato MCP 项目的启动和管理脚本。

## 脚本列表

### 开发环境

- **start-dev.sh** - 启动开发环境所有服务
  - 启动 geelato-mcp-platform (端口 8081)
  - 启动 geelato-mcp-fms (端口 8084)
  - 启动 mcp-test-site (端口 3000)
  
- **stop-dev.sh** - 停止开发环境所有服务

### 生产环境

- **start-prod.sh** - 启动生产环境核心服务
  - 仅启动 geelato-mcp-platform
  
- **stop-prod.sh** - 停止生产环境服务

### 测试脚本

- **test-api.sh** - MCP 服务 API 接口测试
  - 测试认证功能
  - 测试工具调用
  - 测试错误处理

## 使用方法

### 开发环境

```bash
# 启动所有服务
./scripts/start-dev.sh

# 停止所有服务
./scripts/stop-dev.sh
```

### 生产环境

```bash
# 启动服务
./scripts/start-prod.sh

# 停止服务
./scripts/stop-prod.sh

# 查看日志
tail -f /var/log/geelato-mcp-platform.log
```

### API 测试

```bash
# 测试 Platform 服务
export MCP_BASE_URL=http://localhost:8081
export API_KEY=test-api-key-123456
./scripts/test-api.sh

# 测试 FMS 服务
export MCP_BASE_URL=http://localhost:8084
./scripts/test-api.sh
```

## 环境变量

### 开发环境

- `JAVA_HOME` - Java 安装路径 (默认：/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home)

### 生产环境

- `MCP_PLATFORM_PORT` - Platform 服务端口 (默认：8081)
- `MCP_API_KEY` - API Key (默认：prod-api-key-secure-123)
- `JWT_SIGN_KEY` - JWT 签名密钥 (默认：5A1332068BA9FD17)
- `AUTH_TYPE` - 认证类型 (默认：hybrid)

## 日志位置

### 开发环境

- `/tmp/geelato-mcp-platform.log`
- `/tmp/geelato-mcp-fms.log`
- `/tmp/mcp-test-site.log`

### 生产环境

- `/var/log/geelato-mcp-platform.log`

## PID 文件

### 开发环境

- `/tmp/geelato-mcp-platform.pid`
- `/tmp/geelato-mcp-fms.pid`
- `/tmp/mcp-test-site.pid`

### 生产环境

- `/var/run/geelato-mcp-platform.pid`

## 注意事项

1. 生产环境脚本需要 root 权限 (用于写入 /var/log 和 /var/run)
2. 确保端口未被占用
3. 生产环境请配置防火墙规则
4. 建议配置系统服务 (systemd) 实现开机自启
