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
| 50xxx | 系统通用 | 兜底系统异常（透出详细错误消息） |
| 60xxx | 应用打包与部署 | 低代码应用的打包、部署、回滚 |

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
- 用户文案末尾由 `PlatformExceptionHandler` 追加排障凭据，如：`数据操作失败，请稍后重试（错误码 10002，反馈凭据 123456789012345678）`

## 错误响应三层结构（msg 友好 / errorMsg 友好 / stackTraceDetail 技术详情）

异常响应（`ApiResult`）按职责分层，用户看到友好文案的同时技术详情可直接取到：

- **`msg`**（前端直接展示）：`CoreException.getUserMessage()` 友好文案 + 排障凭据
- **`data.errorMsg`**：同友好文案（不含 SQL/参数/堆栈）
- **`data.stackTraceDetail`**：技术详情 = 异常技术消息（如 SQL 执行异常的"原因/执行SQL/参数/数据库错误码"）+ 完整堆栈，默认随响应下发；`GlobalContext.setLogStack(false)` 可关闭（仅隐藏本字段，不影响 msg/errorMsg）
- **反馈凭据即 `logTag`**：技术详情同时写入服务端错误日志（`${LOG_DIR}/error/`，凭 `logTag=` 检索），并**异步持久化到 `platform_exception_log` 表**（catalog=platform-log，默认主库，可配置独立日志库）——运维凭 `GET /api/exceptionLog/byTag/{logTag}` 直接查询该次异常的完整详情（结构化前缀 + 技术消息 + 堆栈），无需登服务器；`GET /api/exceptionLog/page` 支持按错误码/应用/租户/时间分页排查

## 错误码治理规则

- 错误码定义在各异常类的 `ERROR_CODE` 常量中；**本页码表为登记清单，新增错误码必须同步登记**（新增码在对应类别段段尾顺延）。
- 码值全局唯一性靠本页登记表约束（无编译期/运行期强制检测）；新增前先查本页避免重复。
- **码值是前后端契约**：前端存在按码值的分支逻辑（如 20001 弹租户选择、30016 下载错误文件、20002 静默处理），调整码值必须前后端同步发布，并同步更新本页。
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
| 10002 | `cn.geelato.core.orm.SqlExecuteException`（根码） | 数据操作失败，请稍后重试（兜底未分类 SQL 错误；docSlug=sql-execute，独立详情页：[`sql-execute`](/docs/reference/error-codes/sql-execute)） |
| 10003 | `cn.geelato.core.sql.InvalidFilterFieldException` | 查询条件包含不存在的字段 |
| 10004 | `cn.geelato.web.platform.utils.GqlResolveException` | 请求解析失败，请检查表达式 |
| 10005 | `cn.geelato.core.mql.parser.InvalidPageParamException` | 分页参数非法（非整数、非正数或超出上限，文案含参数名与实际值；HTTP 400） |
| 10006 | `cn.geelato.lang.exception.UnSupportedVersionException` | 当前版本不支持该操作 |

### 1002x SQL 异常子类（10002 的细分，由 `SqlExecuteException.of` 分类工厂按根因包装）

| 码值 | 异常类 | 判定依据 | 用户文案 |
|---|---|---|---|
| 10021 | `SqlConnectionException` | 取连接失败、sqlState `08xxx`（PG 08001/08003/08006）、`Communications link failure`/`Connection refused` | 数据库连接中断，系统正在自动恢复，请稍后重试 |
| 10022 | `SqlLockConflictException` | MySQL 1213/1205、sqlState `40001`、PG `40P01`（死锁）/`55P03`（锁不可用） | 当前数据正被其他操作占用，请稍后重试 |
| 10023 | `SqlDuplicateKeyException` | MySQL 1062、PG `23505` | 数据已存在，无法重复提交 |
| 10024 | `SqlConstraintViolationException` | MySQL 1451/1452、sqlState `23xxx` 段（PG `23503` 外键/`23502` 非空/`23514` CHECK） | 数据存在关联引用或不符合约束，请检查后重试 |
| 10025 | `SqlDataTooLongException` | MySQL 1406、sqlState `22001`（PG string_data_right_truncation） | 字段[字段名]的内容超出长度限制，请缩短后重试（文案含从根因消息提取的字段名） |
| 10026 | `SqlDataOutOfRangeException` | MySQL 1690、sqlState `22003`（PG numeric_value_out_of_range） | 字段[字段名]的数值超出允许范围，请调整后重试 |
| 10027 | `SqlDataFormatException` | MySQL 1366（值/字符集非法）、1292（日期非法）、sqlState `22007`/`22008` | 字段[字段名]的数据格式不正确，请检查填写内容后重试 |

> PostgreSQL 说明：PG 驱动的 `getErrorCode()` 恒为 0，上述 PG 判定全部依赖 sqlState。子类 docSlug 继承根类（同一详情页）。

## 20xxx 认证/授权/会话类

| 码值 | 异常类 | HTTP 状态 | 默认文案 |
|---|---|---|---|
| 20001 | `cn.geelato.web.platform.srv.auth.LoginMultiTenantException` | 500 | 请选择租户（前端凭此码弹出租户选择框） |
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

（40005 段位暂未使用。）

## 50xxx 系统通用类

| 码值 | 异常类 | 默认文案 |
|---|---|---|
| 50001 | ——（`PlatformExceptionHandler.handleOtherException` 兜底，常量定义于 handler） | 兜底透出详细错误消息（`ex.getMessage()`；无消息时"系统异常：异常类名"），响应携带 logTag |

## 60xxx 应用打包与部署类

错误码常量集中定义在 `cn.geelato.web.platform.srv.pack.exception.PackException`（一个类 + 场景常量，v1/v2 打包部署链路共用）。

| 码值 | 常量 | 场景 |
|---|---|---|
| 60001 | `ERROR_CODE_APP_NOT_FOUND` | 打包-应用不存在 |
| 60002 | `ERROR_CODE_COLUMN_INCONSISTENT` | 打包前校验-物理表与实体定义列不一致（pre-pack gate） |
| 60003 | `ERROR_CODE_PACKAGE_INVALID` | 部署-版本/包数据缺失或损坏 |
| 60004 | `ERROR_CODE_PLATFORM_MISMATCH` | 部署前校验-平台版本/元数据不匹配 |
| 60005 | `ERROR_CODE_META_NOT_FOUND` | 部署中-元数据或字段在目标平台不存在 |
| 60006 | `ERROR_CODE_DEPLOY_DATA_FAILED` | 部署中-包数据写入失败（事务回滚） |
| 60007 | `ERROR_CODE_ILLEGAL_TABLE_NAME` | 表名非法（防注入校验） |
| 60008 | `ERROR_CODE_PACKAGE_IO` | 应用包文件读写失败（IO） |
| 60009 | `ERROR_CODE_REFRESH_CACHE_FAILED` | 部署成功但元数据缓存刷新失败 |
| 60010 | `ERROR_CODE_NOT_ALLOWED` | 环境限制不允许操作 / 回滚无备份版本 |

> 说明：外部依赖 geelato-package 的 `PackageException` 保留给 market 等外部链路，平台内打包部署链路统一使用 `PackException`。

## 已知限制

- **体系外异常**：`McpException`（字符串型 errorCode）、`ScriptExecutionException` 暂未纳入 `CoreException` 体系，其异常响应不会输出 `docUrl`。后续单独治理时再补充。
- **兜底 handler 不带 docUrl**：非 `CoreException` 体系抛出的异常走 `handleOtherException` 分支，返回 50001 并携带 `logTag`，但不携带 `docUrl`。
