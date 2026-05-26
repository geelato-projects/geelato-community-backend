# geelato-orm

`geelato-orm` 提供面向后端开发者的 Fluent DSL 入口，底层复用 `geelato-core` 的元数据命令执行能力。

## 快速开始

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name"})
        .page(1, 10)
        .list();
```

```java
String id = MetaFactory.insert("User")
        .value("name", "测试用户")
        .save();
```

```java
String id = MetaFactory.update("User")
        .value("id", "1912345678901234567")
        .value("name", "新名称")
        .save();
```

```java
int affected = MetaFactory.delete("User")
        .where(Filter.eq("id", "1912345678901234567"))
        .delete();
```

## 主要能力
- 支持 `query("Entity")` 与 `query(Entity.class)` 双入口
- 支持分页、单条、单列、包装结果
- 支持新增、更新、删除、批量保存
- 支持动态数据源、视图模板参数、`ValueRefs.ctx/fn/parent`

## 说明
- 调试可使用 `toSql()` / `toCountSql()` 查看内核生成语句
- 更完整的使用说明见 `docs/orm/backend-fluent-dsl-guide.md`
