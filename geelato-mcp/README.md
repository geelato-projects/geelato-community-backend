# Geelato MCP 服务

基于 Spring AI MCP 的模型上下文协议服务，为 AI 助手提供平台能力。

## 目录结构

```
geelato-mcp/
├── geelato-mcp-common/      # 公共模块 (安全、工具基类等)
├── geelato-mcp-platform/    # 平台 MCP 服务 (核心)
├── geelato-mcp-fms/         # 国际物流管理系统 (订单 + 物流)
├── mcp-test-site/           # 前端测试站点 (Nuxt 4)
└── scripts/                 # 启动和管理脚本
```

## 模块说明

### 1. geelato-mcp-common
公共模块，提供:
- MCP 安全认证 (API Key, JWT, 混合模式)
- MCP 工具基类
- 通用配置
- 工具测试基类

### 2. geelato-mcp-platform
平台 MCP 服务，提供:
- 用户管理工具
- 角色权限工具
- 数据字典工具
- 页面配置工具
- 实体模型工具
- 视图查询工具

**端口**: 8081  
**端点**: `/mcp/platform/message`

### 3. geelato-mcp-fms
车队管理系统 (订单 + 物流合并),提供:
- 订单查询工具
- 集装箱查询工具
- 物流跟踪工具

**端口**: 8084  
**端点**: `/mcp/fms/message`

### 4. mcp-test-site
前端测试站点，用于:
- MCP 服务功能测试
- UI 自动化测试
- E2E 测试

**端口**: 3000

## 快速开始

### 开发环境

启动所有服务 (Platform + FMS + Test Site):

```bash
./scripts/start-dev.sh
```

停止所有服务:

```bash
./scripts/stop-dev.sh
```

### 生产环境

仅启动核心服务 (Platform):

```bash
./scripts/start-prod.sh
```

停止服务:

```bash
./scripts/stop-prod.sh
```

## 脚本说明

| 脚本 | 用途 | 启动服务 |
|------|------|---------|
| `start-dev.sh` | 开发环境启动 | platform, fms, test-site |
| `start-prod.sh` | 生产环境启动 | platform |
| `stop-dev.sh` | 开发环境停止 | 所有开发服务 |
| `stop-prod.sh` | 生产环境停止 | platform |

详细说明查看 [`scripts/README.md`](scripts/README.md)

## 技术栈

- **后端**:
  - Java 21
  - Spring Boot 3.0+
  - Spring AI 1.1.2
  - MCP SDK 0.17.0

- **前端**:
  - Nuxt 4
  - Vue 3
  - TailwindCSS
  - Playwright

## 认证方式

支持三种认证模式:

1. **API Key**: 通过 `X-API-Key` Header 传递
2. **JWT Token**: 通过 `Authorization: Bearer {token}` Header 传递
3. **混合模式**: 同时支持 API Key 和 JWT

配置示例:

```bash
# API Key 认证
--geelato.mcp.security.auth-type=api-key
--geelato.mcp.security.api-key.keys=your-api-key

# JWT 认证
--geelato.mcp.security.auth-type=jwt
--geelato.mcp.security.jwt.sign-key=your-sign-key

# 混合模式 (推荐)
--geelato.mcp.security.auth-type=hybrid
```

## 测试

### 单元测试

```bash
# 测试 FMS 模块
mvn test -pl geelato-mcp-fms

# 测试 Platform 模块
mvn test -pl geelato-mcp-platform

# 测试 Common 模块
mvn test -pl geelato-mcp-common
```

### E2E 测试

```bash
cd mcp-test-site
pnpm test
```

## 配置

### 环境变量

#### 开发环境
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
```

#### 生产环境
```bash
export MCP_PLATFORM_PORT=8081
export MCP_API_KEY=prod-api-key-secure-123
export JWT_SIGN_KEY=5A1332068BA9FD17
export AUTH_TYPE=hybrid
```

### 日志位置

#### 开发环境
- `/tmp/geelato-mcp-platform.log`
- `/tmp/geelato-mcp-fms.log`
- `/tmp/mcp-test-site.log`

#### 生产环境
- `/var/log/geelato-mcp-platform.log`

## 服务访问

### 开发环境

启动后访问:
- **MCP Platform**: http://localhost:8081
- **MCP FMS**: http://localhost:8084
- **Test Site**: http://localhost:3000

### API Key

开发环境默认 API Key:
- `test-api-key-123456`
- `geelato-mcp-key-789`

## 文档

- [MCP 安全分析报告](MCP_SECURITY_ANALYSIS.md)
- [FMS 模块说明](geelato-mcp-fms/README.md)
- [脚本使用说明](scripts/README.md)
- [测试站点说明](mcp-test-site/README.md)

## 注意事项

1. **端口占用**: 确保 8081, 8084, 3000 端口未被占用
2. **Java 版本**: 需要 Java 21+
3. **生产安全**: 生产环境请更换默认 API Key 和 JWT 签名
4. **防火墙**: 生产环境请配置防火墙规则
5. **HTTPS**: 生产环境建议使用 HTTPS

## 相关项目

- [Geelato Platform](../geelato-web-platform) - 后端平台
- [Geelato Standards](../geelato-standards) - 项目规范和工具集

## License

Copyright © 2026 Geelato
