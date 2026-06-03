# Geelato ORM 后端 Fluent DSL 指引

## 目标
- 面向后端开发者提供一套 Java 风格的元数据 CRUD 入口。
- 避免直接手写 MQL JSON，同时继续复用 `MetaQLManager + SqlManager + Dao` 现有内核。

## 何时使用
- 需要在 Java 服务代码中按实体名或实体类做元数据查询、保存、更新、删除时使用。
- 需要复用现有动态数据源、视图模板参数、`$ctx/$fn/$parent` 这类内核能力时使用。

## 何时不使用
- 前端页面直接走平台通用数据接口时，继续使用 `MetaController + MQL`。
- 已有服务已经稳定依赖 `BaseService + 实体类` 且没有元数据通用化诉求时，可继续沿用原模式。

## 入口
- 字符串实体名：

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name", "mobilePhone"})
        .where(Filter.eq("delStatus", 0))
        .order(Order.desc("updateAt"))
        .list();
```

- 实体类：

```java
List<Map<String, Object>> users = MetaFactory.query(User.class)
        .select(new String[]{"id", "name"})
        .page(1, 20)
        .list();
```

## 查询示例
- 单条查询：

```java
Map<String, Object> user = MetaFactory.query("User")
        .where(Filter.eq("id", "1912345678901234567"))
        .one();
```

- 分页查询：

```java
PageResult<Map<String, Object>> page = MetaFactory.query("User")
        .select(new String[]{"id", "name", "updateAt"})
        .where(Filter.like("name", "张"))
        .order(Order.desc("updateAt"))
        .page(1, 10)
        .page();
```

- 单列查询：

```java
List<String> ids = MetaFactory.query("User")
        .select(new String[]{"id"})
        .where(Filter.eq("delStatus", 0))
        .oneColumn(String.class);
```

- 包装结果：

```java
List<String> names = MetaFactory.query("User")
        .select(new String[]{"name"})
        .wrapperResult(row -> String.valueOf(row.get("name")))
        .list();
```

## 写入示例
- 新增：

```java
String userId = MetaFactory.insert("User")
        .value("name", "测试用户")
        .value("mobilePhone", "13800000000")
        .save();
```

- 默认字段会在保存前自动补齐：
  - 新增场景默认对齐 MQL 规则，自动补 `createAt / creator / creatorName / tenantCode / buId / deptId / updateAt / updater / updaterName / deleteAt`
  - 更新场景默认自动补 `updateAt / updater / updaterName`
  - 这些规则来自 DSL 内置的默认字段 filler，业务侧可通过覆盖 `SaveDefaultValueFiller` Bean 自定义

- 更新：

```java
String userId = MetaFactory.update("User")
        .value("id", "1912345678901234567")
        .value("name", "新名称")
        .save();
```

- 删除：

```java
int affected = MetaFactory.delete("User")
        .where(Filter.eq("id", "1912345678901234567"))
        .delete();
```

## 高级能力
- 多表 join：

```java
List<Map<String, Object>> orders = MetaFactory.query("Order")
        .select(new String[]{"id", "code"})
        .selectRef("userId->name", "userName")
        .list();
```

- 自定义 join on：

```java
List<Map<String, Object>> rows = MetaFactory.query("Order")
        .as("o")
        .select(new String[]{"id", "code"})
        .selectExpr("u.name", "userName")
        .leftJoin("User", "u", on -> on.eqField("userId", "u.id"))
        .groupBy("id", "code", "u.name")
        .havingSql("count(*) > 0")
        .page(1, 20)
        .page();
```

- 动态数据源：

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("portal")
        .page(1, 10)
        .list();
```

- 视图模板参数：

```java
List<Map<String, Object>> rows = MetaFactory.query("SomeViewEntity")
        .viewParams(Map.of("customerId", "C001"))
        .page(1, 10)
        .list();
```

- 上下文与函数值引用：

