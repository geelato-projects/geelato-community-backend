---
title: Permission Integration (Unified Permission Management)
sidebar_label: Permission Integration
---

# Permission Integration (Unified Permission Management)

> This document is for platform subsystem and third-party system developers. It explains how to integrate with the Auth Center's permission management capability, so that your system gains **menu, element, function/API, model, column and data permissions**, with cross-system, cross-database permission provisioning.
>
> Prerequisites: complete the [OAuth2 Integration](oauth2-integration.md) or [Machine-to-Machine Integration (client_credentials)](client-credentials-integration.md) guide first to obtain application credentials and token capabilities.

## 1. Architecture Overview

The Auth Center is the **permission authority (control plane)**; business subsystems are the **permission enforcers (data plane)**. Permissions are configured centrally in the Auth Center, delivered to each subsystem through provisioning channels, and enforced in real time on the subsystem's queries and APIs.

```
┌─────────────────── Auth Center (authorization DB) ─────────────────┐
│  auth_role / auth_menu / auth_permission / auth_*_r_* tables      │
│  Admin UI (role/menu/permission CRUD) + Provisioning API          │
│  Permission change → Redis.convertAndSend("geelato:permission:change") │
└───────────────────────────┬───────────────────────────────────────┘
                            │
        ┌───────────────────┼────────────────────┐
        │ ① Login/scheduled pull (HTTP, with access_token) │ ② Change notification (Redis pub/sub)
        ▼                                       ▼
┌─────────────────── Business subsystem (own DB) ────────────────────┐
│  Request filter → POST /oauth2/introspect (active + permissions/roles) │
│  Permission puller (Bearer → provisioning API → cache + mirror table) │
│  FluentQueryFilterInjector bean (implemented by each subsystem)     │
│    └─→ cache/mirror → replace #currentUser.xxx# → originalWhere    │
│  @RequirePermission/@RequireRole AOP (reads introspection claims)  │
│  PermissionChangeListener (Redis channel → invalidate + refresh)   │
└─────────────────────────────────────────────────────────────────────┘
```

**Two delivery channels:**

| Channel | Direction | Mechanism | Trigger |
|---|---|---|---|
| Pull | Auth Center → subsystem | HTTP `/api/provisioning/*` | After login + scheduled fallback (e.g. every 5 min) |
| Notify | Auth Center → subsystem | Redis pub/sub `geelato:permission:change` | Pushed immediately on permission/role/menu/assignment changes |

Subsystems keep a **local mirror table** (snapshot): queries stay fast (no remote call per request) and keep working when the Auth Center is briefly unavailable (degraded to the latest snapshot).

## 2. The Five Permission Types

Permission points are stored uniformly in the Auth Center's `auth_permission` table, distinguished by `type`. Every permission item uses the same structure (`code` / `name` / `rule` / `object` / `seqNo`):

| Type | Meaning | `object` example | `rule` |
|---|---|---|---|
| `dp` data permission | Row-level data scope | Entity table name | SQL fragment (may contain `#currentUser.xxx#` placeholders) |
| `mp` model permission | Entity CRUD operations | Entity name | optional |
| `cp` column permission | Column visibility | Entity name | may describe visible/hidden columns |
| `ep` element permission | Page element/button level control | Element id / component path | optional |
| `fp` function permission | API endpoint / function point | API path | optional |

Effective permissions = **user roles ∪ directly attached department roles ∪ direct grants**. Three independent concepts:

- **Department role** (`auth_org_r_role`, role attached to a department): defines "which roles this department has" and grants nothing to any user by itself.
- **Department membership** (`auth_user_r_org`, multiple attachments): which departments a user belongs to — **no role inheritance**; `auth_user.org_id` remains the primary department (used by the `#currentUser.orgId#` data permission placeholder).
- **User-attached department role** (`auth_user_r_org_role`, user × source department × role): the **only way** a department role takes effect for a user; the source department need not be one of the user's member departments.

The effective menu tree is the union of menus of all roles (including attached department roles).

## 3. Prerequisites

