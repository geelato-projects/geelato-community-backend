## 目标

为 `MetaFactory`（`geelato-orm/src/main/java/cn/geelato/orm/MetaFactory.java`）增加“基于实体对象”的重载：传入实体对象，按其（非空）映射属性预填 SQL 构建器。

**复用现有 fluent 链路，不新建 SQL 生成逻辑**：实体对象 → 反射出 field→value 的 Map / Filter → 预填到已有的 `MetaInsert`/`MetaUpdate`/`MetaDelete`/`MetaQuery`。`SaveCommandAdapter` 既有的 UID 生成、`defaultEntityMap` 合并、`FluentSaveFieldValueFillRuntimeResolver` 审计字段填充、按主键/过滤构造 WHERE、逻辑删除等能力全部自动生效。

## 设计原则

1. **跳过 null 字段**：反射仅采集非空属性。插入时未涉及字段回退默认值/填充器；更新时不以 NULL 覆盖已有列。
2. **与现有 String/Class 工厂方法一一对应**：现有 `insert(Class)`/`update(Class)`/`delete(Class)`/`query(Class)` 均返回**构建器**，故对象版同样返回预填好的构建器。
3. **不新增 `(entity, connectId)` 重载**：切换数据源一律通过返回构建器上的 `.useDataSource(x)` 链式调用。
4. **`save(entity)` 为唯一的“直接执行”便捷方法**（对应示例 `MetaFactory.save(new Meta())`），按主键自动判定插入/更新。

## 改动范围

仅修改一个文件：`MetaFactory.java`。反射逻辑作为该类 `private static` 辅助方法，最小侵入。

## 新增公共方法

| 方法 | 返回 | 行为 |
|---|---|---|
| `query(Object entity)` | `MetaQuery` | 非空字段 → `Filter.eq` 预填 WHERE；返回可继续 `.useDataSource(x).list()` 等的构建器。 |
| `insert(Object entity)` | `MetaInsert` | 非空字段 → 预填 `values(map)`；返回构建器，链式 `.useDataSource(x).save()`。主键缺省时由 `fromInsert` 生成 UID。 |
| `update(Object entity)` | `MetaUpdate` | 非空字段 → 预填 `values(map)`；若主键存在则预填 `where(eq(主键,主键值))`。返回构建器，链式 `.useDataSource(x).save()`。 |
| `delete(Object entity)` | `MetaDelete` | 非空字段 → 预填 `where(eq...)`。返回构建器，链式 `.useDataSource(x).delete()`。 |
| `save(Object entity)` | `String`（主键） | **直接执行**：主键为空→`insert(entity).save()`；主键非空→`update(entity).save()`。默认数据源。 |

> 重载无歧义：`String`/`Class<?>` 比 `Object` 更具体，`insert("x")`/`insert(Clazz.class)` 仍解析到旧重载，`insert(实体实例)` 解析到对象版。`save` 仅有 `Object` 版（无 `save(String/Class)`）。

## 私有辅助方法（`MetaFactory` 内）

- `private static EntityMeta metaOf(Object entity)`：`MetaManager.singleInstance().get(entity.getClass())`。
- `private static Map<String,Object> extractValues(Object entity)`：遍历 `EntityMeta.getFieldMetas()`，`PropertyUtils.getProperty(entity, fm.getFieldName())` 取值，**跳过 null**，装入 `LinkedHashMap`。反射异常（`IllegalAccessException|NoSuchMethodException|InvocationTargetException`）用 `@Slf4j` 记录后跳过该字段（与 `EntitySaveParser.parse:37-39` 一致）。
- `private static List<Filter> extractFilters(Object entity)`：同上遍历，非空字段生成 `Filter.eq(fieldName, value)`。
- `private static Object idValue(Object entity, EntityMeta em)`：`PropertyUtils.getProperty(entity, em.getId().getFieldName())`。
- `private static boolean hasId(Object id)`：`id != null && !(id instanceof String s && s.isBlank())`。

