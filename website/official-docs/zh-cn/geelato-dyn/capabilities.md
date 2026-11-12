---
title: geelato-dyn 内置能力
sidebar_label: 内置能力
---

# 内置能力

本页汇总 geelato-dyn 自带的框架能力与接入方式。业务模块接入这些能力都是**零配置 `<reference>` 引用 OSGi 服务**，不复制任何地址、账号、密钥。

## 中央数据源

`geelato-dyn-datasource` 集中定义工程所有 MySQL 连接池（HikariCP），按**逻辑名**发布为 OSGi 服务。仓库中连接信息只存在这一处，业务模块按逻辑名引用，不再各自配置 IP/账号/密码。

| 逻辑名 | 库 |
|---|---|
| `platform` | geelato 平台库 |
| `portal` | ob_portal 门户库 |
| `integration` | ob_integrate 集成库 |
| `buss` | 业务库 |

接入两步：

```xml
<!-- Blueprint：按逻辑名引用，本模块不含任何凭据 -->
<reference id="portalDataSource" interface="javax.sql.DataSource"
           filter="(datasource.name=portal)"/>
<bean id="portalJdbcTemplate" class="org.springframework.jdbc.core.JdbcTemplate">
    <property name="dataSource" ref="portalDataSource"/>
</bean>
```

```xml
<!-- features.xml：声明对中央数据源 feature 的依赖 -->
<repository>mvn:cn.geelato/geelato-dyn-datasource/${project.version}/xml/features</repository>
<feature name="geelato-dyn-xxx" version="${project.version}">
    <feature version="${project.version}">geelato-dyn-datasource</feature>
</feature>
```

行为要点：

- `<reference>` 为强依赖，中央数据源未就绪时业务 bundle 等待（GracePeriod），不会带病运行；
- Hikari 池**懒建连**，某环境用不到的库不会被实际连接，也不影响其它池；
- 每个数据源的健康项执行 `SELECT 1`，经健康聚合暴露；
- Java 侧常量 `cn.geelato.dyn.datasource.GeelatoDataSources`；
- 新增逻辑名只需改中央模块（`datasource.xml` 加一组属性 + bean + 服务发布），业务模块无感可用，步骤见仓库 `geelato-dyn-datasource/README.md`。

## 健康检查与聚合

SPI：`cn.geelato.dyn.common.health.ModuleHealthReporter`（`module()` / `snapshot()` / `ttlMs()`），配套 `ModuleHealthSnapshot` / `HealthCheckItem` / `HealthLevel`。

- 模块侧：实现 reporter 并注册为 OSGi 服务；定时任务模块配套 `HealthState`（`recordStart` / `recordSuccess` / `recordFailure` 埋点，failure message 需脱敏）；可选 `cn.geelato.dyn.common.logging.ModuleLogLevelProvider` 补齐聚合输出中的 `logLevel`；
- 聚合端：`geelato-dyn-manage` 用 `<reference-list>` 自动收集所有 reporter，**聚合端无需改代码**，新模块装上即出现在结果里；
- REST：`GET /cxf/health`、`GET /cxf/health/modules`、`GET /cxf/health/modules/{module}`（无鉴权）；
- 快照分 `runtime` / `business` 两级状态与 `items` 明细；`ttlMs()` 过期后聚合端标记 `ttl expired`（定时任务建议取 maxSilenceMs 或调度间隔的 2~3 倍）。

内置实现覆盖 datasource / unified-api / consumer-main / 各 consumer / shipping / periodic-report 等。

## 安全契约与用户/组织查询

`geelato-dyn-common` 内嵌 community 的 `geelato-security` 并导出契约包 `cn.geelato.security`（`SecurityContext` / `User` / `Org` / `Tenant` / `UserProvider` / `OrgProvider`），Karaf 内所有 bundle 直接 `Import-Package` 即可，无需额外安装。

内置实现 `DBUserProvider` / `DBOrgProvider` 直连 geelato 平台库，实时查询 `platform_user` / `platform_org` / `platform_org_r_user` / `platform_role_r_user`，无快照缓存：

