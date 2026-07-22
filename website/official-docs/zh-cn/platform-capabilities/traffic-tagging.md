---
title: 流量染色
sidebar_label: 流量染色
---

# 流量染色

平台为每次请求生成并透出一个统一的流量标记 `trafficTag`，用于灰度标识、链路透传、日志关联和在线用户观察。

## 能力定位

流量染色提供统一的**标记能力**与**观察能力**：

- 为每次请求生成或恢复 `trafficTag`，并通过 Cookie 持久化、响应 Header 透出。
- 在服务端上下文（ThreadLocal、request attribute、MDC）中保留当前请求的 tag。
- 在在线用户明细中记录并展示最近一次活跃请求的 `trafficTag`。

需要特别说明的是：当前能力**仅做标记，不内置网关分流、服务转发或业务拦截逻辑**。基于 `trafficTag` 的灰度路由等能力，可在该标记基础上由上层（网关、业务服务）自行扩展。

## 标记语义

默认提供两档语义：

- `default` —— 默认流量。
- `gray` —— 灰度流量。

tag 为任意合法字符串，支持扩展为自定义值。

## 配置项

流量染色通过 `geelato.traffic.*` 配置，对应配置类 `TrafficColoringProperties`：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `geelato.traffic.enabled` | `true` | 是否启用流量染色。 |
| `geelato.traffic.default-tag` | `default` | 默认标记值。 |
| `geelato.traffic.gray-tag` | `gray` | 灰度标记值。 |
| `geelato.traffic.tag-cookie-name` | `gl_traffic_tag` | 承载 tag 的 Cookie 名称。 |
| `geelato.traffic.tag-header-name` | `X-Gl-Traffic-Tag` | 承载 tag 的响应/请求 Header 名称。 |
| `geelato.traffic.override-header-name` | `X-Gl-Traffic-Override` | 非 `product` 环境下手工覆盖 tag 的请求 Header。 |
| `geelato.traffic.override-query-name` | `glTrafficTag` | 非 `product` 环境下手工覆盖 tag 的查询参数。 |
| `geelato.traffic.request-attribute-key` | `gl.traffic.tag` | 当前请求 tag 写入 request attribute 的 key。 |
| `geelato.traffic.mdc-key` | `trafficTag` | 当前请求 tag 写入 MDC 的 key（可用于日志关联）。 |
| `geelato.traffic.gray-whitelist` | — | 灰度白名单，命中后标记为灰度（逗号分隔，见下文）。 |
| `geelato.traffic.gray-whitelist-location` | — | 灰度白名单资源路径（如 classpath 文件）。 |
| `geelato.traffic.cookie-path` | `/` | Cookie path。 |
| `geelato.traffic.cookie-domain` | — | Cookie domain。 |
| `geelato.traffic.cookie-max-age-seconds` | `2592000`（30 天） | Cookie maxAge，单位秒。 |
| `geelato.traffic.cookie-http-only` | `true` | 是否设置 HttpOnly。 |
| `geelato.traffic.cookie-secure` | `false` | 是否设置 Secure。 |
| `geelato.traffic.signing-enabled` | `true` | 是否对 Cookie 值签名。 |
| `geelato.traffic.signing-secret` | — | 签名密钥。留空时自动生成临时密钥（product 环境下会记录 ERROR 日志，建议显式配置）。 |

## 解析优先级

请求进入时，按以下优先级解析 `trafficTag`：

1. **手工覆盖**（仅非 `product` 环境）：通过 `X-Gl-Traffic-Override` 请求头或 `glTrafficTag` 查询参数显式指定。
2. **已签名 Cookie**：解析 `gl_traffic_tag` Cookie，校验签名通过后复用其携带的 tag。
3. **默认值**：回退到 `default-tag`，并签发新的 Cookie。

解析结果会同时写入 `TrafficTagContext`（ThreadLocal）、request attribute、MDC（日志上下文）、响应 Header，并按需通过 `Set-Cookie` 回写。

## Cookie 签名格式

签名开启时，Cookie 值采用 `v1.{tag}.{iat}.{sig}` 格式：

- `v1` —— 版本号。
- `{tag}` —— 流量标记。
- `{iat}` —— 签发时间戳。
- `{sig}` —— 基于 `signing-secret` 的 HmacSHA256 签名（Base64url 编码）。

验签失败时回退到默认值并覆盖写回，避免非法或篡改的 tag 进入链路与日志。

## 灰度白名单

除手工覆盖外，平台内置 `WhitelistTrafficTagStrategy` 策略：当已登录用户的 `loginName` 命中灰度白名单（来自 `gray-whitelist` 或 `gray-whitelist-location` 资源）时，该用户请求被标记为灰度（`gray-tag`）。该策略通过 `TrafficTagStrategy` SPI 注入，鉴权完成后基于真实用户信息重新评估 tag。

## 在线用户观察

`trafficTag` 会进入在线用户跟踪链路：

- 后端在 Redis 在线状态中记录用户最近一次活跃请求的 `trafficTag`，在线用户明细 `OnlineUserInfo` 包含 `trafficTag` 字段。
- 在线用户列表接口返回的每个用户项携带 `trafficTag`，前端可据此区分 `default`/`gray` 并提供过滤控件。

## 请求链路中的接入

流量染色接入 `DefaultSecurityInterceptor.preHandle` 的最起始位置，早于 `@IgnoreVerify` 早退逻辑，确保即便免鉴权接口也能获得统一的 `trafficTag`。染色过程出现异常时仅做降级处理，不影响正常鉴权流程。请求结束时在 `afterCompletion` 中清理 ThreadLocal 与 MDC，避免线程复用导致上下文串值。

## 运行表现

经过染色处理后，任意请求的响应通常可见：

```
Set-Cookie: gl_traffic_tag=v1.gray.1690000000.aBc...; Path=/; HttpOnly; Max-Age=2592000
X-Gl-Traffic-Tag: gray
```

在非 `product` 环境下显式指定时：

```
# 通过请求头
X-Gl-Traffic-Override: gray
# 或通过查询参数
GET /api/xxx?glTrafficTag=gray
```

响应会返回对应的 `X-Gl-Traffic-Tag: gray` 并同步回写 Cookie。
