---
title: API · 安全与权限
sidebar_label: 安全与权限
---
# 安全与权限（security）

19 个 Controller，共 125 个端点。标注 ⚙ 的为设计态接口。

## AccountController — `/sys/account`

用户自助注册账号。

### create 注册账号

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/sys/account` |
| 参数 | `user`：body，`User` 实体，注册用户的账号信息。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空，仅表示注册是否成功。 |

## AuthCodeController — `/code`

验证码生成与发送：按验证方式（短信/邮箱）生成 6 位验证码，发送消息并写入 Redis 缓存。

### generateUser 生成用户验证码

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/code/generate/user` |
| 参数 | body：`Map<String,Object>`，字段映射到 `AuthCodeParams`：`userId`（用户 ID，用户须已存在）、`action`（业务动作，如 `VALIDATEUSER`）、`validType`（验证方式：手机/邮箱）、`prefix`（手机前缀）、`validBox`（手机号或邮箱，`action` 为 `VALIDATEUSER` 时从用户资料回填）、`authCode`（验证码）。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；失败时 `msg` 带错误信息。 |

### generateAuth 生成鉴权验证码

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/code/generate/auth` |
| 参数 | body：`Map<String,Object>`，字段同上；直接按 `validType` 向 `validBox`（手机/邮箱）发送验证码并写入缓存。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；失败时 `msg` 带错误信息。 |

## EncodingController — `/encoding`

编码规则管理与编码值生成（单据号、序列号等）。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/encoding/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为编码规则（`Encoding`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/encoding/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Encoding>>`：编码规则列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/encoding/get/{id}` |
| 参数 | `id`：path，string，编码规则 ID。 |
| 返回 | `ApiResult<Encoding>`：编码规则详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/encoding/createOrUpdate` |
| 参数 | `form`：body，`Encoding` 实体；`id` 为空新建、非空更新。 |
| 返回 | `ApiResult<Encoding>`：保存后的编码规则；保存后同步刷新编码缓存。 |

### isDelete 删除规则

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/encoding/isDelete/{id}` |
| 参数 | `id`：path，string，编码规则 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除后同步清理编码缓存。 |

### generate 生成编码值

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/encoding/generate/{id}` |
| 参数 | `id`：path，string，编码规则 ID。<br/>`argumentParam`：query，`Map<String,Object>`，生成编码所需参数（使用系统编码 app 时需传 `appId`）。<br/>body：`Map<String,Object>`，可选，额外参数，键值优先级高于 query 参数。 |
| 返回 | `ApiResult<String>`：`data` 为按规则生成的新编码值。 |

## OnlineUserController — `/online`

在线用户查询（基于 Redis 记录的活跃名单）。

### list 在线用户列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/online/list` |
| 参数 | `windowMinutes`：query，int，可选，统计时间窗（分钟），缺省取系统默认值。<br/>`limit`：query，int，可选，返回条数上限，缺省取系统默认值。<br/>仅管理员可调用，非管理员返回失败。 |
| 返回 | `ApiResult<List<OnlineUserInfo>>`：`data` 为在线用户信息列表（用户 ID、登录名、姓名、租户、组织、最后活跃时间等）。 |

## OrgController — `/security/org`

组织机构管理：公司、部门等组织节点的维护与查询。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为组织（`Org`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Org>>`：组织列表。 |

### queryByParams 按 ID 批量查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/queryByParams` |
| 参数 | body：`Map<String,Object>`，需含 `ids`（逗号分隔的组织 ID）。 |
| 返回 | `ApiResult<List<Org>>`：命中的组织列表。 |

### queryTree 查询组织树

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/queryTree` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Map<String,Object>>>`：组织树结构数据。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/get/{id}` |
| 参数 | `id`：path，string，组织 ID。 |
| 返回 | `ApiResult<Org>`：组织详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/createOrUpdate` |
| 参数 | `form`：body，`Org` 实体；`id` 为空新建、非空更新。 |
| 返回 | `ApiResult<Org>`：保存后的组织。 |

### isDelete 删除组织

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/org/isDelete/{id}` |
| 参数 | `id`：path，string，组织 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### validate 编码唯一校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/validate` |
| 参数 | `form`：body，`Org` 实体；取 `code`、`tenantCode`、`id` 参与校验。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示编码可用。 |

