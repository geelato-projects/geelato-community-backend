---
title: 开放管理 API（集成方自助管理）
sidebar_label: 开放管理 API
---

# 开放管理 API（集成方自助管理）

统一认证中心在集中管理所有接入系统（角色 / 菜单 / 权限点 / 授权 / 用户 / 组织 / 会话）的同时，向每个集成方开放一套 **HTTP 管理 API（`/api/open/v1/**`）**：集成方凭自己的机器令牌调用，即可自助管理「属于自己的」数据，并据此实现自己的管理 UI。

> 前置阅读：调用开放 API 需要先换取 client_credentials 机器令牌，详见 [机器对机器接入指南](client-credentials-integration.md)。用户令牌（授权码 / 密码模式签发）**不可**调用开放 API。

## 核心特性

- **鉴权**：OAuth2 `client_credentials` 机器令牌（标准协议，无 Sa-Token 依赖、无共享会话）。
- **隔离**：调用方可操作的数据范围由其 `oauth_client` 记录自动绑定，**无法越权指定**。
- **审计**：所有写操作以 `CLIENT` 身份记入审计哈希链；权限变更保持 Redis 通知（`geelato:permission:change`）与版本号语义不变，子系统镜像体系无需额外处理。

## 身份与隔离模型

| 资源 | 归属维度 | 调用方可见 / 可写范围 |
| --- | --- | --- |
| 角色 / 菜单 / 权限点 / 授权关系 | `app_code`（= 调用方 `oauth_client.system_code`） | 查询：本系统 + `__global__`（全局只读）；写：仅本系统 |
| 用户 / 组织 | `tenant_code`（租户级共享） | 本租户内可读写（SSO 前提：一个用户可访问同租户多个系统） |
| 会话 | 按授权关系 / 登录来源 | 见 [会话管理](#会话管理) |

三条核心规则：

1. **appCode 不可指定**：请求体中的 `appCode` 字段若填写，必须等于调用方自身的 `system_code`，否则返回 `code=403`；不填则自动按调用方赋值。角色的归属 appCode 不允许变更。
2. **`__global__` 数据只读**：全局角色 / 菜单 / 权限点对所有集成方可见（查询、分配给自己的角色可用），但不能经开放 API 修改或删除——那是认证中心管理员的职责。
3. **tenantCode 不可指定**：用户与组织的租户由调用方客户端记录绑定（`oauth_client.tenant_code`）。

## 获取访问令牌

```bash
curl -X POST "https://<认证中心>/oauth2/client_token" \
  -d "grant_type=client_credentials" \
  -d "client_id=<your_client_id>" \
  -d "client_secret=<your_client_secret>"
```

```json
{
  "access_token": "xxx",
  "client_token": "xxx",
  "token_type": "bearer",
  "expires_in": 2592000
}
```

后续请求携带 `Authorization: Bearer <access_token>`（兼容 `?access_token=` 查询参数）。

网关层错误码（非 `ApiResult` 包装的原生 JSON）：

| HTTP | code | 说明 |
| --- | --- | --- |
| 401 | `401` | 缺少 / 无效 / 过期令牌 |
| 403 | `403` | 非 client_credentials 机器令牌，或客户端已停用 / 未开通 client_credentials 授权模式 |

:::tip 换端点了
`client_credentials` 是 sa-token 1.46+ 的独立端点 `/oauth2/client_token`——`/oauth2/token` **不支持**该 grant_type。令牌换取的完整说明（secret 保管、防泄露等）见 [机器对机器接入指南](client-credentials-integration.md)。
:::

## 响应约定

- 业务层统一响应 `ApiResult`：成功 `code=20000`；失败 `code=400`（参数 / 资源不存在）、`403`（越权，如试图操作别家或 `__global__` 数据）、`500`（服务异常），错误信息在 `msg`。
- 分页接口返回 `ApiPagedResult`：列表在 `data`、总数在 `total`，分页参数为 `pageNum` / `pageSize`。

## 角色管理

端点前缀：`/api/open/v1/roles`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/page?keyword=&enableStatus=&pageNum=1&pageSize=10` | 分页查询（本系统 + 全局） |
| GET | `/{id}` | 详情（他系统资源按不存在处理） |
| POST | `/` | 创建，body：`{code, name, seqNo?, remark?}`（`code` 租户内唯一） |
| PUT | `/{id}` | 更新 `{name, seqNo, remark}`（appCode 不可变更） |
| POST | `/{id}/enable`、`/{id}/disable` | 启停 |
| DELETE | `/{id}` | 逻辑删除 |
| GET / PUT | `/{id}/permissions` | 查看 / 覆盖式分配权限点 |
| GET / PUT | `/{id}/menus` | 查看（返回菜单树）/ 覆盖式分配菜单 |
| GET / PUT | `/{id}/users` | 查看 / 覆盖式分配用户 |

**覆盖式分配**：PUT 的 body 为 `{"ids": ["权限/菜单/用户ID", ...]}`，保存后 ids 即为该角色的最终关联集合。分配的权限 / 菜单可包含 `__global__` 全局资源（如四档数据范围 `&all` / `&myself`）。

```bash
# 给角色分配权限点（覆盖式）
curl -X PUT "https://<认证中心>/api/open/v1/roles/<roleId>/permissions" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"ids": ["<permissionId1>", "<permissionId2>"]}'
```

## 菜单管理

端点前缀：`/api/open/v1/menus`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/tree` | 本系统 + 全局的启用菜单树（含 `children` 层级） |
| GET | `/page?keyword=&enableStatus=` | 分页平铺查询 |
| POST | `/` | 创建，body：`{parentId?, name, type, path?, component?, icon?, sortNo?, permissionCode?}` |
| PUT | `/{id}` | 更新（字段同创建） |
| POST | `/{id}/enable`、`/{id}/disable` | 启停 |
| DELETE | `/{id}` | 逻辑删除 |