```xml
<reference id="userProvider" interface="cn.geelato.security.UserProvider"/>
<reference id="orgProvider"  interface="cn.geelato.security.OrgProvider"/>
```

```java
User user = userProvider.getUser(userId);                     // 含角色、挂靠组织
User user2 = userProvider.getUserByExtendKey("zhangsan", "loginName");
Org org = orgProvider.getOrg(orgId);
String deptId = orgProvider.getDeptId(orgId);                 // 沿 pid 链上溯部门/公司
String companyId = orgProvider.getCompanyId(orgId);
```

另有 `cn.geelato.dyn.common.user.UserDirectoryService`（JDBC 实现）：按 userId 批量取联系人，消息收件人解析等场景使用。

## 定时任务

两种方式，按需选择：

**方式一：服务属性式**（Karaf scheduler feature 消费，会出现在管理台"定时服务"tab）：

```xml
<bean id="myJob" class="cn.geelato.dyn.xxx.MyJob"/>
<service ref="myJob" ref-type="service" interface="java.lang.Runnable">
    <service-properties>
        <entry key="scheduler.name" value="my-job"/>
        <entry key="scheduler.expression" value="0 * * * * ?"/>
        <entry key="scheduler.concurrent" value="false"/>
    </service-properties>
</service>
```

**方式二：声明式**（Blueprint extender）：

```xml
<scheduler:scheduledRunnable ref="myJob" cron-expression="0 0/10 * * * ?"/>
```

## 雪花 ID

`cn.geelato.dyn.common.id.SnowflakeIds`：进程内统一发号，落库主键（如回调捕获表）与业务实体 ID 生成共用。

## 管理控制台

`geelato-dyn-manage` 提供可视化控制台，均为**只读探查**，不改变运行状态。页面 `http://<host>:8181/cxf/dyn/manage/`，API 需请求头 `X-Manage-Token`（配置见[配置与日志](configuration.md)）。

| tab | 数据来源 |
|---|---|
| 总览 | `/cxf/health` 聚合结果 |
| 数据源 | Config Admin（`cn.geelato.dyn.datasource`）+ OSGi 注册表 DataSource 服务；密码默认掩码 |
| OpenAPI 消费端 | 注册表中 `UnifiedConsumerBinding` 服务 |
| 定时服务 | 注册表 `scheduler.*` 服务属性扫描（声明式 cron 任务不在此列） |
| 服务装载 | `UserProvider` / `OrgProvider` 注册状态 + 真实调用探测 |
| 统一消息 | `MessageDispatcher` 装载状态 + 渠道注册表 + HTTP 可达性探测 |
| Karaf | bundle:list / feature:list，支持过滤搜索 |

## REST 端点总表

全部 CXF 服务挂在 Karaf 默认 `http://<host>:8181` 下：

| 基地址 | 端点 | 鉴权 | 说明 |
|---|---|---|---|
| `/cxf/health` | `GET /`、`/modules`、`/modules/{module}` | 无 | 健康聚合 |
| `/cxf/unified` | `POST /callback`、`POST /callback/{token}`、`GET /callback/{token}` | token 白名单 | 统一回调北向入口，见[统一回调与消费总线](callback-bus.md) |
| `/cxf/dyn/manage` | `/`（页面）、`/api/datasources`、`/api/openapi/consumers`、`/api/tasks`、`/api/providers`、`/api/providers/probe`、`/api/messages`、`/api/messages/probe`、`/api/karaf/bundles`、`/api/karaf/features` | X-Manage-Token | 管理控制台 |
| `http://0.0.0.0:9080` | `GET /geelato-dyn-unified-consumer-main/branch-logic` | 无 | 消费总线分支规则查询（Camel jetty） |

## 推荐继续阅读

- [统一消息分发](message-dispatch.md) —— MessageDispatcher 与渠道适配器
- [统一回调与消费总线](callback-bus.md) —— 北向入口与 Camel 分发
- [新模块开发](develop-module.md) —— SPI 扩展点总表
