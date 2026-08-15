# @Col 注解约定统一改造方案（已搁置）

> **状态**：已搁置（2026-08），待后续启动。
> **触发条件**：当需要对 `platform_dev_*` 模型类或 `@Entity` 实体类做大规模列名调整，或再次出现"改 `@Col` 后忘记同步硬编码 key"导致的静默数据丢失时，按本方案启动。
> **前置依赖**：无代码依赖；启动前需完成"启动前审计"（见文末）。

## 核心语义声明（本方案的地基）

`@Col` 是**例外标注**，不是必须标注：

1. **不标注 `@Col` 的字段依然要被扫描成实体属性**——列名按约定 `camelToSnake(字段名)` 推导（`entityName` → `entity_name`，`title` → `title`，`id` → `id`）。
2. **仅当字段名与列名有差异时才标 `@Col` 覆盖**（如 `ColumnMeta.name` → `@Col(name="column_name")`）。
3. 该约定贯穿两个应用面：**ORM 类扫描**（`MetaReflex` 解析 `@Entity` 类）与 **Map 装载**（model 类 `Xxx(Map)` 构造函数）。
4. 排除持久化用 `@Transient`，与是否标 `@Col` 无关。
5. **容忍 DB 与实体不一致**：装载路径对缺失列静默跳过（`map.get` 得 null 即不设置），不因实体声明了字段而要求 DB 必有对应列。

## 现状差距

### 应用面一：ORM 类扫描（`MetaReflex.getColumnFieldMetas(Class)`）

现状与上述语义不符，且**两个分支行为互不一致**：

**字段分支**（解析 `Field[]`）——无 `@Col` 的字段被**完全跳过**，不进 ORM：
```java
Col column = field.getAnnotation(Col.class);
if (column != null && column.name() != null) {
    cfm = new FieldMeta(column.name(), fieldName, title);
    ...
    map.put(fieldName, cfm);
}
// 无 @Col → 跳过
```

**方法分支**（解析 `Method[]`）——无 `@Col` 也建列，但**列名用字段名原样**（未做驼峰转下划线）：
```java
if (column != null && column.name() != null) {
    cfm = new FieldMeta(column.name(), fieldName, title);
} else {
    cfm = new FieldMeta(fieldName, fieldName, title);   // ← 列名=字段名原样，entityName 不会变 entity_name
}
```

**目标**：两分支统一为 `列名 = 有 @Col ? @Col.name() : camelToSnake(字段名/属性名)`。

### 应用面二：Map 装载（model 类构造函数）

`TableMeta` / `ColumnMeta` / `TableView` / `TableCheck` / `TableForeign` 的 `Xxx(Map)` 构造函数中，每个字段的列名硬编码一份 `map.get("entity_name")`，与 `@Col.name()` 无联动——改注解漏改构造函数时静默读到 null。

## 方案设计

### 1. 新建 `MetaMapBinder`（geelato-core，`cn.geelato.core.meta` 包）

```java
public final class MetaMapBinder {
    /** 列名推导：@Col 优先，否则 camelToSnake（唯一真相源） */
    public static String resolveColName(AnnotatedElement element, String name);
    /** 按类缓存的字段绑定元数据：{Field, colName, type} */
    private static final Map<Class<?>, List<Binding>> CACHE = new ConcurrentHashMap<>();
    /** 反射填充 target 的所有非 @Transient 字段（含父类） */
    public static void bind(Object target, Map<String, Object> map);
    /** enableStatus 特例共用：v!=null && Boolean.parseBoolean(v.toString()) ? 1 : 0 */
    public static int enableStatusOf(Object v);
}
```

**通用值转换**（按 `field.getType()`）：

| 字段类型 | 转换规则 |
|---|---|
| `String` | `v == null ? null : v.toString()` |
| `boolean` / `Boolean` | `v != null && Boolean.parseBoolean(v.toString())` |
| `int` / `Integer`、`long` / `Long` | 宽容解析（见"已知陷阱"），null 时保留字段初始值 |
| 其他（`Date` 等） | 返回 null（跳过，等价当前不读） |

**null 处理**（与当前手写行为等价）：
- `String` 字段 null → `set null`（覆盖字段初始值，如 `TableMeta.cacheType="none"` → null）
- 原语/包装类型 null → **跳过 set**（保留字段声明的初始值，如 `synced=false`、`enableStatus=ENABLE_STATUS_VALUE`）

**约束**：不调用 `afterSet()`（与当前 Map 装载路径一致）。

### 2. ORM 扫描改造（`MetaReflex.getColumnFieldMetas(Class)`）

