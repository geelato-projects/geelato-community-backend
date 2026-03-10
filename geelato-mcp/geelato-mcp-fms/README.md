# Geelato MCP FMS 模块说明

## 概述

`geelato-mcp-fms` (Fleet Management System) 是合并了原有的 `geelato-mcp-order` (订单服务) 和 `geelato-mcp-logistics` (物流服务) 后的统一 MCP 服务模块。

## 模块结构

```
geelato-mcp-fms/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── cn/geelato/mcp/fms/
│   │   │       ├── McpFmsApplication.java          # 主启动类
│   │   │       └── tool/
│   │   │           ├── OrderQueryTool.java         # 订单查询工具
│   │   │           └── ContainerQueryTool.java     # 集装箱查询工具
│   │   └── resources/
│   │       └── application.properties              # 应用配置
│   └── test/
│       ├── java/
│       │   └── cn/geelato/mcp/fms/tool/
│       │       ├── OrderQueryToolTest.java         # 订单工具测试
│       │       └── ContainerQueryToolTest.java     # 集装箱工具测试
│       └── resources/
│           └── application-test.yml                # 测试配置
└── target/
```

## 功能特性

### 1. 订单查询功能 (OrderQueryTool)
- `queryOrderByNo(String orderNo)` - 根据订单号查询订单信息
- `queryUserOrders(String userId)` - 查询用户的所有订单
- `queryOrderStatistics(String dateRange)` - 查询订单统计信息

### 2. 物流查询功能 (ContainerQueryTool)
- `queryContainerLocation(String containerNo)` - 根据集装箱号查询位置信息
- `queryFreightContainers(String freightId)` - 查询货代的集装箱列表
- `queryContainerTrack(String containerNo)` - 查询集装箱运输轨迹

## 配置说明

### 应用配置 (application.properties)

```properties
# 服务端口
server.port=8084
spring.application.name=geelato-mcp-fms

# MCP 配置
geelato.mcp.enabled=true
geelato.mcp.server-name=geelato-mcp-fms
geelato.mcp.description=Geelato Fleet Management System (Order + Logistics)
spring.ai.mcp.server.name=geelato-mcp-fms
spring.ai.mcp.server.sse-message-endpoint=/mcp/fms/message

# 订单服务配置
geelato.mcp.order.query-timeout=30000

# 物流服务配置
geelato.mcp.logistics.tracking-enabled=true
```

### 安全配置

支持多种认证方式:
- API Key 认证 (X-API-Key Header)
- JWT Token 认证 (Authorization Header)
- 混合模式 (同时支持两种认证)

详细配置参考 `geelato-mcp-common` 模块的安全配置。

## 构建与运行

### 构建
```bash
cd geelato-mcp
mvn clean install -pl geelato-mcp-fms -am -DskipTests
```

### 运行测试
```bash
mvn test -pl geelato-mcp-fms
```

### 启动服务
```bash
java -jar target/geelato-mcp-fms-1.0.0-SNAPSHOT.jar
```

或使用环境变量配置:
```bash
export MCP_API_KEY_1="your-api-key"
export MCP_JWT_SIGN_KEY="your-sign-key"
java -jar target/geelato-mcp-fms-1.0.0-SNAPSHOT.jar \
  --server.port=8084 \
  --geelato.mcp.security.auth-type=hybrid \
  --geelato.mcp.security.api-key.keys=your-api-key-123
```

## API 端点

- **MCP SSE 端点**: `/mcp/fms/message`
- 支持标准的 MCP 协议调用

## 使用示例

### 调用订单查询工具
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "queryOrderByNo",
    "arguments": {
      "orderNo": "ORD123456"
    }
  }
}
```

### 调用集装箱查询工具
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "queryContainerLocation",
    "arguments": {
      "containerNo": "CONT123456"
    }
  }
}
```

## 依赖关系

- `geelato-mcp-common` - MCP 公共模块 (安全、工具基类等)
- Spring Boot 3.0.0
- Spring AI MCP

## 变更历史

### v1.0.0-SNAPSHOT (2026-03-08)
- 合并 `geelato-mcp-order` 和 `geelato-mcp-logistics` 为 `geelato-mcp-fms`
- 统一端口配置 (8084)
- 统一 MCP 端点路径 (`/mcp/fms/message`)
- 保留原有所有工具功能

## 注意事项

1. **端口变更**: 新模块使用端口 8084，不同于原订单服务 (8082) 和物流服务 (8083)
2. **端点路径**: MCP 端点路径统一为 `/mcp/fms/message`
3. **向后兼容**: 所有原有工具功能保持不变
4. **测试资源**: 测试环境禁用了数据库自动配置，使用内存模式运行

## 相关模块

- `geelato-mcp-common` - 公共模块
- `geelato-mcp-platform` - 平台 MCP 服务
- `geelato-mcp-fms` - 车队管理系统 MCP 服务 (本模块)
