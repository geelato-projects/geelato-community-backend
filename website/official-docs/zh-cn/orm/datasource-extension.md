---
title: ORM / 数据源扩展
sidebar_label: ORM / 数据源扩展
---

# ORM / 数据源扩展

本页说明 ORM 的数据源绑定、运行时切换数据源，以及查询过滤与字段填充的 SPI 扩展入口，涵盖三类常见任务：

- 让 ORM 正常绑定到正确的 `Dao`。
- 在运行时切换数据源。
- 通过 SPI 扩展查询过滤和字段填充规则。

若需先理解动态数据源能力本身的工作机制，建议先阅读 [动态数据源](../dynamic-datasource/overview.md)。数据源从哪里来、如何自定义装载，见[数据源定义的装载与注册](../dynamic-datasource/definition-loading.md)。

## 明确场景

修改配置前，先明确要处理的场景，避免按错入口：

- 仅让 Fluent DSL 某条查询走指定数据源：使用 `.useDataSource(...)`。
- 让一段代码或整个请求切到某个动态数据源：使用 `DynamicDataSourceScope.withDataSource(...)` / `switchTo(...)`。
- 让一个 Service 或方法整体切到某个动态数据源：使用 `@UseDynamicDataSource`。
- 替换动态数据源定义来源：实现 `DynamicDataSourceDefinitionLoader`，见[数据源定义的装载与注册](../dynamic-datasource/definition-loading.md)。
- 给查询自动加租户/权限条件，或给保存自动补默认字段：实现对应 SPI。

这四类能力相关但并非一回事，选错入口易导致配置混乱。

## ORM 当前入口

当前 ORM 自动装配入口是 `OrmAutoConfiguration`，核心关注点有两个：

- 创建 `MetaCommandExecutor`
- 创建 `SaveDefaultValueFiller`

其中 `MetaCommandExecutor` 的前提是宿主工程中存在 `Dao` Bean。

## 最短接入路径

新接入宿主工程时，建议按以下顺序执行。

### 第 1 步：先让宿主工程里存在 `Dao`

最小示例：

```java
@Configuration
public class OrmDaoConfiguration {
    @Bean
    public Dao primaryDao(JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
```

没有 `Dao`，ORM 的主要执行链路就没有稳定落点。

### 第 2 步：确认 ORM 绑定的是哪一个 `Dao`

ORM 自动解析优先绑定 `dynamicDao`（只有它能支撑 `.useDataSource(...)` 切库），不存在时回退 `primaryDao`，再回退唯一的 Dao Bean——多 `Dao` 场景无需显式配置。仅当需要绑定其他自定义 `Dao` 时才配置：

```properties
geelato.orm.dao-bean-name=myDao
```

### 第 3 步：再决定是否需要动态数据源

不是所有项目都需要动态数据源。

仅当存在以下需求时，才继续后续步骤：

- 同一套服务代码要访问多个库
- 某些查询/存储过程要按 key 切换到其他库
- 宿主工程需要自己管理动态库清单

## ORM 绑定哪个 Dao

未配置时，ORM 自动解析优先级是：

1. `dynamicDao`（基于路由数据源，唯一能支撑 `.useDataSource(...)` / 实体 `connectId` 切库的 Dao）
2. `primaryDao`
3. 唯一的 Dao Bean

即只要容器中存在 `dynamicDao`，ORM 必绑定它；多 `Dao` 场景无需显式配置。若宿主工程配置了 `geelato.orm.dao-bean-name`，则显式配置优先，用于绑定其他自定义 `Dao` Bean。

## Starter 默认创建哪些 JDBC Bean

在存在 `spring.datasource.primary.jdbc-url` 时，Starter 默认创建：

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `dbGenerateDao`

如果还配置了 `spring.datasource.secondary.jdbc-url`，则继续创建：

- `secondaryDataSource`
- `secondaryJdbcTemplate`
- `secondaryDao`

## 动态数据源扩展点

动态数据源真正的扩展点是定义加载器 `DynamicDataSourceDefinitionLoader`，由宿主工程提供数据源定义，说明见[数据源定义的装载与注册](../dynamic-datasource/definition-loading.md)。

显式配置前缀是：

```properties
geelato.datasource.dynamic.*
```

## 最小启用动态数据源（宿主工程）

动态数据源相关 Bean 的命名约定如下（用于排障与覆盖）：

- 前置：`primaryJdbcTemplate`
- 自动装配的动态数据源 Bean：
  - `dynamicDataSource`
  - `dynamicJdbcTemplate`
  - `dynamicDao`

