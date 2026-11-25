---
title: 动态数据源
sidebar_label: 动态数据源
---

# 动态数据源

本页说明 geelato-orm 提供的动态数据源能力（`cn.geelato.datasource` 包）及其运行机制，包括数据源定义加载、实例构建、路由切换、实体到数据源的解析以及事务相关预留能力。

本页是总览，其余各页按主题展开：

- [装载与注册](definition-loading.md) —— 数据源从哪里来、注册到哪个类、应用如何自定义装载
- [实体绑定与优先级](entity-binding.md) —— 实体如何与数据源绑定，完整优先级链
- [配置方式](configuration.md) —— 连接定义契约、配置参数、默认数据源
- [切换方式](switching.md) —— 自动/手工切换、请求级覆盖、嵌套语义与事务限制
- [主机地址映射](host-mapping.md) —— 开发环境内网地址本地映射

更深一层的 ORM 扩展（`Dao` 绑定、查询过滤 SPI 等）见 [ORM / 数据源扩展](../orm/datasource-extension.md)。

## 能力定位

动态数据源能力不是一个孤立的数据源池工具，而是 geelato-orm 围绕 ORM / `Dao` 执行链路提供的动态路由组件。

它的职责可以概括为：

- 让 `dynamicDao` / `dynamicJdbcTemplate` 可以按实体或上下文切换连接
- 让连接定义转成可用的 `DataSource`
- 让宿主工程在不改 `Dao` 调用方式的前提下接入多数据源

也就是说，业务代码更关心“当前实体应该落到哪个数据源”，而不是手工管理每个连接池实例。

## 当前装配了哪些核心 Bean

动态数据源的装配入口是 `OrmDynamicDataSourceAutoConfiguration`：容器中存在名为 `primaryJdbcTemplate` 的 Bean 时，它导入注册器 `DynamicDataSourceRegistry`、工厂 `DataSourceFactory`、路由数据源 `DynamicRoutingDataSource`、实体归属解析器 `EntityDataSourceResolver` 和切面 `DataSourceInterceptor`。

其中：

- `dynamicDataSource` 是真正的路由数据源
- `dynamicJdbcTemplate` 是基于路由数据源构建的 `JdbcTemplate`
- `DynamicDataSourceRegistry` 是注册中心，保存数据源定义与连接池

定义加载器 `DynamicDataSourceDefinitionLoader` 不在这里装配。它由宿主应用自己提供：框架模块 `geelato-orm` 不带实现，平台宿主自带 `PlatformDynamicDataSourceDefinitionLoader`。装载与注册的完整说明见[装载与注册](definition-loading.md)。

## 整体工作链路

当前动态数据源的大致工作顺序可以理解为：

1. 启动时 `DynamicDataSourceRegistry` 在构造过程中调用定义加载器的 `loadAll()`，加载数据源定义
2. `DynamicDataSourceRegistry` 保存数据源配置，并按需构建 `DataSource`
3. `DataSourceInterceptor` 在 `Dao` 调用前解析实体归属，由 `DynamicDataSourceScope`（切换唯一出入口）将数据源 key 写入线程上下文
4. `DynamicRoutingDataSource` 在执行时根据当前线程中的 key 选择真实数据源
5. 请求结束时由 `DataSourceContextCleanupFilter` 清理线程上下文（请求级覆盖上下文一并回收）

因此它本质上是一条：

- 元数据解析
- ThreadLocal 选源（唯一出入口 `DynamicDataSourceScope`）
- 路由数据源执行

组合出来的运行时能力链路。

## 数据源定义从哪里来

数据源有三个来源：

- Spring 配置的主库与副库：`primary` / `secondary`，不经过注册器
- 定义加载器 Bean `DynamicDataSourceDefinitionLoader`：注册器构造时调用一次 `loadAll()`，每条定义 Map 的 `id` 就是路由 key
- 运行期直接调用 `DynamicDataSourceRegistry.registerDataSource(...)`，登记一个已建好的连接池

关于定义加载器，两条规则决定了宿主应用如何接入：

- 容器中按类型只认**一个**加载器。宿主应用提供自己的实现即可自定义数据源来源；平台宿主已经有一个，需要加 `@Primary` 才能生效。
- 不提供任何实现时，框架只走 `primary` / `secondary`，仍可独立运行。

平台宿主自带的实现是 `PlatformDynamicDataSourceDefinitionLoader`（位于业务层 `geelato-web-platform`），从平台维护的连接定义中加载。

装载时序、注册器的方法、字段契约和自定义示例集中在一页：[装载与注册](definition-loading.md)。

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

- `DynamicDataSourceHolder`（本质上是一个 `ThreadLocal<String>`）

所有对它的写入统一收敛在唯一出入口：

- `DynamicDataSourceScope`——`withDataSource(...)` 代码块作用域 / `switchTo(...)` 请求内默认库

所以当前动态数据源能力的关键模型是：

- 当前线程里先写入一个数据源 key
- 路由数据源在执行时读取这个 key
- 再获取真实 `DataSource`

如果当前 key 对应的数据源还没有真正创建，`DynamicRoutingDataSource` 会触发：

- 延迟创建
- 写回路由映射
- 刷新内部 targetDataSources

这也是当前实现支持“懒加载动态源”的关键。

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
4. 校验该 key 在 `DynamicDataSourceRegistry` 中已注册——未注册直接硬失败，拒绝静默回退主库

