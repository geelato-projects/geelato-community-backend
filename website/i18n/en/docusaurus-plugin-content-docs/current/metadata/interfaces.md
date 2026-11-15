---
title: Metadata Persistence and Interfaces
sidebar_label: Persistence and Interfaces
---

# Metadata Persistence and Interfaces

Metadata exists in three forms: the in-memory registry (MetaManager), the persisted tables (platform_dev_*), and outward-facing contracts (REST endpoints and SimpleFieldMeta). This page covers the latter two; the registry and loading are in [Metadata Loading](./loading.md).

## Persistence: The Six platform_dev_* Tables

Design-time definitions live in the platform database and are read back through the `MetaStore` SPI at startup and refresh:

| Table | Carried object | Notes |
| --- | --- | --- |
| `platform_dev_table` | `TableMeta` | Table/entity definition, incl. connectId, dbType, cacheType, tableType |
| `platform_dev_column` | `ColumnMeta` | Column definition, incl. type/length/precision/select type/encryption |
| `platform_dev_view` | `TableView` | View definition (viewType/viewConstruct/viewColumn) |
| `platform_dev_table_check` | `TableCheck` | CHECK constraints |
| `platform_dev_table_foreign` | `TableForeign` | Foreign-key relations |
| `platform_dev_db_connect` | `ConnectMeta` | Database connections (table → connect id → physical DB) |

DDL scripts live under `geelato-app-scaffold-starter/init/` (e.g. `platform_dev_table.sql`, `platform_dev_column.sql`).

All six classes are themselves annotated `@Entity` (catalog `platform`): the definition of metadata is metadata-driven — querying them, creating their tables, and packaging/deploy checks all go through the same ORM chain. This is also why refresh protection forbids `refreshDBMeta` on `catalog=platform` entities — they take the Java class definition (see [Metadata Loading](./loading.md#same-name-conflict-strategy)).

## The /api/meta Endpoints

Controller: `cn.geelato.web.platform.srv.meta.MetaController` (`@ApiRestController("/meta")`). One prefix serves two endpoint families: **runtime** (generic data interfaces driven by GQL payloads) and **design-time** (querying and maintaining metadata definitions).

### Runtime Endpoints

| Endpoint | Methods | Notes |
| --- | --- | --- |
| `/api/meta/list` | POST/GET | Single-entity GQL query; with `withMeta=true` the response carries a `meta` key holding the SimpleFieldMeta of the requested fields |
| `/api/meta/multiList` | POST | Multi-entity combined query |
| `/api/meta/save/{biz}` | POST | Save (insert/update decided by the primary key) |
| `/api/meta/batchSave` | POST | Batch save |
| `/api/meta/multiSave` | POST | Multi-entity save |
| `/api/meta/delete/{biz}/{id}` | POST | Delete by primary key (delete mode resolved through the three-level configuration) |
| `/api/meta/delete2/{biz}` | POST | Delete by condition |
| `/api/meta/tree/{biz}` | POST | Tree data query |
| `/api/meta/uniqueness` | POST/GET | Uniqueness check |

These endpoints are implemented through `RuleService`; query-result caching also lives there (entityName participates in the cache key, eviction goes through MetaCacheProvider).

### Design-Time Endpoints

| Endpoint | Methods | Notes |
| --- | --- | --- |
| `/api/meta/defined/{entity}` | POST/GET | SimpleFieldMeta list of the named entity (outward-facing form) |
| `/api/meta/fullDefined/{entity}` | POST/GET | The full EntityMeta (table/foreign keys/views included) |
| `/api/meta/entityNames` | POST/GET | All entity names |
| `/api/meta/entityLiteMetas` | POST/GET | Slim entity list |
| `/api/meta/conflicts` | POST/GET | All dual-source conflicts |
| `/api/meta/conflicts/{entity}` | POST/GET | Dual-source diff for one entity |
| `/api/meta/sync/{entity}` | POST | Triggers refreshDBMeta (direction=db-to-class); the manual cross-instance sync entry |
| `/api/meta/codegen/{entity}` | POST/GET | Reverse-generates annotated Java source from metadata |
| `/api/meta/codegen/{entity}/download` | GET | Downloads the generated source |

Related controllers: `MetaDdlController` (generates DDL from metadata), `MetaSyncApiController` in `geelato-maintenance` (three-way consistency tool page `/meta-sync.html`), and the designers `DevTableController` / `DevTableColumnController` / `DevViewController` (write platform_dev_* then call refreshDBMeta).

## The Frontend Contract: SimpleFieldMeta

`/api/meta/defined/{entity}` and `withMeta=true` queries publish `SimpleFieldMeta` — table, schema and connection information is deliberately filtered out, keeping only what rendering needs:

| Attribute | Meaning | Frontend use |
| --- | --- | --- |
| `name` | Field name | Data key |
| `type` | Java type simpleName | Value type inference |
| `title` | Chinese name | Column header / form label |
| `comment` | Comment | Hint text |
| `nullable` | Nullable | Required validation |
| `charMaxLength` | Character max length | Length validation |
| `precision` / `scale` | Precision / scale | Numeric formatting |
| `selectType` | Select type (designer widget type) | Widget selection |
| `typeExtra` / `extraValue` / `extraMap` | Select-type extra data / extra value / mapping | Widget configuration |
| `defaultValue` | Default value | New-form initial value |

Form/table rendering and value validation in the low-code designer are built entirely on this contract — the frontend never needs to know a physical table exists.

## Script and AI Channels

The script engine has no `$gl.meta` kit; metadata reaches scripts through these channels:

| Channel | Entry | Notes |
| --- | --- | --- |
| `$gl.mql` | MqlKit (`list/multiList/save/batchSave/multiSave/delete/deleteByGql/explain`) | Method surface aligned with the /meta/* runtime endpoints; internally MqlQueryProcessor/MetaQLManager consume metadata to build SQL; `entityConnectId(entityName)` reads `TableMeta.connectId` for datasource routing; write methods are gated by `dao.write-enabled` |
| `$gl.sql` cross-database locating | MetaTableLocationResolver | Walks the registry by table name (exact → case-insensitive) to find EntityMeta, then resolves host/port/db via `resolveConnectId` + the `platform_dev_db_connect` row; results cached in LocalBoundedCache (60s/4096) |
| MQL function parameters | GFunction (increment/findinset/fuzzymatch) | `$entity.field` is translated to the real column name via MetaManager, producing `gfn_xxx(...)` functions |
| MCP tool | MetaModelTool (geelato-mcp) | Exposes getAllEntityNames/getEntityMeta (field lists) to AI so models can understand the data model |

## Operations Observability

| Entry | Notes |
| --- | --- |
| `PlatformMetaMonitorEndPoint` | Actuator endpoint emitting a snapshot of all EntityMeta |
| `/api/meta/conflicts` | Dual-source conflict list |
| `/meta-sync.html` | Three-way (class source / metadata DB source / physical DB source) consistency check and repair tool page |

## Further Reading

- [Runtime Operations](./operations.md) — the APIs and refresh mechanics behind the endpoints
- [Where Metadata Extends](./extensions.md) — the full consumption chains of the runtime endpoints and script channels
- [MetaStore Extension](../reference/metastore-extension.md) — field conventions of the six-table contract when replacing the metadata source
