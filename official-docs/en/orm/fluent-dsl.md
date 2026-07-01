# Fluent DSL Guide

This page explains how backend Java services use the Geelato Framework Fluent DSL through `MetaFactory`.

## Goal

- provide a Java-style metadata CRUD entry for backend developers
- avoid direct MQL JSON construction while still reusing the existing `MetaQLManager + SqlManager + Dao` execution kernel

## When to Use It

- when backend services need metadata query, insert, update, or delete by entity name or entity class
- when the service wants to reuse built-in capabilities such as dynamic datasource switching, view parameters, and `$ctx/$fn/$parent` value references

## When Not to Use It

- when frontend pages already go through the platform data API based on `MetaController + MQL`
- when an existing service already works well with `BaseService + entity class` and does not need metadata-driven unification

## Entry Points

By entity name:

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name", "mobilePhone"})
        .where(Filter.eq("delStatus", 0))
        .order(Order.desc("updateAt"))
        .list();
```

By entity class:

```java
List<Map<String, Object>> users = MetaFactory.query(User.class)
        .select(new String[]{"id", "name"})
        .page(1, 20)
        .list();
```

## Query Examples

Single row:

```java
Map<String, Object> user = MetaFactory.query("User")
        .where(Filter.eq("id", "1912345678901234567"))
        .one();
```

Paged query:

```java
PageResult<Map<String, Object>> page = MetaFactory.query("User")
        .select(new String[]{"id", "name", "updateAt"})
        .where(Filter.like("name", "zhang"))
        .order(Order.desc("updateAt"))
        .page(1, 10)
        .page();
```

Single-column query:

```java
List<String> ids = MetaFactory.query("User")
        .select(new String[]{"id"})
        .where(Filter.eq("delStatus", 0))
        .oneColumn(String.class);
```

Result wrapping:

```java
List<String> names = MetaFactory.query("User")
        .select(new String[]{"name"})
        .wrapperResult(row -> String.valueOf(row.get("name")))
        .list();
```

## Write Examples

Insert:

```java
String userId = MetaFactory.insert("User")
        .value("name", "test user")
        .value("mobilePhone", "13800000000")
        .save();
```

Default fields are filled before save:

- inserts align with the current MQL rules and fill fields such as `createAt`, `creator`, `creatorName`, `tenantCode`, `buId`, `deptId`, `updateAt`, `updater`, `updaterName`, and `deleteAt`
- updates fill `updateAt`, `updater`, and `updaterName`
- the behavior comes from the built-in default filler and can be customized by overriding `SaveDefaultValueFiller`

Update:

```java
String userId = MetaFactory.update("User")
        .value("id", "1912345678901234567")
        .value("name", "new name")
        .save();
```

Delete:

```java
int affected = MetaFactory.delete("User")
        .where(Filter.eq("id", "1912345678901234567"))
        .delete();
```

## Advanced Capabilities

Referenced field join:

```java
List<Map<String, Object>> orders = MetaFactory.query("Order")
        .select(new String[]{"id", "code"})
        .selectRef("userId->name", "userName")
        .list();
```

Custom join:

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

Dynamic datasource:

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("portal")
        .page(1, 10)
        .list();
```

View parameters:

```java
List<Map<String, Object>> rows = MetaFactory.query("SomeViewEntity")
        .viewParams(Map.of("customerId", "C001"))
        .page(1, 10)
        .list();
```

Context and function value references:

```java
String id = MetaFactory.insert("Notice")
        .value("creator", ValueRefs.ctx("userId"))
        .value("createAt", ValueRefs.fnNowDateTime())
        .save();
```

MySQL procedure:

```java
List<Map<String, Object>> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .useDataSource("portal")
        .list();
```

Native SQL passthrough:

```java
List<Map<String, Object>> rows = MetaFactory.sql("select id, name from platform_user where del_status = ?")
        .param(0)
        .useDataSource("portal")
        .list();
```

Parent-child nested save:

```java
String parentId = MetaFactory.insert("App")
        .value("name", "demo-app")
        .child("AppVersion", child -> child
                .value("appId", ValueRefs.parent("id"))
                .value("code", "v1"))
        .save();
```

## Join Notes

- `selectRef(...)` fits the case where the main entity already has foreign-key metadata
- the framework can derive the `left join` automatically, so the service code does not need to write the join condition
- if you omit an explicit alias in `selectRef(...)`, the result column uses the remote field name by default

```java
List<Map<String, Object>> orders = MetaFactory.query("Order")
        .select(new String[]{"id", "code", "amount"})
        .selectRef("userId->name", "userName")
        .selectRef("userId->mobilePhone", "userMobile")
        .where(Filter.eq("delStatus", 0))
        .order(Order.desc("updateAt"))
        .list();
```

- custom join fits cases without foreign-key metadata or cases that need explicit join control
- use a stable alias convention, such as `.as("o")` for the main table and short aliases such as `u` or `d` for joined tables
- keep aliases consistent across `selectExpr(...)`, `groupBy(...)`, and `havingSql(...)`

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

## Procedure Notes

- current procedure support is intentionally lightweight and mainly targets MySQL-style `IN` parameters plus one result set
- parameters are added in call order through `.in(name, value)`
- the `name` is mainly for readability; runtime binding is positional

```java
Map<String, Object> row = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .in("status", 1)
        .one();
```

Object wrapping still works:

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

## Native SQL Notes

- use `MetaFactory.sql(...)` when the service already owns the final SQL and only wants to reuse the execution chain
- this path provides datasource switching, parameter binding, and terminal execution methods
- it does not add metadata-aware joins, default audit fields, or expression conversion

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

## Debugging Tips

- use `toSql()` to inspect the generated SQL or procedure call
- use `toCountSql()` when checking pagination count issues
- for join issues, verify aliases and foreign-key metadata first
- for procedure issues, verify parameter order, datasource choice, and result-set shape first
- for native SQL, verify the SQL itself, placeholder count, and terminal method choice first

## Recommended Boundary

- use Fluent DSL first for standard single-table CRUD, light references, and lightweight aggregation
- use `selectRef(...)` first when foreign-key metadata already exists
- use explicit join only when the metadata path is not enough
- use `MetaFactory.sql(...)` when the team intentionally owns the SQL text
- keep MQL, SQL Key, or MyBatis for very complex filtering, recursive SQL, window functions, or multi-result-set procedures

## Suggested Next Reading

- [ORM Overview](overview.md)
- [ORM Annotations](annotations.md)
- [Core Modules](../reference/core-modules.md)