典型工程中，`primaryJdbcTemplate` 由 Starter 提供；未使用 Starter 时，也可在业务工程里自行提供 `primaryJdbcTemplate`（例如基于 Spring Boot 的主数据源构建 `JdbcTemplate`），从而触发动态数据源自动装配。

默认重要属性包括：

- `delay-load-data-source=true`
- `enable-jta-transaction=false`
- `enable-seata-proxy=false`
- 默认连接池参数与 `connection-test-query=SELECT 1`

## 如何切换数据源

这是最常见的部分。建议先从最轻量的用法开始。

### 方式 1：在 Fluent DSL 中显式切换

这适合“只想让某一条 ORM 查询或过程调用切到指定源”的场景。

查询示例：

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("portal")
        .page(1, 10)
        .list();
```

存储过程示例：

```java
List<Map<String, Object>> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .useDataSource("portal")
        .list();
```

SQL 直通示例：

```java
List<Map<String, Object>> rows = MetaFactory.sql("select id, name from platform_user where del_status = ?")
        .param(0)
        .useDataSource("portal")
        .list();
```

若诉求仅为“这条链路切源”，优先采用这种方式，最为直接。

### 方式 2：用 `@UseDynamicDataSource` 控制类、方法或字段

`@UseDynamicDataSource` 适合更偏组件级的场景。

它可以标在：

- 类
- 方法
- 字段

最简单的类级示例：

```java
@Service
@UseDynamicDataSource("portal")
public class PortalUserService {
}
```

方法级示例：

```java
@Service
public class PortalUserService {

    @UseDynamicDataSource("portal")
    public void syncUsers() {
    }
}
```

它还支持实体与数据源的映射配置：

```java
@UseDynamicDataSource(
        value = "primary",
        mappings = {
                @UseDynamicDataSource.EntitySourceMapping(entityName = "Order", dataSource = "portal"),
                @UseDynamicDataSource.EntitySourceMapping(entityName = "Customer", dataSource = "crm")
        }
)
public class SyncService {
}
```

若需某类方法整体使用同一数据源，或需注入 `dynamicDao` 体系，`@UseDynamicDataSource` 比每条查询都写 `.useDataSource(...)` 更简洁。

### 方式 3：代码级切库 `DynamicDataSourceScope`

需要"这一段代码 / 这个请求整体切到某个库"时，使用切换唯一出入口 `cn.geelato.datasource.DynamicDataSourceScope`（业务代码不应直接读写底层的 `DynamicDataSourceHolder`）：

```java
// 代码块作用域：块内生效，正常/异常结束都恢复原值
List<Map<String, Object>> rows = DynamicDataSourceScope.withDataSource("portal", () ->
        dao.querys(SomeEntity.class));

// 请求内默认库：设置后持续生效，请求结束由清理安全网统一回收
DynamicDataSourceScope.switchTo("portal");
```

平台 `BaseController` / `BaseService` 保留的 `switchDbByConnectId(...)` 即 `switchTo` 的薄委托，Controller 场景直接调用即可。嵌套语义、清理安全网与事务内限制的完整说明见[切换方式](../dynamic-datasource/switching.md)。

### 方式 4：让 ORM 默认绑定动态源的 `Dao`

若业务工程中大多数 ORM 操作都应落到动态源链路，无需任何配置：只要容器中存在 `dynamicDao`，ORM 会自动绑定它，Fluent DSL 默认就会走动态源能力（`useDataSource` 切库生效），无需每次手工指定。

### 方式 5：用 `@Entity` 注解声明实体所属数据源

前四种方式都是在"调用时"、"代码块内"或"组件级"决定走哪个数据源。若需实体的数据源归属**固定、写在实体声明里**，则使用 `@Entity` 注解声明。

它适合：

- 某个实体天然属于另一个库（如订单实体固定在订单库）
- 一批实体按模块归到不同库（如 `email` 分组统一走邮件库）
- 不想在每个 Service 或每条查询上重复指定数据源

这种方式与前几种属于不同层级，可以叠加：注解声明的是实体"默认归属"，`.useDataSource(...)` 仍可在单次调用时覆盖。

#### 路径 A：用 `connectId` 显式指定

直接在实体上声明它走哪个数据源，优先级最高：

```java
@Entity(name = "demo_order", table = "t_demo_order", connectId = "order_db")
public class DemoOrder {
    // ...
}
```

此后对该实体的所有 ORM 操作都会自动路由到 `order_db` 数据源，无需在调用处再切源。

#### 路径 B：用 `catalog` 分组 + 配置映射

如果一批实体同属一个库，逐个标 `connectId` 较繁琐。可以给它们标同一个 `catalog`，再通过配置把 `catalog` 映射到数据源：

```java
// 一组实体都标同一个 catalog
@Entity(name = "demo_order", catalog = "business")
public class DemoOrder { }

