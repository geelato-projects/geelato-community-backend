---
title: API · Materialized Views
sidebar_label: Materialized Views
---
# Materialized Views (mv)

1 controller with 13 endpoints in total. All endpoints in this domain are design-time APIs (@DesignTimeApiRestController): engine-neutral materialized view acceleration management. Dual-record architecture — enabling adds a same-named materialized replica row in the platform (metadata loading prefers the materialized row and queries route to the engine), disabling soft-deletes the replica row and falls back to the original view, and dropping removes it entirely; the engine (e.g. RisingWave) is chosen by the connection's `db_type` and dispatched via adapters.

## MaterializedViewController ⚙ — `/model/mv`

Full lifecycle management of materialized views: precheck, CDC source creation, preview/validate/step-by-step execution of the 3-step DDL, view initialization, enable/disable, metadata refresh, data comparison and drop. All endpoints are POST with a JSON object body.

### precheck Enablement precheck

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/precheck` |
| Params | `engineConnectId`: body, string, required, engine connection ID.<br />`originConnectId`: body, string, required, source database connection ID.<br />`viewName`: body, string, optional, view name (used in the precheck report).<br />`tables`: body, string array or comma-separated string, optional, source table list.<br />`database`: body, string, optional, source database name (falls back to the name registered on the source connection).<br />`snapshot`: body, boolean, optional, default `true`, whether to run the initial snapshot backfill. |
| Returns | `ApiResult<Map<String,Object>>`: `passed` indicates whether all checks passed; `items` is the per-item check result (engine adapter, non-empty table list, source object resolution/expansion, engine-side checks, etc.). No DDL is executed. |

### createSource Create CDC source

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/source/create` |
| Params | `engineConnectId`: body, string, required.<br />`originConnectId`: body, string, required.<br />`viewName`: body, string, required (normalized to lowercase automatically).<br />`tables`: body, string array or comma-separated string, required, source table list (MySQL views are expanded to physical tables automatically; an error is raised when no physical table resolves).<br />`database`: body, string, optional, source database name.<br />`snapshot`: body, boolean, optional, default `true`. |
| Returns | `ApiResult<Map<String,Object>>`: `engineType`, `sourceName`, `physicalTables`, `tableMapping` (source table → engine ingest table) and the 3-step DDL payload `ddl`; executes the DDL to set up the ingest channel and persists the DDL JSON to the replica row. |

### previewDdl Preview 3-step DDL

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/ddl/preview` |
| Params | `engineConnectId`: body, string, required.<br />`originConnectId`: body, string, required.<br />`viewName`: body, string, required.<br />`originSql`: body, string, optional, the view's defining SELECT (falls back to the defining SQL of the same-named original view row when absent).<br />`tables`: body, string array or comma-separated string, optional, source table list.<br />`database`: body, string, optional, source database name.<br />`snapshot`: body, boolean, optional, default `true`. |
| Returns | `ApiResult<Map<String,Object>>`: `sourceName`, `expandedSql`, `engineSql`, `physicalTables`, `expandedViews`, `passed` and `steps` (per-step DDL text and per-item check results); no DDL is executed against the engine, and the DDL JSON is persisted to the replica row only on first preview. |

### validateDdl Validate 3-step DDL

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/ddl/validate` |
| Params | Same params as `previewDdl`, plus `ddl`: body, object, optional, the (possibly edited) 3-segment DDL payload sent back by the front end (`sourceDdl`, a `cdcTables` array with `originTable`/`engineTable`/`ddl`, `mvDdl`; CDC ingest table identifiers cannot be modified). |
| Returns | `ApiResult<Map<String,Object>>`: validation result shaped like the preview (`passed` and per-step `steps` conclusions); no DDL is executed. |

