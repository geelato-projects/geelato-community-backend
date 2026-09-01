# 10002 SQL执行异常

`SqlExecuteException` 在 ORM 层执行 SQL 失败时抛出。这是平台中最常见、排障信息最丰富的异常之一，因此提供独立详情页。

- **错误码**：`10002`
- **错误码常量**：`SqlExecuteException.ERROR_CODE`
- **所在类**：`cn.geelato.core.orm.SqlExecuteException`
- **文档 slug**：`sql-execute`（docUrl 指向本页）
- **抛出位置**：`Dao.execute(...)` 模板方法捕获 Spring `DataAccessException` 后统一包装抛出，全工程 `Dao` 中约 16 处。

## 错误含义

底层 JDBC 执行 SQL 时抛出异常（语法错误、约束冲突、连接失败、字段不存在、死锁等），由 `geelato-core` 的 ORM 模板统一捕获并包装为 `SqlExecuteException`。

### 前端看到的文案（getUserMessage）

默认（生产）不再向前端下发 SQL 语句与参数，`msg` 为按根因分类的友好文案，末尾追加错误码与反馈凭据（logTag）：

| 分类 | 判定依据 | 文案 |
|---|---|---|
| 连接中断 | `CannotGetJdbcConnectionException`、sqlState `08xxx`、`Communications link failure`/`Connection refused` | 数据库连接中断，系统正在自动恢复，请稍后重试 |
| 死锁/锁等待 | MySQL `1213/1205`、sqlState `40001`、PG `40P01` | 当前数据正被其他操作占用，请稍后重试 |
| 唯一键冲突 | MySQL `1062`、PG `23505` | 数据已存在，无法重复提交 |
| 外键/约束 | MySQL `1451/1452`、sqlState `23xxx` | 数据存在关联引用或不符合约束，请检查后重试 |
| 其他 | — | 数据操作失败，请稍后重试 |

示例：`数据操作失败，请稍后重试（错误码 10002，反馈凭据 123456789012345678）`。开发模式（`GlobalContext.getLogStack()=true`）下，`data.errorMsg` 保留下方完整技术文案，便于本地排障。

### 技术详情（stackTraceDetail 与服务端日志双通道）

技术详情（含下方结构化信息）默认随异常响应的 `data.stackTraceDetail` 下发（`GlobalContext` LogStack 开关，默认开启，可关闭），同时写入服务端错误日志（`${LOG_DIR}/error/`，凭 logTag 检索）——报障时无需登服务器即可定位：

```
SQL执行异常
原因：<DataAccessException 根因消息>
执行SQL：<实际执行的 SQL>
参数：<绑定参数数组>
数据库错误码：<JDBC errorCode，如 1062 / 1216 / 1205>
SQL状态码：<SQLState，如 23000>
```

同时异常对象携带富字段（仅在 `GlobalContext.getLogStack()=true` 时随响应返回）：

- `sql` —— 执行的 SQL
- `params` —— 绑定参数
- `dbErrorCode` —— 数据库厂商错误码
- `sqlState` —— SQL 标准 SQLState
- `originalSqlException` / `originalDataAccessException` —— 原始异常引用

### 连接类故障的自动重试

`Dao` 执行 SQL 前经 `JdbcRetryExecutor` 对连接类瞬时故障（取连接失败、sqlState `08xxx`、`Communications link failure` 等）做透明重试：固化策略为重试 2 次（退避 300ms/800ms），与连接池 keepalive 配套、属平台必然行为，不设外部开关。**活动事务内不重试**（避免部分写入被重复执行），由上层事务回滚后交由用户重试。

## 常见原因

- **唯一约束冲突**（MySQL `errorCode=1062`，SQLState `23000`）：插入了重复的主键/唯一键值。
- **外键约束失败**（MySQL `errorCode=1216/1452`）：引用了不存在的父表记录。
- **字段不存在**（MySQL `errorCode=1054`）：实体字段映射与数据库表结构不一致。
- **死锁/锁等待超时**（MySQL `errorCode=1205/1213`）：并发事务互相等待。
- **连接失效**：连接池连接被数据库侧关闭（透明重试后仍失败才会报到前端）。

## 排查步骤

1. 从用户反馈文案中提取 **反馈凭据（logTag）**，在服务端错误日志（`${LOG_DIR}/error/`）检索 `logTag=` 定位对应记录。
2. 从日志中的技术详情提取 **执行SQL** 与 **数据库错误码**。
3. 用提取到的 SQL 在目标数据库手动执行，复现问题。
4. 根据数据库错误码定位具体原因（参见上节"常见原因"）。
5. 若 SQL 涉及动态参数，对照 **参数** 列表核验类型与取值。

## 示例

TODO：补充典型示例。
