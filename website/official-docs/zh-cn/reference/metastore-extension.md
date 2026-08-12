---
title: MetaStore 扩展
sidebar_label: MetaStore 扩展
---

# MetaStore 扩展

> **适用范围**：基于 Geelato 框架二次开发，且元数据定义**不来自平台默认表**（`platform_dev_*`）的工程。
>
> **前置条件**：建议先了解 [核心模块说明](core-modules.md) 与 [启动过程](startup-process.md)，知道 `MetaManager` 在启动链路中的位置。

框架将"元数据定义从哪里来"抽象成 `MetaStore` SPI。本页讲清楚三件事：**为什么这么设计**、**对使用者有什么好处**、**怎么实现自己的来源**。

---

## 一、为什么要抽象 MetaStore

### 之前：框架层与平台表强耦合

早期版本里，元数据来源是**硬写死**的——`DefaultMetaStore` 直接用 SQL 查询平台设计器的五张表（`platform_dev_table` / `platform_dev_column` / `platform_dev_view` / `platform_dev_table_check` / `platform_dev_table_foreign`），拿到行数据后交给 `MetaReflex` 解析成 `EntityMeta`。

这在"使用平台设计器"的场景下工作得很好，但带来三个真实痛点：

| 痛点 | 场景 |
| --- | --- |
| **轻量项目用不上** | 一个新服务只想用 ORM / MQL 的元数据驱动能力，却被迫先建一整套平台设计器表（5 张 + 初始化数据），成本与收益不匹配。 |
| **来源在别处时得改源码** | 元数据定义来自配置中心、外部数据资产平台、或构建产出的 JSON 制品时，只能 fork 框架改 `DefaultMetaStore`，升级框架时反复冲突。 |
| **框架层不纯净** | `geelato-core` 本应是稳定的抽象层，却背着"平台表结构"这一具体存储依赖，难以被独立复用到非平台项目。 |

### 设计决策：把"来源"抽象成 SPI，平台表降级为实现之一

我们把"元数据从哪来"这件事从框架层剥离出来，抽象成 `MetaStore` SPI（位于 `geelato-core` 的 `cn.geelato.core.meta.spi` 包）；而"从平台表读"只降级为**众多实现里的一个**——`DefaultMetaStore` 迁移到业务层 `geelato-web-platform`（为兼容现有 import，保留了原 package `cn.geelato.core.meta.support`）。

```
改造前                          改造后
┌─────────────┐               ┌─────────────┐
│ geelato-core │               │ geelato-core │
│  MetaManager │               │  MetaManager │
│   ↓ 硬调     │               │   ↓ 通过 SPI │
│ DefaultMeta  │               │  «MetaStore» │ ← 框架层只认接口
│ Store(SQL查  │               │   ↓ 实现之一 │
│  platform_*) │               │ DefaultMeta  │ ← 平台表实现
└─────────────┘               │ Store(业务层) │    降级、可替换
                              └─────────────┘
```

这样一来，框架层（`geelato-core`）不再依赖任何具体表结构，`MetaManager` 只通过 `MetaStore` 接口拿到 `MetaDefinitionBundle`，至于这些数据是从数据库、文件、还是远程服务来的，框架**不关心**。

### 为什么是 Spring 风格 SPI，而不是 JDK ServiceLoader

框架本身基于 Spring，因此采用 **Spring 依赖注入风格的 SPI**，而不是 JDK 的 `ServiceLoader`：

- 装配更自然：实现类标 `@Component` 即被扫描，无需写 `META-INF/services/...` 注册文件。
- 支持条件化注入：可配合 `@Primary`、`@ConditionalOnProperty`、`@Profile` 等灵活控制"在什么环境下用哪个实现"。
- 能注入依赖：实现类可以通过构造器注入 `Dao`、`RestClient`、配置属性等，而非 ServiceLoader 那样只能无参实例化。

:::note
全代码库中**不存在** `META-INF/services` 目录，也**没有** `java.util.ServiceLoader` 调用。如果你按 JDK SPI 的习惯去建注册文件，不会生效。
:::

---

## 二、对使用者有什么好处

抽象成 SPI 之后，使用者（基于框架做二次开发的团队）获得的是具体可感的收益，而不是"灵活解耦"这种空话：

