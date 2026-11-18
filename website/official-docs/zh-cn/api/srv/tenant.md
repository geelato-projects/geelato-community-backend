---
title: API · 租户
sidebar_label: 租户
---
# 租户（tenant）

1 个 Controller，共 8 个端点。本域为设计态接口。

## TenantController ⚙ — `/tenant`

租户的查询、创建、更新、初始化、邀请与密码重置。

### queryTenantList 租户列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/tenant/list` |
| 参数 | `code`：query，string，租户编码，选填。<br />`companyName`：query，string，公司名称，选填。<br />`corpId`：query，string，企业 ID，选填。 |
| 返回 | `ApiResult<List<Tenant>>`：`data` 为符合条件的租户列表。 |

### queryTenantById 按 ID 查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/tenant/{id}` |
| 参数 | `id`：path，string，租户 ID，必填。 |
| 返回 | `ApiResult<Tenant>`：`data` 为单条租户信息；ID 为空或租户不存在时返回失败。 |

### queryTenantByCode 按编码查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/tenant/code/{code}` |
| 参数 | `code`：path，string，租户编码，必填。 |
| 返回 | `ApiResult<Tenant>`：`data` 为单条租户信息；编码为空或租户不存在时返回失败。 |

### initializeTenant 初始化租户

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/tenant/initialize/{code}` |
| 参数 | `code`：path，string，租户编码，必填。 |
| 返回 | `ApiResult`：`data` 为 Map：`tenant` 初始化后的租户信息、`userName` 初始管理员登录名、`password` 初始密码。初始化会创建租户站点、组织、用户、角色等；租户不存在或已删除（`delStatus=1`）时返回失败。 |

### updateTenant 更新租户

| 项 | 内容 |
| --- | --- |
| URL | `PUT /api/tenant/{id}` |
| 参数 | `id`：path，string，租户 ID，必填。<br />`tenant`：body，`Tenant` 实体，待更新的租户信息。 |
| 返回 | `ApiResult<Boolean>`：`data` 为 `true` 表示更新成功；租户不存在时返回失败。更新人信息取自当前登录用户。 |

### resetPassword 重置密码

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/tenant/reset/password/{id}` |
| 参数 | `id`：path，string，租户 ID，必填。 |
| 返回 | `ApiResult<Map<String,String>>`：`data` 为 Map：`userName` 管理员登录名、`password` 新生成的明文密码。重置该租户管理员用户的密码（无管理员时创建）。 |

### createTenant 创建租户

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/tenant/create` |
| 参数 | `tenant`：body，`Tenant` 实体；`companyName`（公司名称）与 `code`（租户编码）必填。 |
| 返回 | `ApiResult`：`data` 为创建后的租户信息；编码已存在时返回失败。创建人信息取自当前登录用户。 |

### inviteTenant 邀请租户

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/tenant/invite` |
| 参数 | `email`：query，string，邮箱地址，必填（`@RequestParam`，参数位于 URL query）。 |
| 返回 | `ApiResult`：`data` 为创建后的租户信息。仅创建包含邮箱地址的待完善租户（`companyName` 临时为"待完善"，租户编码由邮箱前缀加随机数生成，冲突时追加随机后缀）。 |