也就是说，当前最关键的映射关系为：

- `EntityMeta.tableMeta.connectId -> dynamic datasource key`

这意味着平台实体一旦绑定了 `connectId`，ORM 执行时就能自动解析到对应数据源。

除了从数据库元数据读取 `connectId`，实体还可以通过 `@Entity` 注解声明数据源，并由 `MetaManager.resolveConnectId` 统一解析。解析规则（高 → 低）是：

1. `TableMeta.connectId` 非空——注意 `@Entity(connectId)` 显式指定与运行期登记值（登记入口由平台提供）写入的是**同一个槽位**（运行期登记后到合并，非 platform 实体可覆盖注解值），它们不是两级优先级
2. `@Entity(catalog)` 在 `catalog-mapping` 配置中的映射值
3. 都没有则返回 null，交由 Dao 调用期的兜底链决定

也就是说，即使实体没有在平台表里登记 `connectId`，也可以仅凭 `@Entity(connectId=...)` 或 `@Entity(catalog=...)` + `catalog-mapping` 配置完成路由。绑定方式与完整优先级链的专门说明见 [实体绑定与优先级](entity-binding.md)，注解声明方式的具体用法见 [ORM / 数据源扩展](../orm/datasource-extension.md)。

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

并从参数里按顺序尝试提取（识别失败继续看下一个参数）：

- `BoundPageSql` / `BoundSql`（命令携带 entityName）
- 带 `@Entity` 注解的实体类
- 非空 `List`（batchSave / multiSave / multiDelete 等，取首元素按同样规则识别）
- 带 `@Entity` 注解的实体实例（insert / save / update）

获取实体名后，再调用：

- `EntityDataSourceResolver.resolveDataSource(entityName)`

如果解析成功，就切换到实体对应的数据源；解析不到归属则走兜底链，并在调用结束后恢复外层的 key（保护嵌套切库语义）。

因此当前优先级可以概括为（高 → 低）：

- 实体映射（`TableMeta.connectId`，注解与 DB 登记同槽位 → `@Entity(catalog)` 映射）
- 类/方法级 `@UseDynamicDataSource` 指定的默认源
- 外层已显式设置的 key（`DynamicDataSourceScope.switchTo` / `withDataSource` 设置的线程上下文值）
- 请求级覆盖（仅对无显式归属实体生效，由归档查询请求头等驱动）
- 平台默认 key（启动时由 `OrmAutoConfiguration` 写入 `DataSourceManager`；它是方言快照与默认 key 的持有者，不是数据源注册中心，区别见[装载与注册](definition-loading.md)）
- `primary` 硬兜底

切换方式与事务限制的专门说明见 [切换方式](switching.md)。

## 懒加载和刷新机制

注册器 `DynamicDataSourceRegistry` 维护两张表：

- 定义快照 `dataSourceConfigMap`
- 已建连接池 `dataSourceMap`

默认开启懒加载（`geelato.datasource.dynamic.delay-load-data-source=true`）：启动时只保存定义，不创建连接池；真正路由到某个 key 时才建对应的 `DataSource`。设为 `false` 则在登记定义的同时建池。

这对“动态源很多，但同时活跃的源不多”的场景更友好。

注册器的全部方法、运行期刷新与移除的两步操作，见[装载与注册](definition-loading.md)。

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
- `connect-timeout-ms`
- `socket-timeout-ms`
- `catalog-mapping`：`catalog`（逻辑数据库分组）到数据源 key 的映射，配合 `@Entity(catalog)` 使用，详见 [ORM / 数据源扩展](../orm/datasource-extension.md)

默认设计偏向：

- 先保证普通业务工程能轻量运行
- 重事务和分布式事务默认不开启
- 连接池参数集中统一配置

## JTA / Seata 现状

当前实现预留了：

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
- 把连接定义的事实来源交给定义来源 SPI（平台提供默认实现）
- 把实体元数据里的 `connectId` 当作实体到库的核心绑定点
- 把 `dynamicDao` 作为 ORM 优先使用的动态路由执行入口
- 手工切库统一走 `DynamicDataSourceScope`：代码块作用域用 `withDataSource(...)`，请求内默认库用 `switchTo(...)`（平台 `switchDbByConnectId(...)` 为其薄委托）
- 只在确有需要时再开启 JTA / Seata

## 什么时候看扩展章节

若仅需：

- 理解现有动态数据源怎么工作
- 正常使用动态路由能力
- 通过实体自动切库

这篇文档就够了。

若需进一步执行以下事项，请跳转至：

- [装载与注册](definition-loading.md)
- [ORM / 数据源扩展](../orm/datasource-extension.md)

典型场景包括：

- 替换 `DynamicDataSourceDefinitionLoader`，改成从配置中心或文件加载数据源定义：见装载与注册
- 覆盖默认 ORM 绑定的 `Dao`，或自定义多 `Dao` 策略：见 ORM / 数据源扩展
- 打开 JTA / Seata 并做进一步集成：见 ORM / 数据源扩展

## 推荐继续阅读

- [装载与注册](definition-loading.md)
- [实体绑定与优先级](entity-binding.md)
- [配置方式](configuration.md)
- [切换方式](switching.md)
- [ORM / 数据源扩展](../orm/datasource-extension.md)
- [ORM 总览](../orm/overview.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
- [物化视图加速（平台）](../platform-capabilities/materialized-view.md) —— 外部引擎物化视图承接慢查询（平台能力）