1. **零改框架**：实现 `MetaStore` 接口 + `@Component @Primary`，整条来源链路被替换，不动框架一行代码，后续平滑升级框架。
2. **不必建平台表**：最小接入可用 JSON 文件或内存定义起步，等需要可视化设计时再升级到平台表，**按需付费**而非一次性投入。
3. **来源由你定**：文件 / 配置中心 / 外部元数据 API / 任意存储，只要能组装出 `MetaDefinitionBundle`，框架全盘接收。
4. **能力全继承**：换来源后，`MetaManager` 的包扫描、缓存、冲突检测、实体→数据源路由，以及下游的 ORM / MQL / 字段自动填充等能力**全部自动复用**，无需重写。
5. **可与默认实现并存**：支持灰度迁移——`catalog=platform` 的平台实体仍以 Java 类为准，业务实体走你的自定义来源，互不干扰。

---

## 三、可见扩展点

当前已经落地的元数据 SPI 包括：

- `MetaStore`：元数据**定义来源**（本页主角）。
- `MetaResourceProvider`：元数据相关的**静态资源**来源（默认列定义、列选择类型、表升级列）。
- `MetaBootstrap`：元数据加载完成后的**初始化钩子**。

此外还有一个保留接口 `MetaStoreProvider`，目前**未被装配引用**，属于占位预留，暂不展开。

这些扩展点都位于 `geelato-core` 的 `cn.geelato.core.meta.spi` 包下。

---

## 四、默认实现

模块内的默认实现是 `DefaultMetaStore`，位于业务层 `geelato-web-platform`。它的职责是：

- 从当前平台表结构读取表定义；
- 读取列、视图、校验和外键信息；
- 按实体名或视图名返回元数据定义包。

它仍然兼容现有平台表，但**不再是唯一的框架入口**——只是 `MetaStore` 的一个 `@Component` 实现而已。

---

## 五、装配方式

`geelato-web-platform` 的 `MetaConfiguration`（以及 MCP 应用的 `MetaInitConfig`）通过**可选注入**装配这些 SPI：

- `MetaStore`
- `MetaResourceProvider`
- `MetaBootstrap`

注入后，`MetaManager` 会依次执行：

1. 包扫描元数据解析（扫描 `@Entity` 注解类）；
2. 数据库元数据解析（调用 `MetaStore.load(...)`，若 `MetaStore` 为空则**跳过**，框架可独立运行）；
3. 可选的自定义 `MetaBootstrap` 初始化。

:::note
因为注入是可选的（`@Autowired(required = false)` / `@Nullable`），所以**不提供任何 `MetaStore` 时框架照常启动**，只是没有"DB 来源"的元数据——这是"轻量项目可无 DB 独立运行"的实现基础。
:::

---

## 六、让自定义实现生效：必须加 `@Primary`

这是替换默认实现时**最容易踩的坑**，务必看懂。

`DefaultMetaStore` 是 `@Component`，且**没有** `@Primary`；而装配端 `MetaConfiguration` 的注入点是：

```java
@Autowired(required = false)
private MetaStore metaStore;   // 无 @Qualifier
```

如果你直接写一个 `@Component implements MetaStore`，容器里就会出现**两个** `MetaStore` 类型的 Bean（`defaultMetaStore` + 你的）。此时：

- `required = false` **只允许零个 Bean**（注入 null），**不能容忍多个 Bean**；
- 注入点没有 `@Qualifier`，两个实现都没标 `@Primary`；
- Spring 无法裁决 → 启动直接报 `NoUniqueBeanDefinitionException`。

### 正确写法：在你的实现上加 `@Primary`

```java
@Component
@Primary                       // ← 关键：让本实现胜过默认的 DefaultMetaStore
public class MyMetaStore implements MetaStore {
    // ...
}
```

这是最简洁、最可靠的方式——不需要排除默认 Bean、不需要改框架代码、不依赖任何隐式规则。框架自身在覆盖其他 SPI 时也用同样的手法。

:::warning
**不要**靠"Bean 同名覆盖"来替换默认实现。Spring Boot 2.1+ 默认 `spring.main.allow-bean-definition-overriding=false`，把你的 Bean 命名成 `defaultMetaStore` 会抛 `BeanDefinitionOverrideException`。开启该全局开关有副作用，不推荐。
:::

---

## 七、契约：`MetaStore` 接口与 `MetaDefinitionBundle`

### 接口方法

