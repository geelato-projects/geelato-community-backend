---
title: Materialized View Acceleration
sidebar_label: Materialized Views
---

# Materialized View Acceleration

Slow multi-table queries can be served by materialized views maintained by an external engine: writes still go to the source database (MySQL), the engine incrementally syncs and materializes the query result, and once enabled, queries on the view land directly on the materialized view — **with zero changes to caller queries** (transparent switching).

The framework is **engine-neutral**: orchestration, metadata, routing and the read-only gate are unaware of the concrete engine. Engines (the first being RisingWave) plug in as adapters; replacing or adding engines later does not affect callers or existing views.

## How It Works (dual records + materialized-first loading)

```
MySQL source tables (write path unchanged)
   |  ingestion channel (engine-specific; RisingWave reads binlog via mysql-cdc)
   v
External engine: ingestion channel -> materialized view (incrementally maintained, seconds-fresh)
   ^
   |  same-name materialized copy row in platform_dev_view (connect_id=engine connection)
existing queries (/api/meta/list, MQL, dao) — the view name stays the same; when multiple
same-name rows are loaded, MetaManager registers the materialized one first, so the entity's
connectId points to the engine and routing flows through the existing dynamic datasource chain
```

Placement and semantics:

| Data | Where it lives |
|---|---|
| Engine connection | one `platform_dev_db_connect` row (e.g. `db_type=risingwave`) |
| Original view | the original `platform_dev_view` row is **left untouched** (default/custom/virtual as before) |
| Materialized copy | a **new same-name materialized row** in `platform_dev_view`: copies the original view data, changing only `connect_id` to the engine connection, `view_type=materialized`, `view_construct=**the original definition SQL**` (written with source table names; query building does not consume it for materialized — still queried by the physical view name), `view_column=introspected column JSON` |
| Metadata registration | with multiple same-name rows, `MetaManager` registers the **materialized one first** (regardless of load order); after disabling (soft-deleting the copy row) the original view takes over registration again |
| Ingestion channel / backfill progress | **not persisted platform-side** — the engine is the source of truth, introspected live |

:::info Why same-name dual records
A materialized view is an acceleration copy of an existing view, not a new view: the original view row keeps the full definition and rollback capability; the same-name dual rows make entity registration a natural "materialized-first" routing takeover with zero caller changes. The view type options in the view management UI remain default/custom/virtual only — materialized views are managed via a switch in the "Materialized View" tab of the original view's editor.
:::

**Freshness semantics**: ingestion is incremental CDC (seconds to minutes); queries read **eventually consistent** data, and results are incomplete while the initial backfill is running (status=BACKFILLING). Queries that cannot tolerate this should disable the materialized view (queries immediately fall back to the original database view).

**Read-only hard failure**: while enabled, write operations (save/insert/update/delete, etc.) on the view entity throw an explicit exception at the Dao layer; write to the source tables instead.

## Usage Flow

Design-time API prefix `/api/model/mv` (DesignTime). Register the engine connection first via `/api/model/connect`. On the frontend, the whole flow is driven by a switch in the "Materialized View" tab of the view editor.

### 1. precheck

```json
POST /api/model/mv/precheck
{
  "engineConnectId": "engine connection id",
  "originConnectId": "MySQL source connection id",
  "tables": ["orders", "customers"],
  "viewName": "v_order_summary",
  "database": "optional, defaults to the source connection db_name"
}
```

Returns every check as an item (does not abort midway): engine adapter, source connectivity, `binlog_format=ROW`, `binlog_row_image=FULL`, `REPLICATION SLAVE/CLIENT` grants, source tables exist, view-name status on the engine.

### 2. view/create — enable (engine creates the materialized view + the copy row)

```json
POST /api/model/mv/view/create
{
  "engineConnectId": "engine connection id",
  "viewName": "v_order_summary",
  "originConnectId": "MySQL source connection id",
  "tables": ["orders", "customers"],
  "originSql": "select o.id, o.amount, c.name from orders o join customers c on o.customer_id = c.id",
  "database": "optional",
  "recreate": false
}
```

Write `originSql` with **source table names** (the frontend pre-fills the original view's construct); the framework first expands view references in the statement into their underlying physical tables (mysql-cdc can only ingest physical tables), then lexically rewrites table references to the engine-side ingestion table names, creates the materialized view, introspects columns, and inserts/updates the same-name materialized copy row (the definition SQL is persisted into `view_construct`). Ingestion channels are named deterministically from (source connection + database + view), **one channel per view** — each physical table gets its own CDC ingestion table (RW 2.x/3.x API); repeated calls are idempotent and **appending tables is naturally supported** (a table-list change just creates the missing tables). With `recreate=false`, an existing same-name materialized view in the engine is **reused directly** (re-enabling after a disable skips backfill); `recreate=true` drops and recreates (rebuild after a definition change; backfill restarts). The response contains both `originSql`/`engineSql` definitions, the column list and the backfill status.

### 3. view/status

```json
POST /api/model/mv/view/status
{ "engineConnectId": "engine connection id", "viewName": "v_order_summary" }
```

Queries the engine catalog live: `READY`, `BACKFILLING` (results incomplete), `NOT_EXISTS`, or `UNKNOWN` (engine-version differences; progress unparseable), plus the current definition in the engine (`definition`) and the platform-side enablement (`enabled`).

### 4. view/disable — disable (transparent rollback)

```json
POST /api/model/mv/view/disable
{ "viewName": "v_order_summary" }
```

Soft-deletes the materialized copy row — queries **immediately fall back to the original database view**; the engine-side materialized view and ingestion channel are kept, and re-enabling (view/create with recreate=false) reuses them without backfill. The cost of a misoperation is near zero.

### 5. view/drop — full cleanup

```json
POST /api/model/mv/view/drop
{ "viewName": "v_order_summary", "dropSource": "optional", "originConnectId": "required when dropSource", "database": "optional" }
```

Engine dropView + soft-delete of the copy row; with `dropSource=true` the ingestion channel is dropped as well (fails with a hint if other materialized views still reference it).

## Engine Integration (SPI)

Two steps to add an engine; the framework (orm/core knows no concrete engine type) and callers stay untouched:

1. implement the adapter `cn.geelato.web.platform.srv.mv.engine.MaterializedViewEngine` (a Spring Bean claiming connections via `supports(dbType)`): ingestion channel, view DDL, status and column introspection, drops; every failure must fail hard with locatable context;
2. at startup the adapter declares its JDBC shape via `Dialects.registerAlias(dbType, built-in dialect family)` (e.g. RisingWave registers as the `postgresql` family) — driver, URL template, pooling and connection testing all follow the family automatically, with zero framework branch changes.

For the prerequisites and specifics of the first engine, see [RisingWave Engine](risingwave-engine.md).