### getCompany 查所属公司

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/getCompany/{id}` |
| 参数 | `id`：path，string，组织 ID。 |
| 返回 | `ApiResult<Org>`：该组织所属的公司节点。 |

### getDepartment 查所属部门

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/getDepartment/{id}` |
| 参数 | `id`：path，string，组织 ID。 |
| 返回 | `ApiResult<Org>`：该组织所属的部门节点。 |

### reloadProvider 刷新组织缓存

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/reloadProvider` |
| 参数 | 无 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；触发组织数据源刷新。 |

## OrgRoleMapController — `/security/org/role`

组织角色关系管理（角色挂组织）。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/role/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为组织角色关系（`OrgRoleMap`）分页数据。 |

### pageQueryOf 联查分页

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/role/pageQueryOf` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为视图 `page_query_platform_org_r_role` 的组织角色联查分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/role/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<OrgRoleMap>>`：组织角色关系列表。 |

### insert 批量挂载角色

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/role/insert` |
| 参数 | `form`：body，`OrgRoleMap` 实体；`orgId`、`roleId` 支持逗号分隔多个 ID，按组合去重后批量创建。 |
| 返回 | `ApiResult<List<OrgRoleMap>>`：本次新增的关系列表。 |

### switch 切换挂载

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/role/switch` |
| 参数 | `form`：body，`OrgRoleMap` 实体；`orgId`、`roleId` 支持逗号分隔多个 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；关系已存在则删除、不存在则创建。 |

### isDelete 删除关系

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/org/role/isDelete/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### queryRoles 查组织已挂角色

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/role/queryRoles/{orgId}` |
| 参数 | `orgId`：path，string，组织 ID。<br/>`tenantCode`：query，string，可选，租户编码。 |
| 返回 | `ApiResult<List<Role>>`：该组织已挂载的角色列表。 |

## OrgUserMapController — `/security/org/user`

组织用户关系管理（用户挂组织）。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/user/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为组织用户关系（`OrgUserMap`）分页数据。 |

### pageQueryOf 联查分页

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/user/pageQueryOf` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为视图 `page_query_platform_org_r_user` 的组织用户联查分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/user/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<OrgUserMap>>`：组织用户关系列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/org/user/get/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<OrgUserMap>`：组织用户关系详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/user/createOrUpdate` |
| 参数 | `form`：body，`OrgUserMap` 实体；`id` 为空新建、非空更新。 |
| 返回 | `ApiResult<OrgUserMap>`：保存后的关系。 |

### insert 批量挂载用户

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/org/user/insert` |
| 参数 | `form`：body，`OrgUserMap` 实体；`orgId`、`userId` 支持逗号分隔多个 ID，按组合去重后批量创建。 |
| 返回 | `ApiResult<List<OrgUserMap>>`：本次新增的关系列表。 |

### isDelete 删除关系

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/org/user/isDelete/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

## PermissionController ⚙ — `/security/permission`

权限项管理：设计态维护权限元数据（类型、编码、对象、动作等）。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/permission/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为权限（`Permission`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/permission/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Permission>>`：权限列表。 |

### queryByUser 查用户权限

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/permission/queryByUser` |
| 参数 | `userId`：query，string，可选，用户 ID，缺省取当前登录用户；其余过滤参数见[公共约定](../reference.md)。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：该用户经角色映射获得的权限列表（SQL 查询 `query_permission_by_role_user`）。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/permission/get/{id}` |
| 参数 | `id`：path，string，权限 ID。 |
| 返回 | `ApiResult<Permission>`：权限详情，附 `perDefault` 字段标记是否为该类型的默认权限。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/permission/createOrUpdate` |
| 参数 | `form`：body，`Permission` 实体；`id` 为空新建、非空更新。 |
| 返回 | `ApiResult<Permission>`：保存后的权限。 |

### isDelete 删除权限

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/permission/isDelete/{id}` |
| 参数 | `id`：path，string，权限 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### validate 编码唯一校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/permission/validate` |
| 参数 | `form`：body，`Permission` 实体；取 `code`、`tenantCode`、`id` 参与校验。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示编码可用。 |

