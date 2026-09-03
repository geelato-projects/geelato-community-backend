# 10002 SQL执行异常（根码，1002x 子类细分）

`SqlExecuteException` 在 ORM 层执行 SQL 失败时抛出。这是平台中最常见、排障信息最丰富的异常之一，因此提供独立详情页。

- **错误码**：`10002`（根码，兜底未分类 SQL 错误，如语法错误、权限不足）
- **错误码常量**：`SqlExecuteException.ERROR_CODE`
- **所在类**：`cn.geelato.core.orm.SqlExecuteException`
- **文档 slug**：`sql-execute`（docUrl 指向本页；子类继承同一 slug）
- **抛出位置**：`Dao` / `BaseDao` 捕获 Spring `DataAccessException` 后经 `SqlExecuteException.of(...)` 分类工厂包装抛出

## 错误含义

底层 JDBC 执行 SQL 时抛出异常（语法错误、约束冲突、连接失败、字段不存在、死锁等），由 `geelato-core` 的 ORM 模板统一捕获，`of(...)` 工厂按根因包装为对应子类（各持独立错误码），未归类返回根类。

### 前端看到的文案（getUserMessage）与错误码细分

默认（生产）不再向前端下发 SQL 语句与参数，`msg` 为按根因分类的友好文案，末尾追加错误码与反馈凭据（logTag）：

| 分类 | 错误码 | 异常类 | 判定依据 | 文案 |
|---|---|---|---|---|
| 连接中断 | 10021 | `SqlConnectionException` | `CannotGetJdbcConnectionException`、sqlState `08xxx`（PG 08001/08003/08006）、`Communications link failure`/`Connection refused` | 数据库连接中断，系统正在自动恢复，请稍后重试 |
| 死锁/锁等待 | 10022 | `SqlLockConflictException` | MySQL `1213/1205`、sqlState `40001`、PG `40P01`（死锁）/`55P03`（锁不可用，NOWAIT/lock_timeout） | 当前数据正被其他操作占用，请稍后重试 |
| 唯一键冲突 | 10023 | `SqlDuplicateKeyException` | MySQL `1062`、PG `23505` | 数据已存在，无法重复提交 |
| 外键/约束 | 10024 | `SqlConstraintViolationException` | MySQL `1451/1452`、sqlState `23xxx`（PG `23503` 外键/`23502` 非空/`23514` CHECK） | 数据存在关联引用或不符合约束，请检查后重试 |
| 数据超长 | 10025 | `SqlDataTooLongException` | MySQL `1406`、sqlState `22001` | 字段[字段名]的内容超出长度限制，请缩短后重试 |
| 数值超范围 | 10026 | `SqlDataOutOfRangeException` | MySQL `1690`、sqlState `22003` | 字段[字段名]的数值超出允许范围，请调整后重试 |
| 数据格式不正确 | 10027 | `SqlDataFormatException` | MySQL `1366`/`1292`、sqlState `22007`/`22008` | 字段[字段名]的数据格式不正确，请检查填写内容后重试 |
| 其他（根码） | 10002 | `SqlExecuteException` | 未命中上述分类（语法错误、字段不存在等） | 数据操作失败，请稍后重试 |

> 10025-10027 的用户文案中的"字段名"提取自数据库根因消息（MySQL 格式 `... for column 'xxx' at row 1`）；PG 消息通常不含字段名，退回通用文案（如"数据内容超出字段长度限制"）。

> PostgreSQL 说明：PG 驱动的 `getErrorCode()` 恒为 0，PG 判定全部依赖 sqlState。
> 兼容性：子类均继承 `SqlExecuteException`（`is-a` 成立，与 `FileException` 家族同模式），既有 `catch (SqlExecuteException)` 代码不受影响。

示例：`数据已存在，无法重复提交（错误码 10023，反馈凭据 123456789012345678）`。开发模式（`GlobalContext.getLogStack()=true`）下，`data.errorMsg` 保留下方完整技术文案，便于本地排障。

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

`Dao` 执行 SQL 前经 `JdbcRetryExecutor` 做**有限范围的透明重试**：仅当"从连接池获取连接失败"（`CannotGetJdbcConnectionException`，SQL 必然未发送到数据库，重新执行绝对安全）且无活动事务时，按固化策略重试 2 次（退避 300ms/800ms）。

以下情况**不重试**：

- 执行中途的连接断开（`Communications link failure`、sqlState `08xxx`、Transient 瞬时故障等）——SQL 可能已发送甚至已提交，重试有重复执行风险，由错误码细分（10021）+ 友好文案承接，交由用户重试；
- 活动事务内（`@Transactional`、`batchSave` 事务模式等）——事务连接绑定与多数据源路由语义不允许 Dao 层擅自重新获取连接。

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