```java
String id = MetaFactory.insert("Notice")
        .value("creator", ValueRefs.ctx("userId"))
        .value("createAt", ValueRefs.fnNowDateTime())
        .save();
```

- MySQL 存储过程：

```java
List<Map<String, Object>> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .useDataSource("portal")
        .list();
```

- 原生 SQL 直通执行：

```java
List<Map<String, Object>> rows = MetaFactory.sql("select id, name from platform_user where del_status = ?")
        .param(0)
        .useDataSource("portal")
        .list();
```

- 父子嵌套保存：

```java
String parentId = MetaFactory.insert("App")
        .value("name", "demo-app")
        .child("AppVersion", child -> child
                .value("appId", ValueRefs.parent("id"))
                .value("code", "v1"))
        .save();
```

## 多表 join 补充说明
- 自动关联 join 适合“主实体存在外键元数据”的场景，`selectRef("userId->name", "userName")` 的含义是：
  - `userId` 是主实体上的外键字段
  - `name` 是关联实体上要取出的字段
  - `userName` 是结果集里的列别名
- 自动关联 join 会基于元数据外键自动补 `left join`，业务侧不需要手写 `on`。
- 若只写 `selectRef("userId->name")`，结果列名默认沿用远端字段名；建议在接口对外返回时显式设置别名，避免与主表字段重名。

```java
List<Map<String, Object>> orders = MetaFactory.query("Order")
        .select(new String[]{"id", "code", "amount"})
        .selectRef("userId->name", "userName")
        .selectRef("userId->mobilePhone", "userMobile")
        .where(Filter.eq("delStatus", 0))
        .order(Order.desc("updateAt"))
        .list();
```

- 自定义 join 适合“没有外键元数据”或“需要显式控制关联方式”的场景，推荐固定一套别名约定：
  - 主表先调用 `.as("o")`
  - 关联表统一给出短别名，如 `"u"`、`"d"`、`"t"`
  - `selectExpr(...)`、`groupBy(...)`、`havingSql(...)` 中都使用同一套别名
- `leftJoin/innerJoin/rightJoin` 的 `on` 目前推荐优先使用 `eqField(...)`，表示“字段 = 字段”的关联。

```java
List<Map<String, Object>> rows = MetaFactory.query("Order")
        .as("o")
        .select(new String[]{"id", "code"})
        .selectExpr("u.name", "userName")
        .selectExpr("d.name", "deptName")
        .leftJoin("User", "u", on -> on.eqField("o.userId", "u.id"))
        .leftJoin("Dept", "d", on -> on.eqField("u.deptId", "d.id"))
        .groupBy("id", "code", "u.name", "d.name")
        .havingSql("count(*) > 0")
        .page(1, 20)
        .page();
```

- 自定义 join 里的字段引用建议显式带别名，例如 `o.userId`、`u.id`，这样更容易排障，也更能避免同名字段冲突。
- `select(new String[]{...})` 仍优先用于主实体字段；关联表字段、聚合表达式建议走 `selectExpr(...)`。
- `groupBy(...)` 建议填写最终 SQL 中使用的字段或表达式；如果主表设置了 `.as("o")`，则分组字段建议同步写成 `o.id`、`u.name` 这种显式形式。
- `havingSql(...)` 是原始 SQL 片段，适合聚合后的过滤条件，例如 `count(*) > 0`、`sum(o.amount) >= 1000`。

## 存储过程补充说明
- 当前存储过程能力定位为 MySQL 场景下的轻量调用封装，适用于：
  - 过程只接收 `IN` 参数
  - 过程返回单个结果集
  - 业务侧只需要 `list()` 或 `one()` 读取结果
- 参数通过 `.in(name, value)` 按调用顺序加入，占位符顺序与 `.in(...)` 的书写顺序一致。
- `name` 主要用于可读性和排障，当前运行时按顺序绑定参数，不依赖名称映射。

```java
Map<String, Object> row = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .one();
```

