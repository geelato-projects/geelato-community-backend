# Geelato MCP 构建修复说明

## 修复内容

### 1. 修复 JAVA_HOME 检测逻辑
**文件**: `scripts/build-and-run-dev.sh`

**问题**: 原有的 JAVA_HOME 检测逻辑不够健壮，导致构建失败

**修复**: 
- 优先使用 `/usr/libexec/java_home` 检测 Java 21
- 如果 Java 21 未找到，尝试检测其他版本
- 如果仍未找到，尝试常见安装路径
- 如果所有方法都失败，报错退出

### 2. 移除 FMS 模块从默认构建
**文件**: `pom.xml`

**修改**:
```xml
<modules>
    <module>geelato-mcp-common</module>
    <!-- <module>geelato-mcp-fms</module> FMS 模块已移除，如需使用请单独构建 -->
    <module>geelato-mcp-platform</module>
</modules>
```

### 3. 更新启动脚本移除 FMS 服务
**文件**: `scripts/start-dev.sh`

**修改**:
- 移除了 FMS 服务的启动代码
- 更新服务启动步骤从 `[1/3]` 改为 `[1/2]`

### 4. 修复数据库配置
**文件**: `scripts/build-and-run-dev.sh` 和 `scripts/start-dev.sh`

**修改**:
- 数据库 URL: `jdbc:mysql://127.0.0.1:3306/geelato`
- 用户名：`root`
- 密码：`root123`
- 增加连接超时配置：`connectTimeout=60000&socketTimeout=60000`

### 5. 更新 README 文档
**文件**: `README.md` 和 `scripts/README.md`

**修改**:
- 说明 FMS 模块需要单独构建
- 提供单独构建 FMS 的命令
- 更新启动脚本说明

## 构建验证

### 构建成功输出
```
[INFO] BUILD SUCCESS
[INFO] Total time:  4.184 s
[INFO] Finished at: 2026-03-15T23:37:46+08:00
✓ 构建完成
```

### 构建产物
- `geelato-mcp-common/target/geelato-mcp-common-1.0.0-SNAPSHOT.jar`
- `geelato-mcp-platform/target/geelato-mcp-platform-1.0.0-SNAPSHOT.jar`

## 使用方法

### 一键构建并启动
```bash
cd geelato-mcp
./scripts/build-and-run-dev.sh --skip-tests
```

### 仅启动服务（不构建）
```bash
./scripts/start-dev.sh
```

### 停止服务
```bash
./scripts/stop-dev.sh
```

## 已知问题

### 数据库连接问题
**现象**: 服务启动时无法连接到 Docker 中的 MySQL 数据库

**错误信息**:
```
Caused by: com.mysql.cj.exceptions.CJCommunicationsException: Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
```

**可能原因**:
1. macOS 与 Docker 容器之间的网络连接问题
2. MySQL 用户权限配置问题
3. JDBC 驱动与 MySQL 8.0 的兼容性问题

**临时解决方案**:
1. 确保 MySQL 容器正在运行：`docker ps | grep mysql`
2. 重启 MySQL 容器：`docker restart geelato-mysql`
3. 验证数据库连接：`docker exec geelato-mysql mysql -uroot -proot123 -e "SELECT 1"`

**建议**:
- 使用 Docker Compose 统一管理所有服务
- 或者在 macOS 主机上直接运行 MySQL 服务
- 或者使用 `host.docker.internal` 作为数据库主机名

## FMS 模块单独构建

如需使用 FMS 模块，请单独构建：

```bash
cd geelato-mcp-fms
mvn clean package
```

然后手动启动 FMS 服务：
```bash
java -jar target/geelato-mcp-fms-1.0.0-SNAPSHOT.jar --server.port=8084
```

## 测试验证

### 验证构建成功
```bash
./scripts/build-and-run-dev.sh --skip-tests
```

检查输出中是否包含：
- `BUILD SUCCESS`
- `✓ 构建完成`
- 服务启动成功日志

### 验证服务运行
```bash
# 检查端口占用
lsof -i :8081
lsof -i :3000

# 检查健康状态
curl http://localhost:8081/actuator/health
```

## 修改文件清单

1. `scripts/build-and-run-dev.sh` - 修复 JAVA_HOME 检测和数据库配置
2. `scripts/start-dev.sh` - 移除 FMS 服务启动，修复数据库配置
3. `pom.xml` - 注释掉 FMS 模块
4. `README.md` - 更新 FMS 模块说明
5. `scripts/README.md` - 更新启动脚本说明
