---
id: overview
title: Data Archive Overview
sidebar_label: Data Archive
---

# Data Archive (geelato-archive)

Runtime data (audit logs, number-issuing records, notifications, mail bodies, page version history, low-code business tables) keeps growing and drags down query performance, backup size and packaging size. The data-archive feature provides a **policy-driven, observable, recoverable** general archive engine: it moves expired data from source tables to an archive target (MySQL archive database/table in phase 1), and two-phase reconciliation guarantees that **a crash at any moment leaves at most extra data in the source table — data is never lost**.

## Module Composition

| Module | Responsibility |
|---|---|
| `geelato-archive-policy` | Policy management: policy entity (platform_archive_policy), enable-gate validation (hard failure), preset templates, entity-management "enable archive" entry, policy REST API |
| `geelato-archive-engine` | Archive engine: target table structure check, two-phase reconciled migration (SERVER/LOCAL_FILE/JDBC), daily scheduling, run records (platform_archive_run), manual trigger/cancel/restore, run REST API |

Both modules are fully self-contained (no changes to geelato-web-platform) and are **customization modules** (not in the scaffold or the standard-part version catalog; no host includes them yet). A single dependency on `geelato-archive-engine` activates everything: auto-configuration, idempotent table creation, preset loading, controllers registered via the host's `@ComponentScan("cn.geelato")`.

## Policy Model

- **Policies are table-based** (decoupled from entities): `table_name` + condition. Usually created from entity management "enable archive" (`POST /api/archive/entity/enable {entityName}` → resolve physical table name → insert a disabled policy), or manually via `POST /api/archive/policy/createOrUpdate`.
- **DEFAULT policy**: condition fixed to `create_at < now - retention_days`; the watermark is locked once at run start (no boundary drift during long runs).
- **CUSTOM policy**: `where_condition` is an arbitrary WHERE fragment (e.g. `batch < 'B2024-001'`). Safety model: **the condition only participates in "selecting ids"; migration and deletion always execute precisely by `id IN (...)`** — a wrong condition can only move fewer rows, never lose them. Enabling runs a static safety validation (no semicolons/comments/DML/DDL/file-keywords) + a trial run (syntax errors hard-fail 70001).
- **Target**: `target_type` (MYSQL in phase 1; MONGODB/ELASTICSEARCH in phase 2) + `connect_id` (dev_db_connect system; empty = archive within the primary database) + `target_table_name` (derived if empty: same name cross-database; `{source}_archive` in the same database).
- **Execution parameters**: `execution_mode` (AUTO/SERVER/LOCAL_FILE/JDBC), `batch_size` (default 200), `batch_interval_ms` (default 200 throttle), `max_rows_per_run` (default 1,000,000 — progressive archiving: large tables complete over multiple days), `include_deleted` (default true).
- **Lifecycle**: policies are disabled by default — **with no enabled policy, no data is ever touched**; enabling passes the gate (below); parameters are snapshotted at run start; one run per policy at a time.

## Execution Modes (MYSQL target)

| Mode | Data path | Fit & performance |
|---|---|---|
| SERVER | `INSERT INTO target SELECT … WHERE id IN` + DELETE, all server-side | Same DB/instance; **zero data out of the database, fastest** |
| LOCAL_FILE | Stream export to temp file → `LOAD DATA LOCAL INFILE` into the archive DB | Recommended cross-instance; near-native LOAD DATA (an order faster than INSERT); auto-degrades to JDBC if localInfile is disabled or binary columns exist |
| JDBC | Streaming read + batch insert (`rewriteBatchedStatements`) | General fallback |
| AUTO (default) | Runtime probe: same instance → SERVER; otherwise LOCAL_FILE (falls back to JDBC) | Always the fastest path for the current topology |

## No-Accidental-Delete Guarantee (two-phase reconciliation)

```
Per batch (id cursor ascending, LIMIT batch_size):
  Phase 1 write archive: clear leftovers DELETE → write (by mode) → reconcile (loaded rows == expected AND target count == expected)
        mismatch → 70003 abort (source untouched, zero loss)
  Phase 2 delete source (transaction): DELETE WHERE id IN → reconcile (deleted == expected)
        mismatch → 70004 abort and roll back the whole batch (archive complete; rerun safe)
  Update run progress (rows/batches/cursor) → check cancel flag → sleep(batch_interval_ms)
```

- Crash safety: at any crash point the source table has at most extra data, never missing data; reruns clear leftovers first (no INSERT IGNORE, so errors are not masked).
- Directional principle: **prefer duplicates (data in both sides) over loss (deleted from source, absent in archive)**.
- Run records: `platform_archive_run` records status/rows/batches/cursor/watermark/actual mode/duration; failures carry `error_json` with table, batch index, cursor, expected vs actual counts and the stack trace.

