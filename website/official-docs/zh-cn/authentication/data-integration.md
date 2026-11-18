---
title: 数据集成（组织 / 用户 / 角色同步）
sidebar_label: 数据集成
---

# 数据集成（组织 / 用户 / 角色同步）

统一认证中心是**组织、用户、角色的权威数据源**（目录中心）。数据集成解决的问题是：接入的业务系统如何把这些主数据及其**关系**（用户↔组织、用户↔角色、组织↔角色）同步到自己一侧，或把自己的用户数据开通到认证中心。

登录接入（[Lite 轻量接入](lite-login-integration.md) / [OAuth2 协议接入](oauth2-integration.md)）解决"身份从哪来"；本章解决"数据从哪来、怎么管"。

## 目录模型

- **用户与组织：租户级共享**。同一租户下所有接入系统共用同一份用户（`auth_user`）与组织（`auth_org`）数据——用户账号在各系统间通用，这是统一登录（SSO）的前提。
- **角色：按 appCode 归属**。每个业务系统只能看到并管理「自己的」角色，外加对 `__global__` 全局角色（如四档数据范围）只读引用。
- **用户中心模式**：`geelato.auth.user-directory.mode=central|primary`。独立认证中心与开放管理 API 均操作中心目录表（`auth_user` / `auth_org`）；`primary` 模式用于宿主应用嵌入接入时兼容自有用户表。

### 关系模型

| 关系 | 存储表 | 说明 |
| --- | --- | --- |
| 用户 ↔ 组织 | `auth_user_r_org` | 用户挂到组织（直属部门）；成员身份本身**不**继承该部门的角色 |
| 用户 ↔ 角色 | `auth_user_r_role` | 按 appCode 授权，只影响本系统，不影响用户在其他系统的角色 |
| 组织 ↔ 角色 | `auth_org_r_role` | 部门角色：把某个角色授予某组织 |
| 用户 ↔ 部门角色 | `auth_user_r_org_role` | 用户在某组织中生效的部门角色，是**部门角色生效的唯一途径** |
| 用户 ↔ 权限 | `auth_user_r_permission` | 用户直授权限 |

生效权限聚合 = 用户角色 ∪ 直挂部门角色 ∪ 用户直授，完整说明见 [权限集成](permission-integration.md)。

## 同步通道选型

| 通道 | 方向 | 适用场景 | 说明 |
| --- | --- | --- | --- |
| ① 开放管理 API（CRUD） | 写入 / 查询认证中心 | 初始化主数据、日常自助管理 | 机器令牌调用，端点参考见 [管理集成](open-api-management.md) |
| ② geelato-auth-client SDK | 查询读取 | Java 业务后端查用户 / 组织 / 角色（选人、鉴权上下文） | 机器令牌自动管理 |
| ③ 账号开通 API | 写入认证中心 | 审批流 / 流程平台按业务单据自动开通账号 | 幂等、一次性密码、首登强制改密 |
| ④ SQL 脚本 | 库间同步 | 平台侧历史数据迁移、流程平台读表 | 运维场景，不走 API |

### 通道一：开放管理 API（CRUD）

