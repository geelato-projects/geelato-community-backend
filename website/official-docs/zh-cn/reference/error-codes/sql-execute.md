# 10010 SQL执行异常

`SqlExecuteException` 在 ORM 层执行 SQL 失败时抛出。这是平台中最常见、排障信息最丰富的异常之一，因此提供独立详情页。

- **错误码**：`10010`
- **错误码枚举**：`CoreErrorCodes.SQL_EXECUTE`
- **所在类**：`cn.geelato.core.orm.SqlExecuteException`
- **文档 slug**：`sql-execute`（docUrl 指向本页）
- **抛出位置**：`Dao.execute(...)` 模板方法捕获 Spring `DataAccessException` 后统一包装抛出，全工程 `Dao` / `BoundSqlJdbcSupport` 中约 16 处。

## 错误含义

底层 JDBC 执行 SQL 时抛出异常（语法错误、约束冲突、连接失败、字段不存在、死锁等），由 `geelato-core` 的 ORM 模板统一捕获并包装为 `SqlExecuteException`。

异常响应的 `errorMsg` 会包含结构化的排障信息：

```
SQL执行异常
原因：<DataAccessException 根因消息>
执行SQL：<实际执行的 SQL>
参数：<绑定参数数组>
数据库错误码：<JDBC errorCode，如 1062 / 1216 / 1205>
SQL状态码：<SQLState，如 23000>
```

同时异常对象（`ApiResult.data`）还会携带富字段（仅在 `GlobalContext.getLogStack()=true` 时随响应返回）：

- `sql` —— 执行的 SQL
- `params` —— 绑定参数
- `dbErrorCode` —— 数据库厂商错误码
- `sqlState` —— SQL 标准 SQLState
- `originalSqlException` / `originalDataAccessException` —— 原始异常引用

## 常见原因

- **唯一约束冲突**（MySQL `errorCode=1062`，SQLState `23000`）：插入了重复的主键/唯一键值。
- **外键约束失败**（MySQL `errorCode=1216/1452`）：引用了不存在的父表记录。
- **字段不存在**（MySQL `errorCode=1054`）：实体字段映射与数据库表结构不一致。
- **死锁/锁等待超时**（MySQL `errorCode=1205/1213`）：并发事务互相等待。
- **连接失效**：连接池连接被数据库侧关闭。

## 排查步骤

1. 从异常响应的 `errorMsg` 中提取 **执行SQL** 与 **数据库错误码**。
2. 用提取到的 SQL 在目标数据库手动执行，复现问题。
3. 根据数据库错误码定位具体原因（参见上节"常见原因"）。
4. 若 SQL 涉及动态参数，对照 **参数** 列表核验类型与取值。

## 示例

TODO：补充典型示例。
