# Script Authoring Guide

This page describes how to write platform scripts: structure, variables, the six built-in kits (`$gl.*`), the write channel and dry-run, error handling and best practices.

## Structure

A script is a statement block ending in `return`; the returned value is the result. JavaScript is the default language (`lang` optionally `python`).

```javascript
const rows = $gl.sql.query(`
  SELECT id AS biz_id, eco_no AS biz_no, not_written_off
  FROM grant_apply
  WHERE not_written_off > :minAmount
`, { minAmount: $gl.parameter.get('minAmount') });

return rows.filter(r => r.NOT_WRITTEN_OFF > 0)
    .map(r => ({ bizType: 'BOL_GRANT', bizId: r.BIZ_ID, bizNo: r.BIZ_NO }));
```

In multi-step pipelines (e.g. exception judgment `pipeline_json.steps`), every step after the first can read the previous output via `$input`.

## Variables

| Variable | Notes |
| :--- | :--- |
| `$ctx` / `$gl.ctx.get(key)` / `$gl.ctx.all()` | business context injected by the caller |
| `$params` / `$gl.parameter.get(key)` / `$gl.parameter.all()` | script parameters (thresholds, etc.) |
| `$input` | previous step output in pipelines |
| `$env` | read-only execution metadata (execId, engineVersion, profileId, tenantCode, user) |
| `console` | logging (log/info/warn/error/debug) |

## $gl.sql — SQL Bridge

Named parameters (`:name`); queries are capped by `sql.row-limit` (default 10000) and `sql.query-timeout-ms` (default 10s).

| Method | Description |
| :--- | :--- |
| `$gl.sql.query(sql, params)` | returns an array of rows; row count feeds the execution rowCount |
| `$gl.sql.queryOne(sql, params)` | single row or null |
| `$gl.sql.queryValue(sql, params)` | single value or null |
| `$gl.sql.update(sql, params)` | write statement, returns `{affected, dryRun}` |
| `$gl.sql.execute(sql)` | DDL / no-parameter statement, same gating as update |

### Cross-database queries

`$gl.sql` resolves the physical tables referenced by a statement (via connection registration: `platform_dev_table.connect_id` / `@Entity(connectId)` / catalog mapping) and routes in three tiers by how each table's database relates to the executing database:

| Case | Behavior |
| :--- | :--- |
| All tables local to the executing database (incl. unregistered ones) | executed as-is on the direct connection, zero overhead |
| Same server, different database | database-name prefix added automatically (e.g. `` `bizdb`.`t` ``), still executed on the executing connection; transaction and dry-run rollback semantics unchanged |
| Cross-server / heterogeneous sources | federated execution via Apache Calcite: per-source filters and projections are pushed down; cross-source joins/aggregations run in application memory (not a distributed engine; large results are bounded by row limit and timeout) |

Boundaries: cross-server **write** statements are rejected outright (no cross-database transaction guarantee); unregistered tables, already qualified names, CTE aliases, and unparsable statements pass through unchanged; a registered connection with no `platform_dev_db_connect` row fails hard. The same-server prefix rewrite replaces only the table-name text span; the rest of the statement (comments and hints included) is preserved verbatim. Routing decisions are recorded in the trace (`sql/route` entries) and `KIT-XDB` logs.

## $gl.mql — MQL Metadata Query

Input is an MQL JSON text (see the [MQL guide](/docs/mql/usage)); the method set mirrors the platform `/meta/*` endpoints (MetaController), and entity metadata drives data-source routing and identity.

| Method | Mirrors | Description |
| :--- | :--- | :--- |
| `$gl.mql.list(mqlJson)` | `/meta/list` | single-entity query, returns `{data, total, page, size}` |
| `$gl.mql.multiList(mqlJson)` | `/meta/multiList` | multi-entity query, returns `{data: {entity: {data, total, page, size}}}` |
| `$gl.mql.save(mqlJson)` | `/meta/save/{biz}` | save, returns `{pk, dryRun}`; write-gated |
| `$gl.mql.batchSave(mqlJson)` | `/meta/batchSave` | batch save, returns `{pks, dryRun}`; write-gated |
| `$gl.mql.multiSave(mqlJson)` | `/meta/multiSave` | multi-entity save, returns `{count, dryRun}`; write-gated |
| `$gl.mql.delete(entityName, id)` | `/meta/delete/{biz}/{id}` | delete by id (comma-separated multi-value), returns `{affected, dryRun}` |
| `$gl.mql.deleteByGql(mqlJson)` | `/meta/delete2/{biz}` | delete by condition, returns `{affected, dryRun}`; write-gated |
| `$gl.mql.explain(mqlJson, op)` | — | explain without executing (op=list/save/delete) |

## $gl.http — Outbound HTTP

Gated by `http.enabled` (default off), a host `http.allowlist` (exact host, `*.` prefix, optional `scheme://`) and a `http.timeout-ms` ceiling. No certificate bypass.

| Method | Description |
| :--- | :--- |
| `$gl.http.get(url, params, headers)` | GET, returns the body string |
| `$gl.http.post/put/patch(url, body, headers)` | body string as-is or JSON-serialized object |
| `$gl.http.delete(url, headers)` | DELETE |
| `$gl.http.request({method, url, params, body, headers, timeoutMs})` | full request, returns `{status, headers, body}` |

## $gl.encrypt — Digest and AES

AES follows the platform `AesUtils` convention (AES/GCM, Base64(IV+ciphertext)); the key comes from `geelato.script.engine.encrypt.aes-key` and both methods throw when unset.

| Method | Description |
| :--- | :--- |
| `$gl.encrypt.md5(text)` | 32-char lowercase hex |
| `$gl.encrypt.md5Short(text)` | 16-char slice [8,24) |
| `$gl.encrypt.aes(text)` / `$gl.encrypt.aesDecrypt(cipher)` | AES encrypt/decrypt |

## Write Channel and Dry-Run

Write methods (`$gl.sql.update/execute`, `$gl.mql.save/batchSave/multiSave/delete/deleteByGql`) are gated uniformly:

- **Master switch**: `geelato.script.engine.dao.write-enabled`, default `false` — write calls throw when off;
- **Dry-run**: `geelato.script.engine.dao.dry-run`, default `true` — statements execute inside a transaction that is always rolled back, with `dryRun: true` in the result; `ScriptExecution.dryRun` overrides per execution.

## Error Handling

Kit failures surface as script `RUNTIME` errors carrying the precise cause and configuration key (e.g. "SQL 写通道未启用（geelato.script.engine.dao.write-enabled）"). Use try/catch to convert them to business semantics when appropriate.

## Best Practices

- **Select only what you need**: explicit column lists, paging (`@p`) or WHERE windows;
- **Always parameterize**: named parameters, never string concatenation;
- **Explain first**: validate complex MQL via `$gl.mql.explain` before going live;
- **Leave traces**: `console.log/warn` on key branches;
- **Fail explicitly**: do not swallow exceptions or mask errors with empty arrays.
