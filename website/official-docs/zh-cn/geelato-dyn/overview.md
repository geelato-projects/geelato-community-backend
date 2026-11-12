---
title: geelato-dyn 动态集成框架总览
sidebar_label: 框架总览
---

# geelato-dyn 动态集成框架总览

`geelato-dyn` 是一套基于 **Apache Karaf 4.4.10 + OSGi** 的动态集成框架，独立于 geelato 平台（Spring Boot 宿主）运行，两者之间没有 Maven 依赖关系，只通过**安全契约内嵌、平台库直连、REST 调用**三种方式对接。

它解决的问题是：业务集成模块（定时任务、第三方回调、出站集成等）频繁增删、独立发版，不应反复重启平台进程。每个功能单元是一个 OSGi bundle，通过 Karaf feature 独立安装、更新、卸载，运行期互不影响。

:::note 与"动态数据源"无关
本框架（geelato-dyn）与 Spring 侧的动态数据源模块（geelato-dynamic-datasource，见[数据源章节](../dynamic-datasource/overview.md)）名字相近但毫无关系。动态数据源解决 Spring 宿主内的多库路由；geelato-dyn 是 Karaf 里的独立运行时。
:::

## 技术栈

| 组件 | 版本 | 作用 |
|---|---|---|
| Apache Karaf | 4.4.10 | 容器与 feature 部署体系 |
| OSGi Blueprint | Aries | bean 装配与 `<reference>` 服务注入 |
| OSGi Config Admin | Aries | `etc/*.cfg` 配置与热更新 |
| Apache CXF | JAX-RS 2.1 | REST 服务（挂在 Karaf 默认 `/cxf` 下） |
| Apache Camel | 3.22.4 | 进程内路由（`vm:` 端点分发） |
| Karaf Scheduler | Quartz cron | 定时任务 |
| HikariCP | 5.1.0 | 连接池（中央数据源模块统一持有） |
| pax-logging (log4j2) | — | 按模块分离日志文件 |
| Java | 17 | 编译与运行基线 |

## 内置能力全景

| 能力域 | 提供模块 | 门面 / 入口 |
|---|---|---|
| 中央数据源（按逻辑名引用） | geelato-dyn-datasource | `javax.sql.DataSource` OSGi 服务（`datasource.name=…`） |
| 统一消息分发（6 渠道） | geelato-dyn-common | `cn.geelato.dyn.common.message.MessageDispatcher` |
| 安全契约 + 用户/组织查询 | geelato-dyn-common | `cn.geelato.security.UserProvider` / `OrgProvider` |
| 健康检查与聚合 | geelato-dyn-common + geelato-dyn-manage | `ModuleHealthReporter` SPI → `GET /cxf/health` |
| 统一回调北向入口 | geelato-dyn-unified-api | `POST /cxf/unified/callback/{token}` |
| 统一消费端总线 | geelato-dyn-unified-consumer-main | Camel `vm:` 按 bizCode 分发 |
| 可视化管理控制台 | geelato-dyn-manage | `/cxf/dyn/manage/`（Token 鉴权） |
| 定时任务 | Karaf scheduler / Blueprint | `Runnable` 服务属性 或 `scheduler:scheduledRunnable` |
| 雪花 ID | geelato-dyn-common | `cn.geelato.dyn.common.id.SnowflakeIds` |
| 日志按模块分离 | Karaf 基线配置 | 每模块一个 `dyn-*.log` |

各能力的接入方式见[内置能力](capabilities.md)；消息分发与回调总线因篇幅独立成页：[统一消息分发](message-dispatch.md)、[统一回调与消费总线](callback-bus.md)。

## 模块清单