- 如果过程结果需要直接转换成 DTO，可继续复用 `wrapperResult(...)`：

```java
List<OrderSimpleDto> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .wrapperResult(row -> {
            OrderSimpleDto dto = new OrderSimpleDto();
            dto.setId(String.valueOf(row.get("id")));
            dto.setCode(String.valueOf(row.get("code")));
            return dto;
        })
        .list();
```

- 调试时可先调用 `toSql()` 查看生成的 `call proc_name(?, ?)` 语句，再结合参数顺序排查问题。
- 如果存储过程依赖特定数据源，可继续使用 `.useDataSource("portal")` 指定连接。

## 原生 SQL 直通补充说明
- 当业务侧已经持有完整 SQL，且不希望再拆分成 `MetaQuery` / `MetaInsert` / `MetaUpdate` 时，可使用 `MetaFactory.sql(...)`。
- 该入口属于“直通执行”能力，ORM 只负责：
  - 动态数据源切换
  - 参数顺序绑定
  - `list()/one()/queryForObject()/execute()` 终止执行
- 该入口不会为原生 SQL 自动补元数据字段、外键 join、默认审计字段或条件表达式转换。
- 推荐把占位参数写成 `?`，再通过 `.param(...)` 或 `.params(...)` 传值。

```java
Map<String, Object> row = MetaFactory.sql("select id, name from platform_user where id = ?")
        .param("U1001")
        .one();
```

```java
Long total = MetaFactory.sql("select count(1) from platform_user where del_status = ?")
        .param(0)
        .queryForObject(Long.class);
```

```java
int affected = MetaFactory.sql("update platform_notice set status = ? where id = ?")
        .params("read", "N1001")
        .execute();
```

- `list()` 返回 `List<Map<String, Object>>`
- `one()` 返回单行 `Map<String, Object>`，若无结果返回 `null`
- `queryForObject(Class<T>)` 适合查询单值，如 `Long`、`Integer`、`String`
- `execute()` 适合执行 `update/delete/insert` 这类返回影响行数的 SQL
- 若查询结果仍需转 DTO，可继续使用 `wrapperResult(...)`

```java
List<UserSimpleDto> users = MetaFactory.sql("select id, name from platform_user where del_status = ?")
        .param(0)
        .wrapperResult(row -> new UserSimpleDto(
                String.valueOf(row.get("id")),
                String.valueOf(row.get("name"))
        ))
        .list();
```

## 别名与字段书写建议
- 单表查询时，`select("id")`、`where(Filter.eq("id", ...))` 继续按主实体字段名书写即可。
- 自定义 join 查询时，建议遵循“主表字段显式带主别名、关联字段显式带关联别名”的规则。
- `selectRef("userId->name")` 使用的是“字段路径”语义，不是原始 SQL 表达式，不需要写表别名。
- `selectExpr("u.name", "userName")`、`havingSql("count(*) > 0")` 使用的是原始 SQL 片段语义，需要业务侧自行保证别名、字段名和数据库函数正确。
- 若查询字段与接口对象字段不一致，优先通过别名对齐，再决定是否使用 `wrapperResult(...)` 做二次转换。
- `MetaFactory.sql(...)` 传入的是最终 SQL，不再区分实体字段名与列名，业务侧直接按数据库真实列名、表名、别名书写。

## 调试与排障
- `toSql()` 用于查看当前查询或存储过程最终生成的 SQL。
- `toCountSql()` 只适用于分页查询，常用于排查分页总数不准确的问题。
- 排查 join 问题时，优先检查：
  - 主表是否调用了 `.as(...)`
  - `leftJoin(..., "u", ...)` 中的别名是否与 `selectExpr/groupBy/havingSql` 保持一致
  - `selectRef("field->remoteField")` 中的外键字段是否真的存在元数据外键定义
