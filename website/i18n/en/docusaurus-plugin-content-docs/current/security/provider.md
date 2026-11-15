---
title: Provider Contracts
sidebar_label: Provider Contracts
---

# Provider Contracts

This page documents the four Provider interfaces of `geelato-security` and the paging contract, method by method. Providers are the read boundary for security data: consumers depend on these interfaces only, never on concrete implementations; every method defines read capability only, with no storage or network details.

## Map

| Interface | Paradigm | Responsibility |
| --- | --- | --- |
| `OrgProvider` | Snapshot-style full read + refresh | Org read queries and org relation resolution (department / company / BU) |
| `UserProvider` | Snapshot-style full read + refresh | User read queries, relation reads, extended identity lookup |
| `RoleProvider` | Snapshot-style full read + refresh | Role read queries, org-attached role queries |
| `SecurityProvider` | On-demand single-point queries | Single / batch / people-resolution / hierarchy / permission / decision queries over four entities and four attachment relation types |

The first three serve snapshot-style full loading (paired with [Loaders](loader.md) and [Snapshots](snapshot.md)); `SecurityProvider` serves on-demand queries consumed by workflow assignee resolution, rule/exception alert participant resolution, and data-permission checks. The two paradigms coexist, each with its own job.

## OrgProvider

| Method | Signature | Contract semantics |
| --- | --- | --- |
| `getOrg` | `Org getOrg(String orgId)` | Look up an org by ID, `null` when absent (abstract) |
| `getAllOrgs` | `List<Org> getAllOrgs()` | All orgs; default returns an empty list, snapshot implementations should override |
| `containsOrg` | `boolean containsOrg(String orgId)` | Whether the org exists; default derives from `getOrg != null` |
| `getOrgName` | `String getOrgName(String orgId)` | Org name; default via `getOrg`, `""` when absent |
| `getDeptId` | `String getDeptId(String orgId)` | Org → owning department ID relation-resolution entry (abstract; the resolution rule is defined by the implementation) |
| `getCompanyId` | `String getCompanyId(String orgId)` | Org → owning company ID relation-resolution entry (abstract) |
| `getCompanyName` | `String getCompanyName(String orgId)` | Owning company name; default via `getCompanyId`. When no company exists on the chain it matches `getCompanyId`, taking the top-level ancestor |
| `getCompanyExtendId` | `String getCompanyExtendId(String orgId)` | The company's `extendId`, same source as `getCompanyId`; falls back to the top-level ancestor's when no company exists |
| `getBuId` | `String getBuId(String orgId)` | Business unit ID; default is `getCompanyId` |
| `refresh` | `void refresh()` | Reload the snapshot (abstract) |

`getDeptId`/`getCompanyId` are not mere dictionary lookups but org-relation resolution entries: they derive department and company from tree relations. The default resolution algorithm (first `department`/`company` up the chain, top-level fallback) is documented in [Platform Defaults](platform-defaults.md).

## UserProvider

| Method | Signature | Contract semantics |
| --- | --- | --- |
| `getUser` | `User getUser(String userId)` | Look up a user by ID (abstract) |
| `getUserName` | `String getUserName(String userId)` | User name; default via `getUser`, `""` when absent |
| `getAllUsers` | `List<User> getAllUsers()` | All users, including the `userRoles`/`userOrgs`/`userOrgRoles` attachments, so that `SecurityProvider` bridging can resolve people under departments/roles. Snapshot implementations should override; direct-lookup implementations may not support it (empty list, and the bridged people-resolution methods then return empty) |
| `containsUser` | `boolean containsUser(String userId)` | Whether the user exists; default derives from `getUser != null` |
| `getUserById` | `User getUserById(String userId)` | Same as `getUser`; default delegates |
| `getUserByExtendKey` | `User getUserByExtendKey(String extendKey, String type)` | Look up a user by extended identity key (abstract); `type` is normalized by `normalizeType` |
| `getUserRoles` | `List<UserRole> getUserRoles(String userId)` | Direct roles; default aggregates from `User.userRoles`, empty when absent |
| `getUserOrgs` | `List<UserOrg> getUserOrgs(String userId)` | Org attachments; default aggregates from `User.userOrgs` |
| `getUserOrgRoles` | `List<UserOrgRole> getUserOrgRoles(String userId)` | Org-attached roles; default aggregates from `User.userOrgRoles` |
| `normalizeType` | `String normalizeType(String type)` | Extended key type normalization, see table below |
| `refresh` | `void refresh()` | Reload the snapshot (abstract) |

`normalizeType` rules (case-insensitive):

