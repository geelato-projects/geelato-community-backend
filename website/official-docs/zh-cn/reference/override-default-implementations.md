# 覆盖默认实现

Geelato Framework 的一个核心原则是：保留底层模块中的默认实现，但不把这些默认实现强行上升为唯一框架契约。

换句话说，框架会提供“可直接运行”的默认实现，同时也会保留明确的扩展点，方便宿主项目替换成自己的平台规则。

## 当前可覆盖的能力

当前已经明确支持或预留扩展的能力主要包括：

- `MetaStore`
- `MetaResourceProvider`
- `MetaBootstrap`
- `DynamicDataSourceDefinitionLoader`
- `MetaCommandExecutor`
- `MqlQueryFilterInjector`
- `FluentQueryFilterInjector`
- `MqlSaveFieldValueFiller`
- `FluentSaveFieldValueFiller`
- `EntitySaveFieldValueFiller`
- `OrgProvider`
- `UserProvider`
- Starter 默认提供的 `primaryDataSource` / `primaryJdbcTemplate` / `primaryDao`

其中，查询过滤与字段值填充已经采用新的 SPI 机制承载，推荐优先沿着 SPI 扩展，而不是回到业务层散点补逻辑。

## 覆盖方式一：直接提供同类型 Bean

如果默认 Bean 通过 `@ConditionalOnMissingBean` 保护，宿主项目最直接的方式，就是提供自己的同类型 Bean。

这适用于：

- `MetaCommandExecutor`
- `DynamicDataSourceDefinitionLoader`
- `OrgProvider`
- `UserProvider`
- 安全 Provider 类默认实现

对于查询过滤和字段值填充，虽然同样是注册 Spring Bean，但更推荐按对应 SPI 语义实现，而不是直接沿用旧的默认 Bean 覆盖叙事。

## 覆盖方式二：通过 SPI 扩展平台规则

对于“平台级默认规则”，当前推荐优先通过 SPI 扩展。

### 查询过滤 SPI

- `MqlQueryFilterInjector`
- `FluentQueryFilterInjector`

适用于：

- 租户隔离
- 数据权限
- 组织维度过滤
- 其他需要在查询链路中自动附加的通用规则

### 字段值填充 SPI

- `MqlSaveFieldValueFiller`
- `FluentSaveFieldValueFiller`
- `EntitySaveFieldValueFiller`

适用于：

- 创建人 / 更新人
- 创建时间 / 更新时间
- 租户编码
- BU / 部门等平台字段

这两组 SPI 统一遵循以下运行时规则：

1. 扫描到 `0` 个实现：跳过
2. 扫描到 `1` 个实现：按 `isEnabled()` 决定是否执行
3. 扫描到多个实现：直接报错

完整扩展方式见：

- [查询过滤与字段填充 SPI 扩展](spi-query-filter-and-save-fill-extension.md)

## 覆盖方式三：覆盖同名基础 Bean

Starter 当前对 JDBC 基础 Bean 使用了按名称缺省创建：

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `secondaryDataSource`
- `secondaryJdbcTemplate`
- `secondaryDao`
- `dbGenerateDao`

如果你在宿主工程中先提供这些同名 Bean，Starter 默认实现就不会再创建。

## 覆盖方式四：通过属性选择默认实现

当前 ORM 支持通过：

```properties
geelato.orm.dao-bean-name=dynamicDao
```

来显式指定 `MetaCommandExecutor` 绑定哪个 `Dao` Bean。

如果不配置且存在多个 `Dao`，兼容回退顺序当前是：

1. `dynamicDao`
2. `primaryDao`

## 覆盖方式五：在上层项目重写默认来源

这种方式适用于：

- 自定义 `MetaStore`
- 自定义动态数据源定义来源
- 自定义运行时上传或文件落盘实现
- 自定义组织 / 用户快照加载能力

原则是：底座保留统一消费入口，上层项目根据自己的领域边界替换默认实现。

## 兼容层说明

`SaveDefaultValueFiller` 目前仍保留在 `geelato-orm` 中，但它已经是兼容层，不再是 Fluent 保存链路的主扩展入口。

如果你要扩展当前保存链路，优先使用：

- `MqlSaveFieldValueFiller`
- `FluentSaveFieldValueFiller`
- `EntitySaveFieldValueFiller`

## 不推荐的方式

不建议通过以下方式“硬改默认实现”：

- 直接修改 Starter 已发布契约
- 在页面层或业务层写大量散乱补丁逻辑
- 把强平台语义的能力重新塞回基础模块

## 推荐继续阅读

- [查询过滤与字段填充 SPI 扩展](spi-query-filter-and-save-fill-extension.md)
- [MetaStore 扩展](metastore-extension.md)
- [ORM / 数据源扩展](../orm/datasource-extension.md)
- [安全 Provider 扩展](security-provider-extension.md)
- [基于 app-scaffold-starter 创建业务项目](../guide/app-scaffold-starter-project-guide.md)