- `type` 取值：`catalog` 目录 / `menu` 菜单 / `button` 按钮；按钮节点通过 `permissionCode` 绑定功能权限码。
- `parentId` 可以挂**全局父节点**（如全局「帮助中心」目录下挂本系统菜单）。

## 权限点管理

端点前缀：`/api/open/v1/permissions`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/page?keyword=&type=&object=&pageNum=&pageSize=` | 分页查询；`type` 过滤四类 |
| GET | `/{id}` | 详情 |
| POST | `/` | 创建，body：`{code, name, type, object?, rule?, seqNo?, remark?}` |
| PUT | `/{id}` | 更新（字段同创建） |
| DELETE | `/{id}` | 逻辑删除 |

权限点为四类统一模型：

| 类型 | 含义 | `object` / `rule` 用法 |
| --- | --- | --- |
| `dp` | 数据权限 | `object` 为实体表名，`rule` 为 SQL 片段，支持 `#currentUser.xxx#` 白名单占位符 |
| `mp` | 模型权限 | `object` 为实体名（增删改查等实体操作） |
| `cp` | 字段权限 | `object` 为实体名，`rule` 可描述可见 / 隐藏字段 |
| `fp` | 功能 / 接口权限 | `object` 为接口路径或功能点标识 |

`seq_no` 作为数据权限权重，多条规则并存时**取最大者**（范围最宽）生效。数据权限如何下发并在子查询中注入执行，见 [权限接入指南](permission-integration.md)。

## 用户管理

端点前缀：`/api/open/v1/users`。用户目录由认证中心统一维护（`auth_user` 表），同一租户下各集成系统共用。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/page?keyword=&orgId=&enableStatus=` | 分页查询；`keyword` 匹配登录名 / 姓名 / 邮箱 / 手机 |
| GET | `/{id}` | 详情 |
| POST | `/` | 创建，body：`{loginName, name, password(明文,服务端加密), email?, mobilePhone?, orgId?, ...}` |
| PUT | `/{id}` | 更新资料（登录名不可改） |
| POST | `/{id}/enable`、`/{id}/disable` | 启用 / 停用；停用**强制下线** |
| PUT | `/{id}/password` | 重置密码，body：`{"password": "新密码(明文)"}`；重置后**强制下线** |
| DELETE | `/{id}` | 逻辑删除并强制下线 |
| POST | `/list-by-ids` | 按 ID 批量查询（选人场景），body：`["userId1", "userId2"]` |
| GET / PUT | `/{userId}/roles` | 查询 / 覆盖式分配**本系统**下的角色 |

用户-角色分配的 body 同为 `{"ids": [...]}`；角色须属于本系统或 `__global__`，授权只落在本系统 appCode 下，**不影响**该用户在其他系统的角色。

## 组织管理

端点前缀：`/api/open/v1/orgs`。组织树为租户级共享（`auth_org` 表），用户通过 `orgId` 挂到组织。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/tree?enableStatus=` | 组织树（含 `children`） |
| GET | `/page?keyword=&enableStatus=` | 分页平铺查询 |
| POST | `/` | 创建，body：`{code, name, pid?, type?, seqNo?, remark?}`（`code` 租户内唯一，`pid` 空 = 根） |
| PUT | `/{id}` | 更新（code 不可改；服务端防环校验） |
| POST | `/{id}/enable`、`/{id}/disable` | 启停 |
| DELETE | `/{id}` | 删除；存在子组织时拒绝 |

