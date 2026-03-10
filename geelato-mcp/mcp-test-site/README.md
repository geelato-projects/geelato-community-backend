# Geelato MCP 测试站点

用于测试和验证 MCP 服务的前端应用。

## 技术栈

- Nuxt 4
- Vue 3
- TailwindCSS
- Playwright (E2E 测试)

## 快速开始

### 安装依赖

```bash
pnpm install
```

### 开发模式

```bash
pnpm dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
pnpm build
pnpm preview
```

## E2E 测试

### 运行测试

```bash
pnpm test
```

### 带 UI 运行测试

```bash
pnpm test:ui
```

### 生成测试报告

```bash
pnpm test:report
```

## 配置

编辑 `nuxt.config.ts` 进行配置调整。

## 测试脚本

测试脚本位于 `tests/` 目录，使用 Playwright 框架编写。

## 注意事项

- 本站点仅用于开发测试，不应用于生产环境
- 确保 MCP 服务已启动 (默认端口 8081)
- 测试数据为模拟数据，不连接真实数据库
