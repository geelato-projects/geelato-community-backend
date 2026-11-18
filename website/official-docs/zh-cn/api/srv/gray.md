---
title: API · 灰度规则
sidebar_label: 灰度规则
---
# 灰度规则（gray）

1 个 Controller，共 2 个端点。本域非设计态接口，仅提供内存规则的查看与刷新。

规则 JSON 由运维直接写入 Redis（key 由 `geelato.traffic.gray.redis-key` 决定，默认 `geelato:gray:rules`），修改后调用 `/reload` 即时生效。

## GrayRuleController — `/gray-rules`

灰度规则管理接口：从 Redis 加载规则到内存并供运维核对。

### reload 重载规则

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/gray-rules/reload` |
| 参数 | 无 |
| 返回 | `ApiResult<Map<String,Object>>`：`data.count` 为重新加载后的内存规则条数。 |

### get 查看规则

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/gray-rules/get` |
| 参数 | 无 |
| 返回 | `ApiResult<List<GrayRule>>`：`data` 为当前内存中的灰度规则列表（`GrayRule`：`targetType`、`targetValue`、`priority`、`grayTag` 等字段）。 |