```java
public interface MetaStore {
    // 全量加载（启动期调用），可带过滤参数
    MetaDefinitionBundle load(Map<String, String> params);
    // 按实体名加载（单实体刷新时调用）
    MetaDefinitionBundle loadByEntityName(String entityName);
    // 按视图名加载（单视图刷新时调用）
    MetaDefinitionBundle loadByViewName(String viewName);
}
```

### 返回值 `MetaDefinitionBundle`

不可变值对象，内含五个 `List<Map<String, Object>>`：

```java
new MetaDefinitionBundle(
    tableList,    // 表定义（每行一张表）
    columnList,   // 列定义（每行一列）
    viewList,     // 视图定义
    checkList,    // 检查约束
    foreignList   // 外键定义
);
```

构造器对 null 做了兜底（自动转为 `Collections.emptyList()`），所以**单个 List 传 null 是安全的**。

### 两条硬约束（源码级，违反会报错）

| 约束 | 后果 | 出处 |
| --- | --- | --- |
| `columnList` **不能为空** | 抛 `RuntimeException: column list is empty!` | `MetaReflex.getEntityMetaByTable` |
| 每个表 Map **必须含 `entity_name`** | `NullPointerException`（直接 `.toString()`） | `MetaReflex.getEntityMetaByTable` |

---

## 八、字段约定速查表

`tableList` / `columnList` 里每个 `Map<String, Object>` 的 key 采用 **snake_case**（与 `platform_dev_*` 表字段一致，因为默认实现就是 `select *`）。下面只列出**功能上有意义**的字段，其余均可省略。

### tableMap（表定义）

| 字段 | 必需 | 用途 |
| --- | --- | --- |
| `entity_name` | **必需**（缺失 NPE） | 实体名，`MetaManager` 的主索引 |
| `connect_id` | 重要 | 决定该实体走哪个**数据源**（动态路由 key） |
| `table_name` | 重要 | 物理表名，生成 SQL 时使用 |
| `title` | 可选 | 显示名称 |
| `catalog` | 可选 | 逻辑库分组，可经 catalog 映射解析数据源 |
| `table_type` / `db_type` / `table_schema` | 可选 | 辅助信息 |

### columnMap（列定义）

| 字段 | 必需 | 用途 |
| --- | --- | --- |
| `field_name` | **必需**（为空则该列被跳过） | Java 字段名，列定义的门控 key |
| `data_type` | 重要 | 数据库类型（如 `bigint`/`varchar`/`decimal`），**驱动 Java 字段类型映射** |
| `column_key` | 重要 | 布尔值，`true` 表示**主键** |
| `column_name` | 可选 | 物理列名（缺省时取 `field_name`） |
| `title` | 可选 | 显示名称 |
| `is_nullable` | 可选 | 是否可空，默认 `true` |
| `column_default` | 可选 | 默认值 |

:::tip
记住这三个关键字段的"驱动关系"，调试时就不会迷惑：`entity_name` 驱动**实体注册**、`connect_id` 驱动**数据源路由**、`data_type` 驱动**Java 类型**、`column_key` 驱动**主键识别**。
:::

---

## 九、实战示例

下面三个示例由浅入深，统一用 `@Component @Primary` 生效。

### 示例 A：内存构造（最小演示）

> **解决场景**：快速验证"自定义来源是否真的被框架装配"，或做最小可运行 demo。无需任何外部依赖。

