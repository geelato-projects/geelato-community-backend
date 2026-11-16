---
title: RisingWave Engine
sidebar_label: RisingWave Engine
---

# RisingWave Engine

RisingWave is the first engine adapter of the materialized view acceleration framework: PostgreSQL protocol (default port `4566`), a native `mysql-cdc` connector that reads the MySQL binlog directly to build ingestion channels, and incrementally maintained materialized views.

## Prerequisites

**Source database (MySQL)**:

- `binlog_format=ROW` and `binlog_row_image=FULL` (checked item by item by `/api/model/mv/precheck`)
- the ingestion account needs `REPLICATION SLAVE` and `REPLICATION CLIENT` grants (`GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO ...`)
- source tables must have primary keys (required by CDC)
- RisingWave must be able to reach the source database over the network

**Engine connection**: register it via `/api/model/connect` with `db_type=risingwave`, `db_schema` set to `public` (or the schema where materialized views are created), default port `4566`. At startup the adapter automatically registers `risingwave` as a `postgresql` dialect family alias (`Dialects.registerAlias`), so the driver, URL template, pooling and connection testing all follow the PG family — the framework layers contain no RisingWave concept.

## Ingestion Model

- **Two-stage (RW 3.0 API)**: ① `CREATE SOURCE {channel} WITH (connector='mysql-cdc', hostname, port, username, password, database.name)` — connection definition only, no table list (the 1.x table.name.list is deprecated, and the single-table CDC CREATE TABLE is disabled); ② per physical table `CREATE TABLE {channel}_{table} FROM {channel} TABLE '{db}.{table}'` (**FROM is followed directly by the source name, no SOURCE keyword**) — **the schema is derived automatically by the engine**, no column definitions or type mapping needed
- Channel names `gl_{connection_id}_{database}_{view_name}` (one channel per view); each table attaches to the shared source independently, so **appending tables is naturally supported** (a table-list change only creates the missing tables)
- Source tables must have a primary key (the CDC upsert key); the engine reports on creation otherwise (with context in the error)
- Disable/drop: dropSource first drops the prefixed CDC tables, then the shared source
- the `mysql-cdc` `server.name` takes the channel name, which is naturally unique
- channels do not support appending tables: if an existing channel does not cover a requested table, the call fails hard with remediation hints (DROP SOURCE first, or use a different database/table combination)

## Version Differences

Engine system catalogs (`rw_sources` / `rw_relations` / `rw_materialized_views`) and backfill-progress columns vary across versions; the adapter parses whatever columns are actually returned and reports `UNKNOWN` when progress is unparseable (never guesses). Channel/view DDL is centralized inside the adapter, so engine upgrades only affect that file. After pointing to a new engine version, run a `precheck` plus a `view/create` smoke test first.

## Query-Path Integration

Once enabled, the platform side uses "same-name dual records": the original view row stays untouched, plus a same-name `view_type=materialized` copy row pointing to the engine connection (see [Materialized View Acceleration](materialized-view.md)); when multiple same-name rows are loaded, MetaManager registers the materialized one first, so the entity's `connect_id` points to the engine and queries reach RisingWave through the existing dynamic datasource routing chain. `limit N offset M` pagination, double-quoted identifiers and the count wrapper are all compatible with RisingWave, and the query builder has no engine branches. Materialized view names are uniformly lowercase (matching the view-row `afterSet` folding). Writes are intercepted by the platform read-only gate and never reach the engine.
