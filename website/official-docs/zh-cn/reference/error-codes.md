---
title: 错误码参考
sidebar_label: 错误码参考
---

# 错误码参考

平台业务错误码以 `public static final int ERROR_CODE` 常量定义在各异常类（`CoreException` 子类）中，异常构造时传入码值与文案；**本页是错误码的登记清单与唯一对照表**。异常响应（`ApiResult.data`）携带 `docUrl` 字段，指向本页对应锚点或独立详情页，便于快速定位排障文档。

> 本页描述的是平台错误码（Java 异常类体系）。基于规则引擎的「业务异常」（异常实例、告警工单）是另一套机制，见 [业务异常](/docs/exception/overview)。

## 如何查阅

1. **按码值查表**：接口返回的 `code`（或 msg 中的"错误码 xxxx"）→ 按下方分段规则定位类别段 → 在对应表格中查错误说明与解决方法
2. **凭反馈凭据排障**：需要技术详情时，取 msg 末尾的反馈凭据（logTag），调用 `GET /api/exceptionLog/byTag/{logTag}` 查看该次异常的完整技术消息与堆栈；完整排障流程见 [排障指南](/docs/help/troubleshooting)
3. **docUrl 直达**：异常响应自带 `docUrl` 字段，直接跳转到本页对应错误码行或独立详情页
4. 20xxx 段为常规业务事件（登录/权限），不落服务端日志、响应不带反馈凭据

## 分段规则（按错误类别，不以模块为维度）

| 段位 | 类别 | 说明 |
|---|---|---|
| 10xxx | 数据与解析 | MQL / SQL / GQL 的解析与执行 |
| 20xxx | 认证 / 授权 / 会话 | 登录、令牌、权限、租户 |
| 30xxx | 文件处理 | 文件上传、校验、读取 |
| 40xxx | 插件 | 插件治理与调用 |
| 50xxx | 系统通用 | 兜底系统异常（透出详细错误消息） |
| 60xxx | 应用打包与部署 | 低代码应用的打包、部署、回滚 |
| 70xxx | 数据归档 | 归档策略校验、对账、并发冲突 |

同类错误跨模块归入同一段，模块重构不影响码段；前端可凭段位判断错误大类。

## 异常类写法

```java
public class UnauthorizedException extends CoreException {

    public static final int ERROR_CODE = 20005;

    public UnauthorizedException() { this("未授权访问，请登录"); }
    public UnauthorizedException(String message) { super(ERROR_CODE, message); }

    @Override public int getHttpStatus() { return 401; }  // 默认 500，仅鉴权类覆写
}
```

