---
title: 文档治理
sidebar_label: 文档治理
---

# 文档治理

本页说明 Geelato 官方文档的事实源规则、维护要求与错误码文档的维护流程。

## 事实源规则

- 仓库 Markdown 是事实源，Docusaurus 官方站（`docs.geelato.cn`）是发布形态。
- `docs/` 继续承担工程专题文档角色。
- `SrvExplain/` 继续承担生成型 API 补充说明角色。

## 维护要求

- 框架交付变化时同步更新官方文档。
- API 文档需同时维护 OpenAPI 与 `SrvExplain` 入口关系。
- Runtime / Designer 边界变动时同步更新文档说明。
- 站点需可成功构建、链接可达，且每种语言都能找到最小接入路径。

## 错误码文档维护流程

平台的业务异常通过 `CoreException` 体系统一管理，错误码以模块为单位集中声明在 `XxxErrorCodes` 枚举中（`LangErrorCodes` / `CoreErrorCodes` / `WebCommonErrorCodes` / `PlatformErrorCodes`），并在 [错误码参考](../reference/error-codes.md) 汇总页对外说明。异常响应会输出 `docUrl` 字段，指向前端可直接跳转的文档位置。

**新增错误码时必须同步更新文档**，流程如下：

1. **先在模块枚举注册**：在对应模块的 `XxxErrorCodes` 中新增枚举常量，声明 `code` / `defaultMessage`，按需声明 `httpStatus`（鉴权类 401/403/400）与 `docSlug`（高频错误码提供独立详情页）。
2. **保持码值稳定**：已发布错误码不随意改动，避免前后端契约与文档锚点失效。新错误码复用所属模块的既有码段。
3. **同步更新汇总页**：在 `reference/error-codes.md` 对应模块分类下追加一条，至少包含所在类、错误码枚举、默认文案、原因、处理建议。
4. **声明了 docSlug 的需补详情页**：在 `reference/error-codes/{slug}.md` 新建详情页，补充错误含义、常见原因、排查步骤、示例。
5. **中英文对齐**：英文镜像在 `i18n/en/docusaurus-plugin-content-docs/current/reference/` 下同步维护。

**体系外异常登记**：暂未纳入 `CoreException` 体系的异常（`McpException`、`ScriptExecutionException`）不进入本表，待后续单独治理时再补充。
