# 覆盖默认实现

Geelato Framework 的一个核心原则是：保留模块内默认实现，但不把默认实现强制提升为最终框架契约。

## 可以覆盖什么

当前阶段已经明确支持覆盖的能力主要包括：

- `MetaStore`
- `MetaResourceProvider`
- `MetaBootstrap`
- `DynamicDataSourceDefinitionLoader`
- `MetaCommandExecutor`
- `SaveDefaultValueFiller`
- Starter 默认提供的 `primaryDataSource` / `primaryJdbcTemplate` / `primaryDao`

## 覆盖方式一：直接提供同类型 Bean

如果默认 Bean 使用了 `@ConditionalOnMissingBean`，最直接的方式就是在宿主工程中提供自己的同类型 Bean。

这适用于：

- `MetaCommandExecutor`
- `SaveDefaultValueFiller`
- `DynamicDataSourceDefinitionLoader`
- 安全 Provider 类默认实现

## 覆盖方式二：覆盖同名基础 Bean

Starter 当前对 JDBC 基础 Bean 使用了按名称缺省创建：

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `secondaryDataSource`
- `secondaryJdbcTemplate`
- `secondaryDao`
- `dbGenerateDao`

如果你在宿主工程中先提供这些同名 Bean，Starter 默认实现就不会再创建。

## 覆盖方式三：通过属性选择默认实现

当前 ORM 支持通过：

```properties
geelato.orm.dao-bean-name=dynamicDao
```

来显式指定 `MetaCommandExecutor` 绑定哪个 `Dao` Bean。

如果不配置且存在多个 `Dao`，兼容回退顺序当前是：

1. `dynamicDao`
2. `primaryDao`

## 覆盖方式四：在上层项目重写默认来源

这类方式适用于：

- 自定义 `MetaStore`
- 自定义动态数据源定义来源
- 自定义运行时上传或文件落盘实现
- 自定义组织/用户快照加载能力

原则是：底座保留统一消费入口，上层项目根据自己的领域边界替换默认实现。

## 不推荐的方式

不建议通过以下方式“硬改默认实现”：

- 直接修改 Starter 已发布契约
- 在页面层或业务层写大量散乱补丁逻辑
- 把强平台语义的能力重新塞回基础模块

## 推荐继续阅读

- [MetaStore 扩展](metastore-extension.md)
- [ORM / 数据源扩展](../orm/datasource-extension.md)
- [默认实现与 Sample 定位](../guide/default-implementation-vs-sample.md)