`type`：`company` / `dept` / `group` 等，由接入方自定义。

## 会话管理

端点前缀：`/api/open/v1/sessions`。会话是账号级（按 `loginId` 管理），开放 API 通过两种 scope 限定调用方可见范围：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/page?scope=app-users\|client&pageNum=&pageSize=` | 分页在线会话（详见下表） |
| GET | `/by-user/{userId}` | 该用户在线终端列表 |
| DELETE | `/by-user/{userId}` | 强制下线该用户**全部终端** |
| DELETE | `/by-user/{userId}/device/{deviceType}` | 按设备类型下线（web / app / pc 等） |

`scope` 两种取值：

| scope | 含义 | 实现方式 |
| --- | --- | --- |
| `app-users`（默认） | 在本系统（appCode）下有授权关系（`auth_user_r_role`）的用户的全部在线会话 | 服务端内存关联 |
| `client` | 经本系统 client 令牌登录产生的会话 | `SessionInfo.clientId` 精确匹配 |

按用户操作前，服务端会校验「该用户在本系统下有授权关系」，否则 403。

## 完整调用示例

```bash
BASE=https://<认证中心>

# 1) 换机器令牌
TOKEN=$(curl -s -X POST "$BASE/oauth2/client_token" \
  -d "grant_type=client_credentials&client_id=<client_id>&client_secret=<client_secret>" \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# 2) 建组织与用户
curl -s -X POST "$BASE/api/open/v1/orgs" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"dev-dept","name":"研发部","type":"dept"}'
curl -s -X POST "$BASE/api/open/v1/users" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"loginName":"zhangsan","name":"张三","password":"Init@123"}'

# 3) 建角色并给用户授权（覆盖式）
curl -s -X POST "$BASE/api/open/v1/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"demo-admin","name":"演示系统管理员"}'
curl -s -X PUT "$BASE/api/open/v1/users/<userId>/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"ids":["<roleId>"]}'

# 4) 越权用例（应返回 code=403）：给角色声明别家 appCode
curl -s -X POST "$BASE/api/open/v1/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"x","name":"x","appCode":"other-sys"}'
```

## 与管理端、下发 API 的关系

- **认证中心管理端**（`/api/roles`、`/api/users` 等，管理员白名单）与开放 API 操作**同一份数据**，开放 API 自动叠加调用方隔离；两侧写操作都触发权限变更通知与版本号递增。
- **只读下发 API**（`/api/permission-sync/**`）同样做了归属加固：机器令牌只能拉取自己 appCode 的数据（`appCode` 参数不一致返回 403）。权限镜像与数据权限注入的完整说明见 [权限接入指南](permission-integration.md)。

## 配套资源

- **示例数据**：认证中心 Flyway `V8`/`V9` 种子幂等写入示例数据（组织树、示例用户、角色、菜单、权限点及授权关系），示例用户密码统一 `geelato123`。
- **参考实现**：认证中心仓库根目录 `gl-open-demo/` 是一个独立的轻量 Vue 3 演示站，页面即按本篇 API 实现完整管理台（每个页签内置接口说明），可作为集成方自建管理 UI 的参考代码。
- **跨域（CORS）**：集成方管理 UI 与认证中心不同源时，由认证中心配置 `geelato.auth.open-api.allowed-origins`（逗号分隔，默认 `*`）放行；令牌走 `Authorization` 头，不涉及 Cookie。
