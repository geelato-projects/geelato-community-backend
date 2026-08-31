---
title: 错误码参考
sidebar_label: 错误码参考
---

# 错误码参考

平台业务错误码以 `public static final int ERROR_CODE` 常量定义在各异常类（`CoreException` 子类）中，异常构造时传入码值与文案；**本页是错误码的登记清单与唯一对照表**。异常响应（`ApiResult.data`）携带 `docUrl` 字段，指向本页对应锚点或独立详情页，便于快速定位排障文档。

## 分段规则（按错误类别，不以模块为维度）

| 段位 | 类别 | 说明 |
|---|---|---|
| 10xxx | 数据与解析 | MQL / SQL / GQL 的解析与执行 |
| 20xxx | 认证 / 授权 / 会话 | 登录、令牌、权限、租户 |
| 30xxx | 文件处理 | 文件上传、校验、读取 |
| 40xxx | 插件 | 插件治理与调用 |
| 50xxx | 系统通用 | 兜底系统异常 |

同类错误跨模块归入同一段，模块重构不影响码段；前端可凭段位判断错误大类。

## 异常类写法

```java
public class UnauthorizedException extends CoreException {

    public static final int ERROR_CODE = 20005;

    public UnauthorizedException() { this("未授权访问，请重新登录"); }
    public UnauthorizedException(String message) { super(ERROR_CODE, message); }

    @Override public int getHttpStatus() { return 401; }  // 默认 500，仅鉴权类覆写
}
```

- `CoreException` 仅持有码值；HTTP 状态码（`getHttpStatus()`，默认 500）与文档 slug（`getDocSlug()`，默认 null）通过可覆写方法按需提供
- 无参构造传入的文案即用户可见文案（`getUserMessage()` 默认取 errorMsg）
- 用户文案末尾由 `PlatformExceptionHandler` 追加排障凭据，如：`数据操作失败，请稍后重试（错误码 10002，反馈凭据 123456789012345678）`——用户报障截图后，运维在 `${LOG_DIR}/error/` 检索 `logTag=` 即可取到完整技术详情

## 错误码治理规则

- 错误码定义在各异常类的 `ERROR_CODE` 常量中；**本页码表为登记清单，新增错误码必须同步登记**（新增码在对应类别段段尾顺延）。
- 码值全局唯一性靠本页登记表约束（无编译期/运行期强制检测）；新增前先查本页避免重复。
- **码值是前后端契约**：前端存在按码值的分支逻辑（如 20005 弹租户选择、30016 下载错误文件、20002 静默处理），调整码值必须前后端同步发布，并同步更新本页。
- SQL 执行异常（10002）等场景，开发模式（`GlobalContext.getLogStack()=true`）下响应 `data.errorMsg` 保留完整技术文案便于本地排障；生产默认只返回友好文案。
- 体系外异常（`McpException`、`ScriptExecutionException`）暂不纳入本表，后续单独治理。

## 错误码与文档链接规则

异常响应中的 `docUrl` 按以下规则生成（baseUrl 为 `https://docs.geelato.cn`）：

- 异常类**未覆写** `getDocSlug()`：`{baseUrl}/docs/reference/error-codes#{code}` —— 指向本页对应锚点。
- 异常类**覆写了** `getDocSlug()`（如 SqlExecuteException 返回 `sql-execute`）：`{baseUrl}/docs/reference/error-codes/{slug}` —— 指向独立详情页。

关闭文档跳转：将 `GlobalContext.__DocUrlEnabled__` 设为 `false`，异常响应不再输出 `docUrl` 字段。

---

## 10xxx 数据与解析类

| 码值 | 异常类 | 默认文案（用户可见） |
|---|---|---|
| 10001 | `cn.geelato.core.mql.parser.JsonParseException` | 请求解析失败，请检查数据格式 |
| 10002 | `cn.geelato.core.orm.SqlExecuteException` | 数据操作失败，请稍后重试（docSlug=sql-execute，独立详情页：[`sql-execute`](/docs/reference/error-codes/sql-execute)，用户文案按根因分类） |
| 10003 | `cn.geelato.core.sql.InvalidFilterFieldException` | 查询条件包含不存在的字段 |
| 10004 | `cn.geelato.web.platform.utils.GqlResolveException` | 请求解析失败，请检查表达式 |

## 20xxx 认证/授权/会话类