### default 重置默认权限

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/permission/default/{type}/{object}` |
| 参数 | `type`：path，string，对象类型。<br/>`object`：path，string，对象标识。<br/>`parentObject`：query，string，可选，父对象。<br/>`appId`：query，string，可选，应用 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；按对象类型重置默认权限。 |

### shift 权限排序

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/permission/shift` |
| 参数 | body：`Map<String,Object>`，需含 `ids`（逗号分隔的权限 ID，按传入顺序重设排序号）。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### getEffective 查有效权限

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/permission/getEffective` |
| 参数 | `userId`：query，string，用户 ID。<br/>`entity`：query，string，实体标识。 |
| 返回 | `ApiResult`：`data` 为该用户在该实体上权重最高的有效权限（`cn.geelato.security.Permission`），无权限时为 `null`。 |

## RoleAppMapController ⚙ — `/security/role/app`

角色应用关系管理（角色授权应用）。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/app/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为角色应用关系（`RoleAppMap`）分页数据。 |

### pageQueryOf 联查分页

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/app/pageQueryOf` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为视图 `page_query_platform_role_r_app` 的角色应用联查分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/app/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<RoleAppMap>>`：角色应用关系列表。 |

### insert 批量授权应用

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/app/insert` |
| 参数 | `form`：body，`RoleAppMap` 实体；`roleId`、`appId` 支持逗号分隔多个 ID，按组合去重后批量创建。 |
| 返回 | `ApiResult<List<RoleAppMap>>`：本次新增的关系列表。 |

### isDelete 删除关系

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/role/app/isDelete/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### isDeletes 按应用角色删除

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/role/app/isDelete/{appId}/{roleId}` |
| 参数 | `appId`：path，string，应用 ID。<br/>`roleId`：path，string，角色 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除该角色与该应用间的全部授权关系。 |

## RoleController ⚙ — `/security/role`

角色管理。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为角色（`Role`）分页数据。 |

### pageQueryOf 联查分页

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/pageQueryOf` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为视图 `page_query_platform_role_app` 的角色应用联查分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Role>>`：角色列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/get/{id}` |
| 参数 | `id`：path，string，角色 ID。 |
| 返回 | `ApiResult<Role>`：角色详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/createOrUpdate` |
| 参数 | `form`：body，`Role` 实体；`id` 为空新建、非空更新。 |
| 返回 | `ApiResult<Role>`：保存后的角色。 |

### isDelete 删除角色

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/role/isDelete/{id}` |
| 参数 | `id`：path，string，角色 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### validate 编码唯一校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/validate` |
| 参数 | `form`：body，`Role` 实体；取 `code`、`appId`、`tenantCode`、`id` 参与校验。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示同一应用下角色编码可用。 |

## RolePermissionMapController ⚙ — `/security/role/permission`

角色权限关系管理：支持表级、列级授权的查询与维护。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/permission/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为角色权限关系（`RolePermissionMap`）分页数据。 |

### pageQueryOf 联查分页

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/permission/pageQueryOf` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为视图 `page_query_platform_role_r_permission` 的角色权限联查分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/permission/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<RolePermissionMap>>`：角色权限关系列表。 |

### insert 批量授权

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/permission/insert` |
| 参数 | `form`：body，`RolePermissionMap` 实体；`roleId`、`permissionId` 支持逗号分隔多个 ID，按组合去重后批量创建。 |
| 返回 | `ApiResult<List<RolePermissionMap>>`：本次新增的关系列表。 |

### switch 切换授权

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/permission/switch` |
| 参数 | `form`：body，`RolePermissionMap` 实体。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；关系已存在则删除、不存在则创建。 |

### isDelete 删除关系

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/role/permission/isDelete/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### queryTable 查表级权限

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/permission/queryTable/{type}/{object}` |
| 参数 | `type`：path，string，对象类型。<br/>`object`：path，string，对象标识。<br/>`appId`：query，string，可选，应用 ID。<br/>`tenantCode`：query，string，可选，租户编码。<br/>`parentObject`：query，string，可选，父对象。 |
| 返回 | `ApiResult<Map<String,JSONArray>>`：该角色对目标对象的表级权限集合。 |

### queryColumn 查列级权限

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/permission/queryColumn/{type}/{object}` |
| 参数 | `type`：path，string，对象类型。<br/>`object`：path，string，对象标识。<br/>`appId`：query，string，可选，应用 ID。<br/>`tenantCode`：query，string，可选，租户编码。<br/>`parentObject`：query，string，可选，父对象/关联对象。 |
| 返回 | `ApiResult<Map<String,JSONArray>>`：该角色对目标对象的列级权限集合。 |

