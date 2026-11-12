---
title: geelato-dyn 集成与部署
sidebar_label: 集成与部署
---

# 集成与部署

geelato-dyn 的集成单元是 **Karaf feature**：每个模块发布为 bundle jar + `features.xml`（classifier=`features`），目标 Karaf 通过 `feature:repo-add` 引入仓库定义、`feature:install` 安装。

## 前置条件

| 条件 | 要求 |
|---|---|
| Java | JDK 17 |
| Karaf | 4.4.10 |
| Maven 仓库 | 目标 Karaf 可访问（私服或构建机本地 `~/.m2`） |
| 数据库 | 按模块执行建表脚本（各模块 `src/main/resources/sql/`） |
| 平台侧 | 消息出箱需 geelato-message 消息中心可达；站内信直连需社区平台可达 |

## 发布到 Maven 仓库

在 geelato-dyn 工程根目录构建并安装（或 deploy 到私服）：

```bash
mvn -pl <module> -am clean install
```

确保 `features.xml` 以 `classifier=features` 一并发布——feature 安装依赖它，直接 `bundle:install` 装裸 jar 会缺运行期依赖。

## 最小集安装

```bash
# 基石模块：消息 / 安全 / 用户 / 健康 / ID
feature:repo-add mvn:cn.geelato/geelato-dyn-common/0.0.1-SNAPSHOT/xml/features
feature:install geelato-dyn-common

# 中央数据源：需要数据库的模块都依赖它
feature:repo-add mvn:cn.geelato/geelato-dyn-datasource/0.0.1-SNAPSHOT/xml/features
feature:install geelato-dyn-datasource
```

:::warning 为什么不能只装 bundle
`geelato-dyn-common` 的 Blueprint 声明了 `HikariDataSource`、`JdbcTemplate`、Jackson 等依赖 bundle。只执行 `bundle:install -s mvn:cn.geelato/geelato-dyn-common/0.0.1-SNAPSHOT` 而不同时安装这些 bundle，Blueprint 实例化 bean 时会抛 `ComponentDefinitionException` / `ClassNotFoundException`。统一用 `feature:*` 命令，依赖由 features.xml 传递提供。
:::

## 按需安装

| 场景 | 安装命令（先 repo-add 后 install，坐标同上） | 前置 |
|---|---|---|
| 健康聚合 + 管理控制台 | `feature:install geelato-dyn-manage` | common |
| 统一回调 + 消费总线 | `feature:install geelato-dyn-unified-consumer-main` | common；**该 feature 会连带安装 unified-api** |
| 新增一个回调消费者 | `feature:install geelato-dyn-unified-consumer-ocean-tracking`（其余 consumer 同理） | consumer-main |
| 出站集成引擎 | `feature:install geelato-ob-integrate` | common + status-support |
| 定时业务模块 | `feature:install geelato-dyn-bus-shipping` / `geelato-dyn-bus-periodic-report` | common（bus-shipping 另依赖 datasource） |
| 演示 | `feature:install geelato-dyn-demo` | common + datasource |

安装某个模块时，其 features.xml 声明的依赖 feature 会由 Karaf 自动解析安装；`<reference>` 服务依赖为强依赖（GracePeriod），被依赖模块未就绪时业务 bundle 会等待而不是带病启动。

## 安装后验证

```bash
# Feature / Bundle 状态
feature:list | grep geelato-dyn
bundle:list | grep geelato-dyn

# 中央数据源：应看到 4 个 DataSource 服务，各带 datasource.name 属性
service:list javax.sql.DataSource

# 安全契约服务已注册
services | grep -E "UserProvider|OrgProvider"

# 健康聚合（需安装 manage，无鉴权）
curl "http://localhost:8181/cxf/health"
```

## 更新与卸载

标准更新流程（重发制品后）：

```bash
feature:uninstall <feature-name>
feature:repo-remove mvn:cn.geelato/<module>/0.0.1-SNAPSHOT/xml/features
feature:repo-add mvn:cn.geelato/<module>/0.0.1-SNAPSHOT/xml/features
feature:install <feature-name>
```

仅 jar 内容变化、依赖不变时，可轻量刷新：

```bash
feature:repo-refresh
bundle:update <bundleId>
```

卸载：

```bash
feature:uninstall <feature-name>
feature:repo-remove mvn:cn.geelato/<module>/0.0.1-SNAPSHOT/xml/features
```

## 远程部署（可选）

工程根 POM 预置了 `karaf-deploy` profile（karaf-maven-plugin），可在构建时把 feature 直接推送到远端 Karaf 实例，适合 CI 场景；日常运维仍以 `feature:*` 命令为准。

## 推荐继续阅读

- [配置与日志](configuration.md) —— 部署后的 cfg 覆盖、热更新与日志分离
- [内置能力](capabilities.md) —— 各能力模块的接入方式
