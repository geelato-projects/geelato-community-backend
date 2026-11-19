---
id: data-integration
title: Data Integration (Org / User / Role Sync)
sidebar_label: Data Integration
---

# Data Integration (Org / User / Role Sync)

The unified authentication center is the **authoritative data source for organizations, users, and roles** (the identity directory). Data integration answers: how do integrated business systems sync this master data and its **relationships** (user↔org, user↔role, org↔role) to their own side, or how do they provision their user data into the auth center.

Login integration ([Lite Login Integration](lite-login-integration.md) / [OAuth2 Protocol Integration](oauth2-integration.md)) answers "where the identity comes from"; this chapter answers "where the data comes from and how to manage it".

## Directory model

- **Users and orgs: tenant-level shared.** All integrated systems under the same tenant share one set of users (`auth_user`) and orgs (`auth_org`) — accounts work across systems, which is the prerequisite of unified login (SSO).
- **Roles: bound to appCode.** Each business system only sees and manages "its own" roles, plus read-only references to `__global__` roles (e.g. the four built-in data scopes).
- **User directory mode**: `geelato.auth.user-directory.mode=central|primary`. The standalone auth center and the Open Management API always operate on the central directory tables (`auth_user` / `auth_org`); `primary` mode is for embedded hosting that keeps its own user tables.

### Relationship model

| Relationship | Table | Notes |
| --- | --- | --- |
| User ↔ Org | `auth_user_r_org` | A user belongs to an org (direct department); membership itself does **not** inherit that org's roles |
| User ↔ Role | `auth_user_r_role` | Granted per appCode; only affects this system, never the user's roles in other systems |
| Org ↔ Role | `auth_org_r_role` | Dept role: a role granted to an organization |
| User ↔ Dept role | `auth_user_r_org_role` | The dept role effective for a user in a given org — the **only way** a dept role takes effect |
| User ↔ Permission | `auth_user_r_permission` | Permissions granted directly to a user |

Effective permissions = user roles ∪ effective dept roles ∪ direct grants; see [Permission Integration](permission-integration.md) for details.

## Choosing a sync channel

| Channel | Direction | Best for | Notes |
| --- | --- | --- | --- |
| ① Open Management API (CRUD) | Write / query the auth center | Initializing master data, day-to-day self-management | Machine token; endpoint reference in [Open Management API](open-api-management.md) |
| ② geelato-auth-client SDK | Query / read | Java backends querying users / orgs / roles (picker components, auth context) | Machine token managed automatically |
| ③ Account Provision API | Write to the auth center | Approval flows / process platforms auto-provisioning accounts per business request | Idempotent, one-time password, forced first-login password change |
| ④ SQL scripts | Cross-database sync | Platform-side historical data migration, read-table sync for process platforms | Ops scenario, not API-based |

### Channel 1: Open Management API (CRUD)