### insertTable 表级授权

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/permission/insertTable` |
| 参数 | `form`：body，`RolePermissionMap` 实体。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### insertTableViewPermission 视图表级授权

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/permission/insertTable/view` |
| 参数 | `form`：body，`RolePermissionMap` 实体；可含 `permissionId`，存在时以其 `appId` 回填表单。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### insertColumn 列级授权

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/permission/insertColumn` |
| 参数 | body：`Map<String,Object>`，需含 `roleId`（角色 ID）、`columnId`（列 ID）、`rule`（权限规则）。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

## RoleTreeNodeMapController ⚙ — `/security/role/tree`

角色树节点关系管理（角色挂功能树节点）。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/tree/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为角色树节点关系（`RoleTreeNodeMap`）分页数据。 |

### pageQueryOf 联查分页

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/tree/pageQueryOf` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为视图 `page_query_platform_role_r_tree_node` 的角色树节点联查分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/tree/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<RoleTreeNodeMap>>`：角色树节点关系列表。 |

### insert 批量挂载节点

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/tree/insert` |
| 参数 | `form`：body，`RoleTreeNodeMap` 实体；`roleId`、`treeNodeId` 支持逗号分隔多个 ID，按组合去重后批量创建。 |
| 返回 | `ApiResult<List<RoleTreeNodeMap>>`：本次新增的关系列表。 |

### delete 取消挂载

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/tree/delete` |
| 参数 | `form`：body，`RoleTreeNodeMap` 实体；按 `roleId` + `treeNodeId` 定位。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除已存在的挂载关系。 |

### isDelete 删除关系

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/role/tree/isDelete/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### isDelete 按角色节点删除

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/role/tree/isDelete/{roleId}/{treeNodeId}` |
| 参数 | `roleId`：path，string，角色 ID。<br/>`treeNodeId`：path，string，树节点 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除该角色与该节点间的全部关系。 |

## RoleUserMapController — `/security/role/user`

角色用户关系管理（用户挂角色），变更后清理相关用户的权限缓存。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/user/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为角色用户关系（`RoleUserMap`）分页数据。 |

### pageQueryOf 联查分页

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/user/pageQueryOf` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为视图 `page_query_platform_role_r_user` 的角色用户联查分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/user/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<RoleUserMap>>`：角色用户关系列表。 |

### insert 批量挂载角色

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/user/insert` |
| 参数 | `form`：body，`RoleUserMap` 实体；`roleId`、`userId` 支持逗号分隔多个 ID，按组合去重后批量创建。 |
| 返回 | `ApiResult<List<RoleUserMap>>`：本次新增的关系列表；写后清理相关用户缓存。 |

### switch 切换挂载

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/role/user/switch` |
| 参数 | `form`：body，`RoleUserMap` 实体。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；关系已存在则删除、不存在则创建；写后清理相关用户缓存。 |

### isDelete 删除关系

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/role/user/isDelete/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除后清理相关用户缓存。 |

### queryRoles 查用户已挂角色

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/role/user/queryRoles/{userId}` |
| 参数 | `userId`：path，string，用户 ID。<br/>`appId`：query，string，可选，应用 ID。<br/>`tenantCode`：query，string，可选，租户编码。 |
| 返回 | `ApiResult<List<Role>>`：该用户拥有的角色列表。 |

## SystemController — `/sys`

系统级接口：登录用户信息、登出与密码重置。

### getRoleListByPage 全量角色列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sys/getRoleListByPage` |
| 参数 | 无 |
| 返回 | `ApiPagedResult`：`data` 为全量角色（Map）列表；`page` 固定为 1，`total`/`size` 为列表长度；免鉴权，响应为 UTF-8 JSON。 |

### getUserInfo 当前用户信息

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sys/getUserInfo` |
| 参数 | 无 |
| 返回 | `ApiResult<LoginResult>`：当前登录用户信息（含 `token`、所属公司名称等；`roles` 置空）。 |

### logout 登出

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sys/logout` |
| 参数 | 无 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### resetPassword 重置密码

| 项 | 内容 |
| --- | --- |
| URL | `GET\|POST /api/sys/resetPassword` |
| 参数 | `passwordLength`：query，int，可选，生成密码长度，默认 8，最大 32。 |
| 返回 | `ApiResult<String>`：`data` 为生成的新明文密码（同时更新当前登录用户的密码）。 |

## UserController — `/security/user`