| 码值 | 异常类 | HTTP 状态 | 默认文案 |
|---|---|---|---|
| 20001 | `cn.geelato.web.platform.srv.auth.LoginMultiTenantException` | 500 | 请选择租户（历史码值不变；前端凭此码弹出租户选择框） |
| 20002 | `cn.geelato.web.common.oauth2.InvalidTokenException` | 500 | 令牌校验异常，请重新登录 |
| 20003 | `cn.geelato.web.platform.srv.auth.AuthBadRequestException` | 400 | 请求参数错误 |
| 20004 | `cn.geelato.web.platform.srv.auth.AccountOperationForbiddenException` | 403 | 无权操作该用户 |
| 20005 | `cn.geelato.web.common.interceptor.UnauthorizedException` | 401 | 未授权访问，请重新登录 |

## 30xxx 文件处理类

| 码值 | 异常类 | 默认文案 |
|---|---|---|
| 30000 | `FileException`（根码） | 文件处理失败 |
| 30013 | `FileTypeNotSupportedException` | 文件类型不支持 |
| 30014 | `FileSizeExceedLimitException` | 文件超出大小限制 |
| 30015 | `FileNotFoundException` | 文件不存在 |
| 30016 | `FileContentValidFailedException` | 文件内容校验失败（前端凭此码下载错误提示文件） |
| 30017 | `FileContentIsEmptyException` | 文件内容为空 |
| 30018 | `FileContentReadFailedException` | 文件内容读取失败 |

文件相关异常统一在 `cn.geelato.web.platform.srv.excel.exception` 包，30000 为根码，30013-30018 为子类。

## 40xxx 插件类

| 码值 | 异常类 | 默认文案 |
|---|---|---|
| 40001 | `UnFoundPluginException` | 插件未找到 |
| 40002 | `PluginNotEnabledForTenantException` | 插件未对当前租户启用 |
| 40003 | `PluginNotEnabledForTenantException`（平台级禁用形态） | 插件已被平台级禁用 |
| 40004 | `PluginInvocationTimeoutException` | 插件调用超时 |

（40005 段位空缺：原枚举项 PLUGIN_LOAD_FAILED 无对应异常类，2026-08 常量化时移除，码值不复用。）

## 50xxx 系统通用类

| 码值 | 异常类 | 默认文案 |
|---|---|---|
| 50001 | ——（`PlatformExceptionHandler.handleOtherException` 兜底，常量定义于 handler） | 系统繁忙，请稍后重试（响应携带 logTag，凭反馈凭据可定位服务端日志） |

## 历史保留

| 码值 | 异常类 | 默认文案 |
|---|---|---|
| 10006 | `cn.geelato.lang.exception.UnSupportedVersionException` | 当前版本不支持该操作 |

---

## 旧码 → 新码映射（2026-08 错误码重划）

> **重要**：码值是前后端契约，本次重划涉及前端 2 个依赖点（10007→20002、1216→30016）；多租户 20001 为历史码值保持不变，**前后端必须同步发布**。

| 旧码 | 错误 | 新码 |
|---|---|---|
| 10001 | PLUGIN_NOT_FOUND | 40001 |
| 10003 | GQL_RESOLVE | 10004 |
| 10006 | UNSUPPORTED_VERSION | 10006（不变） |
| 10007 | INVALID_TOKEN | 20002 |
| 10008 | MQL_JSON_PARSE | 10001 |
| 10010 | SQL_EXECUTE | 10002 |
| 10010 | PLUGIN_NOT_ENABLED_FOR_TENANT | 40002 |
| 10011 | INVALID_FILTER_FIELD | 10003 |
| 10011 | PLUGIN_PLATFORM_DISABLED | 40003 |
| 10012 | PLUGIN_INVOCATION_TIMEOUT | 40004 |
| 10013 | PLUGIN_LOAD_FAILED | ——（已移除） |
| 10099 | SYSTEM_BUSY | 50001 |
| 400 | AUTH_BAD_REQUEST | 20003 |
| 401 | UNAUTHORIZED | 20005 |
| 403 | ACCOUNT_OPERATION_FORBIDDEN | 20004 |
| 1200 | FILE | 30000 |
| 1213-1218 | FILE_TYPE_NOT_SUPPORTED ... FILE_CONTENT_READ_FAILED | 30013-30018 |
| 20001 | LOGIN_MULTI_TENANT | 20001（不变） |

---

## 已知限制

- **体系外异常**：`McpException`（字符串型 errorCode）、`ScriptExecutionException` 暂未纳入 `CoreException` 体系，其异常响应不会输出 `docUrl`。后续单独治理时再补充。
- **兜底 handler 不带 docUrl**：非 `CoreException` 体系抛出的异常走 `handleOtherException` 分支，返回 50001 并携带 `logTag`，但不携带 `docUrl`。
