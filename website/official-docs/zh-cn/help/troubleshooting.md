---
title: 报障排查
sidebar_label: 报障排查
---

# 报障排查

本页说明 Geelato 的日志体系、报障定位主工作流，以及常用调试工具，帮助快速定位线上问题与开发期故障。

## 报障最小输入

定位一次故障所需的最小信息：

- **账号**：`loginName`（优先）或 `userId`。
- **故障时间段**：精确到秒，建议取 5–30 分钟窗口。

## 日志分层与统一字段

### 日志分层

| 层 | 内容 |
| --- | --- |
| 访问日志 | 请求入口、响应结果、耗时、状态码。 |
| 业务日志 | 关键业务动作（新增/更新/删除/审批/消息发送）。 |
| 异常日志 | 统一异常模型、错误码、关联键。 |
| 安全日志 | 认证授权、失败原因、来源 IP（敏感信息脱敏）。 |
| 数据层日志 | DAO 执行、动态数据源切换、慢操作告警。 |

### 统一字段

请求与异常日志统一携带以下关联字段，用于串联链路：

`traceId`、`requestId`、`userId`、`loginName`、`tenantCode`、`uri`、`method`、`status`、`durationMs`、`errorCode`、`logTag`。

其中 `traceId` 贯穿单次请求全链路，`logTag` 是异常发生时生成的一次性关联键，二者是排障的核心抓手（见 [术语表](glossary.md)）。

### 级别规范

- `INFO`：关键业务成功路径、请求摘要、数据源切换成功。
- `WARN`：可恢复异常、参数不合法、外部依赖抖动、降级行为。
- `ERROR`：业务失败或系统异常，必须带 `traceId/logTag/errorCode`。
- `DEBUG`：排障临时信息，默认关闭，避免生产噪音。

### 脱敏规则

`password`、`token`、`authorization`、`apiKey`、`secret`、`accessToken`、`refreshToken` 等字段必须脱敏；禁止打印完整凭证、私钥、会话密钥、明文密码。推荐打印掩码摘要（前 4 后 4）与上下文（IP、账号、路径、时间）。

## 排障主工作流

### 一线排查步骤

1. 按账号与时间段查询请求日志：

   ```
   GET /api/run/log/searchByUserTime?userId={userId}&from={from}&to={to}
   ```

   - 时间格式：`yyyy-MM-dd HH:mm:ss.SSS`。
   - `from`/`to` 缺省时默认最近 24 小时。
   - 返回结果包含 `file`、`lineNumber`、`lines`。

2. 在返回结果中找到 `traceId`、`logTag`、`uri`、`status`、`errorCode`。

3. 若有 `logTag`，按其反查异常上下文：

   ```
   GET /api/run/log/search?tag={logTag}
   ```

4. 以 `traceId` 在请求日志、业务日志、数据层日志之间串联，还原完整调用链。

### 研发深挖顺序

1. 先看请求日志的 `uri` / `status` / `durationMs`，判断是否慢请求或错误返回。
2. 再看异常日志中的 `errorCode` / `logTag`，区分业务异常与系统异常。
3. 最后看 DAO / 动态数据源日志，确认 SQL 与数据源切换是否异常。

## SQL 观察

### p6spy SQL 代理日志

平台通过 p6spy 记录实际执行的 SQL，关键配置（见 [系统配置](../system-config/overview.md)）：

- `decorator.datasource.p6spy.enable-logging=true`
- `decorator.datasource.p6spy.log-file=/log/p6spy-sql.log`

排查 SQL 问题时优先查看该日志文件，确认实际下发的 SQL 与参数。

### Fluent DSL 调试

后端 Fluent DSL 可在不执行的情况下预览生成的 SQL（见 [Fluent DSL 指引](../orm/fluent-dsl.md)）：

- `toSql()`：预览查询 SQL。
- `toCountSql()`：预览计数 SQL。

用于排查关联、分页、条件拼接是否符合预期。

## MQL 调试

MQL Playground 是交互式的 MQL 调试与校验工具，通过配置开启：

```
geelato.mql.playground.enabled=true
```

提供以下能力（挂在 `/api/mql` 下）：

- `POST /api/mql/explain`：MQL → SQL 预览（无需数据源）。
- `POST /api/mql/validate`：校验 MQL 语法。
- `POST /api/mql/execute`：针对主库真实执行（需 `primaryJdbcTemplate`）。
- `GET /api/mql/entities`、`GET /api/mql/schema/{entity}`：查看实体与字段元数据。
- `GET /api/mql/scenarios` 等：场景化回归套件（带 schema 初始化与清理）。

执行类操作支持 `runAs` 模拟身份，使租户隔离与数据权限注入器按生产等效方式生效，便于复现带权限上下文的查询问题。

## 错误码定位

业务异常通过 `CoreException` 体系统一管理，异常响应会携带：

- `errorCode`、默认文案。
- `docUrl`：指向排障文档。未声明 `docSlug` 时指向 [错误码参考](../reference/error-codes.md) 对应锚点；声明 `docSlug` 时指向独立详情页（如 [SQL 执行异常](../reference/error-codes/sql-execute.md)）。
- `logTag`：用于按上文工作流反查日志。

通过 `docUrl` 可从前端错误提示直接跳转到对应排障文档。

## 本地连生产库

本地开发需直连生产主库时，若 `platform_dev_db_connect` 中的 host/port 为内网地址，可在不修改表数据的前提下通过 Host/Port 映射文件重写：

- 默认路径：应用运行目录下 `conf/db-host-map.txt`。
- 或通过环境变量指定：`GEELATO_DS_HOST_MAP_FILE=/path/to/db-host-map.txt`。

适用于通过公网、VPN 或 SSH Tunnel 连接生产库的场景。

详见 [动态数据源：内网 Host/Port 映射](../dynamic-datasource/host-mapping.md)。

## 常见症状对照

| 症状 | 定位方法 |
| --- | --- |
| 按账号查不到数据 | 扩大时间窗；确认账号口径（优先 `loginName`，再试 `userId`）。 |
| 只有请求日志、无异常日志 | 检查 `status` 与 `durationMs`，可能是慢请求或前端中断。 |
| ES 短时不可用 | 系统会降级、不阻塞业务；优先查本地文件日志。 |
| MQL 报实体不存在 / 查不到业务实体 | 确认业务包已加入 `geelato.meta.scan-package-names` 与 `scanBasePackages`（见 [FAQ](faq.md)）。 |
| 动态数据源切换不生效 | 确认热刷新已同时执行 Registry 与 RoutingDataSource 两步刷新（见 [FAQ](faq.md)）。 |
| 保存后审计字段未自动填充 | 确认字段填充 SPI 实现唯一且 `isEnabled()` 返回 true（见 [FAQ](faq.md)）。 |
| 启动报 `NoUniqueBeanDefinitionException`（MetaStore） | 自定义实现加 `@Primary`（见 [FAQ](faq.md)）。 |
| SPI 注册后报 `IllegalStateException` | 同一类 SPI 存在多个实现，收敛为单个（见 [FAQ](faq.md)）。 |
| 重启后表结构未自动更新 | `auto-init-tables` 仅首次建表，不做 `ALTER`（见 [FAQ](faq.md)）。 |

## 相关文档

- [错误码参考](../reference/error-codes.md)
- [术语表](glossary.md)
- [常见问题](faq.md)
- [MQL 使用指引](../mql/usage.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
- [动态数据源：内网 Host/Port 映射](../dynamic-datasource/host-mapping.md)