### executeStep Execute one DDL step

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/ddl/step/execute` |
| Params | `stepKey`: body, string, required, step identifier (`source` / `cdc:<originTable>` / `mv`).<br />Other params are the same as `previewDdl` (including the optional edited `ddl` payload). |
| Returns | `ApiResult<Map<String,Object>>`: `executed`/`reused`/`message`/`passed`/`items`/`detail`; steps failing validation are blocked from execution; when `stepKey=mv` executes successfully, `materialized`, `mode`, `columns` and `ddlJson` are included (platform replica row and metadata are synced). |

### createView Initialize materialized view

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/view/create` |
| Params | `engineConnectId`: body, string, required.<br />`originConnectId`: body, string, required.<br />`viewName`: body, string, required.<br />`originSql`: body, string, optional, defining SELECT (falls back to the original view row when absent).<br />`tables`: body, string array or comma-separated string, optional, source table list.<br />`database`: body, string, optional, source database name.<br />`snapshot`: body, boolean, optional, default `true`.<br />`recreate`: body, boolean, optional, default `false`, set `true` when legacy channel naming conflicts or a rebuild is needed.<br />`ddl`: body, object, optional, edited 3-segment DDL payload (validated per step first; no DDL runs when validation fails). |
| Returns | `ApiResult<Map<String,Object>>`: after all prechecks pass (view expansion, SQL rewrite, SELECT syntax, CDC column types, platform default columns), the 3-step DDL is executed; returns `mode`, `reused`, `sourceName`, `expandedSql`/`engineSql`, `physicalTables`, `ddlJson`, `columns`, engine `status` and `message`; before backfill completes (status=BACKFILLING) query results are incomplete — confirm READY via `view/status` before serving queries. |

### viewStatus Query ready status

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/view/status` |
| Params | `engineConnectId`: body, string, required.<br />`viewName`: body, string, required. |
| Returns | `ApiResult<Map<String,Object>>`: `engineType`, `viewName`, `enabled` (whether an enabled materialized replica row exists in the platform) and the engine-reported `status` (used to confirm backfill READY). |

### enable Enable (save replica row)

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/view/enable` |
| Params | `engineConnectId`: body, string, required, engine connection ID.<br />`viewName`: body, string, required, original view name (a materialized view is an acceleration replica of an existing view).<br />`originSql`: body, string, optional, defining SELECT (falls back to the original view row when absent). |
| Returns | `ApiResult<Map<String,Object>>`: only creates/restores the materialized replica row (the engine is not initialized); returns `mode` (created/updated), `enabled=true` and a `note` (view columns pending backfill; run `view/create` before serving queries). |

### disable Disable materialized view

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/view/disable` |
| Params | `viewName`: body, string, required. |
| Returns | `ApiResult<Map<String,Object>>`: soft-deletes the materialized replica row and refreshes metadata (queries fall back to the source database view; the engine-side materialized view is kept and can be reused without backfill on re-enable); returns `enabled=false` and a `note`. |

### refreshMeta Refresh metadata cache

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/view/refreshMeta` |
| Params | `viewName`: body, string, required. |
| Returns | `ApiResult<Map<String,Object>>`: reloads entity metadata and clears the data source routing cache; when the replica row's column metadata is missing and the view exists in the engine, columns are re-synced, returned as `syncedColumns` (-1 when no re-sync was triggered). |

### compareData Compare source data

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/view/compareData` |
| Params | `engineConnectId`: body, string, required.<br />`originConnectId`: body, string, required.<br />`viewName`: body, string, required.<br />`originSql`: body, string, optional, defining SELECT.<br />`tables`: body, string array or comma-separated string, optional, source table list.<br />`database`: body, string, optional, source database name. |
| Returns | `ApiResult<Map<String,Object>>`: `allMatch` and per-table results in `tables` (source row count vs engine ingest table row count with `countMatch`, latest-row column diffs by `update_at` in `latestMismatches`), plus a row-count comparison between the materialized view and the original view's query; large-table COUNTs are slow, avoid frequent calls. |

### checkQuery Check queryability

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/view/checkQuery` |
| Params | `engineConnectId`: body, string, required.<br />`viewName`: body, string, required. |
| Returns | `ApiResult<Map<String,Object>>`: runs `SELECT * ... LIMIT 1` and `COUNT(*)`, returning `queryOk`, `totalRows`, `columns`, `sampleRow` (one sample row) and the engine `status`. |

### drop Drop materialized view

| Item | Content |
| --- | --- |
| URL | `POST /api/model/mv/view/drop` |
| Params | `viewName`: body, string, required.<br />`dropSource`: body, boolean, optional, default `false`, whether to also drop the CDC source and ingest tables.<br />`originConnectId`: body, string, required when `dropSource=true`.<br />`database`: body, string, required when `dropSource=true` (unless the source connection has a registered database name). |
| Returns | `ApiResult<Map<String,Object>>`: drops the engine-side materialized view and soft-deletes the replica row; with `dropSource=true` ingest tables are cleaned according to shared-channel references (a shared source still referenced by other views is kept), returning `droppedCdcTables` and `sharedSourceKept`. |
