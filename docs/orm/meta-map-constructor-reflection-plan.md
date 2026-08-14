# 元数据模型 Map 构造函数反射化方案（已搁置）

> **状态**：已搁置（2026-08），待后续启动。
> **触发条件**：当再次出现"改 `@Col` 注解列名后忘记同步 Map 构造函数硬编码 key"导致的静默数据丢失，或需要对 `platform_dev_*` 模型类做大规模列名调整时，可按本方案启动。
> **前置依赖**：无代码依赖，本方案为独立的模型类构造层改造。

## 背景与目标

`TableMeta` / `ColumnMeta` / `TableView` / `TableCheck` / `TableForeign`（geelato-core，`cn.geelato.core.meta.model` 包）各有一个 `Xxx(Map<String,Object>)` 构造函数，用于把 `DefaultMetaStore` 的 `select * from platform_dev_*` 行 Map 转成强类型对象（`MetaDefinitionBundle` 的元素）。

**脆弱点**：每个字段的列名在两处各写一份——
1. 字段注解 `@Col(name = "entity_name")`（ORM 反射路径消费）
2. Map 构造函数里硬编码的 `map.get("entity_name")`（装载路径消费）

两者无代码层面联动，全靠人工同步。改 `@Col` 列名而漏改构造函数时，编译不报错、运行时 `map.get` 静默读到 null，数据丢失。

**目标**：列名改为运行期反射推导——**有 `@Col` 用 `@Col.name()`，否则 `camelToSnake(字段名)`**——使注解成为唯一真相源，改一处自动两处生效。

## 已验证的设计前提

1. **`@Col` 的设计语义是"例外标注"**：字段名（驼峰）按约定映射列名（下划线）一致时可不标注；仅当字段名与列名有差异时才用 `@Col` 补充（如 `ColumnMeta.name` → `@Col(name="column_name")`）。当前代码中存在冗余标注（如 `entityName` 本可不标），但不影响推导规则。
2. **推导规则覆盖全部字段**：无 `@Col` 的字段（`title`/`linked`/`description`/`synced`/`id` 等）经 `camelToSnake` 推导出的 key 与当前构造函数读取的 key 完全一致（`title`→`title`、`id`→`id`、`linked`→`linked`）。
3. **纯计算字段无需显式排除**：`ColumnMeta` 的 `abstractColumnExpressions` / `isRefColumn` / `abstractColumn` / `befColName`（@Transient）等在 `select *` 结果中无对应 key，`map.get` 得 null 自动跳过。
4. **性能可忽略**：构造仅在启动 `parseDBMeta` 与偶发 `refreshDBMeta` 时调用（非热路径）；反射元数据按类缓存后接近零成本。项目内已有更重的反射在跑（`MetaReflex.getColumnFieldMetas(Class)` 启动时反射所有 @Entity 类）。
5. **camelToSnake 实现参考**：`IdeScriptService.camelToSnake`（geelato-web-designer），大写字母前插下划线并转小写。

## 方案设计

### 1. 新建 `MetaMapBinder`（geelato-core，`cn.geelato.core.meta` 包）

```java
public final class MetaMapBinder {
    /** 按类缓存的字段绑定元数据：{Field, colName, type} */
    private static final Map<Class<?>, List<Binding>> CACHE = new ConcurrentHashMap<>();

    /** 反射填充 target 的所有非 @Transient 字段（含父类） */
    public static void bind(Object target, Map<String, Object> map);

    /** enableStatus 特例共用：v!=null && Boolean.parseBoolean(v.toString()) ? 1 : 0 */
    public static int enableStatusOf(Object v);
}
```

**列名推导**：`field 有 @Col ? @Col.name() : camelToSnake(field.getName())`

**通用值转换**（按 `field.getType()`）：

