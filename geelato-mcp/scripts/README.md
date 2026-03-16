# Geelato MCP 脚本说明

本目录包含用于构建、启动和停止 Geelato MCP 服务的脚本。

## 脚本列表

### 1. build-and-run-dev.sh (新增)
**用途**: 构建项目并启动开发环境 (一键式构建 + 启动)

**使用方法**:
```bash
# 运行测试并构建启动
./build-and-run-dev.sh

# 跳过测试构建启动 (推荐，速度更快)
./build-and-run-dev.sh --skip-tests
# 或
./build-and-run-dev.sh -s
```

**执行流程**:
1. 检查 Java 和 Maven 环境
2. 执行 `mvn clean package` 构建所有模块
3. 检查是否有服务正在运行，如有则自动停止
4. 调用 `start-dev.sh` 启动开发环境

**适用场景**:
- 本地开发测试
- 代码修改后重新构建并启动
- CI/CD 流程中的构建测试环节

---

### 2. start-dev.sh
**用途**: 启动开发环境 (不构建)

**启动的服务**:
- MCP Platform (端口 8081)
- MCP FMS (端口 8084)
- MCP Test Site (端口 3000)

**注意**: FMS 模块目前返回静态测试数据，用于开发测试。

**使用方法**:
```bash
# 使用默认数据库配置启动
./start-dev.sh

# 自定义数据库配置
GEELATO_PRIMARY_JDBCURL="jdbc:mysql://your-host:3306/your-db" \
GEELATO_PRIMARY_JDBCUSER="your-user" \
GEELATO_PRIMARY_JDBCPASSWORD="your-password" \
./start-dev.sh
```

**适用场景**:
- 已经构建过，只需要启动服务
- 快速重启服务 (不重新构建)

---

### 3. start-prod.sh
**用途**: 启动生产环境 (仅 Platform 核心服务)

**启动的服务**:
- MCP Platform (端口 8081)

**使用方法**:
```bash
./start-prod.sh
```

**日志位置**: `~/geelato-logs/geelato-mcp-platform.log`

**适用场景**:
- 生产环境部署
- 仅需要 Platform 服务的场景

---

### 4. stop-dev.sh
**用途**: 停止开发环境的所有服务

**使用方法**:
```bash
./stop-dev.sh
```

**适用场景**:
- 停止开发环境
- 释放端口占用

---

### 5. stop-prod.sh
**用途**: 停止生产环境服务

**使用方法**:
```bash
./stop-prod.sh
```

---

## 快速开始

### 首次使用或代码修改后
```bash
cd /Users/simon/projects/geelato-projects/geelato-community-backend/geelato-mcp/scripts
./build-and-run-dev.sh --skip-tests
```

### 日常启动 (已构建过)
```bash
cd /Users/simon/projects/geelato-projects/geelato-community-backend/geelato-mcp/scripts
./start-dev.sh
```

### 停止服务
```bash
cd /Users/simon/projects/geelato-projects/geelato-community-backend/geelato-mcp/scripts
./stop-dev.sh
```

---

## 配置说明

### 数据库配置
默认数据库配置:
- URL: `jdbc:mysql://localhost:3306/platform`
- 用户：`geelato`
- 密码：`geelato123`

如需修改，通过环境变量设置:
```bash
export GEELATO_PRIMARY_JDBCURL="jdbc:mysql://your-host:3306/your-db"
export GEELATO_PRIMARY_JDBCUSER="your-user"
export GEELATO_PRIMARY_JDBCPASSWORD="your-password"
```

### API Key 配置
开发环境默认 API Keys:
- `test-api-key-123456`
- `geelato-mcp-key-789`

生产环境请设置环境变量:
```bash
export MCP_API_KEYS="your-secure-api-key"
export MCP_JWT_SIGN_KEY="your-secure-jwt-key-at-least-32-chars"
```

---

## 服务访问

### 开发环境
- **MCP Platform**: http://localhost:8081
- **MCP FMS**: http://localhost:8084
- **Test Site**: http://localhost:3000

### 日志文件
- `/tmp/geelato-mcp-platform.log`
- `/tmp/geelato-mcp-fms.log`
- `/tmp/mcp-test-site.log`

---

## 常见问题

### 1. 端口被占用
如果端口被占用，先执行:
```bash
./stop-dev.sh
```

### 2. 数据库连接失败
检查 Docker 容器是否运行:
```bash
docker ps | grep geelato-mysql
```

如果未运行，启动数据库:
```bash
docker start geelato-mysql
```

### 3. 构建失败
确保 Maven 和 Java 21 已安装:
```bash
mvn -version
java -version
```

---

## 注意事项

1. **构建脚本会自动停止现有服务** - `build-and-run-dev.sh` 会先停止所有正在运行的服务，然后重新启动
2. **数据库配置** - 确保数据库容器正在运行且配置正确
3. **端口占用** - 确保 8081, 8084, 3000 端口可用
4. **生产环境** - 生产环境请使用 `start-prod.sh` 并设置安全的环境变量
