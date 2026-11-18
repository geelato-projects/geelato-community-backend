---
title: API · ORM 钩子
sidebar_label: ORM 钩子
---
# ORM 钩子（ormhook）

1 个 Controller，共 7 个端点。ORM 实体钩子是纯附加特性：平台内任何实体的 insert/update/delete 事务提交后，向钩子地址 POST 完整载荷 JSON，下游按 `entityName`/`eventType` 分发处理；触发与执行全部异步，经执行发件箱（`platform_orm_hook_log`）重试/死信，失败永不上抛。本域涵盖钩子配置 CRUD、执行发件箱监控与死信重放。

## OrmHookController — `/ormhook`

钩子配置 CRUD、执行发件箱监控与死信重放；控制器不写 try-catch，异常上抛由全局异常处理器统一转 ApiResult。

### pageQuery 钩子配置分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ormhook/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：钩子配置分页列表（签名声明为原始 `ApiPagedResult`）。 |

### query 钩子配置列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ormhook/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<OrmHook>>`：全量钩子配置数组（设计器实体配置页契约：GET 返回全量数组）。 |

### get 钩子配置详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ormhook/get/{id}` |
| 参数 | `id`：path，string，必填，钩子配置 ID。 |
| 返回 | `ApiResult<OrmHook>`：单条钩子配置；不存在时失败。 |

### createOrUpdate 新建或更新钩子

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ormhook/createOrUpdate` |
| 参数 | `form`：body，OrmHook 对象，必填，常用字段：钩子名称 `title`（必填）、下游接收地址 `httpUrl`（必填，须为合法 http/https URL）、是否启用 `enableStatus`（0 禁用 1 启用，默认 1）、`id`（空=新建，非空=更新）。 |
| 返回 | `ApiResult<OrmHook>`：写入时硬校验（标题/地址缺失或地址格式非法直接拒绝并指明原因），保存后即时生效（刷新钩子规则快照），返回保存后的配置。 |

### isDelete 删除钩子配置

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/ormhook/isDelete/{id}` |
| 参数 | `id`：path，string，必填，钩子配置 ID。 |
| 返回 | `ApiResult<Boolean>`：先置禁用再逻辑删除，删除后即时生效，成功返回 `true`。 |

### logPageQuery 发件箱分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ormhook/log/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md)（执行记录可按 `status`/`entityName`/`hookId` 过滤） |
| 返回 | `ApiPagedResult`：执行发件箱（`platform_orm_hook_log`）分页列表（签名声明为原始 `ApiPagedResult`）。 |

### replay 死信重放

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ormhook/log/replay/{id}` |
| 参数 | `id`：path，string，必填，发件箱记录 ID。 |
| 返回 | `ApiResult<Boolean>`：将 dead 记录重置为 ready（重试次数清零、错误清空并登记内存定时立即拾取）；记录不存在或非 dead 状态返回 `false`。 |
