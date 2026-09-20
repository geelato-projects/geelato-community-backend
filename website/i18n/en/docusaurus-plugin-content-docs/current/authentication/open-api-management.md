---
title: Open Management API (Integrator Self-Service)
sidebar_label: Open Management API
---

# Open Management API (Integrator Self-Service)

While the unified auth server centrally manages data for all connected systems (roles, menus, permissions, grants, users, organizations, and sessions), it also exposes a set of **HTTP management APIs (`/api/open/v1/**`)** for each integrator: using its own machine token, an integrator can self-manage **the data that belongs to it** and build its own admin UI on top.

> Prerequisite: calling the open API requires a client_credentials machine token — see the [Machine-to-Machine Integration Guide](client-credentials-integration.md). User tokens (issued via the authorization code or password grant) **cannot** call the open API.

## Key Characteristics

- **Authentication**: OAuth2 `client_credentials` machine token (standard protocol, no Sa-Token dependency, no shared session).
- **Isolation**: the data scope a caller can operate on is automatically bound to its `oauth_client` record and **cannot be overridden**.
- **Audit**: all write operations are recorded into the audit hash chain with the `CLIENT` actor type; permission changes keep the Redis notification (`geelato:permission:change`) and version semantics unchanged, so subsystem mirror tables need no extra handling.

## Identity and Isolation Model

