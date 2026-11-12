---
id: overview
title: Business Exception Overview
sidebar_label: Business Exception
---

# Business Exception (geelato-exception)

The business exception center is a **rule-based judging → one-instance-per-person → resolution-loop** engine: business semantics like "booking acceptance stuck" or "billed beyond credit" become configurable exception rules; script pipelines detect hits in batch or on change, and a unique exception instance is maintained per "rule × business object × participant" until resolved.

Module: `cn.geelato:geelato-exception`. **The engine is off by default** (`geelato.exception.engine.enabled`, default false); rule-management REST works regardless of the switch — engine beans (scan/event/submit chain) are wired only when enabled.

## Core Concepts

| Concept | Description |
| --- | --- |
| Exception rule | Table `platform_exception_rule`: `rule_code` (`EXC-{domain}-{seq}`), `domain_code`, `level` (P0~P3), `rule_kind` (ALERT / ESCALATE with `escalation_of_rule_code`) |
| Trigger | `trigger_type`: `CHANGE` (entity event re-evaluation) / `SCAN` (scheduled full scan) / `BOTH`; SCAN uses `cron_expression` |
| Judging pipeline | `pipeline_json.steps[{name, lang, content}]` — geelato-script scripts (js/python) run step by step; each step's output feeds the next step's `$input`; the final step returns a **hits contract** array |
| Hits contract | Typed `Hit`: **`bizId` is the only required field**; optional `bizType`/`bizLabel`/`title`/`anchorTime`/`snapshot`; anything else goes to `extras`; validated at three points (save-time static check / enable-gate dry-run / runtime fallback) |
| Participant selectors | `owner_selector`, six kinds: `USER`, `ROLE`, `DEPT`, `DEPT_ROLE` (optionally the business org of the hit), `BIZ_FIELD` (default ownerId), `ORG_TREE_UP` (escalation up the org tree) |
| One-instance-per-person | Table `platform_exception`: fingerprint `active_fingerprint = {rule_code}\|{biz_type}\|{biz_id}\|{participant_id}` is permanently unique; statuses `OPEN/PROCESSING/RESOLVED/IGNORED/ESCALATED`; renewal while active, reopen after closure, group-level AUTO_RECOVER |

## Runtime Model

```
Scan leg (ExceptionScanScheduler, self-managed daemon, 30s tick)
  → CAS claim on the rule row (scan_status/scan_runner, stale recovery, cluster-safe)
  → rebuild tenant context → pipeline SCAN judging → hit submission

Event leg (EntityEventReactor)
  → listen_entities [{entity, operations, role, refField, fields}] matches ORM change events
  → PRIMARY/RELATED normalization (child id → parent id) → RECHECK single-object re-evaluation (same pipeline as scan)
  → events affect freshness only: duplicate/out-of-order/ghost signals are harmless; judging re-queries the database

Hit submission chain (HitSubmitService)
  inhibition (storm_json.inhibitedBy) → circuit break (hitLimit, overflowPolicy) → cooldown (cooldownMinutes)
  → participant solving → one-instance-per-person upsert → notification touch (handlers and cc each become instances)
```

- The scan scheduler does not depend on the host's `@EnableScheduling` (self-managed-thread convention); the due baseline is the last scan log start_at, missed fires replay once.
- **Escalation chains**: ESCALATE rules use `$gl.target.targetInstance(bizId)` to read the parent rule's active instances.
- **Alert integration**: `platform_exception` lifecycle events are the alert center's only built-in event source — see [Business Alert](../alert/overview.md).

## Enabling & Configuration

```properties
geelato.exception.engine.enabled=true
```

| Key (`geelato.exception.engine.*`) | Default | Description |
| --- | --- | --- |
| `scan.tick-seconds` / `thread-pool-size` / `lock-stale-minutes` | 30 / 2 / 30 | Scan cadence / pool / CAS stale recovery |
| `pipeline.step-timeout-ms` / `pipeline-timeout-ms` | 30000 / 120000 | Step / whole-pipeline timeout |
| `pipeline.trial-timeout-ms` | 30000 | Trial-run timeout |
| `pipeline.step-output-max-bytes` | 2MB | Step output size circuit breaker |
| `sql.connect-id` | primary | Default pipeline datasource |
| `http.enabled` / `allowlist` / `timeout-ms` | false / empty / 5000 | Pipeline outbound whitelist (second-level gate above `geelato.script.engine.http.enabled`) |
| `trigger.queue-capacity` | 1000 | Bounded change-event queue (drops with a log when full) |

The script base (geelato-script, prefix `geelato.script.engine`) has three channels on by default: `python-enabled=true`, `http.enabled=true` (empty allowlist still rejects), `dao.write-enabled=true` (`dao.dry-run=true` still rolls back by default).

## Scripting (exception-judge profile)

Judging scripts run under the `exception-judge` profile: **all namespaces and variables are open** — built-in kits and consumer-registered kits are all visible; only timeouts are tightened. Common entries: `$gl.sql` / `$gl.dao` for data access, `$gl.target.targetInstance(bizId)` for escalation chains, and the federated ConnectPoolProvider for cross-datasource queries. See [Rules and Usage](usage.md).

## Current Status

- **Backend fully implemented**: pipeline judging, hits contract, one-instance-per-person, scan/event legs, escalation chains, workbench REST, YAML round-trip, 8 practical seed rules (disabled by default), with unit tests.
- **Front-end workbench not yet shipped**: `/exception/*` APIs are ready; the UI exists as design prototypes (bea/page 09–11), and visual rule editing (V2) is in design.
