---
title: 错误码参考
sidebar_label: 错误码参考
---

# 错误码参考

平台所有业务异常的错误码集中说明。每个错误码对应一个 `CoreException` 子类，抛出后异常响应（`ApiResult.data`）会携带 `docUrl` 字段，指向本页对应锚点或独立详情页，便于快速定位排障文档。

> **说明**：部分错误码的"原因/处理建议"仍在补充中，以各模块 `XxxErrorCodes` 枚举为事实源。

## 错误码与文档链接规则

异常响应中的 `docUrl` 按以下规则生成（baseUrl 为 `https://docs.geelato.cn`）：

- 错误码**未声明** `docSlug`：`{baseUrl}/docs/reference/error-codes#{code}` —— 指向本页对应锚点。
- 错误码**声明了** `docSlug`：`{baseUrl}/docs/reference/error-codes/{slug}` —— 指向独立详情页（适合需要详尽说明的高频错误码）。

关闭文档跳转：将 `GlobalContext.__DocUrlEnabled__` 设为 `false`，异常响应不再输出 `docUrl` 字段。

## 错误码治理规则

- 每个模块维护自己的 `XxxErrorCodes` 枚举（如 `LangErrorCodes`、`CoreErrorCodes`、`WebCommonErrorCodes`、`PlatformErrorCodes`），作为该模块错误码元数据的唯一事实源。
- **码值保持稳定**：新增异常时复用既有码段，不随意改动已发布码值，避免前后端契约与文档锚点失效。
- **新增错误码同步更新本页**：新增 `CoreException` 子类后，需在本表对应模块分类下追加一条说明（原因/处理建议）。
- 体系外异常（`McpException`、`ScriptExecutionException`）暂不纳入本表，后续单独治理。

---

## geelato-lang 模块

基础语言层的错误码。

### 10006 The current version does not support this operation！

- **所在类**：`cn.geelato.lang.exception.UnSupportedVersionException`
- **错误码枚举**：`LangErrorCodes.UNSUPPORTED_VERSION`
- **默认文案**：The current version does not support this operation！
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

---

## geelato-core 模块

ORM / MQL / SQL 相关错误码。

### 10008 MQL json解析异常

- **所在类**：`cn.geelato.core.mql.parser.JsonParseException`
- **错误码枚举**：`CoreErrorCodes.MQL_JSON_PARSE`
- **默认文案**：MQL json解析异常
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

### 10010 SQL执行异常

- **所在类**：`cn.geelato.core.orm.SqlExecuteException`
- **错误码枚举**：`CoreErrorCodes.SQL_EXECUTE`
- **默认文案**：SQL执行异常
- **文档链接**：独立详情页 [`/docs/reference/error-codes/sql-execute`](/docs/reference/error-codes/sql-execute)
- **原因**：见详情页。
- **处理建议**：见详情页。

### 10011 实体过滤字段不存在

- **所在类**：`cn.geelato.core.sql.InvalidFilterFieldException`
- **错误码枚举**：`CoreErrorCodes.INVALID_FILTER_FIELD`
- **默认文案**：实体过滤字段不存在
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

---

## geelato-web-common 模块

鉴权链路上的通用错误码。

### 401 未授权访问[OAUTH]

- **所在类**：`cn.geelato.web.common.interceptor.UnauthorizedException`
- **错误码枚举**：`WebCommonErrorCodes.UNAUTHORIZED`
- **HTTP 状态码**：401
- **默认文案**：未授权访问[OAUTH]
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

### 10007 令牌校验异常[InvalidToken]

- **所在类**：`cn.geelato.web.common.oauth2.InvalidTokenException`
- **错误码枚举**：`WebCommonErrorCodes.INVALID_TOKEN`
- **默认文案**：令牌校验异常[InvalidToken]
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

---

## geelato-web-platform 模块

Web 平台业务错误码（鉴权、插件、GQL、文件处理）。

### 10001 UnFoundPluginException

- **所在类**：`cn.geelato.web.platform.plugin.UnFoundPluginException`
- **错误码枚举**：`PlatformErrorCodes.PLUGIN_NOT_FOUND`
- **默认文案**：UnFoundPluginException
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

### 10003 Gql Resolve Exception

- **所在类**：`cn.geelato.web.platform.utils.GqlResolveException`
- **错误码枚举**：`PlatformErrorCodes.GQL_RESOLVE`
- **默认文案**：Gql Resolve Exception
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

### 400 请求参数错误

- **所在类**：`cn.geelato.web.platform.srv.auth.AuthBadRequestException`
- **错误码枚举**：`PlatformErrorCodes.AUTH_BAD_REQUEST`
- **HTTP 状态码**：400
- **默认文案**：请求参数错误
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

### 403 无权操作该用户

- **所在类**：`cn.geelato.web.platform.srv.auth.AccountOperationForbiddenException`
- **错误码枚举**：`PlatformErrorCodes.ACCOUNT_OPERATION_FORBIDDEN`
- **HTTP 状态码**：403
- **默认文案**：无权操作该用户
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

### 20001 登录异常[MultiTenant]

- **所在类**：`cn.geelato.web.platform.srv.auth.LoginMultiTenantException`
- **错误码枚举**：`PlatformErrorCodes.LOGIN_MULTI_TENANT`
- **默认文案**：登录异常[MultiTenant]
- **原因**：TODO 补充触发场景
- **处理建议**：TODO

### 文件处理（12xx 段）

文件相关异常统一归在 `12xx` 段，`1200` 为根码，`1213-1218` 为子类。

| 码值 | 异常类 | 枚举 | 默认文案 | 原因/处理建议 |
|---|---|---|---|---|
| 1200 | `FileException` | `PlatformErrorCodes.FILE` | 12 File Exception | TODO |
| 1213 | `FileTypeNotSupportedException` | `PlatformErrorCodes.FILE_TYPE_NOT_SUPPORTED` | 12.3 File Type Not Support Exception | TODO |
| 1214 | `FileSizeExceedLimitException` | `PlatformErrorCodes.FILE_SIZE_EXCEED_LIMIT` | 12.4 File Size Exceed Limit Exception | TODO |
| 1215 | `FileNotFoundException` | `PlatformErrorCodes.FILE_NOT_FOUND` | 12.5 File Not Found Exception | TODO |
| 1216 | `FileContentValidFailedException` | `PlatformErrorCodes.FILE_CONTENT_VALID_FAILED` | 12.6 File Content Validate Failed Exception | TODO |
| 1217 | `FileContentIsEmptyException` | `PlatformErrorCodes.FILE_CONTENT_IS_EMPTY` | 12.7 File Content Is Empty Exception | TODO |
| 1218 | `FileContentReadFailedException` | `PlatformErrorCodes.FILE_CONTENT_READ_FAILED` | 12.8 File Content Read Failed Exception | TODO |

---

## 已知限制

- **体系外异常**：`McpException`（字符串型 errorCode）、`ScriptExecutionException` 暂未纳入 `CoreException` 体系，其异常响应不会输出 `docUrl`。后续单独治理时再补充。
- **兜底 handler 不带 docUrl**：非 `CoreException` 体系抛出的异常走 `handleOtherException` 分支，不携带 `docUrl`。
