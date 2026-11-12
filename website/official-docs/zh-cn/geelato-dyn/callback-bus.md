---
title: 统一回调与消费总线
sidebar_label: 回调与消费总线
---

# 统一回调与消费总线

第三方回调的统一入口与内部分发由两个模块配合完成：

```text
第三方系统
   │  POST /cxf/unified/callback/{token}
   ▼
geelato-dyn-unified-api（北向入口）
   │  ① token → bizTag 解析（白名单匹配）
   │  ② 原始请求落库 unified_callback_capture
   │  ③ 处理器分发 CallbackProcessorDispatcher
   ▼
UnifiedConsumerCallbackProcessor（默认启用）
   │  Camel vm: 进程内路由
   ▼
geelato-dyn-unified-consumer-main（消费总线）
   │  按 bizCode 查注册表 → recipientList(${header.targetVm})
   ▼
各 consumer 模块的 dispatchVm 路由（业务处理）
```

## 北向入口

基路径 `/cxf/unified/callback`：

| 端点 | 说明 |
|---|---|
| `POST /callback` | 不带 token 的通用入口，按 URL 规则兜底匹配 bizTag（默认 `default`） |
| `POST /callback/{callbackToken}` | 带 token 精确路由，先白名单匹配再映射到实际 bizTag |
| `GET /callback/{callbackToken}` | 仅支持带 token；请求体固定空字符串入库，Query / Header / URL 仍被捕获 |

约束与返回：

- `GET /callback`（不带 token）不支持；
- token 无匹配返回 400；出现**重复 token 直接报错**，不允许静默覆盖；
- 统一返回 `{ "requestId": <落库主键>, "bizTag": <命中业务标识> }`。

调用示例：

```bash
curl -X POST "http://localhost:8181/cxf/unified/callback/o4c8n1t6r?source=test" \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"A2001","status":"IN_TRANSIT"}'
```

## 落库与排障

| 表 | 内容 |
|---|---|
| `unified_callback_capture` | 原始捕获：URL / headers / body / params / biz_tag / tenant_code |
| `unified_callback_processed` | 处理结果：capture_id、processor_name、process_status、error_message |

约定：主键为雪花 ID，`tenant_code` 默认 `geelato`，建表脚本见模块 `src/main/resources/sql/schema.sql`。两表关联查询可一次看全链路（capture → processed），排障 SQL 见仓库 `geelato-dyn-unified-api/UNIFIED_CALLBACK_GUIDE.md`。

## 处理器 SPI

`AbstractCallbackProcessor`（`processorName()` / `supports(bizTag)` / `doHealthCheck()` / `doProcess()`），由 `CallbackProcessorDispatcher` 按顺序尝试。内置处理器：

| 处理器 | 默认状态 | 行为 |
|---|---|---|
| `UnifiedConsumerCallbackProcessor` | 启用 | 交给统一消费端总线（Camel `vm:` 分发） |
| `DbCallbackProcessor` | 关闭 | 数据库直写 |
| `RabbitMqCallbackProcessor` | 关闭 | 发布到 RabbitMQ exchange |

开关在 unified-api 的 `spring-jdbc.xml` 中以 `enabled` 属性控制。

## UnifiedConsumerBinding SPI

consumer 模块不修改 unified-api 与 consumer-main 的任何配置，**在自己模块内发布 OSGi 服务即可被动态发现**：

| 字段 | 说明 |
|---|---|
| `module` | 模块名 |
| `bizCode` | 业务码（分发路由 key） |
| `callbackToken` | 回调 token（小写字母数字，建议 9 位） |
| `dispatchVm` | 本模块的 Camel `vm:` 分发端点 |
| `healthVm` | 本模块的健康自检端点 |
| `enabled` | 是否启用 |

```xml
<bean id="xxxBinding" class="cn.geelato.dyn.xxx.binding.XxxUnifiedConsumerBinding"/>
<service ref="xxxBinding" interface="cn.geelato.dyn.unified.api.service.UnifiedConsumerBinding"/>
```

unified-api 以 `<reference-list availability="optional">` 动态收集所有 binding；consumer-main 同样动态收集用于路由与自检。token 重复在注册时即报错。

## 消费总线（consumer-main）

- 对外提供 OSGi 服务 `UnifiedConsumerService.consume(bizCode, payload)`，unified-api 内部经 `<reference>` 调用；
- Camel 主路由 `vm:geelato-dyn-unified-consumer-main-dispatch`：按 bizCode 从注册表解析 binding，`recipientList(${header.targetVm})` 分发到目标 consumer 的 `dispatchVm`；
- `bizCode=_health` 是保留值：走全量 binding 的 `healthVm` 自检（配合 30s 定时健康路由）；
- 自带 HTTP 查询端点：`GET http://<host>:9080/geelato-dyn-unified-consumer-main/branch-logic`，返回当前动态收集到的 `module` / `bizCode` / `callbackToken` / `targetVm` / `healthVm`，用于验证注册是否生效。

## 新增一个消费者

1. 生成新的小写字母数字 token（建议 9 位）；
2. 在 consumer 模块实现并发布 `UnifiedConsumerBinding`（声明 bizCode / callbackToken / dispatchVm / healthVm）；
3. 在模块内实现 Camel routeBuilder 消费自己的 `vm:` 端点；
4. 发布模块并安装 feature，总线上自动纳入路由与健康自检——不改动 unified-api / consumer-main 任何代码。

## 推荐继续阅读

- [新模块开发](develop-module.md) —— consumer 模块的完整脚手架流程
- [内置能力](capabilities.md) —— 健康自检如何被聚合暴露
