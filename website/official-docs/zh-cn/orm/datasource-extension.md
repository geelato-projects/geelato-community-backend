---
title: ORM / 数据源扩展
sidebar_label: ORM / 数据源扩展
---

# ORM / 数据源扩展

本页说明 ORM 的数据源绑定、运行时切换数据源，以及查询过滤与字段填充的 SPI 扩展入口，涵盖三类常见任务：

- 让 ORM 正常绑定到正确的 `Dao`。
- 在运行时切换数据源。
- 通过 SPI 扩展查询过滤和字段填充规则。

若需先理解动态数据源能力本身的工作机制，建议先阅读 [动态数据源](../dynamic-datasource/overview.md)。

## 明确场景

修改配置前，先明确要处理的场景，避免按错入口：

- 仅让 Fluent DSL 某条查询走指定数据源：使用 `.useDataSource(...)`。
- 让一个 Service 或方法整体切到某个动态数据源：使用 `@UseDynamicDataSource`。
- 替换动态数据源定义来源：实现 `DynamicDataSourceDefinitionLoader`。
- 给查询自动加租户/权限条件，或给保存自动补默认字段：实现对应 SPI。

这四类能力相关但并非一回事，选错入口易导致配置混乱。

## ORM 当前入口

当前 ORM 自动装配入口是 `OrmAutoConfiguration`，核心关注点有两个：

- 创建 `MetaCommandExecutor`
- 创建 `SaveDefaultValueFiller`

其中 `MetaCommandExecutor` 的前提是宿主工程中存在 `Dao` Bean。

## 最短接入路径

如果你正在新接一个宿主工程，建议按下面顺序做。

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

如果你的工程里有多套 `Dao`，建议显式配置：

```properties
geelato.orm.dao-bean-name=dynamicDao
```

这样 Fluent DSL 最终执行时，绑定关系更清晰，也更利于排障。

### 第 3 步：再决定是否需要动态数据源

不是所有项目都需要动态数据源。

只有当你存在下面这些需求时，才继续往下做：

- 同一套服务代码要访问多个库
- 某些查询/存储过程要按 key 切换到其他库
- 宿主工程需要自己管理动态库清单

## ORM 绑定哪个 Dao

当前显式属性是：

```properties
geelato.orm.dao-bean-name=dynamicDao
```

如果宿主工程配置了这个属性，ORM 会优先绑定指定名称的 `Dao` Bean。

如果未配置且存在多个 `Dao`，当前兼容回退顺序是：

1. `dynamicDao`
2. `primaryDao`

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

动态数据源当前的显式配置前缀是：

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

典型工程中，`primaryJdbcTemplate` 由 Starter 提供；如果你没有使用 Starter，也可以在业务工程里自行提供 `primaryJdbcTemplate`（例如基于 Spring Boot 的主数据源构建 `JdbcTemplate`），从而触发动态数据源自动装配。

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

如果你的诉求只是“这条链路切源”，优先用这种方式，最直接。

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

如果你希望某类方法整体使用同一数据源，或者希望注入 `dynamicDao` 体系，`@UseDynamicDataSource` 会比每条查询都写 `.useDataSource(...)` 更省事。

### 方式 3：让 ORM 默认绑定动态源的 `Dao`

如果你的项目里大多数 ORM 操作都应落到动态源链路，建议把 ORM 绑定的 `Dao` 指到 `dynamicDao`：

```properties
geelato.orm.dao-bean-name=dynamicDao
```

这样 Fluent DSL 默认就会走动态源能力，而不是每次都手工指定。

## 默认数据源定义来源

当前默认动态数据源定义加载器是：

- `PlatformDynamicDataSourceDefinitionLoader`

它通过 `DynamicDataSourceConfiguration` 在宿主工程没有自定义实现时自动创建。

这意味着如果你不替换它，当前默认定义来源仍然是平台表语义。

## 如何覆盖动态数据源定义来源

宿主工程只要提供自己的：

- `DynamicDataSourceDefinitionLoader`

就可以替换默认加载逻辑。

推荐按下面 3 步做。

### 第 1 步：确认你为什么要替换

常见原因包括：

- 想从配置中心读取动态源
- 想从 YAML / 本地文件读取
- 想从外部注册中心或服务发现系统读取

### 第 2 步：实现加载器

最小示例：

```java
@Configuration
public class MyDynamicDataSourceDefinitionConfiguration {
    @Bean
    public DynamicDataSourceDefinitionLoader dynamicDataSourceDefinitionLoader() {
        return new DynamicDataSourceDefinitionLoader() {
            @Override
            public List<Map<String, Object>> loadAll() {
                return List.of();
            }

            @Override
            public Map<String, Object> loadOne(String key) {
                return null;
            }
        };
    }
}
```

适合的场景包括：

- 从配置中心读取动态源
- 从文件或 YAML 读取动态源
- 从外部服务注册中心读取动态源

### 第 3 步：触发一次真实查询验证

实现加载器后，不要只看 Spring 启动成功，建议立刻跑一条真实查询：

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("portal")
        .page(1, 1)
        .list();
```

如果这里能成功执行，再说明“定义加载 + 数据源切换 + ORM 执行链路”是通的。

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

如果你要在一个新项目里同时接 ORM、动态数据源和平台规则，推荐顺序是：

1. 先把 `primaryDao` 或其他基础 `Dao` 跑通
2. 再确认 `geelato.orm.dao-bean-name` 绑定正确
3. 再启用动态数据源并验证 `.useDataSource(...)`
4. 再视需要补 `@UseDynamicDataSource`
5. 最后再实现查询过滤和字段填充 SPI

这样做的好处是，一旦出问题，你能快速判断到底是：

- `Dao` 没配好
- 动态源没生效
- 数据源定义没加载到
- SPI 扩展写错了

## 一步一步排障

如果你感觉“切源没生效”或“SPI 没生效”，建议按这个顺序查：

1. 看容器里是否真的存在 `primaryDao` / `dynamicDao`
2. 看 `geelato.orm.dao-bean-name` 是否绑定到了你预期的 Bean
3. 看动态源定义是否真的能加载出目标 key
4. 看代码里是否真的调用了 `.useDataSource("...")` 或命中了 `@UseDynamicDataSource`
5. 看同类 SPI 是否注册了多个实现
6. 看 SPI 的 `isEnabled()` 是否返回 `true`

## 推荐使用建议

- 常规后端 CRUD 优先使用 ORM
- 动态源定义来源优先通过扩展点覆盖，不要硬改默认实现
- 宿主项目若有多套 `Dao`，建议显式配置 `geelato.orm.dao-bean-name`
- 除非明确需要，否则不要默认开启 JTA / Seata
- 单条链路切源优先用 `.useDataSource(...)`
- 组件级切源再考虑 `@UseDynamicDataSource`
- 平台级查询规则和字段规则优先用 SPI，不要散落在业务代码里

## 推荐继续阅读

- [动态数据源](../dynamic-datasource/overview.md)
- [ORM 总览](overview.md)
- [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)
- [新项目最小接入](../guide/minimal-integration.md)
