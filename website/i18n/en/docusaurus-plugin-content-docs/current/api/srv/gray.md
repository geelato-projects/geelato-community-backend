---
title: API · Gray Release Rules
sidebar_label: Gray Release Rules
---
# Gray Release Rules (gray)

1 controller, 2 endpoints in total. This domain is not a design-time API; it only provides view and refresh of the in-memory rules.

The rule JSON is written to Redis directly by operations (the key is determined by `geelato.traffic.gray.redis-key`, default `geelato:gray:rules`). After changing it, call `/reload` to take effect immediately.

## GrayRuleController — `/gray-rules`

Gray rule management API: loads rules from Redis into memory and lets you inspect them.

### reload Reload rules

| Item | Content |
| --- | --- |
| URL | `POST /api/gray-rules/reload` |
| Params | None |
| Returns | `ApiResult<Map<String,Object>>`: `data.count` is the number of in-memory rules after reloading. |

### get View rules

| Item | Content |
| --- | --- |
| URL | `GET /api/gray-rules/get` |
| Params | None |
| Returns | `ApiResult<List<GrayRule>>`: `data` is the list of current in-memory gray rules (`GrayRule`: `targetType`, `targetValue`, `priority`, `grayTag`, etc.). |
