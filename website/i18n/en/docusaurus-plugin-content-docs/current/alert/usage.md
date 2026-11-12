---
id: usage
title: Business Alert Rules and Usage
sidebar_label: Rules and Usage
---

# Business Alert Rules and Usage

How to configure alert rules, templates and silence, and which REST APIs are available. Overview: [Business Alert](overview.md).

## Enabling Steps

1. Deploy geelato-message (default port 8086) and verify the enqueue contract (see the message integration guide, appendix C);
2. Add the `geelato-alert` dependency and configure:

```properties
geelato.alert.engine.enabled=true
geelato.alert.engine.message-base-url=http://127.0.0.1:8086
geelato.alert.engine.message-system-code=xxx
geelato.alert.engine.message-api-key=xxx
```

3. Enable the exception engine (`geelato.exception.engine.enabled=true`) to produce EXCEPTION events;
4. Import or create alert rules and enable them.

## Rule Configuration

Five elements: **event-source match (match_json) → trigger (EVENT/SCHEDULE/BOTH) → aggregation (INDEPENDENT/AGGREGATE + windows) → receivers (receiver_json) → channel routing (route_json)**.

YAML round-trip like exceptions: `POST /api/alert/rule/import-yaml`, `GET /{id}/export-yaml`; seed rules `ALR-001~ALR-007`.

EXCEPTION-source match_json:

```json
{ "source": "EXCEPTION", "events": ["OPEN", "ESCALATED"], "ruleCodes": ["EXC-BL-015"] }
```

`events` matches `platform_exception` lifecycle event types; when `ruleCodes` is declared, only instances whose source rule code is in the list alert (otherwise all).

## Templates & Rendering

- `platform_alert_template`: `templateCode` + channel variants with `titleScript` / `contentScript` (js) and sample contexts.
- Scripts read `$ctx` / `$source` only (no data access by construction) and produce title/content; disabled or missing variants fall back to `(code, default)`.
- Debugging: `POST /api/alert/template/test-render` (real render; placeholder when the engine is off), `GET /context-schema`, `GET /context-from-record/{recordId}` (use a real record's snapshot as a sample).

## Silence

`platform_alert_silence` suppresses delivery by dimension + time window (RESOLVE notifications can be exempted). `POST /api/alert/silence/match-preview` rehearses "which active alerts would be silenced" without persisting.

## REST API

:::note
Endpoints are declared via `@ApiRestController`; with the `/api` prefix the real path carries `/api`.
:::

**Rules `/api/alert/rule`**

| Endpoint | Description |
| --- | --- |
| `POST /pageQuery`, `GET /{id}`, `POST /save` | Page / detail / save |
| `POST /{id}/enable`, `/disable`, `/delete` | Enable/disable/delete |
| `POST /import-yaml`, `GET /{id}/export-yaml` | YAML round-trip |
| `POST /{id}/trial-run` | Trial run |
| `GET /{id}/records` | Rule alert records |

**Templates `/api/alert/template`**: `POST /pageQuery`, `GET /{code}`, `POST /save`, `POST /{code}/delete`, `POST /test-render`, `GET /context-schema`, `GET /context-from-record/{recordId}`

**Records `/api/alert/record`**: `POST /pageQuery`, `GET /{id}`, `GET /{id}/messages` (message-side messages), `POST /{id}/resend` (snapshot resend), `POST /{id}/silence-from`

**Silence `/api/alert/silence`**: `POST /pageQuery`, `POST /save`, `POST /{id}/revoke`, `POST /match-preview`

**Admin `/api/alert`**: `POST /alert/admin/trigger`, `POST /alert/admin/flush`, `GET /alert/admin/stats`, `POST /alert/event` (event ingestion)

## Operations Notes

- **No missed scans on restart**: source scans look back `source-lookback-seconds` (default 600s).
- **Delivery troubleshooting**: record status `PARTIAL/FAIL` plus message-side messages (`GET /{id}/messages`); FAIL dead letters resend via `resend` (no re-render).
- **Stuck groups**: GROUP groups are bound by `group_wait_seconds` and `group_interval_seconds`; flush manually with `POST /alert/admin/flush`.
- **Daily cap**: deliveries over `daily_limit` are dropped with a log and recover in the next window.