## Enable Gate (hard failure, 70001/70002)

1. Source table name is valid (injection-safe) and not in the protected list (archive's own tables, user/role/org/tenant, dev_db_connect, etc.);
2. Policy type valid: DEFAULT requires retention_days ≥ platform minimum (default 30); CUSTOM requires a condition that passes safety validation;
3. Source table physically exists; DEFAULT requires a date-typed create_at column;
4. Archive connection (connect_id) reachable;
5. In-database archive target must differ from the source table name;
6. One enabled policy per table;
7. CUSTOM condition trial run (syntax interception);
8. Target structure check: create from `SHOW CREATE TABLE` if absent; if present, compare columns — a missing column → 70002 abort (never silently skipped).

## REST API

:::note
All endpoints are declared via `@ApiRestController`/`@ApiRuntimeRestController`; in hosts with the `/api` prefix (quickstart, scaffold) the real path carries `/api`. Authentication goes through the platform interceptor.
:::

| Endpoint | Description |
|---|---|
| `POST /api/archive/policy/pageQuery` | Policy page query |
| `GET /api/archive/policy/get/{id}` | Policy detail |
| `POST /api/archive/policy/createOrUpdate` | Create/update (new policies always disabled) |
| `DELETE /api/archive/policy/isDelete/{id}` | Soft delete (rejected while running) |
| `POST /api/archive/policy/validate/{id}` | Pre-enable validation |
| `POST /api/archive/policy/enable/{id}` · `/disable/{id}` | Enable (gate-checked) / disable |
| `POST /api/archive/entity/enable` | Entity-management "enable archive" (input: entityName) |
| `POST /api/archive/run/pageQuery` · `GET /api/archive/run/get/{id}` | Run history |
| `POST /api/archive/run/trigger/{policyId}` | Trigger one policy (synchronous) |
| `POST /api/archive/run/triggerAll` | Run all enabled policies once (silent afterwards) |
| `POST /api/archive/run/cancel/{policyId}` | Graceful cancel (finishes current batch) |
| `POST /api/archive/scheduler/start` · `/stop` · `GET /api/archive/scheduler/status` | Runtime scheduler start/stop/status (in-memory; resets on restart) |
| `POST /api/archive/run/archivedData` | Online read-only paging over archived data |
| `POST /api/archive/run/restore` | Restore (body: policyId + ids; reverse two-phase reconciliation) |

Error codes 70xxx are listed in the Error Code Reference (70xxx data-archive section).

## Online Query of Archived Data (request-level datasource switch)

Besides `/api/archive/run/archivedData` (per-policy single-table query), the platform provides a **request-level switch**: with the header `X-Gl-Archive-Query: true`, all ORM reads within that request route to the archive database — a business page can toggle between "online data / archived data" views without any API change.

This is a **platform standard part** (neutral request-level datasource override in geelato-orm + archive semantics and request filter in geelato-web-platform); it does **not** depend on the archive customization modules.

### Configuration

```properties
spring.datasource.archive.jdbc-url=jdbc:mysql://localhost:3306/geelato_archive?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8
spring.datasource.archive.username=geelato_user
spring.datasource.archive.password=******
spring.datasource.archive.driver-class-name=com.mysql.cj.jdbc.Driver
```

Configuring `jdbc-url` enables it (nothing is wired otherwise); the archive schema must match the primary database (guaranteed by the engine's table cloning) and dbType must match.

### Switching Semantics

- **Only entities without explicit ownership switch**: entities without `connectId` and without an effective `catalog` route to the archive DB; `catalog=platform` entities (users/dictionaries/menus/metadata) keep their routing — page framework, dictionary translation and permissions keep working.
- **Explicit routing is never hijacked**: explicit entity connectId, DSL `useDataSource`, programmatic `withDataSource` all take precedence over the request-level override.
- **Read-only; writes hard-fail**: insert/update/delete on unowned entities throws a clear exception in archive mode — archive consistency is guaranteed by two-phase reconciliation; online writes would bypass it.
- **Generic entity template endpoints hard-fail**: `BaseService.pageQueryModel/queryModel/getModel` uses primaryDao and does not participate; in archive mode they hard-fail and direct you to `/meta/list` (the MQL generic query path routes correctly).
- Request context is cleaned up automatically; schedulers/engines/async threads without request context are unaffected.

### Constraints

1. **"archive" is a reserved datasource key** — never use it as a platform_dev_db_connect row id;
2. Dynamic switching inside transactions has known limits — archive mode is read-only browsing; avoid write transactions in archive-mode requests;
3. Template queries joining platform tables (e.g. tree queries joining platform_tree_node) hard-fail in archive mode (the archive DB lacks platform tables) — use dedicated endpoints;
4. SQL logging (p6spy) and pool monitoring apply to the archive datasource as well.

### Front-end Contract (geelato-front)

In "view archived data" browse mode the HTTP interceptor injects `X-Gl-Archive-Query: true` on all requests (accepted values true/1/on, case-insensitive); leaving browse mode stops the injection. If the archive datasource is not configured, the backend hard-fails such requests (pointing out the missing `spring.datasource.archive.jdbc-url`), never silently degrading.

## Operation Model: Silent by Default

The engine **does not run by default** — no scheduler thread, no automatic archiving (`geelato.archive.scheduler-enabled` defaults to `false`). Three ways to run:

| Method | Endpoint/config | Description |
|---|---|---|
| Trigger one policy | `POST /api/archive/run/trigger/{policyId}` | Synchronous single run |
| Trigger all once | `POST /api/archive/run/triggerAll` | Serial run of all enabled policies, silent afterwards |
| Temporary daily schedule | `POST /api/archive/scheduler/start` | Daily at schedule-time (default 02:00); `/stop` anytime; in-memory, resets on restart |

For a permanent schedule set `geelato.archive.scheduler-enabled=true`. Policies run serially by seq_no; one failure does not block the rest. The scheduler is a self-managed daemon thread (per the NotificationOutboxScheduler convention, no @EnableScheduling).

## Global Configuration (`geelato.archive.*`)

| Key | Default | Description |
|---|---|---|
| `scheduler-enabled` | false | Start the scheduler with the application |
| `schedule-time` | 02:00 | Daily execution time |
| `min-retention-days` | 30 | Minimum retention gate |
| `protected-tables` | core tables | Protected list |
| `default-batch-size` / `default-batch-interval-ms` | 200 / 200 | Defaults for new policies |
| `default-max-rows-per-run` | 1000000 | Per-run row cap default |
| `temp-dir` | `${java.io.tmpdir}/geelato-archive` | LOCAL_FILE temp directory |
| `auto-init-tables` | true | Idempotent table creation at startup |
| `auto-load-presets` | true | Load preset templates at startup (idempotent) |

## Preset Templates (disabled by default)

| code | Table / retention days |
|---|---|
| tpl-audit-log | platform_audit_log / 730 |
| tpl-encoding-log | platform_encoding_log / 365 |
| tpl-notification · tpl-notification-user | platform_notification(_user) / 180 |
| tpl-mail-message | mail_message / 365 (longtext body — biggest size win) |
| tpl-app-page-log | platform_app_page_log / 180 |
| tpl-schedule-log | platform_schedule_log / 90 |

## Integration Guide (customization module)

Data archive is a **customization module**: not in geelato-app-scaffold-starter, not in geelato-framework-starter / geelato-framework-bom, not registered in the root aggregate pom (same precedent as geelato-mail's commented entry).

**Build standalone** (policy first, then engine):

```bash
mvn clean install -f geelato-archive-policy/pom.xml -DskipTests
mvn clean install -f geelato-archive-engine/pom.xml -DskipTests
```

Then declare the dependency explicitly (with version):

```xml
<dependency>
    <groupId>cn.geelato</groupId>
    <artifactId>geelato-archive-engine</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

(geelato-archive-policy comes in transitively.) A logback routing rule for `cn.geelato.archive` is recommended. On startup: idempotent table creation → preset loading (disabled) → daily scheduler. Review and enable policies from policy management.

## Front-end (entity-management integration)

The front-end entry lives in the **geelato-front** repo: model management (GlModelList) → entity detail tabs → the **"Data Archive" tab** (next to "Entity Hooks").

- Location: `packages/gl-ide-arco/src/views/sidebar/model/archive/`; API wrappers in `packages/gl-runtime-core/src/m/datasource/ModelApi.ts`.
- Shape: **one policy per entity, a configuration panel** — empty state with "Enable Archive" (idempotent policy creation); once enabled, a policy form (type/retention/condition/target DB/target table/execution params) + enable/disable switch (gate failures show the 70001 reason) + save/delete + run records (paged dialog with status/rows/batches/duration/error detail, manual run and cancel).
- Note: the IDE hardcodes Chinese by convention (no i18n); the online-query and restore UIs are not in the phase-1 tab (APIs are ready).

## Phase-2 Roadmap

- MongoDB / Elasticsearch archive channels (Channel SPI: bulkWrite / _bulk; target_type reserved; the request-level switch is dbType-agnostic);
- SeaTunnel external executor (data plane delegated to a dedicated sync engine; executor field reserved);
- Front-end management pages;
- Separate soft-deleted window (by delete_at), tenant-differentiated retention.
