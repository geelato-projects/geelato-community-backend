---
title: Unified Callback & Consumer Bus
sidebar_label: Callback & Consumer Bus
---

# Unified Callback & Consumer Bus

The unified entry for third-party callbacks and its internal dispatch are handled by two cooperating modules:

```text
Third-party system
   │  POST /cxf/unified/callback/{token}
   ▼
geelato-dyn-unified-api (north-bound entry)
   │  ① token → bizTag resolution (whitelist match)
   │  ② raw request persisted to unified_callback_capture
   │  ③ processor dispatch via CallbackProcessorDispatcher
   ▼
UnifiedConsumerCallbackProcessor (enabled by default)
   │  Camel vm: in-process routing
   ▼
geelato-dyn-unified-consumer-main (consumer bus)
   │  resolve registry by bizCode → recipientList(${header.targetVm})
   ▼
Each consumer module's dispatchVm route (business handling)
```

## North-bound Entry

Base path `/cxf/unified/callback`:

| Endpoint | Notes |
|---|---|
| `POST /callback` | Token-less generic entry; bizTag falls back to URL-based matching (default `default`) |
| `POST /callback/{callbackToken}` | Token-based precise routing: whitelist match first, then map to the actual bizTag |
| `GET /callback/{callbackToken}` | Token only; the body is persisted as an empty string while Query / Header / URL are still captured |

Constraints and response:

- `GET /callback` (without token) is not supported;
- An unmatched token returns 400; a **duplicate token fails hard**, silent overrides are never allowed;
- Uniform response: `{ "requestId": <persisted id>, "bizTag": <matched business tag> }`.

Call example:

```bash
curl -X POST "http://localhost:8181/cxf/unified/callback/o4c8n1t6r?source=test" \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"A2001","status":"IN_TRANSIT"}'
```

## Persistence and Troubleshooting

| Table | Contents |
|---|---|
| `unified_callback_capture` | Raw capture: URL / headers / body / params / biz_tag / tenant_code |
| `unified_callback_processed` | Processing result: capture_id, processor_name, process_status, error_message |

Conventions: snowflake primary keys, `tenant_code` defaults to `geelato`, DDL in the module's `src/main/resources/sql/schema.sql`. Joining the two tables shows the full chain (capture → processed); troubleshooting SQL is in the repo's `geelato-dyn-unified-api/UNIFIED_CALLBACK_GUIDE.md`.

## Processor SPI

`AbstractCallbackProcessor` (`processorName()` / `supports(bizTag)` / `doHealthCheck()` / `doProcess()`), tried in order by `CallbackProcessorDispatcher`. Built-in processors:

| Processor | Default | Behavior |
|---|---|---|
| `UnifiedConsumerCallbackProcessor` | enabled | Hands over to the unified consumer bus (Camel `vm:` dispatch) |
| `DbCallbackProcessor` | disabled | Direct database write |
| `RabbitMqCallbackProcessor` | disabled | Publishes to a RabbitMQ exchange |

Switches are `enabled` properties in unified-api's `spring-jdbc.xml`.

## The UnifiedConsumerBinding SPI

A consumer module modifies nothing in unified-api or consumer-main — it **publishes an OSGi service in its own module** and is discovered dynamically:

| Field | Notes |
|---|---|
| `module` | Module name |
| `bizCode` | Business code (dispatch routing key) |
| `callbackToken` | Callback token (lowercase alphanumeric, 9 characters recommended) |
| `dispatchVm` | This module's Camel `vm:` dispatch endpoint |
| `healthVm` | This module's health self-check endpoint |
| `enabled` | Whether active |

```xml
<bean id="xxxBinding" class="cn.geelato.dyn.xxx.binding.XxxUnifiedConsumerBinding"/>
<service ref="xxxBinding" interface="cn.geelato.dyn.unified.api.service.UnifiedConsumerBinding"/>
```

unified-api collects all bindings dynamically with `<reference-list availability="optional">`; consumer-main collects them the same way for routing and self-checks. Duplicate tokens fail at registration time.

## The Consumer Bus (consumer-main)

- Exposes the OSGi service `UnifiedConsumerService.consume(bizCode, payload)`, which unified-api calls internally via `<reference>`;
- Camel main route `vm:geelato-dyn-unified-consumer-main-dispatch`: resolves the binding by bizCode from the registry and dispatches with `recipientList(${header.targetVm})` to the target consumer's `dispatchVm`;
- `bizCode=_health` is reserved: it runs the `healthVm` self-check across all bindings (paired with a 30s scheduled health route);
- Built-in HTTP query endpoint: `GET http://<host>:9080/geelato-dyn-unified-consumer-main/branch-logic`, returning the currently collected `module` / `bizCode` / `callbackToken` / `targetVm` / `healthVm` — useful for verifying that registration took effect.

## Adding a Consumer

1. Generate a new lowercase alphanumeric token (9 characters recommended);
2. Implement and publish a `UnifiedConsumerBinding` in the consumer module (declare bizCode / callbackToken / dispatchVm / healthVm);
3. Implement a Camel routeBuilder in the module that consumes its own `vm:` endpoint;
4. Publish the module and install the feature — the bus picks up routing and health self-checks automatically, with zero changes to unified-api / consumer-main.

## Recommended Reading

- [Developing a New Module](develop-module.md) — the full scaffold workflow for consumer modules
- [Built-in Capabilities](capabilities.md) — how health self-checks surface in aggregation