用户管理：增删改查、密码重置与推送、组织归属、常用联系人标记。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为用户（`User`）分页数据；返回前清除密码类字段，并标记 `stocked`（是否为当前用户的常用联系人）。 |

### pageQueryStock 常用联系人查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/pageQueryStock` |
| 参数 | `stocked`：query，boolean，可选，是否标记常用联系人。<br/>`stockSearch`：query，boolean，可选，是否仅查当前用户的常用联系人。<br/>其余分页与过滤参数见[公共约定](../reference.md)。 |
| 返回 | `ApiPagedResult`：`data` 为用户分页数据；`stockSearch` 为真时按当前用户的常用联系人过滤。 |

### pageQueryOf 查角色勾选矩阵

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/pageQueryOf` |
| 参数 | body：分页与过滤参数见[公共约定](../reference.md)；另支持 `appId`（应用 ID，可选）、`tenantCode`（租户编码，可选）。 |
| 返回 | `ApiPagedResult`：`data` 为用户分页数据并联查应用/租户下的角色；无结果时为用户列表，有结果时为 `{role: 角色列表, table: 用户行列表（每行含用户字段及各角色 ID 的勾选布尔值）}` 结构。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<User>>`：用户列表（已清除密码类字段）。 |

### queryOrgUserByUserId 查用户组织关系

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/queryOrgUserByUserId` |
| 参数 | body：`Map<String,Object>`，需含 `userId`（用户 ID，支持逗号分隔多个）。 |
| 返回 | `ApiResult<List<OrgUserMap>>`：用户的组织关系列表。 |

### queryByParams 按 ID 批量查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/queryByParams` |
| 参数 | body：`Map<String,Object>`，需含 `ids`（逗号分隔的用户 ID）。 |
| 返回 | `ApiResult<List<User>>`：命中的用户列表（已清除密码类字段）。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/get/{id}` |
| 参数 | `id`：path，string，用户 ID。 |
| 返回 | `ApiResult<User>`：用户详情（已清除密码类字段）。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/createOrUpdate` |
| 参数 | `form`：body，`User` 实体；新建时自动生成 8 位随机密码，更新时保留原密码。 |
| 返回 | `ApiResult<User>`：保存后的用户（不含密码与盐；新建时 `plainPassword` 为生成的初始密码）。 |

### insert 新建用户

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/insert` |
| 参数 | `form`：body，`User` 实体；`loginName` 必填且不可重复。 |
| 返回 | `ApiResult<User>`：新建用户（`plainPassword` 为自动生成的 8 位初始密码；不含密码与盐）。 |

### resetPassword 重置用户密码

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/resetPwd/{id}` |
| 参数 | `id`：path，string，用户 ID。 |
| 返回 | `ApiResult<String>`：`data` 为新生成的 8 位明文密码。 |

### resetPush 重置并推送密码

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/resetPush/{id}` |
| 参数 | `id`：path，string，用户 ID。<br/>`type`：query，string，可选，推送方式（短信/邮件）。 |
| 返回 | `ApiResult<String>`：`data` 为新生成的明文密码；推送失败时返回失败结果。 |

### sendMessage 推送密码消息

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/sendMessage/{id}` |
| 参数 | `id`：path，string，用户 ID。<br/>`type`：query，string，发送方式（短信/邮件），缺失时报错。<br/>`message`：query，string，作为新密码推送的内容，缺失时报错。 |
| 返回 | `ApiResult`：发送结果；成功时 `data` 为空，失败时 `msg` 带原因。 |

### isDelete 删除用户

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/user/isDelete/{id}` |
| 参数 | `id`：path，string，用户 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### validate 字段唯一校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/validate/{type}` |
| 参数 | `type`：path，string，校验字段：`loginName`、`enName`、`jobNumber`、`mobilePhone`（其他取值直接返回 `true`）。<br/>`form`：body，`User` 实体；取对应字段与 `tenantCode`、`id` 参与校验。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示可用。 |

### getCompany 查所属公司

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/getCompany/{id}` |
| 参数 | `id`：path，string，用户 ID。 |
| 返回 | `ApiResult<Org>`：用户所属的公司节点；用户未设置组织时报错。 |

### resetCompany 重算公司归属

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/resetCompany` |
| 参数 | 无 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；遍历全部用户按组织重算并保存公司（`buId`）与部门（`deptId`）。 |