- 字段分支与方法分支统一：无 `@Col` 的字段也创建 `FieldMeta`，列名 = `camelToSnake(字段名)`。
- 排除规则不变：`@Transient` 跳过；`static` 跳过。

### 3. 五个 model 类的 Map 构造函数改造

```java
public ColumnMeta(Map<String, Object> map) {
    MetaMapBinder.bind(this, map);      // 通用字段自动填充
    // 特例字段后处理（覆盖通用填充值）
    ...
}
```

**特例清单**（通用转换无法表达，需手写；这些字段列名稳定，硬编码可接受）：

| 类 | 特例字段 | 原因 |
|---|---|---|
| **ColumnMeta** | `enableStatus`（Boolean→0/1）、`dataType`（toUpperCase）、`selectType`（toUpperCase）、`comment`（空回退 title）、`defaultValue`（blank→null） | 类型转换特殊 / 跨字段回退 |
| **TableMeta** | `enableStatus`（Boolean→0/1）、`title`（空回退 tableName/entityName） | 同上 |
| **TableForeign** | `enableStatus`（Boolean→0/1） | Boolean 解析 |
| **TableCheck** | `synced`（parseInt==1） | 与其他类 synced 的 parseBoolean 规则不一致 |
| **TableView** | 无特例 | enableStatus 用 parseInt = 通用规则正好匹配 |

### 4. 过读行为说明

反射会填当前手写构造故意不读的字段（`ColumnMeta.nullable`/`extra`/`tableSchema`、`TableForeign.appId`、基类 `createAt` 等）。有意接受：
- 这些字段在 `select *` 结果里都有值，填它们让对象更完整；
- `Date` 等不识别类型自动跳过（等价当前不读）；
- 下游 `MetaReflex` 只读它需要的字段，多填无害；
- 如某字段被填后引发问题，可加 `@Transient` 排除。

## 实施步骤

1. 新建 `MetaMapBinder`（含 camelToSnake、元数据缓存、通用转换、enableStatusOf）。
2. ORM 扫描改造：统一 `MetaReflex.getColumnFieldMetas(Class)` 两分支的列名推导。
3. 逐类改造 5 个 model 类的 Map 构造函数（`bind` + 特例后处理）。
4. 等价性单元测试：对每个类构造含全部列的 Map，断言反射构造结果与旧手写构造**逐字段相等**（重点验 enableStatus / synced / cacheType / title 回退）。
5. ORM 行为回归：抽查 `platform_dev_table` 等表的 CRUD SQL（无 `@Col` 字段进 SQL 后不报 unknown column）。
6. 测试通过后清理旧手写代码，编译验证 geelato-core / web-platform / meta-sync / web-designer。

## 启动前审计（必做）

ORM 扫描放开"无 `@Col` 也进映射"后，**所有 `@Entity` 实体中"无 `@Col`、非持久化、又未标 `@Transient`"的字段会突然进 CRUD SQL**，需全仓库审计：

```bash
# 找出所有 @Entity 类中无 @Col 且无 @Transient 的字段，逐个确认是否真实列
grep -rn "private" --include="*.java" | grep -v "@Col\|@Transient\|static\|final"
```

已知案例：`TableMeta.title/linked/description/synced`（`platform_dev_table` 真实列，属修复）；`TableMeta.versionControl`（部分库无此列，**刻意不标 `@Col`，靠当前"只认 @Col"的旧行为排除在 ORM 外**——ORM 扫描改造后需改用 `@Transient` 或别名机制排除）。

## 已知陷阱（2026-08 实际踩过）

1. **tinyint(1) 返回 Boolean**：JDBC 默认（`tinyInt1isBit=true`）把 `tinyint(1)` 映射为 `Boolean`，任何 `Integer.parseInt(map.get(...).toString())` 都会因 `"true"` 抛 `NumberFormatException`（见 `TableView.parseTinyint` 修复）。**通用转换必须用宽容解析**（Boolean 实例 / "true"/"false" / 数字字符串通吃），不得用裸 `parseInt`。
2. **类型擦除**：`List<Map>` 与 `List<强类型>` 不能重载（擦除后同为 `List`），兼容入口需独立命名（`getXxxFromMap`，见 `MetaReflex` 现状）。
3. **加显式构造函数会吞掉默认无参构造**：给原本无显式构造的类（如 `ColumnMeta`/`TableView`）加 `Xxx(Map)` 后必须补 `public Xxx() {}`，否则 `new Xxx()` 全部编译失败。
4. **不调 afterSet**：Map 装载路径不得触发 `afterSet()`（`TableView.afterSet` 会 lowercase viewName，`ColumnMeta.afterSet` 会重算 type/extra/defaultValue）。
