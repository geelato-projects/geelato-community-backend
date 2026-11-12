---
id: usage
title: Business Exception Rules and Usage
sidebar_label: Rules and Usage
---

# Business Exception Rules and Usage

How to define exception rules, write judging pipelines, and which REST APIs are available. Overview: [Business Exception](overview.md).

## Rule Definition

Rules live in `platform_exception_rule` (DDL `db/exception-schema.sql`, idempotent). Key columns:

| Column | Description |
| --- | --- |
| `rule_code` / `rule_name` / `domain_code` | Code (`EXC-{domain}-{seq}`, UK), name, domain |
| `level` | P0~P3 |
| `rule_kind` / `escalation_of_rule_code` | ALERT / ESCALATE (points to the parent rule code) |
| `trigger_type` / `cron_expression` | CHANGE / SCAN / BOTH; SCAN cron |
| `listen_entities` | Event subscriptions: `[{entity, operations, role, refField, fields}]` |
| `pipeline_json` | Judging pipeline `{steps:[{name, lang, content}]}` |
| `threshold_json` | Threshold parameters, read as `$params` in scripts |
| `owner_selector` | `{handlers:[…], cc:[…]}` (six selector kinds, same as alert) |
| `storm_json` | `{hitLimit, overflowPolicy, cooldownMinutes, inhibitedBy}` storm control |
| `resolve_policy` / `mutual_exclusive_group` / `flow_template` | ANY/ALL/SELF, exclusive group, STANDARD/INTERCEPT/NOTICE |
| `biz_url_template` | "Go handle" jump template (`{bizId}` placeholder) |
| `engine_version` / `version` | Script contract snapshot / caliber version |

### YAML Import/Export

`POST /api/exception/rule/import-yaml`, `POST /import-yaml-batch`, `GET /{id}/export-yaml` provide lossless YAML round-trip for version-controlling rules.

## Writing Pipelines

A pipeline is an ordered run of single scripts: `steps[{name, lang, content}]`; `lang` supports `js` (GraalVM, IIFE return) and `python` (assign `__result__`); each step's output feeds the next step's `$input`.

- Bindings: `$ctx{trigger, bizId, rule{code, level, kind}}`, thresholds `$params`, previous step `$input`.
- Data access: `$gl.sql.query(sql, $params)` supports `:name` parameters; `$gl.dao` for ORM writes; `$gl.target.targetInstance(bizId)` for escalation chains.
- Exit contract: the final step returns a hits array — `{bizId required, bizType?, bizLabel?, title?, anchorTime?, snapshot?, …extras}`; step output is snake→camelCase normalized with a size circuit breaker.
- Validation at three points: save-time static check (js must contain `return`, python `__result__`), enable-gate dry-run (real TRIAL execution), runtime fallback (non-array drops all `HITS_NOT_ARRAY`; missing bizId drops the entry with a log; missing title/snapshot/anchorTime logs only).

### Example (seed rule EXC-BL-015, single-step js pipeline)

```javascript
// threshold_json: {"daysAfer":7,"lookbackDays":90}
const rows = $gl.sql.query(`
  SELECT 'ORDER' AS biz_type,
         b.order_id AS biz_id, e.eco_no AS biz_label,
         COALESCE(g.grant_time, b.update_at) AS grant_time,
         DATEDIFF(NOW(), COALESCE(g.grant_time, b.update_at)) AS days_since_grant
  FROM il_order_bol b
  JOIN il_eco e ON e.id = b.order_id AND e.del_status = 0
  WHERE b.del_status = 0
    AND b.bol_status IN ('2','3','4')
    AND DATEDIFF(NOW(), COALESCE(g.grant_time, b.update_at)) >= :daysAfer
`, $params)

return rows.map(r => ({
  bizId: r.biz_id,
  bizType: r.biz_type,
  bizLabel: r.biz_label,
  title: `Order ${r.biz_label} released ${r.days_since_grant} days, receivable not written off`,
  anchorTime: r.grant_time,
  snapshot: { daysSinceGrant: r.days_since_grant }
}))
```

:::warning Performance
SCAN condition queries run in full inside the database; ensure indexes exist for large-table rules (`bea/rules-practical/_scan_rule_indexes.sql` lists the scan-leg indexes).
:::

## REST API

:::note
Endpoints are declared via `@ApiRuntimeRestController`; with the `/api` prefix the real path carries `/api`. Rule-management REST is unaffected by the engine switch; trial-run/scan/recalculate return clear errors while the engine is off.
:::

**Rule management `/api/exception/rule`**

| Endpoint | Description |
| --- | --- |
| `POST /pageQuery` | Rule page query (app-isolated; no appId fails closed to an empty set) |
| `GET /{id}`, `POST /save` | Detail / save |
| `POST /{id}/enable`, `POST /{id}/disable` | Enable (contract gate dry-run) / disable |
| `POST /import-yaml`, `POST /import-yaml-batch`, `GET /{id}/export-yaml` | YAML round-trip |
| `POST /{id}/trial-run` | Trial run (real TRIAL execution, bounded timeout) |
| `POST /{id}/scan?appId` | Trigger a single-rule scan |
| `GET /escalation-chain/{ruleCode}` | Escalation chain query |
| `POST /contract-check` | Pipeline contract static check |

**Workbench `/api/exception`**

| Endpoint | Description |
| --- | --- |
| `POST /pageQuery`, `GET /summary`, `GET /domain-stats` | Instances / summary / domain stats |
| `GET /{id}`, `GET /{id}/steps`, `GET /{id}/handles` | Instance detail / steps / handle log |
| `POST /{id}/claim`, `/note`, `/resolve`, `/ignore`, `/escalate` | Claim / note / resolve / ignore / escalate |
| `POST /{id}/recalculate` | Manual re-evaluation (same kernel) |

**Admin `/api/exception/admin`**

| Endpoint | Description |
| --- | --- |
| `POST /entity-event` | Change-event entry |
| `POST /scan`, `GET /scan-logs` | Manual full scan / scan logs |
| `POST /archive` | Instance archive |

## Built-in Seed Rules

`db/exception-rule-practical-init.sql` ships 8 practical business rules (**disabled by default**), all single-step js pipelines with SCAN triggers:

| Rule | Semantics | Domain / level |
| --- | --- | --- |
| EXC-ODR-001 | Booking acceptance stuck | ODR |
| EXC-ODR-018 | Not gated in before cutoff | ODR |
| EXC-ODR-019 | Container not picked up overdue | ODR |
| EXC-ODR-020 | Container not returned overdue | ODR |
| EXC-BL-015 | B/L released without payment | BL / P0 |
| EXC-BL-016 | Overdue B/L release | BL |
| EXC-CUS-001 | Customer over credit | CUS |
| EXC-FIN-018 | Import order settlement overdue | FIN |