| 字段类型 | 转换规则 |
|---|---|
| `String` | `v == null ? null : v.toString()` |
| `boolean` / `Boolean` | `v != null && Boolean.parseBoolean(v.toString())` |
| `int` / `Integer` | `v != null ? Integer.parseInt(v.toString()) : null` |
| `long` / `Long` | `v != null ? Long.parseLong(v.toString()) : null` |
| 其他（`Date` 等） | 返回 null（跳过，等价当前不读） |

**null 处理**（保证与当前手写行为等价）：
- `String` 字段 null → `set null`（覆盖字段初始值，如 `TableMeta.cacheType="none"` → null，匹配当前行为）
- 原语/包装类型（`int`/`Boolean` 等）null → **跳过 set**（保留字段声明的初始值，如 `synced=false`、`enableStatus=ENABLE_STATUS_VALUE`）

**约束**：不调用 `afterSet()`（与当前 Map 装载路径一致）。

### 2. 五个类的 Map 构造函数改造

```java
public ColumnMeta(Map<String, Object> map) {
    MetaMapBinder.bind(this, map);      // 通用字段自动填充
    // 特例字段后处理（覆盖通用填充值）
    this.enableStatus = MetaMapBinder.enableStatusOf(map.get("enable_status"));
    ...
}
```

### 3. 特例清单（通用转换无法表达，需手写）

这些字段的核心列名稳定（不会被随意改），硬编码其列名的风险可接受。

| 类 | 特例字段 | 原因 |
|---|---|---|
| **ColumnMeta** | `enableStatus`（Boolean→0/1）、`dataType`（toUpperCase）、`selectType`（toUpperCase）、`comment`（空回退 title）、`defaultValue`（blank→null） | 类型转换特殊 / 跨字段回退 |
| **TableMeta** | `enableStatus`（Boolean→0/1）、`title`（空回退 tableName/entityName） | 同上 |
| **TableForeign** | `enableStatus`（Boolean→0/1） | Boolean 解析 |
| **TableCheck** | `synced`（parseInt==1） | 与其他类 synced 的 parseBoolean 规则不一致 |
| **TableView** | 无特例 | enableStatus 用 parseInt = 通用规则正好匹配 |

### 4. 过读行为说明

反射会填当前手写构造**故意不读**的字段（`ColumnMeta.nullable`/`extra`/`tableSchema`、`TableForeign.appId`、基类 `createAt` 等）。这是有意接受的：
- 这些字段在 `select *` 结果里都有值，填它们让对象更完整；
- `Date` 等不识别类型自动跳过（等价当前不读）；
- 下游 `MetaReflex` 只读它需要的字段，多填无害；
- `afterSet` 不被调用，不触发派生重算；
- 如某字段被填后引发问题，可加 `@Transient` 排除。

## 实施步骤

1. 新建 `MetaMapBinder`（含 camelToSnake、元数据缓存、通用转换、enableStatusOf）。
2. 逐类改造 5 个 Map 构造函数：`bind` + 特例后处理（按上表）。
3. 加等价性单元测试：对每个类构造含全部列的 Map，断言反射构造结果与旧手写构造**逐字段相等**（重点验 enableStatus / synced / cacheType / title 回退）。
4. 测试通过后清理旧手写代码，编译验证 geelato-core / web-platform / meta-sync / web-designer。

## 风险评估

- **中低**：核心风险在"通用转换 + null 处理"与当前逐字段行为是否等价，已通过 null 规则（String set null、原语保留初始值）与特例清单覆盖。
- **缓解**：等价性单元测试兜底，确认完全一致后再删旧代码。
- **已知陷阱（2026-08 实际踩过）**：JDBC 对 `tinyint(1)` 列默认（`tinyInt1isBit=true`）返回 `Boolean` 而非数字，任何 `Integer.parseInt(map.get(...).toString())` 都可能因 `"true"` 抛 `NumberFormatException`（见 `TableView` 修复）。反射化的通用转换必须用宽容解析（Boolean 实例 / "true"/"false" / 数字字符串通吃），参考 `TableView.parseTinyint` 的实现。