凭机器令牌（[获取方式](oauth2-integration.md#场景二机器对机器client_credentials)）调用 `/api/open/v1/**`，即可完成组织、用户、角色及关系的全部增删改查：

- 组织：`POST /api/open/v1/orgs` 创建、`GET /tree` 拉取组织树、`GET /{orgId}/users-tree` 拉组织及人员、`GET /{orgId}/ancestors|descendants` 祖先 / 后代链；
- 用户：`POST /api/open/v1/users` 创建（body 传 `orgId` 即挂到组织）、`PUT /{userId}/roles` 覆盖式分配角色；
- 角色：`POST /api/open/v1/roles` 创建（自动归属本系统 appCode）、`PUT /{roleId}/permissions` 分配权限点、`PUT /{roleId}/users` 覆盖式分配用户；
- 部门角色：`GET /api/open/v1/orgs/{orgId}/roles`、`GET /{orgId}/roles/{roleId}/users` 查看部门角色及其生效用户。

完整端点参考见 [管理集成](open-api-management.md)。

**新系统初始化示例**（建组织 → 建用户挂组织 → 建角色 → 授权）：

```bash
BASE=https://<认证中心>
# 1) 换机器令牌
TOKEN=$(curl -s -X POST "$BASE/oauth2/client_token" \
  -d "grant_type=client_credentials&client_id=<client_id>&client_secret=<client_secret>" \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# 2) 建组织
curl -s -X POST "$BASE/api/open/v1/orgs" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"dev-dept","name":"研发部","type":"dept"}'
# 3) 建用户并挂到组织
curl -s -X POST "$BASE/api/open/v1/users" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"loginName":"zhangsan","name":"张三","password":"Init@123","orgId":"<orgId>"}'
# 4) 建角色、分配权限点、给用户授权（覆盖式）
curl -s -X POST "$BASE/api/open/v1/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"demo-admin","name":"演示系统管理员"}'
curl -s -X PUT "$BASE/api/open/v1/roles/<roleId>/permissions" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"ids":["<permissionId1>","<permissionId2>"]}'
curl -s -X PUT "$BASE/api/open/v1/users/<userId>/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"ids":["<roleId>"]}'
```

### 通道二：geelato-auth-client SDK（查询读取）

Java 业务后端推荐直接使用 SDK，免去手写 HTTP 与令牌管理：

```xml
<dependency>
    <groupId>cn.geelato</groupId>
    <artifactId>geelato-auth-client</artifactId>
</dependency>
```

配置（前缀 `geelato.auth.client.*`）：

| 配置项 | 说明 | 默认 |
| --- | --- | --- |
| `enabled` | 是否启用自动装配 | `true` |
| `base-url` | 认证中心地址；**配置后才装配** SDK | 无 |
| `client-id` / `client-secret` | OAuth 客户端凭证（机器令牌） | 无 |
| `connect-timeout-millis` / `read-timeout-millis` | 超时 | 5000 / 15000 |
| `token-refresh-seconds` | 机器令牌提前刷新余量 | 3600 |
| `provisioning-cache-ttl-seconds` | 权限拉取缓存 TTL（0 = 禁用） | 0 |

能力一：`AuthCenterSecurityProvider` 实现 `geelato-security` 的 `SecurityProvider` 契约，提供用户 / 组织 / 角色的**查询读取**：`getUserById` / `getUserByLoginName` / `getOrgByCode` / `getRoleByCode` / `getUserRoles` / `queryUsersByIds` / `queryUsersOfOrg` / `queryUsersOfOrgRole` / `queryOrgRoles` / `querySubOrgs` / `queryAncestorOrgs` / `queryPermissionsOfRole` / `queryPermissionsOfUser` 等，底层全部映射 `/api/open/v1/**`。机器令牌由 `AuthCenterTokenManager` 自动获取与缓存。适合选人组件、单据上落组织 / 人员信息等场景。

能力二：`AuthCenterProvisioningClient` 拉取用户生效权限与可见菜单（`getPermissions` / `getMenus`，`mode=simple|full`），属于权限分发通道，详见 [权限集成](permission-integration.md)。

### 通道三：账号开通 API（审批流对接）

端点前缀：`/api/open/v1/account-provisions`。面向流程平台 / 审批系统：审批单通过后，按单自动在认证中心开通账号。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/` | 开通账号，body：`{requestId, loginName, name, mobilePrefix?, mobilePhone?, email?, roleCodes 或 roleIds, orgId?, tenantCode?}` |
| GET | `/{requestId}` | 按请求单号查开通结果 |
| POST | `/{requestId}/regenerate-password` | 为已开通账号重新生成初始密码 |

关键语义：

- **幂等续跑**：`requestId` 是调用方的幂等键（对应认证中心 `auth_account_request` 表）。已完成的请求重复调用返回 `repeated=true`，且**不再**返回密码；`PENDING` / `FAILED` 状态自动续跑。
- **密码一次性下发**：初始密码为 12 位强随机，明文**仅随成功响应返回一次**，服务端不落库；忘记初始密码只能走 `regenerate-password` 重新生成。
- **首登强制改密**：开通的用户 `must_change_password=1`，登录响应中 `user.mustChangePassword=true`，前端据此引导改密（[Lite 轻量接入](lite-login-integration.md) 的登录响应已携带该字段及改密验证码所需的 `mobilePrefix` / `mobilePhone`）。
- **角色 fail-closed**：`roleCodes` 与 `roleIds` 必须二选一（都传返回 400）；任一角色解析不到 / 属于其他系统 / 已停用，整单置 `FAILED` 并报错，不会开出一半授权的账号。

### 通道四：SQL 脚本（运维场景）

面向「平台库表与认证中心库表之间」的批量同步（数据库层面，非 API 集成）。认证中心仓库 `docs/sql/` 提供：

| 脚本 | 用途 |
| --- | --- |
| `migrate-platform-user-to-auth-user.sql` | `platform_org/user/...` → `auth_org/user/...` 一次性幂等迁移（切换中心目录模式时使用） |
| `sync-auth-to-platform.sql` | `auth_*` → `platform_*` 反向只增回补（中心目录模式后回补平台侧） |
| `sync-auth-core-tables-to-wf.sql` | `auth_*` → `wf_*` 全量幂等 upsert（供流程平台读侧建表同步） |
| `create-auth-core-tables.sql` | 手工创建 7 张核心表（与 Flyway 最终态一致） |

:::tip
脚本仅适合同实例 / 可直连的库间运维场景，由 DBA 按需执行；常规集成一律走通道一至三的 API。
:::

## 隔离与审计

- **appCode 绑定**：角色、菜单、权限点、授权关系按 `app_code`（= 调用方 `oauth_client.system_code`）隔离，请求中不可越权指定（填错 403，缺省自动赋值）；
- **tenantCode 绑定**：用户与组织为租户级数据，租户由客户端记录绑定，不可指定；
- **`__global__` 只读**：全局角色 / 菜单 / 权限点对所有集成方可见、可引用（如分配给自己的角色），但不能经开放 API 修改或删除；
- **审计与通知**：所有写操作以 `CLIENT:clientId` 身份记入审计哈希链；涉及权限的变更自动触发 Redis 通知（`geelato:permission:change`）与版本号递增，子系统镜像体系无需额外处理。

## 典型场景

### 场景 A：新系统上线，初始化组织与用户

走通道一：按上文示例依次建组织、建用户（挂组织）、建角色并分配权限点、给用户授权；随后接入 [权限集成](permission-integration.md) 拉取生效权限。

### 场景 B：审批通过自动开通账号

走通道三：审批单通过 → `POST /api/open/v1/account-provisions`（`requestId` = 审批单号）→ 响应中的 `initialPassword` 通过短信 / 工单一次性告知用户 → 用户首次登录强制改密。

### 场景 C：业务后端查组织 / 人员数据

走通道二：引入 `geelato-auth-client`，通过 `SecurityProvider` 契约调用 `queryUsersOfOrg` / `querySubOrgs` / `getUserByLoginName` 等，实现选人组件、单据展示等。
