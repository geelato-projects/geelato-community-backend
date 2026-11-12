---
title: geelato-dyn Built-in Capabilities
sidebar_label: Built-in Capabilities
---

# Built-in Capabilities

This page summarizes the framework capabilities that ship with geelato-dyn and how to consume them. Business modules access all of them by **referencing OSGi services with a zero-config `<reference>`** — no addresses, accounts or secrets are copied anywhere.

## Central Datasources

`geelato-dyn-datasource` centrally defines every MySQL connection pool (HikariCP) and publishes them as OSGi services by **logical name**. Connection credentials exist in exactly one place in the repo; business modules reference by logical name and never configure IP/username/password themselves.

| Logical name | Database |
|---|---|
| `platform` | geelato platform database |
| `portal` | ob_portal portal database |
| `integration` | ob_integrate integration database |
| `buss` | Business database |

Two-step integration:

```xml
<!-- Blueprint: reference by logical name; this module carries no credentials -->
<reference id="portalDataSource" interface="javax.sql.DataSource"
           filter="(datasource.name=portal)"/>
<bean id="portalJdbcTemplate" class="org.springframework.jdbc.core.JdbcTemplate">
    <property name="dataSource" ref="portalDataSource"/>
</bean>
```

```xml
<!-- features.xml: declare the dependency on the central datasource feature -->
<repository>mvn:cn.geelato/geelato-dyn-datasource/${project.version}/xml/features</repository>
<feature name="geelato-dyn-xxx" version="${project.version}">
    <feature version="${project.version}">geelato-dyn-datasource</feature>
</feature>
```

Behavior notes:

- `<reference>` is a strong dependency: while the central datasource module is not ready, business bundles wait (GracePeriod) instead of starting broken;
- Hikari pools **connect lazily**: an unused database in some environment is never physically connected and does not affect other pools;
- Each datasource contributes a health item running `SELECT 1`, exposed through health aggregation;
- Java constant: `cn.geelato.dyn.datasource.GeelatoDataSources`;
- Adding a new logical datasource only touches the central module (a property group + bean + service publication in `datasource.xml`); business modules can use it immediately — see the repo's `geelato-dyn-datasource/README.md` for the steps.

## Health Check and Aggregation

SPI: `cn.geelato.dyn.common.health.ModuleHealthReporter` (`module()` / `snapshot()` / `ttlMs()`), with `ModuleHealthSnapshot` / `HealthCheckItem` / `HealthLevel`.

- Module side: implement a reporter and register it as an OSGi service; scheduled-job modules pair it with a `HealthState` (`recordStart` / `recordSuccess` / `recordFailure` instrumentation, failure messages must be sanitized); optionally `cn.geelato.dyn.common.logging.ModuleLogLevelProvider` fills the `logLevel` field in aggregated output;
- Aggregation side: `geelato-dyn-manage` collects all reporters automatically via `<reference-list>` — **no aggregation-side code changes**, a newly installed module shows up on its own;
- REST: `GET /cxf/health`, `GET /cxf/health/modules`, `GET /cxf/health/modules/{module}` (no auth);
- Snapshots carry `runtime` / `business` status levels plus `items` detail; after `ttlMs()` expires the aggregator marks the entry `ttl expired` (for scheduled jobs use maxSilenceMs or 2–3× the schedule interval).

Built-in reporters cover datasource / unified-api / consumer-main / each consumer / shipping / periodic-report, etc.

## Security Contracts and User/Org Lookup

`geelato-dyn-common` embeds the community `geelato-security` jar and exports the contract package `cn.geelato.security` (`SecurityContext` / `User` / `Org` / `Tenant` / `UserProvider` / `OrgProvider`). Every bundle in Karaf only needs to `Import-Package` it — nothing else to install.

The built-in `DBUserProvider` / `DBOrgProvider` connect directly to the geelato platform database, querying `platform_user` / `platform_org` / `platform_org_r_user` / `platform_role_r_user` in real time with no snapshot cache:

```xml
<reference id="userProvider" interface="cn.geelato.security.UserProvider"/>
<reference id="orgProvider"  interface="cn.geelato.security.OrgProvider"/>
```

```java
User user = userProvider.getUser(userId);                     // includes roles and affiliated orgs
User user2 = userProvider.getUserByExtendKey("zhangsan", "loginName");
Org org = orgProvider.getOrg(orgId);
String deptId = orgProvider.getDeptId(orgId);                 // walks up the pid chain to dept/company
String companyId = orgProvider.getCompanyId(orgId);
```

Additionally `cn.geelato.dyn.common.user.UserDirectoryService` (JDBC implementation) fetches contacts in batch by userId — used for message recipient resolution and similar scenarios.

## Scheduled Jobs

Two styles, pick per need:

**Style 1: service properties** (consumed by the Karaf scheduler feature; shows up in the console's "scheduled services" tab):

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

**Style 2: declarative** (Blueprint extender):

```xml
<scheduler:scheduledRunnable ref="myJob" cron-expression="0 0/10 * * * ?"/>
```

## Snowflake IDs

`cn.geelato.dyn.common.id.SnowflakeIds`: process-wide ID generation, shared by persistence primary keys (e.g. callback capture tables) and business entity IDs.

## Management Console

`geelato-dyn-manage` ships a visual console. Everything is **read-only** and never changes runtime state. Page: `http://<host>:8181/cxf/dyn/manage/`; APIs require the `X-Manage-Token` header (configuration in [Configuration & Logging](configuration.md)).

| Tab | Data source |
|---|---|
| Overview | Aggregated result of `/cxf/health` |
| Datasources | Config Admin (`cn.geelato.dyn.datasource`) + registered DataSource services from the OSGi registry; passwords masked by default |
| OpenAPI consumers | `UnifiedConsumerBinding` services in the registry |
| Scheduled services | Registry scan of `scheduler.*` service properties (declarative cron jobs are not listed) |
| Service loading | `UserProvider` / `OrgProvider` registration status + live call probing |
| Unified messaging | `MessageDispatcher` wiring status + channel registry + HTTP reachability probing |
| Karaf | bundle:list / feature:list with filtering |

## REST Endpoint Table

All CXF services hang off Karaf's default `http://<host>:8181`:

| Base path | Endpoints | Auth | Purpose |
|---|---|---|---|
| `/cxf/health` | `GET /`, `/modules`, `/modules/{module}` | none | Health aggregation |
| `/cxf/unified` | `POST /callback`, `POST /callback/{token}`, `GET /callback/{token}` | token whitelist | Unified callback entry, see [Unified Callback & Consumer Bus](callback-bus.md) |
| `/cxf/dyn/manage` | `/` (page), `/api/datasources`, `/api/openapi/consumers`, `/api/tasks`, `/api/providers`, `/api/providers/probe`, `/api/messages`, `/api/messages/probe`, `/api/karaf/bundles`, `/api/karaf/features` | X-Manage-Token | Management console |
| `http://0.0.0.0:9080` | `GET /geelato-dyn-unified-consumer-main/branch-logic` | none | Consumer bus branch rule query (Camel jetty) |

## Recommended Reading

- [Unified Message Dispatch](message-dispatch.md) — MessageDispatcher and channel adapters
- [Unified Callback & Consumer Bus](callback-bus.md) — north-bound entry and Camel dispatch
- [Developing a New Module](develop-module.md) — SPI extension point table
