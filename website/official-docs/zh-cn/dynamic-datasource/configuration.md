---
title: 动态数据源配置
sidebar_label: 配置方式
---

# 动态数据源配置

本页说明动态数据源的全部配置入口：连接定义存在哪里、YAML 能调什么、默认数据源如何确定，以及连接变更后的刷新操作。

配置分为三层：

- **连接定义**：每一条物理库连接的信息，存在平台表 `platform_dev_db_connect` 中
- **模块参数**：连接池、懒加载、catalog 映射等，配置前缀 `geelato.datasource.dynamic.*`
- **ORM 侧参数**：默认数据源与 Dao 绑定，配置前缀 `geelato.orm.*`

## 连接定义：`platform_dev_db_connect` 表

动态数据源定义的默认事实来源是平台主库中的连接表，对应实体 `ConnectMeta`：

| 字段 | 列名 | 说明 |
|---|---|---|
| `id` | `id` | 主键，即**路由 key**——实体绑定、切库 API 传的 connectId 都是它 |
| `appId` | `app_id` | 归属应用 |
| `dbConnectName` | `db_connect_name` | 连接名称（必填） |
| `dbName` | `db_name` | 数据库名（必填） |
| `dbSchema` | `db_schema` | 数据库 schema（必填） |
| `dbType` | `db_type` | 数据库类型（必填），当前支持 `mysql` / `postgresql` |
| `dbUserName` | `db_user_name` | 用户名（必填） |
| `dbPassword` | `db_password` | 密码（必填，**加密存储**，建池时自动解密） |
| `dbPort` | `db_port` | 连接端口 |
| `dbHostnameIp` | `db_hostname_ip` | 主机名或 IP（必填） |
| `enableStatus` | `enable_status` | 启用状态，1 启用 / 0 停用 |

几个要点：

- **路由 key 与主键对齐**。`@Entity(connectId = "xxx")`、`useDataSource("xxx")`、`switchDbByConnectId("xxx")` 里的 `xxx` 都是这张表的 `id`
- 建池由 `DataSourceFactory` 统一完成：按 `dbType` 选择方言（仅 MySQL / PostgreSQL），使用 HikariCP，解密密码，套用统一连接池参数
- 开发环境需要把内网主机地址映射到本地时，见[主机地址映射](host-mapping.md)
- 想换成从配置中心、YAML 或外部服务加载连接定义，属扩展能力，见 [ORM / 数据源扩展](../orm/datasource-extension.md)

## 模块参数：`geelato.datasource.dynamic.*`

### catalog 映射

`catalog`（逻辑数据库分组）到数据源 key 的映射，配合 `@Entity(catalog)` 使用：

```yaml
geelato:
  datasource:
    dynamic:
      catalog-mapping:
        platform: primary
        business: biz_db
```

声明了 `@Entity(catalog = "business")` 的实体路由到 `biz_db`。映射在查询期即时解析，启动后补配置同样生效。解析优先级的完整说明见[实体绑定](entity-binding.md)。

### 连接池与超时

这些参数会套用到每一个动态构建的数据源上（HikariCP）：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `minimum-idle` | `5` | 最小空闲连接 |
| `maximum-pool-size` | `20` | 最大连接数 |
| `idle-timeout-ms` | `600000` | 空闲超时（毫秒） |
| `max-lifetime-ms` | `1800000` | 连接最大存活时间 |
| `connection-timeout-ms` | `5000` | 从池中借连接的超时 |
| `validation-timeout-ms` | `3000` | 连接校验超时 |
| `keepalive-time-ms` | `300000` | 保活探测间隔 |
| `initialization-fail-timeout-ms` | `0` | 初始化失败超时（0 表示不阻塞启动） |
| `connection-test-query` | `SELECT 1` | 连接有效性测试语句 |
| `connect-timeout-ms` | `5000` | TCP 建连超时，追加到 JDBC URL |
| `socket-timeout-ms` | `300000` | socket 读超时，追加到 JDBC URL；超过该时长的查询会被驱动掐断 |

### 懒加载与事务增强

| 参数 | 默认值 | 说明 |
|---|---|---|
| `delay-load-data-source` | `true` | 懒加载：启动时只缓存连接定义，真正路由到某个 key 时才建池。对"动态源很多、同时活跃的少"的场景友好 |
| `enable-jta-transaction` | `false` | 开启 Atomikos JTA 分布式事务装配位（可选增强，默认轻量本地事务） |
| `enable-seata-proxy` | `false` | 为动态数据源包装 Seata `DataSourceProxy`（需自行引入 Seata 环境） |

JTA / Seata 属于预留能力，普通业务工程无需开启，详见[总览](overview.md)中"JTA / Seata 现状"。

## ORM 侧参数：`geelato.orm.*`

| 参数 | 默认值 | 说明 |
|---|---|---|
| `default-data-source-key` | 自动推断 | 平台默认数据源 key（优先级链第 4 级）。未配置时：容器中存在 `primaryDataSource` / `primaryJdbcTemplate` / `primaryDao` 任一 bean 则取 `primary` |
| `dao-bean-name` | 自动解析 | ORM 绑定的 Dao Bean。自动解析顺序：`dynamicDao` > `primaryDao` > 唯一的 Dao Bean，通常无需配置 |

`default-data-source-key` 的解析发生在启动完成阶段（所有单例 Bean 就绪后），由 `OrmAutoConfiguration` 写入 `DataSourceManager`。运行期的优先级链如何消费它，见[实体绑定](entity-binding.md)。

## Bean 装配条件

理解哪些 Bean 在什么条件下出现，有助于排查"为什么没切库"：

- **Starter 侧**（`spring.datasource.primary.jdbc-url` 存在时）：`primaryDataSource` → `primaryJdbcTemplate` → `primaryDao`；配置了 `spring.datasource.secondary.*` 时另有 `secondaryDataSource` 等。`primaryDao` 直连物理主库，**不经过路由**
- **动态数据源体系**（容器中存在 `primaryJdbcTemplate` 时自动装配）：`dynamicDataSource`（路由数据源）→ `dynamicJdbcTemplate` → `dynamicDao`，以及定义加载器、注册器、切面等
- 未使用 Starter 的工程，只要自行提供一个名为 `primaryJdbcTemplate` 的 Bean，同样能触发动态数据源体系装配

Bean 拓扑与"只有 `dynamicDao` 能切库"的完整说明，见[切换方式](switching.md)。

## 连接变更后的刷新

连接定义存在数据库里，修改后无需重启应用，通过平台接口刷新：

- `POST /model/connect/refresh/{id}` —— 刷新单个连接（重新读取定义、重建该 key 的数据源）
- `POST /model/connect/refreshAll` —— 全量刷新

刷新由 `DevDbConnectService` 完成：先更新注册器中的定义与实例，再刷新路由数据源的映射表；已被删除或停用的连接会被移出路由。懒加载模式下，未建过池的连接只更新定义，仍等到首次路由时才建池。

:::note 实体映射缓存

`EntityDataSourceResolver` 对"实体 → 数据源"的解析结果有内存缓存，但每次命中都会校验目标数据源仍然注册在案，数据源被移除时缓存自动失效，因此刷新连接定义后无需额外清理实体映射。

:::

## 推荐继续阅读

- [实体绑定与优先级](entity-binding.md) —— 四种绑定方式与完整优先级链
- [切换方式](switching.md) —— 自动切库、手工切换与事务限制
- [主机地址映射](host-mapping.md) —— 开发环境内网地址本地映射
