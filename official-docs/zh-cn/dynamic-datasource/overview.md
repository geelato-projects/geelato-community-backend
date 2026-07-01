# 动态数据源

这篇文档说明 `geelato-dynamic-datasource` 模块当前提供的动态数据源能力，以及它在运行时如何完成：

- 数据源定义加载
- 数据源实例构建
- 数据源路由切换
- 实体到数据源的解析
- 事务相关预留能力

如果你的目标不是“理解现有能力”，而是“替换默认定义来源或做深度扩展”，请继续阅读：

- [ORM / 数据源扩展](../orm/datasource-extension.md)

## 模块定位

`geelato-dynamic-datasource` 不是一个孤立的数据源池工具，而是框架里围绕 ORM / `Dao` 执行链路提供的动态路由模块。

它的职责可以概括为：

- 让 `dynamicDao` / `dynamicJdbcTemplate` 可以按实体或上下文切换连接
- 让平台表中的动态库定义转成可用的 `DataSource`
- 让宿主工程在不改 `Dao` 调用方式的前提下接入多数据源

也就是说，业务代码更关心“当前实体应该落到哪个数据源”，而不是手工管理每个连接池实例。

## 当前装配了哪些核心 Bean

动态数据源配置入口是：

- `DynamicDataSourceConfiguration`

它当前会装配：

- `dynamicDataSource`
- `dynamicJdbcTemplate`
- `DynamicDataSourceDefinitionLoader`

其中：

- `dynamicDataSource` 是真正的路由数据源
- `dynamicJdbcTemplate` 是基于路由数据源构建的 `JdbcTemplate`
- `DynamicDataSourceDefinitionLoader` 是动态数据源定义来源

如果宿主工程没有自定义 `DynamicDataSourceDefinitionLoader`，默认会创建：

- `PlatformDynamicDataSourceDefinitionLoader`

## 整体工作链路

当前动态数据源的大致工作顺序可以理解为：

1. 启动时由 `DynamicDataSourceRegistry` 加载数据源定义
2. `DynamicDataSourceRegistry` 保存数据源配置，并按需构建 `DataSource`
3. `DynamicRoutingDataSource` 在执行时根据当前线程中的 key 选择真实数据源
4. `DataSourceInterceptor` 在 `Dao` 调用前解析实体并设置数据源 key
5. ORM / `Dao` 执行完成后继续走当前选中的数据源

因此它本质上是一条：

- 元数据解析
- ThreadLocal 选源
- 路由数据源执行

组合出来的运行时能力链路。

## 数据源定义从哪里来

当前默认定义加载器是：

- `PlatformDynamicDataSourceDefinitionLoader`

它默认从主库读取：

- `platform_dev_db_connect`

对应 SQL 是：

- `SELECT * FROM platform_dev_db_connect`
- `SELECT * FROM platform_dev_db_connect WHERE id = ?`

这说明当前默认设计是：

- 动态数据源定义由平台表统一维护
- 路由 key 和平台连接定义主键 `id` 对齐

如果你不想继续使用平台表作为事实来源，而是想改成配置中心、YAML、外部服务或别的注册中心，就不要修改默认实现，而是去看：

- [ORM / 数据源扩展](../orm/datasource-extension.md)

## 数据源实例如何构建

真正负责把连接定义转成 `DataSource` 的类是：

- `DataSourceFactory`

它当前支持：

- MySQL
- PostgreSQL

并默认使用：

- `HikariDataSource`

构建连接池。

同时它还做了几件事：

- 对数据库密码做解密
- 通过 `DbHostMapFileLoader` 进行主机地址映射
- 套用统一连接池参数
- 在开启 Seata 代理时包装 `DataSourceProxy`

也就是说，这里不只是简单拼一个 JDBC URL，而是把：

- 连接地址
- 驱动类型
- 连接池配置
- 安全解密
- 宿主机映射

统一收口在一个工厂里。

## 路由数据源如何切换

路由核心类是：

- `DynamicRoutingDataSource`

它继承自：

- `AbstractRoutingDataSource`

当前切换依据来自：

- `DynamicDataSourceHolder`

而 `DynamicDataSourceHolder` 本质上是一个：

- `ThreadLocal<String>`

所以当前动态数据源能力的关键模型是：

- 当前线程里先写入一个数据源 key
- 路由数据源在执行时读取这个 key
- 再拿到真实 `DataSource`

如果当前 key 对应的数据源还没有真正创建，`DynamicRoutingDataSource` 会触发：

- 延迟创建
- 写回路由映射
- 刷新内部 targetDataSources

这也是当前模块支持“懒加载动态源”的关键。

## 为什么默认主库一定存在

`DynamicRoutingDataSource` 在刷新映射时强制要求：

- `primaryDataSource` 不能为 `null`

并把它作为：

- 默认数据源

同时如果存在 `secondaryDataSource`，也会一起加入路由表。

因此当前设计里：

