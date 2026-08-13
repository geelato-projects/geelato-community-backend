---
title: 术语表
sidebar_label: 术语表
---

# 术语表

本页汇总 Geelato 文档中的专有术语，按领域分组。每条给出定义并链接到详解页面，便于在阅读其他章节时快速对照。

## 数据访问

### MQL（Meta Query Language）

平台侧基于 JSON 的元数据查询与操作协议，面向前端页面、低代码界面与平台通用数据接口，提供统一的数据访问表达方式。与后端 Java 侧的 Fluent DSL 解决不同层级的问题。

详见 [MQL](../mql/overview.md)、[MQL 使用指引](../mql/usage.md)。

### ORM

Object-Relational Mapping（对象关系映射）。在 Geelato 中并非单一 API，而是围绕"元数据 + 查询协议 + Java DSL + 扩展机制"组织的体系，分为注解层、协议层（MQL）、Java API 层（Fluent DSL）与扩展层。

详见 [ORM](../orm/overview.md)。

### Fluent DSL

后端 Java 服务的元数据 CRUD 入口，通过 `MetaFactory` 的链式 API 完成查询、保存、更新、删除、关联、过程调用与原生 SQL。它是面向 Java 服务代码的独立入口，而非 MQL 的字符串包装。

详见 [Fluent DSL 指引](../orm/fluent-dsl.md)。

### MetaFactory

后端 Fluent DSL 的统一入口类，暴露 `query/insert/update/delete/procedure/sql(...)` 链。需要容器中存在 `Dao` Bean 以自动装配执行器。

### MetaController

MQL 的核心处理类，暴露平台通用数据接口（`/api/meta/list`、`/api/meta/save/{biz}`、`/api/meta/delete/{biz}/{id}` 等）。

### Dao / dynamicDao / primaryDao

`Dao` 封装 `JdbcTemplate` 承载 ORM 执行。`primaryDao` 绑定主数据源，`dynamicDao` 基于路由数据源。存在多个 `Dao` 时通过 `geelato.orm.dao-bean-name=dynamicDao` 指定，兼容回退顺序为 `dynamicDao` → `primaryDao`。

### connectId

`@Entity(connectId=...)` 属性，显式声明实体所属的动态数据源 key，是实体到数据源的核心绑定点。解析优先级：`@Entity(connectId)` > `@Entity(catalog)` 映射 > 数据库元数据 `connect_id` > 默认主库。

详见 [动态数据源](../dynamic-datasource/overview.md)、[ORM 注解说明](../orm/annotations.md)。

## 元数据

### MetaStore

定义"元数据从哪里来"的 SPI（`cn.geelato.core.meta.spi`）。默认实现 `DefaultMetaStore` 从平台设计时表读取表/列/视图/校验/外键定义。属 Spring 风格 SPI，非 JDK `ServiceLoader`。

详见 [MetaStore 扩展](../reference/metastore-extension.md)。

### MetaConfiguration

`geelato-web-platform` 中的装配类，在 `setApplicationContext(...)` 阶段完成元数据扫描与数据库元数据装载，早于 `BootApplication.run(...)`。

### MetaManager

统一元数据消费者。扫描 `@Entity` 类（由 `geelato.meta.scan-package-names` 控制扫描包），解析为 `EntityMeta/FieldMeta/ColumnMeta`，并维护来自类与来自数据库两份元数据，通过统一缓存对外提供访问。

### MetaBootstrap

元数据装载完成后的二次增强入口（`metaBootstrap.bootstrap(metaManager, dao)`），适用于元数据补丁、启动时注册或宿主工程自定义收尾。可选。

### BaseEntity

业务实体的推荐基类，承载通用字段 `id/tenantCode/creator/creatorName/updater/updaterName/createAt/updateAt`，与 Fluent DSL 保存链路的默认字段填充天然配合。

## 注解

### `@Entity`

将类标注为框架可识别的元数据实体。关键属性：`name`（实体名，默认取类名）、`table`（表名）、`catalog`（逻辑库分组/数据源路由，`platform` 表示受保护系统实体）、`connectId`（显式数据源 key）。

### `@Col`

声明列映射或列约束（`name`、`charMaxlength`、`precision`、`scale`），在默认推导与实际不一致时使用。

### `@Title`

为实体或字段补充 `title` 与 `description`，服务于元数据管理、界面展示与文档理解。

### `@Transient`

标记不参与持久化的运行时属性，仍可参与 Java 业务逻辑，但不映射到数据库列。

详见 [ORM 注解说明](../orm/annotations.md)。

## 运行时与上下文

### PlatformWebRuntime

Geelato Web 平台的运行时应用壳，基于共享底座 `geelato-web-platform` 启动，面向业务执行场景。Spring Boot 启动类，继承 `BootApplication`。