| Resource | Ownership dimension | Visible / writable scope for a caller |
| --- | --- | --- |
| Roles / menus / permissions / grants | `app_code` (= the caller's `oauth_client.system_code`) | Query: own system + `__global__` (read-only); write: own system only |
| Users / organizations | `tenant_code` (tenant-level shared) | Read/write within the tenant (SSO premise: one user may access multiple systems in the same tenant) |
| Sessions | By grant relation / login origin | See [Session Management](#session-management) |

Three core rules:

1. **appCode cannot be specified**: if the `appCode` field in a request body is present, it must equal the caller's own `system_code`, otherwise the API returns `code=403`; when omitted it is filled automatically with the caller's appCode. A role's owning appCode cannot be changed.
2. **`__global__` data is read-only**: global roles / menus / permissions are visible to all integrators (queryable, assignable to your own roles), but cannot be modified or deleted via the open API — that is the auth server administrator's job.
3. **tenantCode cannot be specified**: the tenant of users and organizations is bound to the caller's client record (`oauth_client.tenant_code`).

## Obtaining an Access Token

```bash
curl -X POST "https://<auth-host>/oauth2/client_token" \
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

Subsequent requests carry `Authorization: Bearer <access_token>` (the `?access_token=` query parameter also works).

Gateway-level error codes (raw JSON, not wrapped in `ApiResult`):

| HTTP | code | Description |
| --- | --- | --- |
| 401 | `401` | Missing / invalid / expired token |
| 403 | `403` | Not a client_credentials machine token, or the client is disabled / not enrolled for the client_credentials grant |

:::tip Separate endpoint
`client_credentials` uses a dedicated endpoint `/oauth2/client_token` (built into sa-token 1.46+) — `/oauth2/token` does **not** support this grant type. See the [Machine-to-Machine Integration Guide](client-credentials-integration.md) for full token-handling details (secret storage, leak response, etc.).
:::

## Response Conventions

- Business responses use the unified `ApiResult`: success `code=20000`; failures use `code=400` (bad request / not found), `403` (access denied, e.g. touching another system's or `__global__` data), `500` (server error), with the message in `msg`.
- Paginated endpoints return `ApiPagedResult`: the list in `data`, the total in `total`; pagination parameters are `pageNum` / `pageSize`.

## Role Management

Endpoint prefix: `/api/open/v1/roles`

| Method | Path | Description |
| --- | --- | --- |
| GET | `/page?keyword=&enableStatus=&pageNum=1&pageSize=10` | Paginated query (own system + global) |
| GET | `/{id}` | Detail (another system's resources are treated as not found) |
| POST | `/` | Create, body: `{code, name, seqNo?, remark?}` (`code` unique per tenant) |
| PUT | `/{id}` | Update `{name, seqNo, remark}` (appCode immutable) |
| POST | `/{id}/enable`, `/{id}/disable` | Enable / disable |
| DELETE | `/{id}` | Soft delete |
| GET / PUT | `/{id}/permissions` | List / replace-set assign permissions |
| GET / PUT | `/{id}/menus` | List (returns menu tree) / replace-set assign menus |
| GET / PUT | `/{id}/users` | List / replace-set assign users |

**Replace-set assignment**: the PUT body is `{"ids": ["permission/menu/user ID", ...]}` — after saving, the ids become the role's final association set. Assigned permissions / menus may include `__global__` resources (such as the four data-scope presets `&all` / `&myself`).

```bash
# Assign permissions to a role (replace-set)
curl -X PUT "https://<auth-host>/api/open/v1/roles/<roleId>/permissions" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"ids": ["<permissionId1>", "<permissionId2>"]}'
```

## Menu Management

Endpoint prefix: `/api/open/v1/menus`

| Method | Path | Description |
| --- | --- | --- |
| GET | `/tree` | Enabled menu tree of own system + global (with `children`) |
| GET | `/page?keyword=&enableStatus=` | Paginated flat query |
| POST | `/` | Create, body: `{parentId?, name, type, path?, component?, icon?, sortNo?, permissionCode?}` |
| PUT | `/{id}` | Update (same fields as create) |
| POST | `/{id}/enable`, `/{id}/disable` | Enable / disable |
| DELETE | `/{id}` | Soft delete |

- `type`: `catalog` / `menu` / `button`; button nodes bind a functional permission code via `permissionCode`.
- `parentId` may reference a **global parent node** (e.g. your menu under the global "Help Center" catalog).

## Permission Management

Endpoint prefix: `/api/open/v1/permissions`

| Method | Path | Description |
| --- | --- | --- |
| GET | `/page?keyword=&type=&object=&pageNum=&pageSize=` | Paginated query; `type` filters the four kinds |
| GET | `/{id}` | Detail |
| POST | `/` | Create, body: `{code, name, type, object?, rule?, seqNo?, remark?}` |
| PUT | `/{id}` | Update (same fields as create) |
| DELETE | `/{id}` | Soft delete |

Permissions use a unified four-type model:

| Type | Meaning | `object` / `rule` usage |
| --- | --- | --- |
| `dp` | Data permission | `object` is the entity table; `rule` is an SQL fragment supporting `#currentUser.xxx#` whitelisted placeholders |
| `mp` | Model permission | `object` is the entity name (entity CRUD operations) |
| `cp` | Field permission | `object` is the entity name; `rule` may describe visible / hidden fields |
| `fp` | Functional / API permission | `object` is an API path or functional identifier |

`seq_no` acts as the data-permission weight; when multiple rules coexist, the **largest** one (widest scope) wins. See the [Permission Integration Guide](permission-integration.md) for how data permissions are distributed and injected into subsystem queries.

## User Management

Endpoint prefix: `/api/open/v1/users`. The user directory is maintained by the auth server (`auth_user` table) and shared by all systems in the same tenant.

| Method | Path | Description |
| --- | --- | --- |
| GET | `/page?keyword=&orgId=&enableStatus=` | Paginated query; `keyword` matches login name / name / email / mobile |
| GET | `/{id}` | Detail |
| POST | `/` | Create, body: `{loginName, name, password(plaintext, encrypted server-side), email?, mobilePhone?, orgId?, ...}` |
| PUT | `/{id}` | Update profile (login name immutable) |
| POST | `/{id}/enable`, `/{id}/disable` | Enable / disable; disabling **forces logout** |
| PUT | `/{id}/password` | Reset password, body: `{"password": "new password (plaintext)"}`; **forces logout** afterwards |
| DELETE | `/{id}` | Soft delete and force logout |
| POST | `/list-by-ids` | Batch query by IDs (user pickers), body: `["userId1", "userId2"]` |
| GET / PUT | `/{userId}/roles` | List / replace-set assign roles **under the own system** |

The user-role assignment body is also `{"ids": [...]}`; the roles must belong to the own system or `__global__`, and the grant only lands under the own appCode — it does **not** affect the user's roles in other systems.

## Organization Management

Endpoint prefix: `/api/open/v1/orgs`. The org tree is tenant-level shared (`auth_org` table); users attach via `orgId`.

| Method | Path | Description |
| --- | --- | --- |
| GET | `/tree?enableStatus=` | Org tree (with `children`) |
| GET | `/page?keyword=&enableStatus=` | Paginated flat query |
| POST | `/` | Create, body: `{code, name, pid?, type?, seqNo?, remark?}` (`code` unique per tenant, empty `pid` = root) |
| PUT | `/{id}` | Update (code immutable; server-side cycle check) |
| POST | `/{id}/enable`, `/{id}/disable` | Enable / disable |
| DELETE | `/{id}` | Delete; refused while child organizations exist |

`type`: `company` / `dept` / `group`, etc., customized by the integrator.

## Session Management

Endpoint prefix: `/api/open/v1/sessions`. Sessions are account-level (managed by `loginId`); the open API restricts visibility through two scopes:

| Method | Path | Description |
| --- | --- | --- |
| GET | `/page?scope=app-users\|client&pageNum=&pageSize=` | Paginated online sessions (see below) |
| GET | `/by-user/{userId}` | The user's online terminals |
| DELETE | `/by-user/{userId}` | Force logout of the user's **all terminals** |
| DELETE | `/by-user/{userId}/device/{deviceType}` | Force logout by device type (web / app / pc, etc.) |

Two `scope` values:

| scope | Meaning | Implementation |
| --- | --- | --- |
| `app-users` (default) | All online sessions of users having a grant relation (`auth_user_r_role`) under the own system (appCode) | Server-side in-memory join |
| `client` | Sessions produced by logins through the own client's token | Exact match on `SessionInfo.clientId` |

Before per-user operations, the server verifies that the user has a grant relation under the calling system, otherwise 403.

## End-to-End Example

```bash
BASE=https://<auth-host>

# 1) Obtain a machine token
TOKEN=$(curl -s -X POST "$BASE/oauth2/client_token" \
  -d "grant_type=client_credentials&client_id=<client_id>&client_secret=<client_secret>" \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# 2) Create an organization and a user
curl -s -X POST "$BASE/api/open/v1/orgs" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"dev-dept","name":"R&D Dept","type":"dept"}'
curl -s -X POST "$BASE/api/open/v1/users" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"loginName":"zhangsan","name":"Zhang San","password":"Init@123"}'

# 3) Create a role and grant it to the user (replace-set)
curl -s -X POST "$BASE/api/open/v1/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"demo-admin","name":"Demo Admin"}'
curl -s -X PUT "$BASE/api/open/v1/users/<userId>/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"ids":["<roleId>"]}'

# 4) Access-violation case (expect code=403): declare another system's appCode
curl -s -X POST "$BASE/api/open/v1/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"x","name":"x","appCode":"other-sys"}'
```

## Relationship with the Admin Console and Distribution API

- The **auth server admin console** (`/api/roles`, `/api/users`, etc., guarded by the administrator whitelist) and the open API operate on **the same data**, with the open API applying caller isolation automatically; writes on either side trigger permission-change notifications and version increments.
- The **read-only distribution API** (`/api/permission-sync/**`) is equally hardened: a machine token can only pull data of its own appCode (a mismatched `appCode` parameter returns 403). See the [Permission Integration Guide](permission-integration.md) for the full mirror-table and data-permission injection story.

## Companion Resources

- **Sample data**: the auth server's Flyway `V8`/`V9` seeds idempotently insert sample data (org tree, sample users, roles, menus, permissions and grants); the sample users share the password `geelato123`.
- **Reference implementation**: `gl-open-demo/` at the auth server repository root is a standalone lightweight Vue 3 demo site implementing a complete admin console on this API (with per-tab API descriptions), usable as reference code for integrators building their own UI.
- **CORS**: when an integrator's admin UI is hosted on a different origin, the auth server allows it via `geelato.auth.open-api.allowed-origins` (comma-separated, default `*`); tokens travel in the `Authorization` header, no cookies involved.
