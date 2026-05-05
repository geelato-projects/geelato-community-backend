# Geelato 日志规范

## 1. 目标
- 报障最小输入：`账号(loginName/userId)` + `故障时间段`。
- 研发侧定位路径：先查请求日志拿 `traceId/logTag`，再关联异常日志与业务日志。

## 2. 日志分层
- 访问日志：请求入口、响应结果、耗时、状态码。
- 业务日志：关键业务动作（新增/更新/删除/审批/消息发送）。
- 异常日志：统一异常模型、错误码、关联键。
- 安全日志：认证授权、失败原因、来源IP（敏感信息脱敏）。
- 数据层日志：DAO执行、动态数据源切换、慢操作告警。

## 3. 统一字段
- 必填：`traceId` `requestId` `userId` `loginName` `tenantCode` `uri` `method` `status` `durationMs` `errorCode` `logTag`。
- ES扩展：`app` `env` `logger` `thread` `@timestamp`。

## 4. 级别规范
- `INFO`：关键业务成功路径、请求摘要、数据源切换成功。
- `WARN`：可恢复异常、参数不合法、外部依赖抖动、降级行为。
- `ERROR`：业务失败或系统异常，必须带 `traceId/logTag/errorCode`。
- `DEBUG`：排障临时信息，默认关闭，避免生产噪音。

## 5. 脱敏规则
- 必脱敏字段：`password` `token` `authorization` `apiKey` `secret` `accessToken` `refreshToken`。
- 禁止打印：完整凭证、私钥、会话密钥、明文密码。
- 推荐打印：掩码摘要（前4后4）与上下文（IP、账号、路径、时间）。

## 6. ES写入规范
- 索引命名：`${geelato.es.log-index-prefix}${category}-${yyyy.MM.dd}`。
- 分类：`app` `request` `interceptor` `auth` `message` `schedule`。
- 写入方式：应用内异步批量 `bulk`，失败重试，不阻塞业务线程。
- 关键开关：`geelato.es.log-enabled` `geelato.es.log-bulk-size` `geelato.es.log-flush-interval-ms`。
