# 流量染色

这篇文档说明平台如何为每次请求生成、解析并透出一个统一的流量标记 `trafficTag`，用于后续灰度、过滤、转发、拦截和在线用户观察。

当前规划里，默认先提供两档语义：

- `default`
- `gray`

但实现层允许继续扩展为任意合法字符串 tag。

## 能力目标

流量染色的目标包括：

- 为每次请求生成或恢复一个 `trafficTag`
- 通过 Cookie 持久化该标记
- 通过响应 Header 向客户端透出
- 在服务端上下文中保留当前请求的 tag
- 把在线用户最近一次活跃请求的 `trafficTag` 展示到管理端卡片上
- 支持按 `trafficTag` 过滤在线用户列表

## 约束规则

当前规则如下：

- 标记载体同时使用 Cookie 和 Header
- 非 `product` 环境允许手工指定 tag
- Cookie 值需要签名
- 验签失败后要回退并覆盖写回
- 本次先做“染色能力”，不在业务服务内部直接实现分流逻辑

## 推荐实现结构

### `TrafficColoringProperties`

建议新增统一配置类，集中管理：

- `enabled`
- `tagCookieName`
- `tagHeaderName`
- `overrideHeaderName`
- `overrideQueryName`
- `defaultTag`
- Cookie 的 path、domain、maxAge、httpOnly、secure
- `signingEnabled`
- `signingSecret`
- `mdcKey`
- `requestAttributeKey`

这样可以把协议细节和运行参数从代码里抽出来。

### `TrafficTagContext`

建议提供请求级上下文，保存当前线程内的 `trafficTag`，方便在：

- 拦截器
- 服务层
- 异步任务提交前

统一读取当前流量标记。

### `TrafficTagSigner`

负责：

- 生成签名
- 校验签名
- 校验 tag 合法性

推荐使用：

- `HmacSHA256`

并对 tag 做格式限制，避免非法值进入 Cookie、Header 或日志链路。

### `TrafficTagResolver`

负责在请求进入时完成解析、回退和签发。

推荐优先级：

1. 非 `product` 环境的手工覆盖值
2. 已存在且验签通过的 Cookie
3. 默认 tag，并签发新 Cookie

最终结果应同时写入：

- `TrafficTagContext`
- request attribute
- MDC
- 响应 Header
- 需要时写回 `Set-Cookie`

## 在鉴权链路中的位置

最合适的接入点是 `DefaultSecurityInterceptor`。

### `preHandle`

流量染色应放在 `preHandle` 的最开始执行，并且要早于：

- `@IgnoreVerify` 早退逻辑

这样即使是无需鉴权的接口，也能拿到统一的 `trafficTag`。

同时需要保证：

- 染色异常只降级，不阻断正常鉴权流程

### `afterCompletion`

请求结束时应清理：

- `TrafficTagContext`
- MDC 中的 `trafficTag`

避免线程复用导致上下文串值。

## 在线用户展示

流量染色不仅用于请求链路，还应进入在线用户观察能力。

### 后端

在线用户明细建议增加字段：

- `trafficTag`

并在 Redis 在线状态里记录“最近一次活跃请求”的 tag。

对应目标是：

- `/api/online/list` 的每个用户项都能返回 `trafficTag`

### 前端

在线用户卡片建议：

- 增加 `trafficTag` 展示
- 使用标签样式区分 `default` 和 `gray`
- 提供“全部 / default / gray”过滤控件

过滤只影响展示，不改变原有轮询和刷新机制。

## 接口表现

任意请求经过染色处理后，理想上都应能看到：

- `Set-Cookie: gl_traffic_tag=...`
- `X-Gl-Traffic-Tag: default|gray`

在非 `product` 环境下，如果显式指定：

- `X-Gl-Traffic-Override: gray`

或：

- `?glTrafficTag=gray`

则响应应返回：

- `X-Gl-Traffic-Tag: gray`

并同步写回对应 Cookie。

## 验证建议

### 静态检查

重点检查：

- 染色逻辑是否位于鉴权早退之前
- `afterCompletion` 是否做了上下文清理
- 验签失败是否回退到默认值并覆盖写回

### 在线用户链路

重点检查：

- 在线状态写入时是否带上 `trafficTag`
- `/api/online/list` 是否正确返回 `trafficTag`
- 前端卡片是否能展示和过滤 `trafficTag`

## 当前边界

这项能力的目标是先提供统一的“标记能力”和“观察能力”，而不是立刻在每个业务服务里实现具体灰度策略。

也就是说，当前阶段先解决：

- 如何生成 tag
- 如何透出 tag
- 如何存储 tag
- 如何在管理端观察 tag

至于基于该 tag 做网关分流、服务转发或业务拦截，可以在后续能力上继续扩展。