| Input | Normalized to |
| --- | --- |
| `loginName` / `login_name` | `loginName` |
| `weixinUnionId` / `weixin_unionId` / `unionId` | `weixinUnionId` |
| `weixinWorkUserId` / `weixin_work_userId` / `workUserId` | `weixinWorkUserId` |
| anything else | returned as-is |

This is why the runtime auth chain can use `WeixinUnionId`, `WeixinWorkUserId`, and similar extended identity entrances directly (see [Authentication & Authorization](../authentication/security-authentication.md)).

## RoleProvider

| Method | Signature | Contract semantics |
| --- | --- | --- |
| `getRole` | `Role getRole(String roleId)` | Look up by role ID, `null` when absent (abstract) |
| `getRoleByCode` | `Role getRoleByCode(String roleCode)` | Look up by role code; business code always references roles by `code` (abstract) |
| `getOrgRoles` | `List<OrgRole> getOrgRoles(String orgId)` | Roles attached to the org (department roles, direct department only, no upward recursion). Small cardinality, not paged; default returns an empty list |
| `refresh` | `void refresh()` | Reload the snapshot (abstract) |

## SecurityProvider

The security data provider: on-demand single-point queries over four entities (org / user / role / permission) and four attachment relation types. Methods come in six groups, all default-empty so an integrating system can implement just the methods it needs. The constant `MAX_AGGREGATE_SIZE = 10_000` caps list aggregation: exceeding it throws `IllegalStateException` — fail rather than silently truncate; use the paging overloads for large result sets.

### 1. Single-entity queries (get)

| Method | Contract semantics |
| --- | --- |
| `getUserById(String userId)` | User by ID; `null` when absent or not visible |
| `getUserByLoginName(String loginName)` | User by login name (the find-person-by-login-name entry for notification/alerting) |
| `getOrgById(String orgId)` | Org by ID |
| `getOrgByCode(String orgCode)` | Org by code |
| `getRoleById(String roleId)` | Role by ID (backfill scenario: you already hold role IDs, e.g. results of `getUserRoles`/`queryOrgRoles`) |
| `getRoleByCode(String roleCode)` | Role by code (business code and data-permission rules always use code) |
| `getUserRoles(String userId)` | The user's roles as a `UserRoles` aggregate, separating direct roles from org-attached roles (with source org); effective roles via `UserRoles.getEffectiveRoles()` |
| `getUserOrgs(String userId)` | Org attachments (membership; multiple orgs returned together; unrelated to role attachment) |

### 2. Batch queries (query, avoiding network N+1)

| Method | Contract semantics |
| --- | --- |
| `queryUsersByIds(List<String> userIds)` | Batch query by user IDs; empty ids return an empty list |
| `queryOrgsByIds(List<String> orgIds)` | Batch query by org IDs |
| `queryRolesByIds(List<String> roleIds)` | Batch query by role IDs |

### 3. People resolution (participant resolution)

People resolution along three dimensions (org / role / org-role) is the high-frequency entry for workflows and alerting. Naming pattern: the fully-parameterized `pageQuery*` is the core abstraction (implement it); flag-less overloads are default derivations (recursive by default); the `query*` full lists are default auto-pagination aggregations.

| Method | Contract semantics |
| --- | --- |
| `pageQueryUsersOfOrg(String orgId, boolean includeSubOrgs, PageParam pageParam)` | Paged people under a department (membership only, no role inheritance). `includeSubOrgs=true` includes the department and all its descendants (recursive, users deduplicated); `false` only the department itself. Core method — implement it |
| `pageQueryUsersOfOrg(String orgId, PageParam pageParam)` | Same, recursive by default |
| `queryUsersOfOrg(String orgId, boolean includeSubOrgs)` | Full list; default auto-pagination aggregation |
| `queryUsersOfOrg(String orgId)` | Same, recursive by default (department and all descendants, users deduplicated) |
| `pageQueryUsersOfRole(String roleCode, PageParam pageParam)` | Paged users directly attached to the role; queried by role code — business sides (workflow assignee configuration, alert rules) always store codes. Core method |
| `queryUsersOfRole(String roleCode)` | Full list; default auto-pagination aggregation |
| `pageQueryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs, PageParam pageParam)` | Paged users of a department role (attached via "user-org role"; the user need not be a member of the department). Core method |
| `pageQueryUsersOfOrgRole(String orgId, String roleCode, PageParam pageParam)` | Same, recursive by default |
| `queryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs)` | Full list; default auto-pagination aggregation |
| `queryUsersOfOrgRole(String orgId, String roleCode)` | Same, recursive by default |