### reloadProvider 刷新用户缓存

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/reloadProvider` |
| 参数 | 无 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；触发用户数据源刷新。 |

## UserDelegateMapController — `/security/user/delegate`

用户委托（助理）关系管理。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/delegate/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为委托关系（`UserDelegateMap`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/delegate/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<UserDelegateMap>>`：委托关系列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/delegate/get/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<UserDelegateMap>`：委托关系详情。 |

### insert 新建委托关系

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/delegate/insert` |
| 参数 | `form`：body，`UserDelegateMap` 实体；`userId`（委托人）、`delegateUserId`（代理人）、`scope`（业务范围，可多值，为空表示全部业务）；重复关系跳过。 |
| 返回 | `ApiResult<List<UserDelegateMap>>`：本次创建的关系列表。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/delegate/createOrUpdate` |
| 参数 | `form`：body，`UserDelegateMap` 实体。 |
| 返回 | `ApiResult<List<UserDelegateMap>>`：保存委托关系（当前实现与新建一致）。 |

### isDelete 删除关系

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/user/delegate/isDelete/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### validate 唯一性校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/delegate/validate/{type}` |
| 参数 | `type`：path，string，校验类型；`duplicate` 时校验同委托人 + 代理人 + 业务范围是否重复，其他取值直接返回 `true`。<br/>`form`：body，`UserDelegateMap` 实体。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示可用。 |

## UserOrgRoleMapController — `/security/user/orgRole`

用户组织角色关系管理（用户在组织下挂角色），变更后清理相关用户的权限缓存。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/orgRole/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为用户组织角色关系（`UserOrgRoleMap`）分页数据。 |

### pageQueryOf 联查分页

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/orgRole/pageQueryOf` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为视图 `page_query_platform_user_r_org_role` 的用户组织角色联查分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/orgRole/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<UserOrgRoleMap>>`：用户组织角色关系列表。 |

### insert 批量挂载角色

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/orgRole/insert` |
| 参数 | `form`：body，`UserOrgRoleMap` 实体；`userId`、`orgId`、`roleId` 支持逗号分隔多个 ID，按组合去重后批量创建。 |
| 返回 | `ApiResult<List<UserOrgRoleMap>>`：本次新增的关系列表；写后清理相关用户缓存。 |

### switch 切换挂载

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/orgRole/switch` |
| 参数 | `form`：body，`UserOrgRoleMap` 实体。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；关系已存在则删除、不存在则创建；写后清理相关用户缓存。 |

### isDelete 删除关系

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/user/orgRole/isDelete/{id}` |
| 参数 | `id`：path，string，关系 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除后清理相关用户缓存。 |

### queryOrgRoles 查用户组织角色

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/orgRole/queryOrgRoles/{userId}` |
| 参数 | `userId`：path，string，用户 ID。<br/>`tenantCode`：query，string，可选，租户编码。 |
| 返回 | `ApiResult<List<UserOrgRoleMap>>`：该用户的组织角色关系列表。 |

## UserStockMapController — `/security/user/stock`

常用联系人（收藏用户）管理，操作对象为当前登录用户。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/stock/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为常用联系人关系（`UserStockMap`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/stock/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<UserStockMap>>`：常用联系人关系列表。 |

### total 统计数量

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/security/user/stock/total` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<Integer>`：当前登录用户的常用联系人数量（附加 `userId` 条件后统计）。 |

### create 新增常用联系人

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/user/stock/create` |
| 参数 | `form`：body，`UserStockMap` 实体；`stockId` 为被收藏用户 ID，支持逗号分隔多个；以当前登录用户为 `userId`，先删除已存在关系再创建。 |
| 返回 | `ApiResult<List<UserStockMap>>`：本次创建的关系列表。 |

### remove 移除常用联系人

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/security/user/stock/remove/{stockId}` |
| 参数 | `stockId`：path，string，被移除用户的 ID，支持逗号分隔多个。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

## WeChatController — `/security/wechat`

微信账号绑定与解绑。

### signOut 解绑微信

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/wechat/signOut/{id}` |
| 参数 | `id`：path，string，用户 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；清空用户的 `weixinUnionId`。 |

### signIn 绑定微信

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/security/wechat/signIn/{id}` |
| 参数 | `id`：path，string，用户 ID。<br/>`code`：query，string，微信授权 code。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；用 `code` 换取 `unionId` 并绑定，获取失败或已被其他用户占用时返回失败。 |