- 排查存储过程问题时，优先检查：
  - `.in(...)` 参数顺序是否与数据库过程定义一致
  - 当前数据源是否正确
  - 过程是否只返回一个结果集
- 排查原生 SQL 问题时，优先检查：
  - SQL 本身是否可在数据库客户端直接执行
  - `?` 占位符数量是否与 `.param/.params` 传值一致
  - 返回单值时是否选择了 `queryForObject(Class<T>)`
  - 写入 SQL 是否调用了 `execute()`

## 推荐使用边界
- 简单单表 CRUD、带少量关联字段的列表页、轻量聚合查询，优先使用 Fluent DSL。
- 已存在外键元数据时，优先使用 `selectRef(...)`，比手写 join 更短、更稳定。
- 需要显式控制关联表、别名和 `on` 条件时，再使用 `leftJoin/innerJoin/rightJoin`。
- 已经存在成熟 SQL、报表 SQL、临时排障 SQL，且业务方明确接受“自己维护完整 SQL”时，可使用 `MetaFactory.sql(...)`。
- 超复杂跨组过滤、递归 CTE、窗口函数、多结果集存储过程，继续保留 MQL / SQL Key / MyBatis。

## 约束说明
- 当前 Fluent DSL 的过滤表达仍以简单 `Filter` 组合为主，复杂跨组逻辑建议继续走 MQL。
- 自定义 join 的 `on` 条件、`selectExpr(...)`、`havingSql(...)` 以原始 SQL 片段表达式为主，业务侧需保证字段名与别名正确。
- 当前 `where(Filter...)` 仍优先面向主实体字段；若涉及复杂关联过滤，建议继续走 MQL / SQL Key / MyBatis。
- `MetaFactory.sql(...)` 不做 SQL 安全兜底与元数据纠错，完整 SQL 的正确性、兼容性、可维护性由调用方负责。
- `update()` 推荐显式传 `id` 或显式 `where(...)`，避免更新条件不清晰。
- `toSql()` 与 `toCountSql()` 用于排障和调试，不建议把其输出当成业务主流程接口。
- DSL 写入链路在生成 `SaveCommand` 后、生成 SQL 前执行默认字段填充。
- 框架提供一份与当前 MQL 规则对齐的默认实现；若业务需要差异化规则，可覆盖 `SaveDefaultValueFiller` Bean。

## 迁移建议
- 原来后端手写 MQL JSON 的场景，可优先迁移到 `MetaFactory.query/insert/update/delete`。
- 原来仅依赖具体实体类的 `BaseService` 场景，无需强制迁移；只有在需要元数据统一能力时再切换。

## P1 替代样板
- `QueryWrapper.like/eq/orderByDesc` 可直接映射为 `MetaFactory.query(...).where(...).order(...)`。
- `BaseMapper.insert/updateById` 可映射为 `MetaFactory.insert/update`，实体转 `Map` 后仅保留非空业务字段，审计字段交给 DSL 默认 filler 补齐。
- 查询结果默认返回 `Map<String, Object>`；若接口仍对外暴露实体对象，可用 `wrapperResult(...)` 或服务层统一做 `Map -> Entity` 转换。

```java
List<Tenant> tenants = MetaFactory.query(Tenant.class)
        .where(
                Filter.like("code", code),
                Filter.eq("delStatus", 0)
        )
        .order(Order.desc("createAt"))
        .wrapperResult(row -> JSON.parseObject(JSON.toJSONString(row), Tenant.class))
        .list();
```

```java
String noticeId = MetaFactory.update(Notice.class)
        .where(
                Filter.eq("id", id),
                Filter.eq("delStatus", 0)
        )
        .value("status", "read")
        .save();
```

## 保留 MyBatis 的场景
- 递归 CTE、超复杂聚合统计、多结果集存储过程，继续保留原生 SQL / MyBatis。
- 结果映射高度依赖手写 `resultMap` 的场景，优先保持现状，避免在 P1 阶段引入行为偏差。