- `CoreException` 仅持有码值；HTTP 状态码（`getHttpStatus()`，默认 500）与文档 slug（`getDocSlug()`，默认 null）通过可覆写方法按需提供
- 无参构造传入的文案即用户可见文案（`getUserMessage()` 默认取 errorMsg）
- 用户文案末尾由 `PlatformExceptionHandler` 追加排障凭据，如：`数据操作失败，请稍后重试（错误码 10002，反馈凭据 123456789012345678）`
- **是否记录日志由异常类定义者决定**（`shouldLog()`，默认 `true`）：覆写为 `false` 的异常属常规业务事件（如鉴权不通过），处理器跳过 `log.error` 与 `platform_exception_log` 落库，响应不带反馈凭据、msg 为纯文案——见 [20xxx 认证/授权/会话类](#20xxx-认证授权会话类)

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
- 未接入 `CoreException` 体系的异常（如 `McpException`、`ScriptExecutionException`）不占用码段，以文末[体系外异常](#体系外异常未接入-coreexception-体系)附表形式登记。

## 错误码与文档链接规则

异常响应中的 `docUrl` 按以下规则生成（baseUrl 为 `https://docs.geelato.cn`）：

- 异常类**未覆写** `getDocSlug()`：`{baseUrl}/docs/reference/error-codes#{code}` —— 指向本页对应锚点。
- 异常类**覆写了** `getDocSlug()`（如 SqlExecuteException 返回 `sql-execute`）：`{baseUrl}/docs/reference/error-codes/{slug}` —— 指向独立详情页。

关闭文档跳转：将 `GlobalContext.__DocUrlEnabled__` 设为 `false`，异常响应不再输出 `docUrl` 字段。

---

## 10xxx 数据与解析类

| 码值 | 异常类 | 错误说明（默认文案） | 常见原因与解决方法 |
|---|---|---|---|
| <a id="10001"></a>10001 | `cn.geelato.core.mql.parser.JsonParseException` | 请求解析失败，请检查数据格式 | 请求体不是合法 JSON（格式错误、转义错误、编码问题）。检查请求体 JSON 格式与 `Content-Type` |
| <a id="10002"></a>10002 | `cn.geelato.core.orm.SqlExecuteException`（根码） | 数据操作失败，请稍后重试（docSlug=sql-execute，独立详情页：[`sql-execute`](/docs/reference/error-codes/sql-execute)） | SQL 执行异常的根码，实际返回由 1002x 分类工厂按根因细分为具体子码；未分类故障的兜底为 10028，根类不再直接作为兜底返回值 |
| <a id="10003"></a>10003 | `cn.geelato.core.sql.InvalidFilterFieldException` | 实体[实体名]的过滤字段[字段名]不存在（文案含 operator 与 scene 信息） | 查询条件中的过滤字段不在实体元数据中（字段名拼写错误、字段未定义或未发布）。对照实体定义修正过滤字段名 |
| <a id="10004"></a>10004 | `cn.geelato.web.platform.utils.GqlResolveException` | 请求解析失败，请检查表达式 | GQL 表达式语法错误或无法解析。检查表达式写法与引用的字段/函数是否存在 |
| <a id="10005"></a>10005 | `cn.geelato.core.mql.parser.InvalidPageParamException` | 分页参数非法（文案含参数名与实际值，HTTP 400） | 分页参数为非整数等非法值。按提示改为合法整数；`pageSize=-1` 表示全量查询，数值越界会被自动钳制到有效区间不报错 |
| <a id="10006"></a>10006 | `cn.geelato.lang.exception.UnSupportedVersionException` | 当前版本不支持该操作 | 预留码（当前无抛出点）。出现时升级平台或应用至支持该操作的版本 |
| <a id="10007"></a>10007 | `cn.geelato.core.mql.ViewTemplateParamException` | 实体[实体名]收到@pf参数[参数列表]，但[原因]。@pf参数仅支持虚拟视图（virtual）类型视图实体 | 对非 virtual 类型的视图实体使用了 `@pf` 模板参数。将该视图实体改为虚拟视图（virtual），或移除 `@pf` 参数（见 MQL 文档的 `@pf` 模板参数说明） |

### 1002x SQL 异常子类（10002 的细分，由 `SqlExecuteException.of` 分类工厂按根因包装）

| 码值 | 异常类 | 判定依据 | 错误说明（用户文案） | 常见原因与解决方法 |
|---|---|---|---|---|
| <a id="10021"></a>10021 | `SqlConnectionException` | 取连接失败、sqlState `08xxx`（PG 08001/08003/08006）、`Communications link failure`/`Connection refused` | 数据库连接中断，系统正在自动恢复，请稍后重试 | 数据库不可达或连接池耗尽。稍后重试（系统自动恢复）；频繁出现时检查数据库可用性、连接池配置与网络 |
| <a id="10022"></a>10022 | `SqlLockConflictException` | MySQL 1213/1205、sqlState `40001`、PG `40P01`（死锁）/`55P03`（锁不可用） | 当前数据正被其他操作占用，请稍后重试 | 行锁/表锁竞争或死锁。稍后重试；频繁出现时排查长事务、未提交事务与并发更新热点 |
| <a id="10023"></a>10023 | `SqlDuplicateKeyException` | MySQL 1062、PG `23505` | 数据已存在，无法重复提交 | 唯一键/唯一索引冲突（重复提交、业务键重复）。改为更新已有数据，或调整业务键后重试 |
| <a id="10024"></a>10024 | `SqlConstraintViolationException` | MySQL 1451/1452、sqlState `23xxx` 段（PG `23503` 外键/`23502` 非空/`23514` CHECK） | 数据存在关联引用或不符合约束，请检查后重试 | 外键引用存在（如删除被引用数据）、非空约束、CHECK 约束不满足。按业务含义补齐或清理关联数据后重试 |
| <a id="10025"></a>10025 | `SqlDataTooLongException` | MySQL 1406、sqlState `22001`（PG string_data_right_truncation） | 字段[字段名]的内容超出长度限制，请缩短后重试 | 字段值超过列长度定义。按文案中的字段名缩短内容，或调整表字段长度 |
| <a id="10026"></a>10026 | `SqlDataOutOfRangeException` | MySQL 1690、sqlState `22003`（PG numeric_value_out_of_range） | 字段[字段名]的数值超出允许范围，请调整后重试 | 数值超出列类型范围或 CHECK 约束。按字段名调整数值/精度 |
| <a id="10027"></a>10027 | `SqlDataFormatException` | MySQL 1366（值/字符集非法）、1292（日期非法）、sqlState `22007`/`22008` | 字段[字段名]的数据格式不正确，请检查填写内容后重试 | 值与列类型不匹配（日期格式、字符集、非法字符）。按字段名修正填写格式 |
| <a id="10028"></a>10028 | `SqlUnexpectedException` | 未纳入 10021~10027 分类的 SQL 故障兜底（如 MySQL 1052 用法错误、1064 语法错误、权限不足） | 数据操作出现未预期错误，已记录日志，请联系管理员排查 | 数据库语法错误、权限不足等需开发/DBA 排查的根因。稍后重试；持续出现时凭反馈凭据（logTag）提取完整 SQL 与错误码定位 |

> PostgreSQL 说明：PG 驱动的 `getErrorCode()` 恒为 0，上述 PG 判定全部依赖 sqlState。子类 docSlug 继承根类（同一详情页）。

## 20xxx 认证/授权/会话类

本段异常均覆写 `shouldLog()` 为 `false`：属常规业务事件（过期会话、无权访问、登录流程），不是服务端错误——不写 error 日志、不落 `platform_exception_log`、响应不带反馈凭据（msg 为纯文案）。

| 码值 | 异常类 | HTTP 状态 | 错误说明（默认文案） | 常见原因与解决方法 |
|---|---|---|---|---|
| <a id="20001"></a>20001 | `cn.geelato.web.platform.srv.auth.LoginMultiTenantException` | 500 | 请选择租户 | 多租户账号登录需选择归属租户。前端凭此码自动弹出租户选择框，选择后继续登录（历史码值不变，现由登录接口直接返回） |
| <a id="20002"></a>20002 | `cn.geelato.web.common.oauth2.InvalidTokenException` | 500 | 令牌校验异常，请重新登录 | 令牌无效、过期或被顶替。重新登录获取新令牌；前端通常静默处理并跳转登录 |
| <a id="20003"></a>20003 | `cn.geelato.web.platform.srv.auth.AuthBadRequestException` | 400 | 请求参数错误（文案为具体原因，如"头像文件不能为空"） | 鉴权链路参数缺失或非法。按文案补齐/修正参数后重试 |
| <a id="20004"></a>20004 | `cn.geelato.web.platform.srv.auth.AccountOperationForbiddenException` | 403 | 无权操作该用户 | 操作目标不是当前登录用户自身可管理的账号。切换到对应账号或由管理员操作 |
| <a id="20005"></a>20005 | `cn.geelato.web.common.interceptor.UnauthorizedException` | 401 | 未授权访问，请登录 | 未登录或会话已失效。重新登录；前端自动跳转登录页 |
| <a id="20006"></a>20006 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException` | 401 | 登录凭证无效，请重新进入小程序 | 微信登录凭证（js_code）无效或已过期。重新进入小程序触发登录 |
| <a id="20007"></a>20007 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException` | 401 | 登录凭证已被使用，请重新获取 | js_code 一次性凭证被重复消费。重新调用 `wx.login` 获取新凭证 |
| <a id="20008"></a>20008 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException` | 401 | 手机号授权失败，请重新授权 | 手机号授权凭证无效或过期。重新发起手机号授权 |
| <a id="20009"></a>20009 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException` | 401 | 绑定确认已过期，请重新发起绑定 | 绑定确认票据过期、签名不匹配或已消费。重新发起绑定流程 |
| <a id="20010"></a>20010 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException` | 401 | 无法获取微信身份标识，请联系管理员 | 小程序未绑定微信开放平台，拿不到 unionid。管理员在微信开放平台完成绑定配置 |
| <a id="20011"></a>20011 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException` | 401 | 请先绑定账号 | 微信身份尚未绑定平台账号。先完成账号绑定 |
| <a id="20012"></a>20012 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppAccountDisabledException` | 403 | 账号已停用，请联系业务员 | 目标账号已停用。联系管理员启用账号 |
| <a id="20013"></a>20013 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppBindConflictException` | 409 | 该微信已绑定其他账号，请先解绑 | 同一微信只能绑定一个账号（一对一约束）。先解绑旧账号再绑定 |
| <a id="20014"></a>20014 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppBindConflictException` | 409 | 该账号已绑定其他微信，请先解绑 | 同一账号只能绑定一个微信。先解绑旧微信再绑定 |
| <a id="20015"></a>20015 | `cn.geelato.web.platform.srv.weixin.miniapp.MiniAppRateLimitException` | 429 | 操作过于频繁，请稍后再试 | 登录/绑定链路触发防撞库限流。稍后重试 |

## 30xxx 文件处理类

文件相关异常统一在 `cn.geelato.web.platform.srv.excel.exception` 包，30000 为根码，30013-30018 为子类（30001~30012 段位未使用）。

| 码值 | 异常类 | 错误说明（默认文案） | 常见原因与解决方法 |
|---|---|---|---|
| <a id="30000"></a>30000 | `FileException`（根码） | 文件处理失败（可带详细信息） | 文件处理通用失败。按文案中的详细信息排查 |
| <a id="30013"></a>30013 | `FileTypeNotSupportedException` | 文件类型不支持 | 上传的扩展名/MIME 不在支持范围。更换为平台支持的文件类型 |
| <a id="30014"></a>30014 | `FileSizeExceedLimitException` | 文件超出大小限制 | 上传文件超过大小上限。压缩或拆分文件后重新上传 |
| <a id="30015"></a>30015 | `FileNotFoundException` | 文件不存在 | 文件已被删除/清理或路径失效。确认文件存在后重新上传 |
| <a id="30016"></a>30016 | `FileContentValidFailedException` | 文件内容校验失败（前端凭此码下载错误提示文件） | 导入数据校验不通过。按下载的错误提示文件逐行修正数据后重新导入 |
| <a id="30017"></a>30017 | `FileContentIsEmptyException` | 文件内容为空 | 上传了空文件或空 sheet。填写内容后重新上传 |
| <a id="30018"></a>30018 | `FileContentReadFailedException` | 文件内容读取失败 | 文件损坏或流读取失败。重新上传；持续失败联系管理员检查存储服务 |

## 40xxx 插件类

| 码值 | 异常类 | 错误说明（默认文案） | 常见原因与解决方法 |
|---|---|---|---|
| <a id="40001"></a>40001 | `UnFoundPluginException` | 插件未找到 | 容器中不存在该插件 Bean（未安装/未注册）。确认插件已部署且正确注册 |
| <a id="40002"></a>40002 | `PluginNotEnabledForTenantException` | 插件未对当前租户启用 | 租户级未启用该插件。在插件管理中为当前租户启用 |
| <a id="40003"></a>40003 | `PluginNotEnabledForTenantException`（平台级禁用形态） | 插件已被平台级禁用 | 插件被平台级禁用。由平台管理员在平台级启用 |
| <a id="40004"></a>40004 | `PluginInvocationTimeoutException` | 插件调用超时（文案为动态值，含插件 ID 与超时阈值毫秒数） | 插件扩展方法执行超过阈值被中断（防卡死）。优化插件逻辑性能、调大超时阈值后重试 |

（40005 段位暂未使用。）

## 50xxx 系统通用类

| 码值 | 异常类 | 错误说明（默认文案） | 常见原因与解决方法 |
|---|---|---|---|
| <a id="50001"></a>50001 | ——（`PlatformExceptionHandler.handleOtherException` 兜底，常量定义于 handler） | 系统繁忙，请稍后重试（msg 透出详细错误消息：`ex.getMessage()`；无消息时"系统异常：异常类名"） | 未接入 CoreException 体系的未知异常兜底。凭响应中的反馈凭据（logTag）通过 `GET /api/exceptionLog/byTag/{logTag}` 查看完整堆栈定位根因 |

## 60xxx 应用打包与部署类

错误码常量集中定义在 `cn.geelato.web.platform.srv.pack.exception.PackException`（一个类 + 场景常量，v1/v2 打包部署链路共用）。

| 码值 | 常量 | 错误说明 | 常见原因与解决方法 |
|---|---|---|---|
| <a id="60001"></a>60001 | `ERROR_CODE_APP_NOT_FOUND` | 打包-应用不存在 | 应用 ID/编码无效。确认应用存在后重新选择打包目标 |
| <a id="60002"></a>60002 | `ERROR_CODE_COLUMN_INCONSISTENT` | 打包前校验-物理表与实体定义列不一致（pre-pack gate） | 物理表结构与实体元数据出现列差异（硬失败门禁）。先对齐表结构与实体定义（刷新表结构/补列）后重新打包 |
| <a id="60003"></a>60003 | `ERROR_CODE_PACKAGE_INVALID` | 部署-版本/包数据缺失或损坏 | 应用包内容不完整。重新打包或重新上传完整的应用包 |
| <a id="60004"></a>60004 | `ERROR_CODE_PLATFORM_MISMATCH` | 部署前校验-平台版本/元数据不匹配 | 目标平台版本或元数据基线与包不匹配。升级目标平台或改用匹配的包版本 |
| <a id="60005"></a>60005 | `ERROR_CODE_META_NOT_FOUND` | 部署中-元数据或字段在目标平台不存在 | 目标环境缺少包内依赖的元数据。确认目标环境已安装对应实体/字段后重试 |
| <a id="60006"></a>60006 | `ERROR_CODE_DEPLOY_DATA_FAILED` | 部署中-包数据写入失败（事务回滚） | 目标库写入异常。查看目标库错误日志，修复后重新部署 |
| <a id="60007"></a>60007 | `ERROR_CODE_ILLEGAL_TABLE_NAME` | 表名非法（防注入校验） | 实体表名含非法字符。修正实体表名（仅字母/数字/下划线）后重新打包 |
| <a id="60008"></a>60008 | `ERROR_CODE_PACKAGE_IO` | 应用包文件读写失败（IO） | 磁盘空间不足、文件权限或存储服务异常。检查存储环境后重试 |
| <a id="60009"></a>60009 | `ERROR_CODE_REFRESH_CACHE_FAILED` | 部署成功但元数据缓存刷新失败 | 部署已成功，仅缓存刷新失败。手动刷新元数据缓存或重启服务保证一致性 |
| <a id="60010"></a>60010 | `ERROR_CODE_NOT_ALLOWED` | 环境限制不允许操作 / 回滚无备份版本 | 当前环境策略禁止该操作（如生产环境），或回滚缺少备份版本。按环境策略操作或先确认备份 |

> 说明：外部依赖 geelato-package 的 `PackageException` 保留给 market 等外部链路，平台内打包部署链路统一使用 `PackException`。

## 70xxx 数据归档类

错误码常量集中定义在 `cn.geelato.archive.exception.ArchiveException`（geelato-archive-policy 模块）。
归档引擎以"确保不误删数据"为第一目标：所有对账类失败均为硬失败中止（源库未动或整批回滚，重跑安全），
错误信息包含表名/批次/游标/期望与实际行数等上下文，支持精确诊断（详见 `platform_archive_run.error_json`）。

| 码值 | 常量 | 错误说明 | 常见原因与解决方法 |
|---|---|---|---|
| <a id="70001"></a>70001 | `VALIDATION_ERROR` | 策略校验失败（源表/列不存在、归档库连接不可达、WHERE 条件语法或安全校验不通过、保留期低于平台最小值、目标类型不支持、保护名单、同库目标同名等） | 策略配置不合法。按错误信息逐项修正策略配置后重新校验 |
| <a id="70002"></a>70002 | `SCHEMA_DRIFT` | 归档目标表列结构漂移（源表存在而目标缺少列，禁止静默跳列） | 目标表缺少源表已有列。同步目标表结构（补齐缺失列）后重跑 |
| <a id="70003"></a>70003 | `TARGET_WRITE_MISMATCH` | 归档目标写入对账不一致（载入行数或目标复核数与本批期望不符） | 目标库写入丢失或多余。源库未动、零丢失，排查目标库故障（磁盘/约束/连接）后重跑安全 |
| <a id="70004"></a>70004 | `SOURCE_DELETE_MISMATCH` | 源表删除对账不一致（整批已回滚，归档库数据完整） | 源表删除行数与预期不符。整批已回滚，排查并发写入等干扰后重跑安全 |
| <a id="70005"></a>70005 | `POLICY_RUNNING_CONFLICT` | 策略并发冲突（同策略已有运行中的 run，或运行中禁止停用/删除/修改） | 同策略已有运行中的归档。等待当前 run 结束，或处理异常挂起的运行后重试 |

## 体系外异常（未接入 CoreException 体系）

以下异常不继承 `CoreException`，不占用错误码分段、不输出 `docUrl`，在此登记备查：

| 异常类 | 模块 | 错误说明 | 常见原因与解决方法 |
|---|---|---|---|
| `MailAccessException`（code=40903） | geelato-mail | 邮件 Service 无法解析当前登录用户（未登录/会话过期） | 重新登录后再使用邮件功能；由 `MailExceptionHandler` 统一转为 `ApiResult.fail(40903, msg)` |
| `McpException`（字符串型 errorCode） | geelato-mcp-common | MCP 工具调用失败 | 按 errorCode 与消息排查 MCP 服务端状态与工具配置 |
| `ScriptExecutionException`（无码，携带 api 与 retryable 标志） | geelato-web-platform（脚本） | 脚本执行失败（脚本不存在、执行异常等） | 按消息排查脚本定义与依赖；`retryable=true` 时可安全重试 |
| `DeliverException`（无码） | geelato-alert | 告警投递失败（message 调用异常/渠道映射缺失/渲染失败） | 检查告警通道配置与消息服务可用性；错误详情记录在告警记录的 `error_message` 列 |
| `IdeOptimisticLockException`（无码） | geelato-web-designer | IDE 脚本乐观锁冲突：脚本版本冲突（服务端/客户端 version 不一致） | 脚本已被他人修改。先 pull 最新版本，合并改动后再提交 |

## 已知限制

- **体系外异常不输出 docUrl**：文末附表中的异常未接入 `CoreException` 体系，异常响应不携带 `docUrl` 字段。
- **兜底 handler 不带 docUrl**：非 `CoreException` 体系抛出的异常走 `handleOtherException` 分支，返回 50001 并携带 `logTag`，但不携带 `docUrl`。
