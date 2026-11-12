---
title: Developing a New Module
sidebar_label: Developing a Module
---

# Developing a New Module

New business modules are created by copying the scaffold `geelato-dyn-template`. The template mirrors the structure of real business modules: three Blueprint templates, a DDL script and a scheduled-job skeleton.

## Creating from the Template

1. Copy the `geelato-dyn-template` directory and rename it to `geelato-dyn-{your-module}`;
2. Global-replace three identifiers:

| Replace | Example |
|---|---|
| `Template` → `{YourModule}` (leading capital) | `TemplateJob` → `InventoryJob` |
| `template` → `{your-module}` (all lowercase) | package `cn.geelato.dyn.template` → `cn.geelato.dyn.inventory` |
| `tt_` → `{your_prefix}_` | table prefix, e.g. `tt_entity` → `inv_task` |

3. Change the `pom.xml` artifactId to `geelato-dyn-{your-module}`;
4. Edit and run the DDL script `src/main/resources/sql/init.sql`;
5. Add the new module to the root `pom.xml` `<modules>`.

## Naming Conventions

| Component | Pattern | Example |
|---|---|---|
| Package | `cn.geelato.dyn.{module}` | `cn.geelato.dyn.inventory` |
| Job entry | `{Module}Job implements Runnable` | `InventoryJob` |
| Business service | `{Module}Service` | `InventoryService` |
| Database service | `{Module}DatabaseService` | `InventoryDatabaseService` |
| Entity | `{Module}Entity` | `InventoryEntity` |
| Business Blueprint | `{module}.xml` | `inventory.xml` |
| Table prefix | custom prefix + `_` | `inv_` |

## The Three Blueprint Templates

| File | Responsibility |
|---|---|
| `{module}.xml` | Business bean wiring (Service / Job / DatabaseService dependency injection) |
| `spring-jdbc.xml` | Datasource reference: `<reference>` by logical name to the central datasource + own `JdbcTemplate`; no credentials in this module |
| `cron.xml` | Declarative scheduled job `scheduler:scheduledRunnable` |

## Consuming Framework Capabilities (zero-config `<reference>`)

```xml
<!-- Central datasource -->
<reference id="dataSource" interface="javax.sql.DataSource" filter="(datasource.name=buss)"/>

<!-- Unified message dispatch -->
<reference id="messageDispatcher" interface="cn.geelato.dyn.common.message.MessageDispatcher"/>

<!-- Security contracts: users / orgs -->
<reference id="userProvider" interface="cn.geelato.security.UserProvider"/>
<reference id="orgProvider"  interface="cn.geelato.security.OrgProvider"/>
```

To appear in health aggregation, add a `ModuleHealthReporter` (see [Built-in Capabilities / Health Check](capabilities.md#health-check-and-aggregation)). For config keys, declare a PID with `<cm:property-placeholder>` and ship an `etc/<PID>.cfg` template (see [Configuration & Logging](configuration.md)).

## SPI Extension Point Table

| SPI | Owner | Purpose |
|---|---|---|
| `cn.geelato.dyn.common.health.ModuleHealthReporter` | common | Module health snapshot reporting (runtime / business / items / ttlMs) |
| `cn.geelato.dyn.common.logging.ModuleLogLevelProvider` | common | Fills this module's logLevel in aggregated output |
| `cn.geelato.dyn.common.message.channel.MessageChannelAdapter` | common | Add a message channel (outbox or direct style) |
| `cn.geelato.dyn.unified.api.service.UnifiedConsumerBinding` | unified-api | Register callback token → bizCode routing and self-check endpoints |
| `...processor.AbstractCallbackProcessor` | unified-api | Add a callback processing style beyond persistence / MQ / bus |
| Outbound integration engine: `ChannelAdapter` / `PayloadBuilder` / `IntegrationTaskDataValidator` | geelato-ob-integrate | Plug in new external systems / payload types / validation scenarios |

## Integrating with the geelato Platform (community)

Three options, choose as needed:

**Security contracts (recommended, zero cost)**: `geelato-dyn-common` already exports the `cn.geelato.security` contract package plus `DBUserProvider` / `DBOrgProvider` implementations — `<reference>` them to query users and orgs.

**Message REST (recommended)**: `<reference>` the `MessageDispatcher`; delivery is handled by the message center (see [Unified Message Dispatch](message-dispatch.md)).

**community ORM (advanced, see the demo)**: `geelato-dyn-demo` demonstrates hosting the community `OrmAutoConfiguration` inside an embedded Spring Context in a bundle, making the Fluent DSL (`MetaFactory.query/insert/...`) usable (ADR 0002). Key points:

- Embedded dependencies: `geelato-orm` / `geelato-core` / `geelato-lang` / `geelato-utils` + spring-boot(autoconfigure);
- Blueprint injects the central datasource OSGi service, registered as a singleton in the embedded Context before `context.refresh()`;
- **TCCL switch**: the Blueprint extender thread's contextClassLoader is Karaf's root classloader — temporarily switch it to the bundle classloader around refresh, otherwise `@ConditionalOnBean` handler classes cannot be loaded;
- **Disable entity auto-scan**: scanning is unreliable under OSGi's multiple classloaders; set `geelato.orm.entity-auto-scan-enabled=false` and register entities explicitly with `MetaManager.parseOne(clazz)`;
- Do not manually register `OrmProperties` again (`OrmAutoConfiguration` already provides it; a duplicate causes `NoUniqueBeanDefinitionException`).

Reference implementation: `geelato-dyn-demo/.../orm/DemoOrmBootstrap.java` and the ADR docs under `examples/geelato-karaf-showcase`.

## Build and Deploy

```bash
# Build (bundle jar + features classifier)
mvn -pl geelato-dyn-{your-module} -am clean install
```

```bash
# Install / update / uninstall
feature:repo-add mvn:cn.geelato/geelato-dyn-{your-module}/0.0.1-SNAPSHOT/xml/features
feature:install geelato-dyn-{your-module}
```

The feature declares its dependencies on `geelato-dyn-common` (and `geelato-dyn-datasource` as needed), which Karaf installs transitively.

## Delivery Checklist

- [ ] Blueprint references datasources by logical name only; no IP/username/password anywhere in the module
- [ ] Scheduled jobs carry health instrumentation (recordStart / recordSuccess / recordFailure, sanitized failures) and a registered `ModuleHealthReporter`
- [ ] A logger block for this module was added to `karaf/etc/org.ops4j.pax.logging.cfg` (otherwise logs go to karaf.log)
- [ ] A `config/<PID>.cfg` template exists for any cfg-managed settings
- [ ] features.xml declares all prerequisite features
- [ ] `mvn -pl <module> -am clean install` passes and `features.xml` is published with classifier=features
- [ ] After installation, `GET /cxf/health/modules` lists this module

## Recommended Reading

- [Integration & Deployment](installation.md) — feature install and update commands
- [Unified Callback & Consumer Bus](callback-bus.md) — wiring consumer-style modules