## 实现要点（伪码）

```java
public static MetaQuery query(Object entity) {
    MetaQuery q = new MetaQuery(entity.getClass());
    extractFilters(entity).forEach(q::where);
    return q;
}
public static MetaInsert insert(Object entity) {
    return new MetaInsert(entity.getClass()).values(extractValues(entity));
}
public static MetaUpdate update(Object entity) {
    MetaUpdate u = new MetaUpdate(entity.getClass()).values(extractValues(entity));
    EntityMeta em = metaOf(entity);
    Object id = idValue(entity, em);
    if (hasId(id)) u.where(Filter.eq(em.getId().getFieldName(), id));
    return u;
}
public static MetaDelete delete(Object entity) {
    MetaDelete d = new MetaDelete(entity.getClass());
    extractFilters(entity).forEach(d::where);
    return d;
}
public static String save(Object entity) {
    return hasId(idValue(entity, metaOf(entity)))
            ? update(entity).save()
            : insert(entity).save();
}
```

新增 import：`MetaManager`、`EntityMeta`、`FieldMeta`、`Filter`、`PropertyUtils`、`LinkedHashMap`/`Map`/`List`、`@Slf4j`、反射异常类。

## 复用的现有能力（无需新增）

- 插入主键生成：`SaveCommandAdapter.fromInsert:39-43`。
- 默认值合并 + 审计填充：`defaultEntityMap` + `FluentSaveFieldValueFillRuntimeResolver`（`SaveCommandAdapter:37-51,96-102`）。
- 更新去主键于 SET + 构造 WHERE：`fromUpdate:80-107`。
- 反射取值范式：`EntitySaveParser.parse:37-39`。
- 删除（含逻辑删除）：`MetaDelete.delete()` → `DeleteCommandAdapter`。

## 边界与说明

- 实体须为已注册映射实体（`@Entity`/`@Col`/`@Id`），否则 `MetaManager.get(Class)` 抛异常（调用方错误，任其上抛）。
- `update(entity)` 直接调用且主键缺失时：不预填 WHERE，需调用方自行 `.where(...)`，否则 `.save()` 时由 `fromUpdate` 抛 `IllegalArgumentException`（既有校验）。
- `delete(entity)`：按所有非空字段构造等值 WHERE 删除匹配行（通常仅主键）。
- 仅 `null` 视为“未设置”；`0`/`false`/`""` 视为已设置的值参与条件/写入。
- 需切换数据源时：`MetaFactory.insert(entity).useDataSource(x).save()`、`MetaFactory.query(entity).useDataSource(x).list()` 等。

## 使用示例

```java
// 1) 一行执行：新增（主键空→插入，自动生成 UID）
ColumnMeta m = new ColumnMeta(); m.setAppId("demo"); m.setName("code");
String id = MetaFactory.save(m);

// 2) 一行执行：更新（主键非空→按主键更新）
m.setId(id); m.setDataType("varchar");
MetaFactory.save(m);

// 3) 构建器版（可链式切换数据源）
MetaFactory.insert(m).useDataSource("biz").save();
MetaFactory.update(m).useDataSource("biz").save();
MetaFactory.delete(m).useDataSource("biz").delete();        // 按主键删
MetaFactory.query(m).useDataSource("biz").list();           // 按非空字段查询

// 4) 按属性查询：非空字段作为条件
ColumnMeta example = new ColumnMeta(); example.setAppId("demo");
List<Map<String,Object>> rows = MetaFactory.query(example).list();
```

## 验证

1. 编译：`mvn -q -pl geelato-orm -am compile` 确认无编译错误。
2. 手测：用任一映射实体（如 `ColumnMeta`）跑通 save（插入/更新）、insert、update、query、delete 五条路径；可对构建器 `.toSql()` 断言生成的 SQL 符合预期。