1. **Registered as an OAuth client**: register an `oauth_client` record in the Auth Center admin UI to get `client_id` / `client_secret`. Its `system_code` is your subsystem's **appCode** (permission ownership identifier).
2. **Network connectivity**: the subsystem can reach the Auth Center's HTTP endpoints and Redis instance.
3. **OAuth2 login integrated**: subsystem users log in through the Auth Center and obtain an OAuth2 `access_token` used as `Authorization: Bearer` to call the provisioning API. The subsystem **does not need Sa-Token, shared Sa-Token sessions or shared Redis**.
4. **Depends on geelato-orm**: the data-permission query injector is based on geelato-orm's `FluentQueryFilterInjector` SPI.

## 4. Provisioning API Reference

All provisioning endpoints require an OAuth2 access token: header `Authorization: Bearer <access_token>` (or query parameter `?access_token=`).

### 4.1 Pull a user's effective permissions

```
GET /api/provisioning/user/{userId}?appCode=xxx&tenantCode=yyy&mode=simple
```

Optional `mode` (`simple`/`full`, default `simple`; invalid values are rejected):

- **simple (default, lightweight)** — the minimal payload for per-request permission checks: the five permission groups are `PermissionItem` (`code/name/rule/object/seqNo`), `menus` is a slim tree (rendering fields only), and `roles` is not sent.
- **full (complete superset)** — usable **both for permission checks and for persisting into your own mirror tables**: the five groups are full `Permission` objects (adding `id/type/appCode/tenantCode/remark/audit timestamps`), `roles` contains full role objects (`id/code/name/appCode/enableStatus`, insertable into a mirrored `auth_role`), and `menus` is the full tree (with `id/parentId/enableStatus/audit timestamps`, flattenable for storage). `roleCodes/deptRoleCodes` are returned in both modes.

**Response `ProvisioningPayload` (simple form):**

```jsonc
{
  "code": 20000,            // outer wrapper is ApiResult; below is the data field
  "success": true,
  "data": {
    "userId": "u123",
    "appCode": "community",
    "tenantCode": "geelato",
    "mode": "simple",        // echo of the delivery form
    "version": 1690000000000,
    "dataPermissions": [    // data permissions (type=dp), rule contains unresolved placeholders
      { "code": "order:view-dept", "name": "View department", "rule": "dept_id=#currentUser.deptId#", "object": "xxx_order", "seqNo": 2 }
    ],
    "modelPermissions": [],                                  // model permissions (type=mp)
    "columnPermissions": [],                                 // column permissions (type=cp)
    "elementPermissions": [],                                // element permissions (type=ep, page element/button level)
    "functionPermissions": [                                 // function permissions (type=fp)
      { "code": "order:export", "name": "Export orders", "object": "/api/orders/export", "seqNo": 0 }
    ],
    "menus": [                                               // effective menu slim tree (hierarchy via children; no id/parentId)
      { "name": "Orders", "path": "/orders", "component": "order/index",
        "sortNo": 1, "type": "menu", "permissionCode": "order:view", "children": [] }
    ],
    "roleCodes": ["sales"],                                  // directly assigned role codes
    "deptRoleCodes": ["manager"]                             // department role codes directly attached to the user
  }
}
```

### 4.2 Pull a user's visible menu tree (frontend menu rendering)

```
GET /api/provisioning/user/{userId}/menus?appCode=xxx&tenantCode=yyy&mode=simple
```

Optional `mode` (default `simple`): `simple` returns a slim tree (only `name/path/component/icon/sortNo/permissionCode/type/children`, render directly); `full` returns the complete tree (`id/parentId/enableStatus/audit timestamps`, flattenable into mirror tables).
Both forms are trees sorted by `sortNo`. The `menus` field in 4.1's payload is identical; you only need this endpoint if you fetch permissions elsewhere.