```java
package com.acme.platform.meta;

import cn.geelato.core.meta.spi.MetaDefinitionBundle;
import cn.geelato.core.meta.spi.MetaStore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小演示：在代码里直接构造元数据定义。
 */
@Component
@Primary                              // 让本实现胜过默认 DefaultMetaStore
public class InMemoryMetaStore implements MetaStore {

    @Override
    public MetaDefinitionBundle load(Map<String, String> params) {
        // 1. 构造一张表
        Map<String, Object> table = new HashMap<>();
        table.put("entity_name", "demo_order");   // 必需，缺失会 NPE
        table.put("table_name", "demo_order");     // 物理表名
        table.put("title", "订单表");
        table.put("connect_id", "primary");        // 走 primary 数据源

        // 2. 构造列（columnList 不能为空）
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(column("id", "id", "bigint", true));         // 主键
        columns.add(column("orderNo", "order_no", "varchar", false));
        columns.add(column("amount", "amount", "decimal", false));

        return new MetaDefinitionBundle(
                List.of(table),    // tableList
                columns,           // columnList —— 不能为空
                List.of(),         // viewList
                List.of(),         // checkList
                List.of()          // foreignList
        );
    }

    /** 列定义快捷构造 */
    private Map<String, Object> column(String fieldName, String columnName,
                                       String dataType, boolean isKey) {
        Map<String, Object> c = new HashMap<>();
        c.put("field_name", fieldName);    // 必需，为空该列被跳过
        c.put("column_name", columnName);
        c.put("data_type", dataType);      // 驱动 Java 类型
        c.put("column_key", isKey);        // true → 主键
        c.put("title", fieldName);
        return c;
    }

    @Override
    public MetaDefinitionBundle loadByEntityName(String entityName) {
        return load(null);
    }

    @Override
    public MetaDefinitionBundle loadByViewName(String viewName) {
        return new MetaDefinitionBundle(null, null, null, null, null);
    }
}
```

**预期结果**：启动后执行 `MetaManager.singleInstance().getEntityMeta("demo_order")` 能取到该实体，且 `id` 被识别为主键。

### 示例 B：从 JSON 文件加载（最常用）

> **解决场景**：新项目用文件管理元数据，不建平台表；或把元数据作为制品随应用一起打包发布。

先准备一个 JSON 文件，放在 `src/main/resources/meta/demo-order.json`：

```json
{
  "entityName": "demo_order",
  "tableName": "demo_order",
  "title": "订单表",
  "connectId": "primary",
  "columns": [
    { "fieldName": "id",      "columnName": "id",       "dataType": "bigint",   "columnKey": true,  "title": "主键" },
    { "fieldName": "orderNo", "columnName": "order_no", "dataType": "varchar",  "columnKey": false, "title": "订单号" },
    { "fieldName": "amount",  "columnName": "amount",   "dataType": "decimal", "columnKey": false, "title": "金额" }
  ]
}
```

再实现 `MetaStore`，启动时把 `classpath:meta/*.json` 全部读入：

```java
package com.acme.platform.meta;

import cn.geelato.core.meta.spi.MetaDefinitionBundle;
import cn.geelato.core.meta.spi.MetaStore;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 从 classpath:meta/*.json 加载元数据定义。
 */
@Component
@Primary
public class JsonFileMetaStore implements MetaStore {

    private final List<Map<String, Object>> tables = new ArrayList<>();
    private final List<Map<String, Object>> columns = new ArrayList<>();

    public JsonFileMetaStore() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        for (Resource res : resolver.getResources("classpath:meta/*.json")) {
            try (InputStream in = res.getInputStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject json = JSON.parseObject(text);

                // 注意：Map 的 key 必须是框架约定的 snake_case
                Map<String, Object> table = new HashMap<>();
                table.put("entity_name", json.getString("entityName"));
                table.put("table_name", json.getString("tableName"));
                table.put("title", json.getString("title"));
                table.put("connect_id", json.getString("connectId"));
                tables.add(table);

                JSONArray cols = json.getJSONArray("columns");
                for (int i = 0; i < cols.size(); i++) {
                    JSONObject c = cols.getJSONObject(i);
                    Map<String, Object> col = new HashMap<>();
                    col.put("field_name", c.getString("fieldName"));
                    col.put("column_name", c.getString("columnName"));
                    col.put("data_type", c.getString("dataType"));
                    col.put("column_key", c.getBooleanValue("columnKey"));
                    col.put("title", c.getString("title"));
                    columns.add(col);
                }
            }
        }
    }

    @Override
    public MetaDefinitionBundle load(Map<String, String> params) {
        return new MetaDefinitionBundle(tables, columns, List.of(), List.of(), List.of());
    }

    @Override
    public MetaDefinitionBundle loadByEntityName(String entityName) {
        return load(null);   // 简化：全量返回，由 MetaManager 匹配
    }

    @Override
    public MetaDefinitionBundle loadByViewName(String viewName) {
        return new MetaDefinitionBundle(null, null, null, null, null);
    }
}
```

**预期结果**：把任意数量的 `*.json` 丢进 `meta/` 目录即自动生效，无需改代码。

### 示例 C：从外部 HTTP API 加载

> **解决场景**：企业内部已有统一的元数据注册中心 / 数据资产管理平台，应用启动时从它拉取定义，保持单一事实源。