详见 [PlatformWebRuntime](../runtime/platform-web-runtime.md)。

### BootApplication

`cn.geelato.web.platform.boot.BootApplication`，Geelato Runtime 的启动编排器（非 Spring Boot 最外层入口）。其 `run(...)` 完成数据源定义缓存、SQL/DB 脚本、Graal 上下文、环境配置缓存的收尾初始化。业务启动类继承它。

详见 [启动过程](../reference/startup-process.md)。

### SecurityContext

请求级（线程级）安全上下文。硬约束：安全主体只能在鉴权成功后由安全链路内部写入。

详见 [SecurityContext 生命周期](../runtime/security-context-lifecycle.md)。

### GlobalContext

`cn.geelato.core.GlobalContext`，平台底层全局运行参数入口，承载环境标识、密级、列加密/API 加密开关、加密算法与密钥读取等。

详见 [全局上下文](../platform-capabilities/global-context.md)。

### TrafficTag（流量染色）

为每次请求生成并透出的统一流量标记 `trafficTag`，用于灰度标识、链路透传、日志关联与在线用户观察。默认两档语义：`default`、`gray`。当前仅做标记，不内置网关分流。

详见 [流量染色](../platform-capabilities/traffic-tagging.md)。

### tenantCode（租户）

租户隔离字段。约定通过 `Tenant-Code` 请求头传递（未提供时默认 `geelato`），新增时自动填充。

## 扩展

### 查询过滤 SPI / 字段填充 SPI

将平台默认规则从底层模块抽离、改为在上层工程通过 SPI 显式接入：

- 查询过滤 SPI（`MqlQueryFilterInjector`、`FluentQueryFilterInjector`）：向查询链路自动注入租户、权限、组织等条件。
- 字段填充 SPI（`MqlSaveFieldValueFiller`、`FluentSaveFieldValueFiller`、`EntitySaveFieldValueFiller`）：向保存链路自动补齐审计字段。

运行时统一规则：0 个实现跳过、1 个实现按 `isEnabled()` 生效、多个实现直接报错。

详见 [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)。

### PF4J / 插件机制

插件机制基于 PF4J 实现，宿主工程仅依赖扩展点接口（`PluginExtensionPoint` 继承自 `org.pf4j.ExtensionPoint`），插件实现可独立打包、按需启停，由 `SpringPluginManager` 管理并通过 `PluginBeanProvider` 调用。

详见 [插件机制概览](../plugin-mechanism/overview.md)。

## 交付

### BOM / Starter

框架采用 `BOM + Starter` 模式交付：`geelato-framework-bom` 负责版本对齐，`geelato-framework-starter` 是推荐的最小框架底座统一入口。

详见 [BOM 与 Starter](../reference/bom-and-starter.md)。

### App Scaffold（脚手架）

基于 `geelato-app-scaffold-starter` 的"胖脚手架"，开箱即用提供登录、组织、用户、角色、字典、文件上传、MQL 与自动建表，适合从零开始的新项目。与"瘦启动"最小化接入（`geelato-framework-starter`，零建表）相对。

详见 [脚手架快速启动指南](../guide/app-scaffold-starter-project-guide.md)、[快速开始](../guide/quick-start.md)。

## 接口与报障

### SrvExplain

从源码静态扫描控制器、生成逐 Controller Markdown 的接口补充说明，用于补充尚未完全 OpenAPI 化的端点。与 OpenAPI、MQL 构成"标准契约 / 静态补充 / 平台协议"的双轨/三轨关系。

详见 [API 参考](../api/reference.md)、[SrvExplain API 目录](../api/srvexplain-catalog.md)。

### CoreException

平台业务异常的统一基类体系。每个错误码对应一个 `CoreException` 子类，异常响应会携带错误码、文案以及 `docUrl` 文档链接与 `logTag` 关联键，便于排障定位。

详见 [错误码参考](../reference/error-codes.md)。

### docUrl

异常响应中指向排障文档的字段。规则：错误码未声明 `docSlug` 时指向错误码参考页对应锚点（`.../error-codes#{code}`）；声明 `docSlug` 时指向独立详情页（`.../error-codes/{slug}`）。可通过 `GlobalContext.__DocUrlEnabled__ = false` 关闭。

### logTag

异常发生时生成的一次性关联键，写入异常响应与日志。通过日志搜索接口按 `logTag` 反查，可定位到该次异常的完整上下文日志。详见 [报障排查](troubleshooting.md)。

### traceId

贯穿单次请求全链路的追踪标识。以 `traceId` 在请求日志、业务日志、数据层日志之间串联，可还原一次请求的完整调用链。详见 [报障排查](troubleshooting.md)。
