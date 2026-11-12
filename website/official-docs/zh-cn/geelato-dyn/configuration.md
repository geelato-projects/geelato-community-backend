---
title: geelato-dyn 配置与日志
sidebar_label: 配置与日志
---

# 配置与日志

geelato-dyn 不使用 Spring 的 `application.yml`，配置体系建立在 **OSGi Config Admin** 之上：Blueprint 里的 `cm:property-placeholder` 定义默认值，Karaf `etc/` 目录下的 `.cfg` 文件按 PID 覆盖，修改后热生效。

## 配置体系

```text
Blueprint cm:default-properties（代码内默认值，开箱即用）
        ▲ 覆盖
Karaf etc/<PID>.cfg（环境差异值；修改后 Config Admin 自动重建 bean，无需重启）
```

要点：

- **cfg 优先于默认值**，且可以只覆盖需要差异化的键；
- `update-strategy="reload"`：修改 cfg 保存即热生效（如数据源连接池自动重建）；
- cfg 原生支持 `${env:XXX}` 引用环境变量，敏感凭据建议这样写，避免明文落盘；
- 若把 Blueprint 默认值清空而 cfg 缺失，bundle 启动会 fail-fast 并明确报出缺失属性名，防止静默连错库。

## PID 清单

| PID（cfg 文件名） | 所属模块 | 关键配置 |
|---|---|---|
| `cn.geelato.dyn.datasource` | geelato-dyn-datasource | 4 个逻辑库的连接与池参数 |
| `cn.geelato.dyn.manage` | geelato-dyn-manage | 管理台访问令牌 |
| 各业务模块 | bus-* / ob-integrate 等 | 见各模块 Blueprint 中的 `cm:property-placeholder` |

### `cn.geelato.dyn.datasource`

键模式：`<逻辑名>.jdbcUrl / username / password / pool.maxSize / pool.minIdle`。cfg 模板见仓库 `geelato-dyn-datasource/config/`：

```properties
platform.jdbcUrl = jdbc:mysql://<host>:3306/geelato?useSSL=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
platform.username = geelato_user
platform.password = ${env:GEELATO_PLATFORM_DB_PASSWORD}
platform.pool.maxSize = 10
platform.pool.minIdle = 2
# portal / integration / buss 三组同构
```

数据源逻辑名的含义与引用方式见[内置能力 / 中央数据源](capabilities.md#中央数据源)。

### `cn.geelato.dyn.manage`

```properties
manage.console.token=<访问令牌>
```

管理台展示数据源账号密码等敏感信息，**必须配置非空令牌**；未配置时 `/cxf/dyn/manage/api/*` 一律返回 503（页面可打开但无数据）。修改保存即热生效。

## Blueprint 内 bean 配置

一部分配置不是 cfg 键，而是直接写在 Blueprint XML 的 bean 属性上，修改需重新构建发布 bundle：

| 位置 | 内容 |
|---|---|
| `geelato-dyn-common/.../geelato-common-message.xml` | 消息出箱 `MessageCenterOutbox`（baseUrl / systemCode / apiKey / timeoutMs）；站内信直连 `InAppDirectChannelAdapter`（enabled / baseUrl / communityToken / timeoutMs） |
| `geelato-dyn-unified-api/.../spring-jdbc.xml` | 回调落库数据源；处理器开关（unifiedConsumer=true，db / rabbitMq 默认 false）；RabbitMQ 连接参数 |
| 各业务模块 `*.xml` | 业务 Config bean（API 地址、密钥、cron 表达式等） |

:::note 配置归属选择
同一份代码两种用法：不配 cfg 时直接用 XML 默认值（零运维）；把 cfg 模板放进 `etc/` 后按环境覆盖（运维侧收口）。需要跨环境频繁变化的（数据库、令牌）优先走 cfg。
:::

## 日志：按模块分离

Karaf（pax-logging-log4j2）基线配置为 13 个模块的根包名各配独立 logger + 独立日志文件，**不改动任何 Java 代码**：

- 日志文件位于 Karaf 的 `data/log/`，命名 `dyn-<module>.log`（如 `dyn-common.log`、`dyn-unified-api.log`）；
- 所有模块 logger `additivity=false`，模块日志只进自己的文件；`karaf.log` 只保留容器与 Camel / CXF / Spring / Blueprint 框架日志；
- 滚动策略与 karaf.log 对齐：单文件 16MB，每模块最多保留 20 个滚动文件；
- 按代码所在包归属：`cn.geelato.dyn.common` 中的类被其它模块调用时，日志计入 `dyn-common.log`；
- 嵌套包不串日志（Log4J2 按最具体 logger 匹配）。

基线文件与推送脚本在仓库 `karaf/` 目录：`karaf/etc/org.ops4j.pax.logging.cfg` + `karaf/scripts/apply-logging-config.sh`（先备份目标实例现有配置再覆盖）。若目标实例的 logging cfg 做过自定义修改，不要整文件覆盖，改为把基线中 `geelato-dyn modules` 块追加到现有文件末尾。

### 运行期调整级别

```bash
karaf@root()> log:set DEBUG cn.geelato.dyn.bus.shipping
karaf@root()> log:get cn.geelato.dyn.bus.shipping
karaf@root()> log:set INFO cn.geelato.dyn.bus.shipping    # 调回
```

`log:set` 会把变更回写到实例的 `org.ops4j.pax.logging.cfg`，之后与仓库基线出现差异属预期，以实例文件为准。

### 新增模块时

在 `org.ops4j.pax.logging.cfg` 末尾照抄一组模块配置（appender + logger 三处成对改名），并重新推送基线，模板见仓库 `karaf/README-logging.md`。

## 推荐继续阅读

- [内置能力](capabilities.md) —— 中央数据源 / 健康 / 安全 / 调度 / 管理台
- [新模块开发](develop-module.md) —— 脚手架与配置接入