```java
package com.acme.platform.meta;

import cn.geelato.core.meta.spi.MetaDefinitionBundle;
import cn.geelato.core.meta.spi.MetaStore;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 从外部元数据服务 HTTP 接口加载定义。
 * 约定接口返回：{ "tables": [...], "columns": [...] }
 */
@Component
@Primary
public class HttpApiMetaStore implements MetaStore {

    private final RestClient client = RestClient.builder()
            .baseUrl("http://meta-registry.internal.svc")
            .build();

    @Override
    public MetaDefinitionBundle load(Map<String, String> params) {
        Map<String, Object> resp = client.get()
                .uri("/api/meta/definitions")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) resp.get("tables");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) resp.get("columns");
        return new MetaDefinitionBundle(tables, columns, List.of(), List.of(), List.of());
    }

    @Override
    public MetaDefinitionBundle loadByEntityName(String entityName) {
        return load(null);
    }

    @Override
    public MetaDefinitionBundle loadByViewName(String viewName) {
        return new MetaDefinitionBundle(null, null, null, null, null);
    }
}
```

**预期结果**：应用启动即从元数据服务同步定义；只要接口返回的字段符合上面的约定，框架侧无需任何改动。

---

## 十、最典型的替换场景

适合自定义 `MetaStore` 的场景包括：

- 不使用平台默认元数据表；
- 元数据定义来自外部配置中心；
- 元数据定义来自 JSON / YAML / 文件系统；
- 需要在启动时合并多种元数据来源。

---

## 十一、推荐实现边界

建议把职责拆开，避免把扫描、读取、缓存、启动初始化全部揉进一个类：

- `MetaStore`：负责**定义来源**；
- `MetaResourceProvider`：负责**资源文件来源**（默认列定义等）；
- `MetaBootstrap`：负责**启动后补充初始化**（如校验、补全、注册派生实体）。

---

## 十二、注意事项

- **是 Spring 风格 SPI**，不是 JDK `ServiceLoader`，无需写 `META-INF/services` 文件。
- **包扫描要覆盖到你的实现类**：默认 `@ComponentScan` 扫描 `cn.geelato`，配置项为 `geelato.meta.scan-package-names`。如果你的实现放在 `com.acme` 下，需把该包加入扫描范围。
- **`catalog=platform` 的实体有特殊规则**：不可通过 `refreshDBMeta` 刷新，且与 Java 类冲突时**恒以 Java 类为准**。自定义来源适合放业务实体，平台实体仍走 Java 注解。
- **返回空 bundle 是安全的**：当 `MetaStore` 返回空时框架跳过 DB 元数据解析，因此框架可在无 DB 环境独立运行。

---

## 十三、常见问题排查

| 症状 | 原因 | 排查 |
| --- | --- | --- |
| 启动报 `NoUniqueBeanDefinitionException` | 容器里同时存在 `DefaultMetaStore` 和你的实现 | 在你的实现类上加 `@Primary` |
| 自定义实现没生效，仍读平台表 | 你的包未被 `@ComponentScan` 覆盖 | 把实现类所在包加入 `geelato.meta.scan-package-names` |
| 启动报 `NullPointerException`（`entity_name`） | 某个表 Map 缺 `entity_name` | 检查每行 tableMap 都含 `entity_name` |
| 启动报 `column list is empty` | `columnList` 为空 | 至少返回一个列定义 |
| 实体字段类型不对 | `data_type` 写错或缺失 | 核对 `data_type` 取值（`bigint`/`varchar`/`decimal`/`datetime` 等） |
| 主键没被识别 | 缺 `column_key` 或值不是 `true` | 给主键列设 `column_key = true` |

---

## 十四、使用建议

- 如果只是兼容现有平台表，继续使用默认实现即可。
- 如果是新项目或非平台表结构项目，推荐：自定义 `MetaStore`；保留 `MetaManager` 作为统一消费入口；继续使用框架现有的扫描、缓存和 ORM / MQL 访问链路。

---

## 推荐继续阅读

- [核心模块说明](core-modules.md)
- [新项目最小接入](../guide/minimal-integration.md)
- [ORM / 数据源扩展](../orm/datasource-extension.md)
- [查询过滤与字段填充 SPI 扩展](spi-query-filter-and-save-fill-extension.md)
