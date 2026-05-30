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

- 父子嵌套保存：

```java
String parentId = MetaFactory.insert("App")
        .value("name", "demo-app")
        .child("AppVersion", child -> child
                .value("appId", ValueRefs.parent("id"))
                .value("code", "v1"))
        .save();
```

## 约束说明
- 当前 Fluent DSL 的过滤表达仍以简单 `Filter` 组合为主，复杂跨组逻辑建议继续走 MQL。
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
- 多表 join、递归 CTE、复杂聚合统计，继续保留原生 SQL / MyBatis。
- 结果映射高度依赖手写 `resultMap` 的场景，优先保持现状，避免在 P1 阶段引入行为偏差。
