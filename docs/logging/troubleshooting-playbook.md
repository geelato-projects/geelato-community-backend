# Geelato 报障排查手册

## 1. 用户提供信息
- 账号：`loginName`（优先）或 `userId`。
- 时间段：精确到秒，建议 5-30 分钟窗口。

## 2. 一线排查步骤
1. 调用 `/api/run/log/searchByAccountTime?account={account}&from={from}&to={to}`。
2. 在返回结果中拿到 `traceId`、`logTag`、`uri`、`status`、`errorCode`。
3. 如果有 `logTag`，继续调用 `/api/run/log/search?tag={logTag}` 读取异常上下文。
4. 以 `traceId` 在请求日志、业务日志、数据层日志中串联完整链路。

## 3. 研发深挖步骤
1. 先看请求日志的 `uri/status/durationMs` 判断是否慢请求或错误返回。
2. 再看异常日志中的 `errorCode/logTag` 判断业务异常或系统异常。
3. 最后看 DAO/动态数据源日志，确认 SQL 与数据源切换是否异常。

## 4. 常见问题
- 查不到数据：扩大时间窗，确认账号口径（先 loginName 后 userId）。
- 只有请求无异常：检查 `status` 与 `durationMs`，可能是慢请求或前端中断。
- ES短时不可用：系统会降级，不阻塞业务；优先查本地文件日志。