Full aggregation is implemented by the static `collectAll`: pages of 200 until no next page; exceeding `MAX_AGGREGATE_SIZE` throws `IllegalStateException`.

### 4. Department roles and the org tree (hierarchy traversal)

| Method | Contract semantics |
| --- | --- |
| `queryOrgRoles(String orgId, boolean includeSubOrgs)` | Roles attached to the org (roles on orgs). `true` includes the department and all descendants; `false` only direct attachments. Core method, small cardinality, not paged |
| `queryOrgRoles(String orgId)` | Same, recursive by default |
| `querySubOrgs(String orgId, boolean recursive)` | Sub-departments. `false` only direct children; `true` all descendants (level by level). Core method |
| `querySubOrgs(String orgId)` | All descendants, excluding itself |
| `queryAncestorOrgs(String orgId)` | The ancestor chain from the direct parent to the root, excluding itself |

### 5. Permission queries

| Method | Contract semantics |
| --- | --- |
| `queryPermissionsOfRole(String roleId)` | Permissions assigned to the role. Org roles are roles attached to orgs — permissions take effect through roles, so one query serves both; queried by role ID (backfill scenario) |
| `queryPermissionsOfUser(String userId)` | The user's effective permissions: direct roles ∪ org-attached roles → role permissions, then ∪ directly granted user permissions. Data permissions take effect per entity by the largest weight (consumer-side logic) |

### 6. Decision checks (Check)

| Method | Contract semantics |
| --- | --- |
| `hasRole(String userId, String roleCode)` | Whether the user has a role, judged by effective roles (direct ∪ org-attached); default derives from `getUserRoles` — override when you have a faster check (cache / dedicated endpoint) |
| `hasPermission(String userId, String permissionCode)` | Whether the user has a permission (permissions are identified by code); default derives from `queryPermissionsOfUser` — override for faster checks |

## Naming and Parameter Conventions

Naming:

| Prefix | Meaning |
| --- | --- |
| `get*` | Single-entity query (a single object, or a single user's aggregate view) |
| `query*` | Full list query |
| `pageQuery*` | Paged query |

Parameters:

- `appCode`/`tenantCode` are bound by the implementation (configuration or client identity); contract methods never carry tenant/subsystem parameters.
- Roles are always referenced by `code` (stable across environments); only batch role queries (`queryRolesByIds`) and role permission queries (`queryPermissionsOfRole`) take IDs — they serve backfill scenarios where you already hold ID lists or role objects, and are not business reference entrances.
- Orgs are always referenced by `orgId`; department-related people/role resolution carries an `includeSubOrgs` flag (`true` = the department and all descendants, recursively). **Overloads without the flag default to recursive, all descendants**; in `querySubOrgs(orgId, recursive)`, `recursive` toggles direct children / all descendants.
- All methods are default-empty; implement only what you need.

## Paging Contract: Page and PageParam

### PageParam

| Member | Meaning |
| --- | --- |
| `DEFAULT_PAGE_NUM = 1` / `DEFAULT_PAGE_SIZE = 20` | Default page number and size |
| `MAX_PAGE_SIZE = 500` | Page-size cap |
| `pageNum` | Page number, starting at 1 |
| `pageSize` | Page size, clamped to `[1, 500]` by `of()` |
| `of(int pageNum, int pageSize)` | Constructor with clamping |
| `getOffset()` | Query offset, `(pageNum - 1) * pageSize` |

### Page

| Member | Meaning |
| --- | --- |
| `items` | Current page data (null replaced by an empty list) |
| `total` | Total count |
| `pageNum` / `pageSize` | Page number and size |
| `hasMore` | Whether a next page exists (the termination signal for aggregation and paging) |
| `of(items, total, pageNum, pageSize, hasMore)` | Constructor |
| `empty(PageParam)` | An empty page; falls back to defaults when `pageParam` is null |

## Summary

- The first three providers are snapshot-style read boundaries (full loading + refresh); `SecurityProvider` is the on-demand query boundary (six method groups). The two paradigms coexist, each with its own job.
- Naming `get*`/`query*`/`pageQuery*` maps to single/full/paged; roles are referenced by code, orgs by orgId; tenant and app are bound by the implementation.
- Full aggregation has a hard cap (`MAX_AGGREGATE_SIZE`) that throws instead of silently truncating; use paging overloads for large sets.
- The default implementation's resolution algorithms and assembly are documented in [Platform Defaults](platform-defaults.md).

## Suggested Reading

- [Security Model](model.md) — what the returned objects look like
- [Loading Abstractions](loader.md) — where snapshots come from
- [Platform Defaults](platform-defaults.md) — out-of-the-box implementations and replacement options