| 模块（artifactId） | 角色 | 部署 |
|---|---|---|
| `geelato-dyn-common` | 基石 bundle：消息 / 安全 / 用户 / 健康 / ID 的契约与装配 | 必装（所有模块的前置） |
| `geelato-dyn-datasource` | 中央数据源：4 个 HikariCP 逻辑库按逻辑名发布 | 需要数据库的模块前置 |
| `geelato-dyn-manage` | 健康聚合 REST + 可视化管理控制台 | 可选（运维） |
| `geelato-dyn-unified-api` | 统一回调北向入口 + 捕获/处理落库 | 可选（回调场景） |
| `geelato-dyn-unified-consumer-main` | 统一消费端总线（Camel `vm:` 分发） | 可选（与 unified-api 配套，其 feature 会连带安装 unified-api） |
| `geelato-dyn-unified-consumer-*` | 回调消费者实现（海运跟踪 / 海运预警 / GSF 申报状态） | 业务模块，按需 |
| `geelato-ob-integrate` | 出站集成引擎（含 ChannelAdapter / PayloadBuilder 等 SPI） | 业务模块，按需 |
| `geelato-dyn-ob-integrate-status-support` | 出站集成的状态映射支持库 | 业务支持库，按需 |
| `geelato-dyn-bus-shipping` | 定时任务业务模块示例 | 业务模块，按需 |
| `geelato-dyn-bus-periodic-report` | 周期报告模块示例（报告五段式 SPI） | 业务模块，按需 |
| `geelato-dyn-demo` | 消息 / 安全 / **community ORM 集成**演示 | 演示，按需 |
| `geelato-dyn-template` | 新模块脚手架（不进入部署） | 不部署 |
| `examples/geelato-karaf-showcase` | OSGi 独立示例工程与 ADR 文档 | 不部署 |

业务 consumer 与 bus 模块的具体业务逻辑不在本章节范围内，文档只覆盖框架与集成。

## 模块依赖拓扑

```text
geelato-dyn-common（基石：消息 / 安全 / 用户 / 健康 / ID）
   ├── geelato-dyn-datasource（中央数据源，需要数据库的模块依赖它）
   ├── geelato-dyn-manage（运维聚合，独立安装）
   ├── geelato-dyn-unified-api（回调入口）
   │       └── geelato-dyn-unified-consumer-main（消费总线，feature 连带安装 unified-api）
   │               ├── unified-consumer-ocean-tracking
   │               ├── unified-consumer-ocean-tracking-warning
   │               └── unified-consumer-ob-integrate-gsf-status
   ├── geelato-ob-integrate（出站集成，依赖 status-support）
   ├── geelato-dyn-bus-shipping / geelato-dyn-bus-periodic-report（定时业务）
   └── geelato-dyn-demo（演示，另内嵌 community ORM）
```

没有"一个 starter"式的集成入口：最小运行集 = `geelato-dyn-common`（+ 需要数据库时 `geelato-dyn-datasource`），其余模块全部按需安装、可插拔。

## 与 geelato 平台的关系

| 对接点 | 方式 | 说明 |
|---|---|---|
| 安全契约 | 内嵌 jar | `geelato-dyn-common` 以 Embed-Dependency 内嵌 community 的 `geelato-security`，并 `Export-Package: cn.geelato.security`，Karaf 内所有 bundle 可直接 import |
| 用户 / 组织数据 | 平台库直连 | `DBUserProvider` / `DBOrgProvider` 直查 `platform_user` / `platform_org` 等表，实时无缓存 |
| 消息发送 | REST | 出箱经 geelato-message 消息中心 `POST /message/enqueue`，由消息中心落库与分发 |
| 站内信 | REST 直连 | `inapp` 渠道直连社区平台（`Authorization: SystemToken` 固定密钥），不经消息中心 |
| ORM（可选） | bundle 内嵌 Spring Context | demo 演示在 bundle 内承载 community `OrmAutoConfiguration`，Fluent DSL 可用（见[新模块开发](develop-module.md)） |

## 推荐继续阅读

- [集成与部署](installation.md) —— 环境准备、feature 安装、更新与验证
- [配置与日志](configuration.md) —— Config Admin 热更新、PID 清单、日志分离
- [内置能力](capabilities.md) —— 数据源 / 健康 / 安全 / 调度 / 管理台 / REST 端点总表
