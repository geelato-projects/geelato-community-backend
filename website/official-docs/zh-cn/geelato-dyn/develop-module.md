---
title: 新模块开发指南
sidebar_label: 新模块开发
---

# 新模块开发指南

新业务模块从脚手架 `geelato-dyn-template` 复制创建。模板源自实际业务模块的结构，含三份 Blueprint 模板、建表脚本与定时任务骨架。

## 从模板创建

1. 复制 `geelato-dyn-template` 目录，重命名为 `geelato-dyn-{your-module}`；
2. 全局替换三组标识：

| 替换 | 示例 |
|---|---|
| `Template` → `{YourModule}`（首字母大写） | `TemplateJob` → `InventoryJob` |
| `template` → `{your-module}`（全小写） | 包名 `cn.geelato.dyn.template` → `cn.geelato.dyn.inventory` |
| `tt_` → `{your_prefix}_` | 表名前缀，如 `tt_entity` → `inv_task` |

3. 修改 `pom.xml` 的 artifactId 为 `geelato-dyn-{your-module}`；
4. 编辑 `src/main/resources/sql/init.sql` 建表脚本并执行；
5. 把新模块加入根 `pom.xml` 的 `<modules>`。

## 命名规范

| 组件 | 模式 | 示例 |
|---|---|---|
| 包名 | `cn.geelato.dyn.{module}` | `cn.geelato.dyn.inventory` |
| 定时任务入口 | `{Module}Job implements Runnable` | `InventoryJob` |
| 业务服务 | `{Module}Service` | `InventoryService` |
| 数据库服务 | `{Module}DatabaseService` | `InventoryDatabaseService` |
| 实体类 | `{Module}Entity` | `InventoryEntity` |
| 业务 Blueprint | `{module}.xml` | `inventory.xml` |
| 表名前缀 | 自定义前缀 + `_` | `inv_` |

## 三份 Blueprint 模板

| 文件 | 职责 |
|---|---|
| `{module}.xml` | 业务 bean 装配（Service / Job / DatabaseService 的依赖注入） |
| `spring-jdbc.xml` | 数据源引用：`<reference>` 按逻辑名取中央数据源 + 自建 `JdbcTemplate`，本模块不含任何凭据 |
| `cron.xml` | 声明式定时任务 `scheduler:scheduledRunnable` |

## 接入框架能力（零配置 `<reference>`）

```xml
<!-- 中央数据源 -->
<reference id="dataSource" interface="javax.sql.DataSource" filter="(datasource.name=buss)"/>

<!-- 统一消息分发 -->
<reference id="messageDispatcher" interface="cn.geelato.dyn.common.message.MessageDispatcher"/>

<!-- 安全契约：用户 / 组织 -->
<reference id="userProvider" interface="cn.geelato.security.UserProvider"/>
<reference id="orgProvider"  interface="cn.geelato.security.OrgProvider"/>
```

需要出现在健康聚合里，再补一个 `ModuleHealthReporter`（见[内置能力 / 健康检查](capabilities.md#健康检查与聚合)）。配置键通过 `<cm:property-placeholder>` 声明 PID 并建 `etc/<PID>.cfg` 模板（见[配置与日志](configuration.md)）。

## SPI 扩展点总表

| SPI | 所属 | 用途 |
|---|---|---|
| `cn.geelato.dyn.common.health.ModuleHealthReporter` | common | 模块健康快照上报（runtime / business / items / ttlMs） |
| `cn.geelato.dyn.common.logging.ModuleLogLevelProvider` | common | 向聚合端补齐本模块 logLevel |
| `cn.geelato.dyn.common.message.channel.MessageChannelAdapter` | common | 新增消息渠道（出箱式或直连式） |
| `cn.geelato.dyn.unified.api.service.UnifiedConsumerBinding` | unified-api | 注册回调 token → bizCode 路由与自检端点 |
| `...processor.AbstractCallbackProcessor` | unified-api | 新增回调处理方式（落库 / MQ / 总线之外的） |
| 出站集成引擎：`ChannelAdapter` / `PayloadBuilder` / `IntegrationTaskDataValidator` | geelato-ob-integrate | 接入新的外部系统 / 报文 / 校验场景 |

## 对接 geelato 平台（community）

三种方式按需选择：

**安全契约（推荐，零成本）**：`geelato-dyn-common` 已导出 `cn.geelato.security` 契约包与 `DBUserProvider` / `DBOrgProvider` 实现，`<reference>` 引用即可查用户与组织。

**消息 REST（推荐）**：`<reference>` 引用 `MessageDispatcher`，出箱由消息中心完成（见[统一消息分发](message-dispatch.md)）。

**community ORM（高级，参考 demo）**：`geelato-dyn-demo` 演示了在 bundle 内嵌 Spring Context 承载 community `OrmAutoConfiguration`、让 Fluent DSL（`MetaFactory.query/insert/...`）可用的模式（ADR 0002），要点：

- 内嵌依赖：`geelato-orm` / `geelato-core` / `geelato-lang` / `geelato-utils` + spring-boot(autoconfigure)；
- Blueprint 注入中央数据源 OSGi 服务，`context.refresh()` 前以 singleton 注册进内嵌 Context；
- **TCCL 切换**：Blueprint extender 线程的 contextClassLoader 是 Karaf root classloader，refresh 前必须临时换成 bundle classloader，否则 `@ConditionalOnBean` 等注解处理类加载不到；
- **关闭实体自动扫描**：OSGi 多 ClassLoader 下扫描不可靠，设 `geelato.orm.entity-auto-scan-enabled=false`，改用 `MetaManager.parseOne(clazz)` 显式注册实体；
- 不重复手动注册 `OrmProperties`（`OrmAutoConfiguration` 已自带，重复会 `NoUniqueBeanDefinitionException`）。

参考实现：`geelato-dyn-demo/.../orm/DemoOrmBootstrap.java` 与 `examples/geelato-karaf-showcase` 的 ADR 文档。

## 构建与部署

```bash
# 构建（bundle jar + features classifier）
mvn -pl geelato-dyn-{your-module} -am clean install
```

```bash
# 安装 / 更新 / 卸载
feature:repo-add mvn:cn.geelato/geelato-dyn-{your-module}/0.0.1-SNAPSHOT/xml/features
feature:install geelato-dyn-{your-module}
```

feature 声明对 `geelato-dyn-common`（及按需 `geelato-dyn-datasource`）的依赖，由 Karaf 传递安装。

## 交付检查清单

- [ ] Blueprint 只按逻辑名引用数据源，模块内无 IP/账号/密码
- [ ] 定时任务已埋健康点（recordStart / recordSuccess / recordFailure，failure 脱敏）并注册 `ModuleHealthReporter`
- [ ] `karaf/etc/org.ops4j.pax.logging.cfg` 补了本模块的 logger 组（否则日志进 karaf.log）
- [ ] 需要 cfg 的配置已提供 `config/<PID>.cfg` 模板
- [ ] features.xml 声明了全部前置 feature
- [ ] `mvn -pl <module> -am clean install` 通过，`features.xml` 以 classifier=features 发布
- [ ] 安装后 `GET /cxf/health/modules` 能看到本模块条目

## 推荐继续阅读

- [集成与部署](installation.md) —— feature 安装与更新命令
- [统一回调与消费总线](callback-bus.md) —— consumer 类模块的接入
