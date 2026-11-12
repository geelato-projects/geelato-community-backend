---
title: geelato-dyn Integration & Deployment
sidebar_label: Integration & Deployment
---

# Integration & Deployment

The integration unit of geelato-dyn is a **Karaf feature**: each module is published as a bundle jar plus `features.xml` (classifier=`features`). The target Karaf adds the repository definition with `feature:repo-add` and installs with `feature:install`.

## Prerequisites

| Item | Requirement |
|---|---|
| Java | JDK 17 |
| Karaf | 4.4.10 |
| Maven repository | Reachable from the target Karaf (private repo or the build machine's local `~/.m2`) |
| Database | Run each module's DDL script (under each module's `src/main/resources/sql/`) |
| Platform side | Message outbox requires a reachable geelato-message center; in-app direct delivery requires a reachable community platform |

## Publishing to the Maven Repository

Build and install (or deploy to your private repo) from the geelato-dyn project root:

```bash
mvn -pl <module> -am clean install
```

Make sure `features.xml` is published with `classifier=features` — feature installation depends on it; installing the bare jar with `bundle:install` alone leaves out the runtime dependencies.

## Minimal Installation

```bash
# Foundation: messaging / security / users / health / IDs
feature:repo-add mvn:cn.geelato/geelato-dyn-common/0.0.1-SNAPSHOT/xml/features
feature:install geelato-dyn-common

# Central datasources: prerequisite for any module that needs a database
feature:repo-add mvn:cn.geelato/geelato-dyn-datasource/0.0.1-SNAPSHOT/xml/features
feature:install geelato-dyn-datasource
```

:::warning Why installing the bundle alone is not enough
The Blueprint of `geelato-dyn-common` declares `HikariDataSource`, `JdbcTemplate`, Jackson and other dependency bundles. Running only `bundle:install -s mvn:cn.geelato/geelato-dyn-common/0.0.1-SNAPSHOT` without those bundles makes Blueprint bean instantiation fail with `ComponentDefinitionException` / `ClassNotFoundException`. Always use `feature:*` commands — dependencies are provided transitively by features.xml.
:::

## Installing on Demand

| Scenario | Install command (repo-add first, coordinates as above) | Prerequisites |
|---|---|---|
| Health aggregation + management console | `feature:install geelato-dyn-manage` | common |
| Unified callback + consumer bus | `feature:install geelato-dyn-unified-consumer-main` | common; **this feature installs unified-api transitively** |
| Adding a callback consumer | `feature:install geelato-dyn-unified-consumer-ocean-tracking` (other consumers likewise) | consumer-main |
| Outbound integration engine | `feature:install geelato-ob-integrate` | common + status-support |
| Scheduled business modules | `feature:install geelato-dyn-bus-shipping` / `geelato-dyn-bus-periodic-report` | common (bus-shipping also needs datasource) |
| Demo | `feature:install geelato-dyn-demo` | common + datasource |

When installing a module, Karaf resolves and installs the feature dependencies declared in its features.xml. `<reference>` service dependencies are strong (GracePeriod): a business bundle waits instead of starting in a broken state when its prerequisite module is not ready.

## Post-install Verification

```bash
# Feature / bundle status
feature:list | grep geelato-dyn
bundle:list | grep geelato-dyn

# Central datasources: expect 4 DataSource services, each with a datasource.name property
service:list javax.sql.DataSource

# Security contract services registered
services | grep -E "UserProvider|OrgProvider"

# Health aggregation (requires manage, no auth)
curl "http://localhost:8181/cxf/health"
```

## Updating and Uninstalling

Standard update flow (after re-publishing artifacts):

```bash
feature:uninstall <feature-name>
feature:repo-remove mvn:cn.geelato/<module>/0.0.1-SNAPSHOT/xml/features
feature:repo-add mvn:cn.geelato/<module>/0.0.1-SNAPSHOT/xml/features
feature:install <feature-name>
```

For jar-only changes with unchanged dependencies, a lightweight refresh works:

```bash
feature:repo-refresh
bundle:update <bundleId>
```

Uninstalling:

```bash
feature:uninstall <feature-name>
feature:repo-remove mvn:cn.geelato/<module>/0.0.1-SNAPSHOT/xml/features
```

## Remote Deployment (Optional)

The root POM ships a `karaf-deploy` profile (karaf-maven-plugin) that pushes features directly to a remote Karaf instance at build time, which suits CI pipelines; day-to-day operations should still use the `feature:*` commands.

## Recommended Reading

- [Configuration & Logging](configuration.md) — cfg overrides, hot reload and log separation after deployment
- [Built-in Capabilities](capabilities.md) — how to consume each capability
