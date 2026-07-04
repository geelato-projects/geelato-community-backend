# ORM / 数据源扩展

这篇文档说明当前框架里 ORM 内置的数据源能力与动态数据源扩展点，以及宿主工程如何在 Spring Boot 环境中完成接入与覆盖。

如果你想先理解“现有动态数据源能力本身怎么工作”，建议先看：

- [动态数据源](../dynamic-datasource/overview.md)

## ORM 当前入口

当前 ORM 自动装配入口是 `OrmAutoConfiguration`，核心关注点有两个：

- 创建 `MetaCommandExecutor`
- 创建 `SaveDefaultValueFiller`

其中 `MetaCommandExecutor` 的前提是宿主工程中存在 `Dao` Bean。

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

## 默认数据源定义来源

当前默认动态数据源定义加载器是：

- `PlatformDynamicDataSourceDefinitionLoader`

它通过 `DynamicDataSourceConfiguration` 在宿主工程没有自定义实现时自动创建。

这意味着如果你不替换它，当前默认定义来源仍然是平台表语义。

## 如何覆盖动态数据源定义来源

宿主工程只要提供自己的：

- `DynamicDataSourceDefinitionLoader`

就可以替换默认加载逻辑。

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

## JTA / Seata 何时开启

当前 JTA 和 Seata 都不是默认开启能力。

只有显式配置后才进入相应能力链路，例如：

```properties
geelato.datasource.dynamic.enable-jta-transaction=true
```

因此最小骨架和普通业务工程默认不会被重事务依赖污染。

## 推荐使用建议

- 常规后端 CRUD 优先使用 ORM
- 动态源定义来源优先通过扩展点覆盖，不要硬改默认实现
- 宿主项目若有多套 `Dao`，建议显式配置 `geelato.orm.dao-bean-name`
- 除非明确需要，否则不要默认开启 JTA / Seata

## 推荐继续阅读

- [动态数据源](../dynamic-datasource/overview.md)
- [ORM 总览](overview.md)
- [覆盖默认实现](../reference/override-default-implementations.md)
- [新项目最小接入](../guide/minimal-integration.md)
