---
id: overview
title: Built-in Maintenance
sidebar_label: Built-in Maintenance
---

# Built-in Maintenance (geelato-maintenance)

The maintenance module targets **teams building on top of Geelato** and provides two sets of development-time self-check tools:

- **Entity three-way sync check and repair** (`cn.geelato.metasync`): checks consistency between "Java entity class ↔ entity definition (platform_dev_table) ↔ physical table structure" and repairs toward a chosen baseline.
- **MQL Playground** (`cn.geelato.mqltest`): online MQL debugging and test suite with explain preview, real execution, identity simulation and scenario-based regression.

Module coordinates: `cn.geelato:geelato-maintenance`, already included by the quickstart; not a scaffold standard part — add the dependency explicitly in business projects.

## Enabling

The module registers two Spring Boot auto-configurations, each controlled by its own switch, **both off by default**:

| Switch | Controls | Default |
| --- | --- | --- |
| `geelato.meta.sync.enabled` | Three-way sync tool (page + REST) | false |
| `geelato.mql.playground.enabled` | MQL Playground (page + REST) | false |

The quickstart `application.properties` sets both to `true` with a note that **they should be false in production**. Tool page paths are pre-allowed by the security interceptor defaults.

## Entity Three-way Sync Check and Repair

The "three sides" are three forms of the same entity. Manual changes on any side (table DDL, class annotations, designer edits) can drift from the others.

| Form | Source |
| --- | --- |
| Java class | `@Model` / `@Col` annotations on entity classes |
| Entity definition | Platform designer table `platform_dev_table` and column definitions |
| Physical table | Real table structure in information_schema |

### Page and REST

- Page: `GET /meta-sync.html`
- REST prefix: `/api/meta-sync`

| Endpoint | Description |
| --- | --- |
| `GET/POST /api/meta-sync/scan?baseline=table\|java\|meta` | Full scan of all three sides, compared against the baseline |
| `GET /api/meta-sync/check/{tableName}` | Single-table consistency check |
| `GET /api/meta-sync/diff/{tableName}` | Field-level diff (`FieldDiff` / `EntitySyncReport`) |
| `GET /api/meta-sync/entities` | List checkable entities |
| `POST /api/meta-sync/fix/table-to-meta` | Fix `platform_dev_table` metadata from the physical table |
| `POST /api/meta-sync/fix/gen-java` | Generate Java entity source code |
| `POST /api/meta-sync/fix/java-to-meta` | Fix metadata from the Java class |

Config: `geelato.meta.scan-package-names` (default `cn.geelato`) controls the class scan scope. The module has no business tables of its own.

## MQL Playground

- Page: `GET /mql-playground.html`
- REST prefix: `/api/mql`

| Endpoint | Description |
| --- | --- |
| `POST /api/mql/explain` | MQL→SQL dry-run preview (query/save/delete), works without a datasource |
| `POST /api/mql/validate` | MQL syntax validation |
| `POST /api/mql/execute` | Real execution on the primary datasource |
| `GET /api/mql/entities`, `GET /api/mql/schema/{entity}` | Entity list and schema |
| `GET /api/mql/identities/tenants`, `GET /api/mql/identities/users` | Identities for runAs simulation (tenant and data-permission injectors behave as in production) |
| `GET /api/mql/scenarios`, `POST /api/mql/scenarios/run/{id}`, `POST /api/mql/scenarios/runAll` | Scenario-based regression suite |
| `POST /api/mql/scenarios/initSchema`, `POST /api/mql/scenarios/cleanup` | Init/clean scenario test tables |

Scenario resources live in `mql-test-scenarios/*.json`; test entities (MqlTestOrder/OrderItem/Org/User) are registered at startup and do not affect business entities.

## Production Recommendations

- Set both switches to `false` in production to avoid exposing debug endpoints.
- `fix/*` endpoints rewrite metadata or generate code; use them only in dev/test environments and confirm the baseline direction first.