> **Machine-token calls**: the provisioning API also accepts client_credentials machine tokens (`appCode` must match the client's bound `system_code`); when a machine token is used without an explicit `appCode`, the server automatically adopts the client's bound `system_code`.
>
> **Client SDK (geelato-auth-client)**: depend on `cn.geelato:geelato-auth-client` and use the auto-configured `AuthCenterProvisioningClient` to pull payloads with a machine token (`getPermissions(userId, mode)` / `getMenus(userId, mode)`), with optional TTL caching and `invalidate(userId)` — no hand-written HTTP or token management. Configure `geelato.auth.client.base-url/client-id/client-secret`.

### 4.3 Pull all permission points of a subsystem (mirror rebuild)

```
GET /api/provisioning/app/{appCode}/permissions?type=dp&tenantCode=yyy
```

Returns `PermissionListItem[]` (`id/code/name/type/object/rule/appCode/seqNo`). `type` is optional; all types are returned when omitted.

### 4.4 Query permission version

```
GET /api/provisioning/version?appCode=xxx
```

Returns a `Long` (timestamp as an approximate version). Call it on a schedule and only trigger a full refresh when the version differs, avoiding useless pulls.

### 4.5 Standard OAuth2 introspection / userinfo (with function permission claims)

Besides the provisioning API, clients can validate tokens and directly obtain **function permissions/roles** via standard OAuth2 endpoints — enough for API/button-level authorization without the provisioning API:

- `POST /oauth2/introspect` (RFC 7662, client auth required): in addition to standard fields (`active/subject/scope/exp`), **extension claims `permissions` (function permission codes) and `roles` (role codes)** are included. Pass `appCode` to scope to a subsystem (defaults to `__global__`).
- `GET /oauth2/userinfo-oidc?access_token=xxx&appCode=xxx`: claims contain `permissions` / `roles`.

> **Function permissions** can come straight from introspection/userinfo; **data permission rules** (type=dp `rule`) still go through 4.1 (larger payloads, matched per entity, suitable for local mirroring). Together they cover both layers.

## 5. Integration Steps

> The following steps apply to any geelato-orm based subsystem. A runnable reference implementation is the `geelato-sample-auth` example project (remote client demo, function + data permissions, zero Sa-Token dependency, including Bearer filter / pull-cache-mirror / injector / AOP / change listener).

### Step 1: create the local mirror table

Execute the following DDL in the subsystem database (stores the parsed permission snapshot pulled from the Auth Center):

```sql
CREATE TABLE IF NOT EXISTS auth_perm_mirror (
    id              VARCHAR(64)  NOT NULL COMMENT 'PK (=permission id or userId+permissionId)',
    user_id         VARCHAR(64)           DEFAULT NULL COMMENT 'user id (user-level snapshot)',
    perm_type       VARCHAR(8)   NOT NULL COMMENT 'dp/mp/cp/ep/fp',
    entity          VARCHAR(255)          DEFAULT NULL COMMENT 'controlled entity/api path',
    name            VARCHAR(128)          DEFAULT NULL COMMENT 'permission/rule name',
    rule            VARCHAR(1024)         DEFAULT NULL COMMENT 'data permission rule (with placeholders)',
    weight          INT          NOT NULL DEFAULT 0,
    perm_code       VARCHAR(128)          DEFAULT NULL COMMENT 'function/model permission code',
    menu_id         VARCHAR(64)           DEFAULT NULL COMMENT 'menu id (menu snapshot)',
    app_code        VARCHAR(128)          DEFAULT NULL,
    tenant_code     VARCHAR(64)           DEFAULT NULL,
    synced_at       DATETIME              DEFAULT NULL COMMENT 'last sync time',
    PRIMARY KEY (id),
    KEY idx_apm_user (user_id, perm_type),
    KEY idx_apm_entity (entity, perm_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='auth center permission mirror';
```

### Step 2: implement the permission puller (Bearer → provisioning API → cache + mirror)

Use the **current user's access_token** (`Authorization: Bearer`) to call the 4.1 API, map the result into your runtime permission model and write it into the in-memory cache and mirror table:

```java
@Service
public class AuthCenterPermissionPuller {
    private final RestTemplate restTemplate = new RestTemplate();
    private final JdbcTemplate jdbc;
    @Value("${geelato.permission.auth-center-base-url}")
    private String baseUrl;

    @SuppressWarnings("unchecked")
    public List<Permission> pull(String userId, String appCode, String tenantCode, String bearerToken) {
        // 1. Call the provisioning API with the user Bearer token
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/provisioning/user/" + userId)
                .queryParam("appCode", appCode).queryParam("tenantCode", tenantCode).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        Map<String, Object> resp = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();

        // 2. Unwrap the ApiResult envelope — note the fields are status="success" / code=20000
        if (resp == null || !"success".equals(resp.get("status"))) return List.of();
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        List<Map<String, Object>> dps = (List<Map<String, Object>>) data.getOrDefault("dataPermissions", List.of());

        // 3. Map to Permission and overwrite the mirror (delete then insert)
        //    dataPermissions items are code/name/rule/object/seqNo (unified five-type structure)
        jdbc.update("DELETE FROM auth_perm_mirror WHERE user_id = ?", userId);
        List<Permission> result = new ArrayList<>();
        for (Map<String, Object> dp : dps) {
            String entity = (String) dp.get("object");
            String rule = (String) dp.get("rule");
            int weight = dp.get("seqNo") == null ? 0 : ((Number) dp.get("seqNo")).intValue();
            jdbc.update("INSERT INTO auth_perm_mirror(id,user_id,perm_type,entity,name,rule,weight,perm_code,synced_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,NOW())",
                    UUID.randomUUID().toString(), userId, "dp", entity, (String) dp.get("name"), rule, weight, (String) dp.get("code"));
            Permission p = new Permission();
            p.setEntity(entity); p.setName((String) dp.get("name"));
            p.setRule(rule); p.setWeight(weight);
            result.add(p);
        }
        return result;   // caller writes the in-memory cache; on Auth Center outage degrade to the mirror table (section 8)
    }
}
```

> **Easier**: depend on `cn.geelato:geelato-auth-client` and skip the hand-written HTTP/token/unwrapping code above — the auto-configured `AuthCenterProvisioningClient` calls the provisioning API with a client_credentials machine token (`getPermissions(userId, mode)` / `getMenus(userId, mode)`), with optional TTL caching and `invalidate(userId)`; just configure `geelato.auth.client.base-url/client-id/client-secret`. Pass `mode=full` when you need mirror persistence (the payload then carries `id/type/audit timestamps` plus full `roles`/`menus`).

### Step 3: implement `FluentQueryFilterInjector` (data permission enforcement, the core)

This is where data permissions actually take effect. Implement a Spring bean: get the current user → match rules by entity name → replace placeholders → append to the query condition:

```java
@Component
public class DataPermissionInjector implements FluentQueryFilterInjector {

    @Value("${geelato.permission.data.enabled:true}")
    private boolean enabled;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void inject(QueryCommand command, MetaQuery query) {
        // 1. Current user context (ThreadLocal, populated by the subsystem request filter)
        User user = SecurityContext.getCurrentUser();
        if (user == null) return;

        // 2. Match data permission by queried entity (largest weight wins per entity)
        String entity = command.getEntityName();
        if (!StringUtils.hasText(entity)) return;
        Permission dp = user.getDataPermissionByEntity(entity);
        if (dp == null || !StringUtils.hasText(dp.getRule())) return;

        // 3. Replace #currentUser.xxx# with literal values
        String rule = PermissionRuleUtils.replaceRuleVariable(dp, user);
        if (!StringUtils.hasText(rule)) return;

        // 4. Write into originalWhere (raw SQL fragment), AND-merged with existing conditions
        String existing = command.getOriginalWhere();
        command.setOriginalWhere(StringUtils.hasText(existing)
                ? "(" + existing + ") AND (" + rule + ")"
                : rule);
    }
}
```

> **Key notes:**
> - The injection point runs inside `QueryCommandAdapter.adapt()`, **before datasource switching**, so the injected condition applies to whichever datasource the query finally hits — multi-database subsystems are supported naturally.
> - Data permissions must be written via `command.setOriginalWhere(...)` (raw SQL fragment path); **not** `QueryCommand.ACL` — ACL only participates in cache signatures and is never appended to SQL.
> - When multiple `FluentQueryFilterInjector` beans exist, at most one may have `isEnabled()==true` at runtime, otherwise an `IllegalStateException` is thrown.
> - The no-rule-for-entity policy is up to the subsystem: the demo appends nothing (= full visibility for comparison); the platform defaults to least privilege (`creator='<userId>'`).
> - The request filter must call `SecurityContext.setCurrentUser(user)` before business execution (including `deptId/buId` etc. for placeholder replacement) and `SecurityContext.clear()` afterwards.

### Step 4: subscribe to change notifications (Redis pub/sub)

Receive change events pushed by the Auth Center and invalidate the local cache (the next request re-pulls and refreshes the mirror):

```java
@Component
public class PermissionChangeListener implements MessageListener {
    public static final String CHANNEL = "geelato:permission:change";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RedisMessageListenerContainer container;

    public PermissionChangeListener(RedisMessageListenerContainer container) {
        this.container = container;
    }

    @PostConstruct
    public void register() {
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode evt = MAPPER.readTree(new String(message.getBody()));
            String userId = evt.path("userId").asText(null);
            if (userId != null && !userId.isBlank()) {
                // invalidate this user's permission snapshot cache
            } else {
                // appCode/global change: invalidate all caches
            }
        } catch (Exception ignored) {
        }
    }
}
```

### Step 5: function/API permission authorization

Function permission codes (`fp`) control API access and frontend buttons. **The claim source is the `permissions`/`roles` extension claims of introspection/userinfo** (see 4.5); the subsystem authorizes against them **without Sa-Token**:

```java
// Recommended (protocol-level, zero Sa-Token): custom annotation + AOP reading cached introspection claims
@RequirePermission("order:export")
@PostMapping("/api/orders/export")
public void export() { ... }

@RequireRole("admin")
@GetMapping("/api/orders/audit")
public void audit() { ... }
```

Frontend button control: call `GET /oauth2/userinfo-oidc?access_token=xxx&appCode=xxx`, take `permissions`/`roles` from the claims and check with a `v-permission` directive or `hasPermission(code)`. Element permissions (`ep`) work the same way, using `elementPermissions` from the 4.1 payload.

## 6. Data Permission Rule Syntax

The data permission `rule` field holds a SQL fragment supporting `#currentUser.<field>#` placeholders, replaced with literal user values by the subsystem's injector at query time.

### 6.1 Supported placeholder fields (whitelist)

| Placeholder | Meaning |
|---|---|
| `#currentUser.userId#` | user id |
| `#currentUser.orgId#` | organization id |
| `#currentUser.defaultOrgId#` | default organization id |
| `#currentUser.cooperatingOrgId#` | cooperating organization id |
| `#currentUser.companyId#` | company id |
| `#currentUser.extendId#` | extension id |
| `#currentUser.deptId#` | department id |
| `#currentUser.buId#` | business unit id |
| `#currentUser.weixinUnionId#` | WeChat union id |
| `#currentUser.weixinWorkUserId#` | WeCom user id |
| `#currentUser.tenantCode#` | tenant code |
| `#currentUser.defaultOrg.orgId#` | default org → organization id |
| `#currentUser.defaultOrg.deptId#` | default org → department id |
| `#currentUser.defaultOrg.companyId#` | default org → company id |
| `#currentUser.defaultOrg.extendId#` | default org → extension id |

Fields outside the whitelist are **rejected** when creating/updating permissions in the Auth Center.

### 6.2 Built-in four data scopes

The Auth Center pre-seeds 4 generic scope permissions (`__global__`, usable by all subsystems):

| Code | Name | Rule | Weight |
|---|---|---|---|
| `&all` | View all | `1=1` | 4 |
| `&myBusiness` | View company/BU | `bu_id=#currentUser.buId#` | 3 |
| `&myDept` | View department | `dept_id=#currentUser.deptId#` | 2 |
| `&myself` | View own | `creator=#currentUser.userId#` | 0 |

**Merging multiple rules**: when a user has multiple data permissions for the same entity (e.g. role-granted "view department" + directly granted "view own"), the **largest** `weight` wins (widest scope first); entities are computed independently.

### 6.3 Custom rule examples

```text
# Only rows created by me and enabled
creator=#currentUser.userId# AND status=1

# Multiple department columns
(dept_id=#currentUser.deptId# OR bu_id=#currentUser.buId#)

# By default organization's department
dept_id=#currentUser.defaultOrg.deptId#
```

## 7. Configuration Reference

Add the following to the subsystem's `application.properties`:

```properties
# Permission source: platform (local query) / auth-center (delivered by Auth Center)
geelato.permission.source=auth-center

# Auth Center base URL
geelato.permission.auth-center-base-url=http://auth-center-host:9000

# This subsystem's appCode (equals oauth_client.system_code)
geelato.permission.app-code=community

# OAuth client credentials of this subsystem (client auth for /oauth2/introspect, to read permissions/roles claims)
geelato.permission.client-id=community
geelato.permission.client-secret=xxx

# Introspection result cache per token, in seconds
geelato.permission.introspect-cache-seconds=30

# Data permission injection switch
geelato.permission.data.enabled=true

# Scheduled full refresh interval (minutes), as notification fallback
geelato.permission.refresh-interval-minutes=5

# Redis connection (for notifications; same instance/cluster as the Auth Center)
spring.data.redis.host=xxx
spring.data.redis.port=6379
spring.data.redis.password=xxx
```

## 8. Fault Tolerance and Degradation

| Scenario | Behavior |
|---|---|
| Auth Center briefly unavailable | Subsystem reads the local `auth_perm_mirror` mirror (degraded to the latest snapshot); business keeps running |
| Redis notification lost | Scheduled full refresh (`refresh-interval-minutes`) guarantees eventual consistency |
| First login of a new user, mirror empty | One pull attempt; on failure no data permissions — least privilege, empty result |
| Cache TTL | User context 5–30 min recommended; permission changes actively invalidate via notification |

**Suggested caching strategy**: user profile 30 minutes, data permissions 5 minutes (aligned with the refresh interval); invalidate immediately on notification.

## 9. FAQ

**Q1: Why do data permissions only apply to `MetaFactory.query(...)` and not JSON/text MQL queries?**
A: `FluentQueryFilterInjector` only covers the Fluent DSL path. For MQL query paths, implement geelato-core's `MqlQueryFilterInjector` SPI (a separate SPI).

**Q2: How is multi-tenancy handled?**
A: All permission tables carry `tenant_code`. The provisioning API accepts a `tenantCode` parameter; the subsystem passes the tenant code via the `X-Tenant-Code` header or login state, and the Auth Center computes permissions per tenant.

**Q3: How quickly do permission changes take effect?**
A: **Seconds** via Redis pub/sub; **minutes** via the scheduled fallback if notifications fail. To troubleshoot delays, check that the subsystem subscribes to `geelato:permission:change` and that cache invalidation works.

**Q4: Our subsystem is not based on geelato-orm (e.g. plain MyBatis). How do we integrate?**
A: The data permission injector depends on geelato-orm's SPI; other stacks need their own SQL interception (e.g. a MyBatis plugin rewriting SQL to append the data permission where clause). Function/menu permissions do not depend on the ORM — any stack can integrate via the provisioning API + claim-based authorization.

**Q5: What is appCode for?**
A: It identifies which subsystem a permission belongs to. Set `appCode` when configuring permissions (or leave empty for global `__global__`). A user's effective permissions in a subsystem = appCode-specific permissions + global permissions, so subsystems stay isolated while sharing common rules.

## Appendix: Auth Center table reference

| Table | Description |
|---|---|
| `auth_role` | Role (code/name/appCode/enableStatus) |
| `auth_menu` | Menu (tree, parentId/type=catalog,menu,button/permissionCode) |
| `auth_permission` | Permission point (unified, type=dp,mp,cp,ep,fp / object / rule / seqNo) |
| `auth_role_r_permission` | role-permission mapping |
| `auth_role_r_menu` | role-menu mapping |
| `auth_user_r_role` | user-role mapping |
| `auth_org_r_role` | role-department mapping (defines which roles a department has) |
| `auth_user_r_org` | user-department membership (multiple attachments, no role inheritance) |
| `auth_user_r_org_role` | user-attached department role (user × source department × role; the only way department roles take effect) |
| `auth_user_r_permission` | direct user-permission grant |

> These tables are created automatically by the Auth Center's Flyway migrations. Subsystems **do not** create them locally (they only keep the local `auth_perm_mirror` mirror table).

## 10. Integrator self-service management (Open API)

Besides the read-only provisioning API, the Auth Center exposes **self-service management APIs** (`/api/open/v1/**`): with a `client_credentials` machine token, integrators manage **their own** roles, menus, permission points, grant relationships, users, organizations and sessions to build their own admin UI — operating on the same data as the Auth Center admin console, with automatic caller isolation (appCode/tenant binding, no cross-tenant access).

Key points:

- Token: `POST /oauth2/client_token` (grant_type=client_credentials); machine tokens are the only valid credential for the Open API; user tokens are rejected.
- Isolation: roles/menus/permission points are strictly isolated by appCode (`__global__` is read-only); users/organizations are shared at tenant level.
- Side effects: writes trigger the `geelato:permission:change` notification and version bump; all writes are recorded in the audit hash chain.
- Hardening: when a client_credentials token calls `/api/provisioning/**`, the appCode parameter must equal the caller's own system_code, otherwise 403.
