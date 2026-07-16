# Geelato MQL 到 SQL 执行时序图

## 目标

本文说明 `geelato-community` 中一条典型 MQL 查询请求，从 Web 入口进入后，如何逐步完成：

- MQL JSON 解析
- `QueryCommand` 构造
- 条件与参数组装
- SQL 文本生成
- JDBC 参数绑定与数据库执行

同时补充 ORM Fluent DSL 与这条链路的复用关系。

## 核心调用链

```text
HTTP /meta/list
-> MetaRuntimeController
-> RuleService
-> MetaQLManager
-> JsonTextQueryParser
-> QueryCommand
-> SqlManager
-> MetaQuerySqlProvider
-> MetaBaseSqlProvider
-> BoundSql / BoundPageSql
-> Dao
-> JdbcTemplate
-> Database
```

## 主查询时序图

```mermaid
sequenceDiagram
    autonumber
    participant Client as 前端/调用方
    participant C as MetaRuntimeController
    participant R as RuleService
    participant M as MetaQLManager
    participant P as JsonTextQueryParser
    participant S as SqlManager
    participant Q as MetaQuerySqlProvider
    participant B as MetaBaseSqlProvider
    participant D as Dao
    participant J as JdbcTemplate
    participant DB as Database

    Client->>C: POST /meta/list?gql=...
    C->>C: resolveQueryPayload()
    C->>R: queryForMapList(gql, withMeta, paramsByEntity)

    R->>M: generateQuerySql(gql)
    M->>P: parse(gql)

    P->>P: 校验 entity
    P->>P: 创建 QueryCommand
    P->>P: 默认加入 tenantCode 过滤
    P->>P: resolveOriginalWhere() 注入数据权限
    P->>P: 解析 @fs / @order / @p / @b
    P->>P: 普通字段转 FilterGroup.Filter
    P-->>M: QueryCommand
    M-->>R: QueryCommand

    R->>R: applyViewTemplateParams()
    R->>R: processQueryCommandFunctions()
    R->>S: generatePageQuerySql(command)

    S->>Q: generate(command)
    Q->>Q: buildOneSql()
    Q->>Q: build select/from/join
    Q->>B: buildConditions(where)
    B->>B: 递归展开 FilterGroup / childFilterGroup
    B->>B: buildWhereParams()
    B->>B: buildWhereTypes()
    B-->>Q: SQL片段 + params + types
    Q-->>S: BoundSql

    S->>Q: buildCountSql(command)
    Q-->>S: countSql
    S-->>R: BoundPageSql

    R->>D: queryForMapList(boundPageSql)
    D->>J: query(sql, params, types)
    J->>DB: PreparedStatement 查询
    DB-->>J: rows
    J-->>D: List<Map>

    R->>D: queryTotal(boundPageSql)
    D->>J: query(countSql, params, types)
    J->>DB: 执行 count
    DB-->>J: total
    J-->>D: total

    D-->>R: data + total
    R-->>C: page result
    C-->>Client: ApiPagedResult
```

## 条件与参数构造时序图

```mermaid
sequenceDiagram
    autonumber
    participant P as JsonTextQueryParser
    participant FG as FilterGroup
    participant Q as MetaQuerySqlProvider
    participant B as MetaBaseSqlProvider
    participant O as ConditionOperator
    participant BS as BoundSql
    participant J as JdbcTemplate

    P->>FG: addFilter(field, operator, value/rawValue)
    P->>FG: setChildFilterGroup(...) 处理 @b 括号嵌套
    P-->>Q: QueryCommand(where = FilterGroup)

    Q->>B: buildConditions(filterGroup)
    B->>B: 遍历 filters
    B->>O: ConditionOperator.from(operator)
    O-->>B: 生成条件片段
    Note over O,B: eq -> col = ?\ncontains -> col like CONCAT('%',?,'%')\nin -> col in(?,?,?)\nnil -> is null / is not null

    B->>B: 递归 childFilterGroup
    Note over B: 生成 where SQL，参数占位顺序与过滤器遍历顺序保持一致

    Q->>B: buildWhereParams(command)
    B->>B: 按相同顺序提取参数
    Note over B: 优先 rawValue，其次 value\nin/notin 会展开多个参数\nnil/bt/fis 不生成参数

    Q->>B: buildWhereTypes(command)
    B->>B: 按字段元数据推导 JDBC Types
    Note over B: like 一律 VARCHAR\n普通字段按列类型映射

    B-->>BS: sql + params[] + types[]
    BS->>J: query/update(sql, params, types)
    Note over J: 这里才是真正的参数绑定点
```

## ORM Fluent DSL 复用链路

ORM Fluent DSL 前半段不是解析 MQL JSON，而是先把 DSL 适配成同一个 `QueryCommand`，后半段继续复用 `SqlManager -> BoundSql -> Dao/JdbcTemplate`。

```mermaid
sequenceDiagram
    autonumber
    participant Biz as 业务代码
    participant MQ as MetaQuery
    participant A as QueryCommandAdapter
    participant F as FilterAdapter
    participant S as SqlManager
    participant Q as MetaQuerySqlProvider
    participant D as Dao/ExecutionStrategy
    participant J as JdbcTemplate
    participant DB as Database

    Biz->>MQ: MetaFactory.query(...).where(...).order(...).page(...)
    MQ->>A: forList(this)
    A->>F: adapt(filters)
    F-->>A: FilterGroup
    A-->>MQ: QueryCommand
    MQ->>S: generatePageQuerySql(QueryCommand)
    S->>Q: build SQL + params + types
    Q-->>S: BoundPageSql
    MQ->>D: queryForMapList / queryForPage
    D->>J: query(sql, params, types)
    J->>DB: 执行 SQL
    DB-->>J: 结果
    J-->>D: rows/total
    D-->>MQ: PageResult/List
    MQ-->>Biz: 查询结果
```