@Entity(name = "demo_order_item", catalog = "business")
public class DemoOrderItem { }
```

然后在配置里建立 `catalog` 到数据源的映射：

```properties
geelato.datasource.dynamic.catalog-mapping.platform=primary
geelato.datasource.dynamic.catalog-mapping.business=order_db
```

等价的 YAML 写法：

```yaml
geelato:
  datasource:
    dynamic:
      catalog-mapping:
        platform: primary
        business: order_db
```

这样的好处是：换库时只改配置，不改代码。`business` 分组下的所有实体一起切到新的数据源。

#### 解析优先级

实体最终走哪个数据源，按以下顺序确定（高 → 低）：

1. `TableMeta.connectId` 非空——`@Entity(connectId)` 显式指定与运行期登记值（登记入口由平台提供）写入的是**同一个槽位**（运行期登记后到合并，非 platform 实体可覆盖注解值），它们不是两级优先级
2. `@Entity(catalog)` 在 `catalog-mapping` 中的映射值
3. 都没有则交由 Dao 调用期兜底链决定（注解默认 > 外层显式 key > 请求级覆盖 > 平台默认 > `primary`）

优先级在运行期由 `MetaManager.resolveConnectId` 即时解析，因此配置何时注入都不影响结果。完整优先级链与决策流程见[实体绑定与优先级](../dynamic-datasource/entity-binding.md)。

#### 注意事项

- 映射指向的数据源 key 必须是已注册的动态数据源（已由定义来源提供该 key，或通过自定义加载器等注册途径提供）；未注册时首次解析即硬失败（抛出 `IllegalStateException`，拒绝静默回退主库）。
- `catalog` 值为 `platform` 的系统实体，其既有保护逻辑不变（强制以 Java 类为准、禁止刷新、禁止移交）；建议在 `catalog-mapping` 中将 `platform` 映射到 `primary`，保持与系统库一致。
- 解析在查询期即时完成，与实体扫描时序无关，无需关心框架启动阶段的先后顺序。

## 数据源定义来源：装载与注册

动态数据源的"定义从哪来"由 SPI `DynamicDataSourceDefinitionLoader`（`geelato-orm` 的 `cn.geelato.datasource.spi` 包）承担，定义统一注册在 `DynamicDataSourceRegistry`。抽象成 SPI 之后，连接信息可以来自配置中心、文件或外部服务，宿主工程不必为多数据源能力引入额外的表结构。框架模块自身不带实现，不提供任何实现时只走 `primary` / `secondary`。

在宿主工程里自定义数据源来源时，有三个要点：

- 注册器按类型只认**一个**加载器。容器里已有加载器时（平台宿主自带 `PlatformDynamicDataSourceDefinitionLoader`），自定义实现必须加 `@Primary`，否则启动报 `NoUniqueBeanDefinitionException`。
- 加了 `@Primary` 就是完全替换，原有来源的连接不再被登记；需要保留原来源并追加自有来源时，写组合加载器。
- 定义变更后的刷新分两步：先刷新 `DynamicDataSourceRegistry`，再刷新 `DynamicRoutingDataSource`。

接口契约、注册器的方法、启动时序、完整示例和运行期刷新，统一见[数据源定义的装载与注册](../dynamic-datasource/definition-loading.md)。

## 如何实现 SPI

数据源扩展经常和平台规则扩展一起出现。

例如：

- 切到某个租户数据源后，还希望自动加租户过滤
- 某些保存链路希望自动补齐租户编码、创建人、更新时间

这类不要写死在业务 Service 里，更推荐实现 SPI。

### 先决定该实现哪一个 SPI

按入口选择：

- 想影响 Fluent DSL 查询：实现 `FluentQueryFilterInjector`
- 想影响 Fluent DSL 保存：实现 `FluentSaveFieldValueFiller`
- 想影响 MQL 查询：实现 `MqlQueryFilterInjector`
- 想影响 MQL 保存：实现 `MqlSaveFieldValueFiller`

### 示例 1：实现 Fluent DSL 查询过滤 SPI

```java
@Component
public class DemoFluentQueryFilterInjector implements FluentQueryFilterInjector {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command, MetaQuery query) {
        // 这里向 Fluent DSL 查询命令注入平台级过滤条件
    }
}
```

### 示例 2：实现 Fluent DSL 保存字段填充 SPI

```java
@Component
public class DemoFluentSaveFieldValueFiller implements FluentSaveFieldValueFiller {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(FluentSaveFieldValueFillContext context) {
        // 这里补齐默认保存字段
    }
}
```

### SPI 的运行时规则一定要记住

每一类 SPI 都遵循同一条关键规则：

1. `0` 个实现：跳过
2. `1` 个实现：按 `isEnabled()` 决定是否执行
3. 多个实现：直接抛异常

这意味着：

- 不要同时启用两个 `FluentQueryFilterInjector`
- 不要同时启用两个 `FluentSaveFieldValueFiller`
- 如果项目里有多个候选实现，必须在上层收敛成唯一启用实现

更完整的 SPI 说明见 [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)。

## JTA / Seata 何时开启

当前 JTA 和 Seata 都不是默认开启能力。

只有显式配置后才进入相应能力链路，例如：

```properties
geelato.datasource.dynamic.enable-jta-transaction=true
```

因此最小骨架和普通业务工程默认不会被重事务依赖污染。

## 推荐的实操顺序

若需在新项目中同时接入 ORM、动态数据源和平台规则，推荐顺序是：

1. 先验证 `primaryDao` 或其他基础 `Dao` 可用
2. 再确认 ORM 自动绑定到了 `dynamicDao`（存在时必绑定；绑定 `primaryDao` 时切库不生效）
3. 再启用动态数据源并验证 `.useDataSource(...)`
4. 再视需要补 `@UseDynamicDataSource`
5. 最后再实现查询过滤和字段填充 SPI

这样做的好处是，一旦出问题，可以快速定位究竟是：

- `Dao` 没配好
- 动态源没生效
- 数据源定义没加载到
- SPI 扩展写错了

## 一步一步排障

若出现“切源未生效”或“SPI 未生效”，建议按以下顺序排查：

1. 看容器里是否真的存在 `primaryDao` / `dynamicDao`
2. 看 ORM 绑定的是否是 `dynamicDao`（未显式配置 `geelato.orm.dao-bean-name` 时应自动绑定它）
3. 看动态源定义是否真的能加载出目标 key
4. 看代码里是否真的调用了 `.useDataSource("...")` 或命中了 `@UseDynamicDataSource`
5. 看同类 SPI 是否注册了多个实现
6. 看 SPI 的 `isEnabled()` 是否返回 `true`
7. 自定义 loader 启动报 `NoUniqueBeanDefinitionException` → 实现类漏了 `@Primary`
8. 报 `UnsupportedOperationException: 不支持的数据库类型` → Map 的 `db_type` 不是内置的 `mysql` / `postgresql`，也不是经 `Dialects.registerAlias` 注册过方言家族的类型
9. 改了来源但切源没生效 → 只刷了 `DynamicDataSourceRegistry`，没刷 `DynamicRoutingDataSource`（两步缺一不可）

定义加载器相关的完整排障见[数据源定义的装载与注册](../dynamic-datasource/definition-loading.md)。

## 推荐使用建议

- 常规后端 CRUD 优先使用 ORM
- 动态源定义来源优先通过扩展点覆盖，不要硬改默认实现
- 多 `Dao` 场景无需配置，ORM 自动优先绑定 `dynamicDao`；仅绑定其他自定义 `Dao` 时配置 `geelato.orm.dao-bean-name`
- 除非明确需要，否则不要默认开启 JTA / Seata
- 单条链路切源优先用 `.useDataSource(...)`
- 代码块 / 请求内整段切库用 `DynamicDataSourceScope.withDataSource(...)` / `switchTo(...)`，不要直接读写 `DynamicDataSourceHolder`
- 组件级切源再考虑 `@UseDynamicDataSource`
- 实体数据源归属固定时，优先用 `@Entity(connectId/catalog)` 在实体上声明，而非每次调用切源
- 平台级查询规则和字段规则优先用 SPI，不要散落在业务代码里

## 推荐继续阅读

- [动态数据源](../dynamic-datasource/overview.md)
- [数据源定义的装载与注册](../dynamic-datasource/definition-loading.md)
- [ORM 总览](overview.md)
- [MetaStore 扩展](../reference/metastore-extension.md)
- [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)
- [新项目最小接入](../guide/minimal-integration.md)
