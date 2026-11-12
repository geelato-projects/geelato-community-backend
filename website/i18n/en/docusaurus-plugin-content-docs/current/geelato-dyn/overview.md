---
title: geelato-dyn Overview
sidebar_label: Overview
---

# geelato-dyn Dynamic Integration Framework Overview

`geelato-dyn` is a dynamic integration framework built on **Apache Karaf 4.4.10 + OSGi**. It runs independently of the geelato platform (the Spring Boot host); the two share no Maven dependency and are integrated only through **embedded security contracts, direct platform-database access, and REST calls**.

The problem it solves: business integration units (scheduled jobs, third-party callbacks, outbound integrations) are added and removed frequently and released independently — they should not force repeated restarts of the platform process. Each functional unit is an OSGi bundle that is installed, updated and uninstalled independently through Karaf features, without affecting other bundles at runtime.

:::note Unrelated to the "dynamic datasource"
This framework (geelato-dyn) has nothing to do with the Spring-side dynamic datasource module (geelato-dynamic-datasource, see the [Datasource chapter](../dynamic-datasource/overview.md)) despite the similar name. The dynamic datasource handles multi-database routing inside the Spring host; geelato-dyn is a separate runtime inside Karaf.
:::

## Technology Stack

| Component | Version | Role |
|---|---|---|
| Apache Karaf | 4.4.10 | Container and feature-based deployment |
| OSGi Blueprint | Aries | Bean wiring and `<reference>` service injection |
| OSGi Config Admin | Aries | `etc/*.cfg` configuration with hot reload |
| Apache CXF | JAX-RS 2.1 | REST services (mounted under Karaf's default `/cxf`) |
| Apache Camel | 3.22.4 | In-process routing (`vm:` endpoints) |
| Karaf Scheduler | Quartz cron | Scheduled jobs |
| HikariCP | 5.1.0 | Connection pools (held centrally by the datasource module) |
| pax-logging (log4j2) | — | Per-module log file separation |
| Java | 17 | Build and runtime baseline |

## Built-in Capabilities at a Glance

| Capability | Provided by | Facade / Entry point |
|---|---|---|
| Central datasources (referenced by logical name) | geelato-dyn-datasource | `javax.sql.DataSource` OSGi service (`datasource.name=…`) |
| Unified message dispatch (6 channels) | geelato-dyn-common | `cn.geelato.dyn.common.message.MessageDispatcher` |
| Security contracts + user/org lookup | geelato-dyn-common | `cn.geelato.security.UserProvider` / `OrgProvider` |
| Health check & aggregation | geelato-dyn-common + geelato-dyn-manage | `ModuleHealthReporter` SPI → `GET /cxf/health` |
| Unified callback north-bound entry | geelato-dyn-unified-api | `POST /cxf/unified/callback/{token}` |
| Unified consumer bus | geelato-dyn-unified-consumer-main | Camel `vm:` dispatch by bizCode |
| Visual management console | geelato-dyn-manage | `/cxf/dyn/manage/` (token auth) |
| Scheduled jobs | Karaf scheduler / Blueprint | `Runnable` service properties or `scheduler:scheduledRunnable` |
| Snowflake IDs | geelato-dyn-common | `cn.geelato.dyn.common.id.SnowflakeIds` |
| Per-module log separation | Karaf baseline config | One `dyn-*.log` per module |

See [Built-in Capabilities](capabilities.md) for how to consume each capability. Message dispatch and the callback bus get their own pages: [Unified Message Dispatch](message-dispatch.md) and [Unified Callback & Consumer Bus](callback-bus.md).

## Module Inventory

| Module (artifactId) | Role | Deployment |
|---|---|---|
| `geelato-dyn-common` | Foundation bundle: contracts and wiring for messaging / security / users / health / IDs | Required (prerequisite of all modules) |
| `geelato-dyn-datasource` | Central datasources: 4 HikariCP logical pools published as services | Prerequisite for modules that need a database |
| `geelato-dyn-manage` | Health aggregation REST + visual management console | Optional (operations) |
| `geelato-dyn-unified-api` | Unified callback north-bound entry + capture/processed persistence | Optional (callback scenarios) |
| `geelato-dyn-unified-consumer-main` | Unified consumer bus (Camel `vm:` dispatch) | Optional (pairs with unified-api; its feature installs unified-api transitively) |
| `geelato-dyn-unified-consumer-*` | Callback consumer implementations (ocean tracking / ocean tracking warning / GSF declaration status) | Business modules, as needed |
| `geelato-ob-integrate` | Outbound integration engine (with ChannelAdapter / PayloadBuilder SPIs) | Business module, as needed |
| `geelato-dyn-ob-integrate-status-support` | Status-mapping support library for outbound integration | Business support library, as needed |
| `geelato-dyn-bus-shipping` | Scheduled-job business module example | Business module, as needed |
| `geelato-dyn-bus-periodic-report` | Periodic report module example (five-stage report SPI) | Business module, as needed |
| `geelato-dyn-demo` | Demos for messaging / security / **community ORM integration** | Demo, as needed |
| `geelato-dyn-template` | Scaffold for new modules (not deployed) | Not deployed |
| `examples/geelato-karaf-showcase` | Standalone OSGi example project and ADR docs | Not deployed |

The concrete business logic of consumer and bus modules is out of scope for this chapter; these docs cover the framework and integration only.

## Module Dependency Topology

```text
geelato-dyn-common (foundation: messaging / security / users / health / IDs)
   ├── geelato-dyn-datasource (central datasources; required by DB-backed modules)
   ├── geelato-dyn-manage (operations aggregation, installed independently)
   ├── geelato-dyn-unified-api (callback entry)
   │       └── geelato-dyn-unified-consumer-main (consumer bus; its feature installs unified-api)
   │               ├── unified-consumer-ocean-tracking
   │               ├── unified-consumer-ocean-tracking-warning
   │               └── unified-consumer-ob-integrate-gsf-status
   ├── geelato-ob-integrate (outbound integration, depends on status-support)
   ├── geelato-dyn-bus-shipping / geelato-dyn-bus-periodic-report (scheduled business)
   └── geelato-dyn-demo (demos; also embeds the community ORM)
```

There is no single-starter integration entry point: the minimal runtime set is `geelato-dyn-common` (plus `geelato-dyn-datasource` when a database is needed). Everything else is installed on demand and fully pluggable.

## Relationship with the geelato Platform

| Integration point | Mechanism | Notes |
|---|---|---|
| Security contracts | Embedded jar | `geelato-dyn-common` embeds the community `geelato-security` jar via Embed-Dependency and exports `cn.geelato.security`; every bundle in Karaf can import it directly |
| User / org data | Direct platform DB access | `DBUserProvider` / `DBOrgProvider` query `platform_user` / `platform_org` and related tables in real time, no cache |
| Message sending | REST | Outbox submits to the geelato-message center via `POST /message/enqueue`; the center persists and delivers |
| In-app message | Direct REST | The `inapp` channel calls the community platform directly (fixed `Authorization: SystemToken` key), bypassing the message center |
| ORM (optional) | Spring Context embedded in a bundle | The demo hosts the community `OrmAutoConfiguration` inside a bundle, enabling the Fluent DSL (see [Developing a New Module](develop-module.md)) |

## Recommended Reading

- [Integration & Deployment](installation.md) — prerequisites, feature installation, updates and verification
- [Configuration & Logging](configuration.md) — Config Admin hot reload, PID inventory, log separation
- [Built-in Capabilities](capabilities.md) — datasources / health / security / scheduling / console / REST endpoint table