With a machine token ([how to obtain](oauth2-integration.md#mode-2-machine-to-machine-client_credentials)), call `/api/open/v1/**` to create, read, update, and delete orgs, users, roles, and their relationships:

- Orgs: `POST /api/open/v1/orgs` create, `GET /tree` fetch the org tree, `GET /{orgId}/users-tree` fetch an org with its users, `GET /{orgId}/ancestors|descendants` ancestor / descendant chains;
- Users: `POST /api/open/v1/users` create (pass `orgId` in the body to attach to an org), `PUT /{userId}/roles` overwrite-style role assignment;
- Roles: `POST /api/open/v1/roles` create (auto-bound to the caller's appCode), `PUT /{roleId}/permissions` assign permissions, `PUT /{roleId}/users` overwrite-style user assignment;
- Dept roles: `GET /api/open/v1/orgs/{orgId}/roles`, `GET /{orgId}/roles/{roleId}/users` inspect dept roles and their effective users.

Full endpoint reference: [Open Management API](open-api-management.md).

**New-system initialization example** (create org → create user → create role → grant):

```bash
BASE=https://<auth-center>
# 1) Exchange machine token
TOKEN=$(curl -s -X POST "$BASE/oauth2/client_token" \
  -d "grant_type=client_credentials&client_id=<client_id>&client_secret=<client_secret>" \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# 2) Create an org
curl -s -X POST "$BASE/api/open/v1/orgs" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"dev-dept","name":"R&D","type":"dept"}'
# 3) Create a user attached to the org
curl -s -X POST "$BASE/api/open/v1/users" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"loginName":"zhangsan","name":"Zhang San","password":"Init@123","orgId":"<orgId>"}'
# 4) Create a role, assign permissions, grant the role to the user (overwrite-style)
curl -s -X POST "$BASE/api/open/v1/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"demo-admin","name":"Demo Admin"}'
curl -s -X PUT "$BASE/api/open/v1/roles/<roleId>/permissions" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"ids":["<permissionId1>","<permissionId2>"]}'
curl -s -X PUT "$BASE/api/open/v1/users/<userId>/roles" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"ids":["<roleId>"]}'
```

### Channel 2: geelato-auth-client SDK (query / read)

Java backends should prefer the SDK over hand-written HTTP and token handling:

```xml
<dependency>
    <groupId>cn.geelato</groupId>
    <artifactId>geelato-auth-client</artifactId>
</dependency>
```

Configuration (prefix `geelato.auth.client.*`):

| Property | Description | Default |
| --- | --- | --- |
| `enabled` | Enable auto-configuration | `true` |
| `base-url` | Auth center base URL; the SDK is only wired when set | — |
| `client-id` / `client-secret` | OAuth client credentials (machine token) | — |
| `connect-timeout-millis` / `read-timeout-millis` | Timeouts | 5000 / 15000 |
| `token-refresh-seconds` | Machine token proactive refresh window | 3600 |
| `provisioning-cache-ttl-seconds` | Provisioning cache TTL (0 = disabled) | 0 |

Capability 1: `AuthCenterSecurityProvider` implements the `geelato-security` `SecurityProvider` contract for **querying** users / orgs / roles: `getUserById` / `getUserByLoginName` / `getOrgByCode` / `getRoleByCode` / `getUserRoles` / `queryUsersByIds` / `queryUsersOfOrg` / `queryUsersOfOrgRole` / `queryOrgRoles` / `querySubOrgs` / `queryAncestorOrgs` / `queryPermissionsOfRole` / `queryPermissionsOfUser`, etc. — all mapped to `/api/open/v1/**`. Machine tokens are obtained and cached automatically by `AuthCenterTokenManager`. Ideal for user-picker components and org / person lookups on business forms.

Capability 2: `AuthCenterProvisioningClient` pulls effective permissions and visible menus (`getPermissions` / `getMenus`, `mode=simple|full`); this belongs to the permission distribution channel — see [Permission Integration](permission-integration.md).

### Channel 3: Account Provision API (approval-flow integration)

Endpoint prefix: `/api/open/v1/account-provisions`. Designed for process / approval platforms: once an approval is passed, provision the account automatically per request.

| Method | Path | Description |
| --- | --- | --- |
| POST | `/` | Provision an account; body: `{requestId, loginName, name, mobilePrefix?, mobilePhone?, email?, roleCodes or roleIds, orgId?, tenantCode?}` |
| GET | `/{requestId}` | Query the provisioning result by request id |
| POST | `/{requestId}/regenerate-password` | Regenerate the initial password for a provisioned account |

Key semantics:

- **Idempotent resume**: `requestId` is the caller's idempotency key (backed by the `auth_account_request` table). Calling a COMPLETED request again returns `repeated=true` and **never** returns the password again; `PENDING` / `FAILED` requests resume automatically.
- **One-time password delivery**: the initial password is 12 strong-random characters, returned **once** in the success response only, never stored server-side; if lost, regenerate via `regenerate-password`.
- **Forced first-login password change**: provisioned users get `must_change_password=1`; the login response carries `user.mustChangePassword=true` so the frontend guides the change (the [Lite Login](lite-login-integration.md) response already includes it together with `mobilePrefix` / `mobilePhone` for the change-password verification code).
- **Roles fail-closed**: pass exactly one of `roleCodes` / `roleIds` (both → 400); if any role cannot be resolved / belongs to another system / is disabled, the whole request fails with `FAILED` — no partially-authorized accounts.

### Channel 4: SQL scripts (ops scenario)

For bulk sync between platform tables and auth center tables (database level, not API integration). The auth center repo provides under `docs/sql/`:

| Script | Purpose |
| --- | --- |
| `migrate-platform-user-to-auth-user.sql` | One-shot idempotent migration `platform_org/user/...` → `auth_org/user/...` (when switching to the central directory mode) |
| `sync-auth-to-platform.sql` | Reverse add-only backfill `auth_*` → `platform_*` (re-populating platform tables after switching modes) |
| `sync-auth-core-tables-to-wf.sql` | Full idempotent upsert `auth_*` → `wf_*` (read-side tables for the process platform) |
| `create-auth-core-tables.sql` | Manually create the 7 core tables (identical to the Flyway final state) |

:::tip
Scripts only fit ops scenarios where both databases are reachable on the same instance; regular integrations should always use channels 1–3.
:::

## Isolation and audit

- **appCode binding**: roles, menus, permissions, and grants are isolated by `app_code` (= the caller's `oauth_client.system_code`); requests must not specify another appCode (mismatch → 403, omitted → auto-assigned);
- **tenantCode binding**: users and orgs are tenant-level data; the tenant comes from the client record and cannot be specified;
- **`__global__` is read-only**: global roles / menus / permissions are visible and referencable to every integrator (e.g. assignable to your own roles), but cannot be modified or deleted via the Open API;
- **Audit and notifications**: every write is recorded in the audit hash chain as `CLIENT:clientId`; permission-related changes automatically trigger the Redis notification (`geelato:permission:change`) and version bumps — downstream mirror systems need no extra handling.

## Typical scenarios

### Scenario A: initialize orgs and users for a new system

Use channel 1: follow the example above to create orgs, users (attached to orgs), roles with permission assignments, then grant roles to users; afterwards wire up [Permission Integration](permission-integration.md) to pull effective permissions.

### Scenario B: auto-provision accounts from an approval flow

Use channel 3: approval passed → `POST /api/open/v1/account-provisions` (`requestId` = the approval id) → deliver `initialPassword` once via SMS / ticket → the user changes the password on first login.

### Scenario C: query org / person data from a business backend

Use channel 2: add `geelato-auth-client` and call `queryUsersOfOrg` / `querySubOrgs` / `getUserByLoginName` etc. through the `SecurityProvider` contract for picker components and form displays.