- `primary` 是基础保底数据源
- `secondary` 是可选附加固定源
- 其他动态源在此基础上继续扩展

## 实体如何映射到动态数据源

负责按实体解析数据源的类是：

- `EntityDataSourceResolver`

它当前流程是：

1. 先查实体到数据源的本地缓存
2. 如果缓存失效，再从实体元数据里读取
3. 取出实体表元数据中的 `connectId`
4. 判断该 key 在 `DynamicDataSourceRegistry` 中是否存在

也就是说，当前最关键的映射关系其实是：

- `EntityMeta.tableMeta.connectId -> dynamic datasource key`

这意味着平台实体一旦绑定了 `connectId`，ORM 执行时就能自动解析到对应数据源。

同时它还提供了手工能力：

- `addEntityMapping(...)`
- `removeEntityMapping(...)`
- `clearCache()`

适合做运行期缓存修正或局部覆盖。

## `Dao` 调用时如何自动选源

负责在 ORM 执行前设置当前数据源 key 的切面是：

- `DataSourceInterceptor`

它做了两类事情。

### 1. 处理 `@UseDynamicDataSource`

如果类或方法上标注了：

- `@UseDynamicDataSource`

它会把注解里的 `value()` 写成当前默认数据源。

这适合：

- 某一类服务整体默认走某个数据源
- 某个方法明确指定默认路由目标

### 2. 拦截 `Dao.*` 方法

它会环绕：

- `cn.geelato.core.orm.Dao.*(..)`

并从参数里尝试提取：

- `BoundSql`
- `BoundPageSql`
- 带 `@Entity` 注解的实体类

拿到实体名后，再调用：

- `EntityDataSourceResolver.resolveDataSource(entityName)`

如果解析成功，就切换到实体对应的数据源；如果解析不到，则回退到 `@UseDynamicDataSource` 指定的默认源。

因此当前优先级可以概括为：

- 优先按实体元数据解析数据源
- 解析不到时再看注解指定的默认源
- 最终仍可回退到主数据源

## 懒加载和刷新机制

动态数据源注册器是：

- `DynamicDataSourceRegistry`

它同时维护：

- 数据源实例缓存 `dataSourceMap`
- 数据源配置缓存 `dataSourceConfigMap`

当前支持：

- 启动时全量刷新
- 按 key 刷新单个数据源
- 移除数据源
- 外部直接注册一个已经构建好的数据源

如果配置了：

```properties
geelato.datasource.dynamic.delay-load-data-source=true
```

那么启动时只缓存定义，不立即创建所有连接池；只有真正路由到某个 key 时，才延迟创建对应 `DataSource`。

这对“动态源很多，但同时活跃的源不多”的场景更友好。

## 连接池与运行参数

动态数据源统一配置前缀是：

```properties
geelato.datasource.dynamic.*
```

当前主要参数包括：

- `delay-load-data-source`
- `enable-jta-transaction`
- `enable-seata-proxy`
- `minimum-idle`
- `maximum-pool-size`
- `idle-timeout-ms`
- `max-lifetime-ms`
- `connection-timeout-ms`
- `validation-timeout-ms`
- `keepalive-time-ms`
- `initialization-fail-timeout-ms`
- `connection-test-query`

默认设计偏向：

- 先保证普通业务工程能轻量运行
- 重事务和分布式事务默认不开启
- 连接池参数集中统一配置

## JTA / Seata 现状

当前模块确实预留了：

- JTA
- Seata

相关配置和事务类也已经存在，但默认策略仍然是：

- 不自动开启

需要特别说明的是：

- `TransactionalAspect` 当前并未激活真实拦截
- Seata 代理只是保留兼容入口
- JTA / 分布式事务属于可选增强链路，不是最小默认能力

因此在普通业务工程里，更应把它理解为：

- 有预留
- 有装配位
- 但默认仍以轻量、本地事务和普通路由为主

## 推荐使用方式

当前更推荐这样理解和使用动态数据源：

- 把主库当作稳定保底源
- 把 `platform_dev_db_connect` 当作默认动态源定义中心
- 把实体元数据里的 `connectId` 当作实体到库的核心绑定点
- 把 `dynamicDao` 作为 ORM 优先使用的动态路由执行入口
- 只在确有需要时再开启 JTA / Seata

## 什么时候看扩展章节

如果你只是想：

- 理解现有动态数据源怎么工作
- 正常使用动态路由能力
- 通过实体自动切库

这篇文档就够了。

如果你想继续做下面这些事情，就应该跳到：

- [ORM / 数据源扩展](../orm/datasource-extension.md)

典型场景包括：

- 替换 `DynamicDataSourceDefinitionLoader`
- 改成从配置中心或文件加载数据源定义
- 覆盖默认 ORM 绑定的 `Dao`
- 自定义多 `Dao` 策略
- 打开 JTA / Seata 并做进一步集成

## 推荐继续阅读

- [ORM / 数据源扩展](../orm/datasource-extension.md)
- [ORM 总览](../orm/overview.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
