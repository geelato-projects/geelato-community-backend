---
id: overview
title: Business Alert Overview
sidebar_label: Business Alert
---

# Business Alert (geelato-alert)

The business alert center decouples "things people need to know" from business code: **event/schedule triggering → rule matching → aggregation & suppression → template rendering → multi-channel delivery**. It is a separate engine from [Business Exception](../exception/overview.md) but interconnected — exception instance lifecycle events are the alert's only built-in event source.

Module: `cn.geelato:geelato-alert`. **The engine is off by default**; enable with `geelato.alert.engine.enabled=true` (quickstart default `${GEELATO_ALERT_ENGINE_ENABLED:false}`). The render wiring (alert-render profile) is always registered.

## Rule Model

Rules live in `platform_alert_rule` (4-table set: rule / template / alert / silence):

| Column | Description |
| --- | --- |
| `alert_code` | `ALR-{seq}` |
| `match_json` | Event-source match `{source, events, ruleCodes}` — **generalized event-source design**; currently only `EXCEPTION` is built-in (maps to `platform_exception`, events via framework ORM events); unknown sources hard-fail |
| `trigger_mode` / `cron_expression` / `dataset_json` | `EVENT` / `SCHEDULE` / `BOTH`; SCHEDULE defines a dataset caliber |
| `aggregate_mode` | `INDEPENDENT` (pass-through) / `AGGREGATE` (group), i.e. SINGLE/GROUP |
| `group_wait_seconds` / `group_interval_seconds` / `repeat_interval_minutes` | Group window / min re-notify interval / repeat reminder for active groups |
| `max_items` / `resolve_notify` / `daily_limit` | List render cap / resolve notification / daily delivery cap |
| `receiver_json` / `route_json` | Receiver selectors / channel routing `{default:{channels, templateCode}}` |

## Engine Pipeline

```
AlertEngineScheduler (self-managed single thread)
  main tick serially: source scan → flush → deliver → REPEAT
  schedule tick independently polls SCHEDULE crons (default 30s)

Event leg: AlertMatcher (event type + ruleCodes) → AlertGrouper
  SINGLE: straight to PENDING (flush_at=now)
  GROUP: append to active group (eventIds deduped) or create group (group_wait window + unique active_group_key)
  source_json (business data snapshot) is collected at record creation — rendering never re-queries source tables

Flush: FlushScanner (PENDING→READY→render snapshot) → RepeatScanner → ResolveHandler (RESOLVE notify)
Deliver: DeliverEngine (silence final check → render fallback → receiver solving → daily_limit → per-channel enqueue)
```

Alert record state machine: `PENDING → READY → SENT / PARTIAL / FAIL / SILENCED`.

## Rendering (structurally no data access)

- Template table `platform_alert_template`: `templateCode` + channel variants (titleScript/contentScript js); missing or disabled variants fall back to `(code, default)`.
- Three context shapes: SINGLE=`$source` / GROUP=list+stats / SCHEDULE=dataset injection.
- **Data access is structurally forbidden**: rendering runs under the `alert-render` profile — frozen read-only bindings, no SQL kit or datasource wired; template scripts can only transform `$ctx`/`$source`. Per-script timeout 2s; title ≤120, content ≤32KB truncated.
- Render switch `geelato.alert.engine.enabled` (testRender is a placeholder when off); `workbench-base-url` (default `/fms/exception-center`) for deep links.

## Delivery (geelato-message integration)

- `MessageTransport` → `POST {message-base-url}/message/enqueue` (default port 8086), sender=`fms-alert`, buss=`alert`; auth headers `X-System-Code` + `Authorization: Bearer`.
- Split per channel from `route_json` (`wework`→`weixin_work_group` mapping); `bizKey={recordId}:{batchNo}:{channel}`, idempotency backed by message-side idempotency_key.
- All-channel failures retry with backoff (`backoffBaseSeconds * 2^n`); over `maxRetry` → FAIL dead letter, manual resend only (`POST /api/alert/record/{id}/resend` replays the snapshot without re-rendering).

## Receivers & Silence

- `AlertReceiverSolver` supports the same six selector kinds as exceptions (USER/ROLE/DEPT/DEPT_ROLE/BIZ_FIELD/ORG_TREE_UP) + `fallback-role-code` (empty → FAIL logged); first delivery snapshots receiver_ids/names.
- Silence `platform_alert_silence`: dimension + time window; RESOLVE notifications can be exempted; `match-preview` rehearses matching.

## Configuration (`geelato.alert.engine.*`)

| Key | Default | Description |
| --- | --- | --- |
| `enabled` | false | Engine & render master switch |
| `message-base-url` | required when enabled | geelato-message base URL |
| `message-timeout-ms` | 10000 | enqueue timeout |
| `message-system-code` / `message-api-key` | — | enqueue auth |
| `fallback-role-code` | — | Receiver-solving fallback role |
| `tick-seconds` | 5 | Main tick |
| `deliver-batch-size` | 20 | Delivery batch |
| `max-retry` / `backoff-base-seconds` | 3 / 60 | Retry count / backoff base |
| `schedule-tick-seconds` | 30 | SCHEDULE poll cadence |
| `source-lookback-seconds` | 600 | Restart lookback watermark (no missed scans) |
| `source-batch-size` | 200 | Source scan batch |

## Current Status

- **Backend fully implemented** with unit tests: matching/aggregation/flush/rendering/delivery/silence/repeat/schedule/admin endpoints; rule YAML seeds (ALR-001~007) and template seed SQL ready.
- **Event-source generalization**: `EXCEPTION` is the only built-in source; others are design + skeleton (`AlertSources.KNOWN`).
- **Front-end not yet shipped**: rule builder / template studio / record pages exist as design prototypes (bea/page 12–14).