## 关键阶段说明

### 1. MQL 解析阶段

`JsonTextQueryParser` 负责把 JSON 文本变成 `QueryCommand`：

- 识别实体名
- 解析 `@fs`、`@order`、`@p`、`@b`
- 普通字段解析成 `FilterGroup.Filter`
- 默认增加 `tenantCode`
- 增加 `originalWhere` 数据权限条件

这一阶段还没有直接执行 SQL。

### 2. 条件表达阶段

`FilterGroup` 是 where 条件的中间表达结构：

- `filters` 表示当前层级条件
- `logic` 表示当前层级是 `and` 还是 `or`
- `childFilterGroup` 表示括号嵌套条件

因此复杂条件并不是先拼原始 SQL，而是先表达成树状条件模型。

### 3. SQL 生成阶段

`MetaQuerySqlProvider` 按固定顺序生成 SQL：

1. `select`
2. `from`
3. `join`
4. `where`
5. `and (originalWhere)`
6. `group by`
7. `having`
8. `order by`
9. `limit offset`

分页总数 SQL 则由 `buildCountSql()` 生成。

### 4. 参数绑定阶段

`MetaBaseSqlProvider` 做三件事：

- `buildConditions()` 生成带 `?` 的 where SQL
- `buildWhereParams()` 生成参数数组
- `buildWhereTypes()` 生成 JDBC 类型数组

参数顺序的根本原则是：

- SQL 中 `?` 的出现顺序
- `params[]` 的填充顺序
- `types[]` 的填充顺序

三者保持一致。

### 5. 数据库执行阶段

`Dao` 最终调用 `JdbcTemplate`：

- 查询：`query(...)`
- 单值：`queryForObject(...)`
- 更新：`update(...)`

如果 `BoundSql.types` 非空，则走带类型的 JDBC 参数绑定；否则只传 `params`。

## 参数与条件的几个关键结论

### 大多数普通条件会走参数绑定

例如：

- `eq`
- `neq`
- `gt/gte/lt/lte`
- `contains/startWith/endWith`
- `in/notin`

这类操作会生成 `?` 占位符，并在 `params[]` 中注入对应参数。

### `rawValue` 优先于字符串值

`FilterGroup.Filter` 同时保留：

- `value`：字符串值
- `rawValue`：原始 Java 类型值

组装参数时优先使用 `rawValue`，这样可以减少数值、布尔、日期类型在 JDBC 绑定时被错误当成字符串。

### 并非所有条件都完全参数化

当前实现中，下列内容可能直接写入 SQL 文本：

- `originalWhere` 数据权限 SQL
- 视图模板参数 `@pf`
- `bt`
- `fis`
- 某些 JSON 条件分支

所以这条链路是“以参数化为主”，但不是“全链路全部 PreparedStatement 化”。

## 建议阅读顺序

如果要继续顺着代码深挖，建议按下面顺序看：

1. `MetaRuntimeController`
2. `RuleService`
3. `MetaQLManager`
4. `JsonTextQueryParser`
5. `QueryKeyword`
6. `SqlManager`
7. `MetaQuerySqlProvider`
8. `MetaBaseSqlProvider`
9. `ConditionOperator`
10. `Dao`

## 关键代码位置

- `MetaRuntimeController.list`
  - `geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaRuntimeController.java`
- `RuleService.queryForMapList`
  - `geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/platform/service/RuleService.java`
- `MetaQLManager.generateQuerySql`
  - `geelato-core/src/main/java/cn/geelato/core/mql/MetaQLManager.java`
- `JsonTextQueryParser.parse`
  - `geelato-core/src/main/java/cn/geelato/core/mql/parser/JsonTextQueryParser.java`
- `QueryKeyword`
  - `geelato-core/src/main/java/cn/geelato/core/mql/parser/keyword/QueryKeyword.java`
- `SqlManager.generatePageQuerySql`
  - `geelato-core/src/main/java/cn/geelato/core/sql/SqlManager.java`
- `MetaQuerySqlProvider.buildOneSql`
  - `geelato-core/src/main/java/cn/geelato/core/sql/provider/MetaQuerySqlProvider.java`
- `MetaBaseSqlProvider.buildConditions/buildWhereParams/buildWhereTypes`
  - `geelato-core/src/main/java/cn/geelato/core/sql/provider/MetaBaseSqlProvider.java`
- `ConditionOperator`
  - `geelato-core/src/main/java/cn/geelato/core/sql/provider/ConditionOperator.java`
- `Dao`
  - `geelato-core/src/main/java/cn/geelato/core/orm/Dao.java`

## 总结

从架构上看，这套实现不是“前端 JSON 直接拼 SQL”，而是：

```text
MQL JSON
-> QueryCommand
-> FilterGroup
-> BoundSql
-> JdbcTemplate
-> Database
```

也就是说：

- MQL 先转语义对象
- SQL 再由元数据驱动生成
- 参数在 `BoundSql` 阶段统一收敛
- 最终由 Spring JDBC 完成绑定与执行

这是当前 `geelato-community` 中 MQL 与 ORM Fluent DSL 共用的底层执行主干。
