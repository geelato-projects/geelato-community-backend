---
title: Platform Defaults
sidebar_label: Platform Defaults
---

# Platform Defaults

This page documents every default implementation of the security contracts by layer, together with the Spring assembly and the replacement paths for host projects. Contracts live in `geelato-security`; framework-layer defaults (snapshot providers, bridging, refresh coordination) live in `geelato-core`; platform-layer JDBC loaders and the unified assembly live in `geelato-web-platform`. All three share the package name `cn.geelato.security` (a split package, kept deliberately for import consistency).

## Layer Map

| Layer | Module | Defaults |
| --- | --- | --- |
| Framework | `geelato-core` | `DefaultOrgProvider`, `OrgRelationResolver` (package-private), `DefaultUserProvider`, `DefaultRoleProvider`, `DefaultUserOrgInfoEnricher`, `DefaultUserExtInfoEnricher`, `BridgeSecurityProvider`, `SecurityDataRefreshCoordinator` |
| Platform | `geelato-web-platform` | `DBOrgSnapshotLoader`, `DBUserSnapshotLoader`, `DBRoleSnapshotLoader`, `SecurityProviderConfiguration` (assembly) |

The default architecture in one sentence: **loaders assemble the platform primary DB into snapshots (platform layer), providers cache the snapshots and expose read-only queries (framework layer), and every bean is assembled `@ConditionalOnMissingBean` so it can be replaced wholesale.**

## Framework-Layer Defaults (geelato-core)

### DefaultOrgProvider

Constructor takes an `OrgSnapshotLoader`; internally it holds an `AtomicReference<OrgSnapshot>` and an `AtomicReference<OrgRelationResolver>`.

| Method | Implementation |
| --- | --- |
| `getOrg` | Current snapshot's `getOrg` |
| `getAllOrgs` | All orgs of the current snapshot (value-copied into an `ArrayList`) |
| `getDeptId` | Delegates to `OrgRelationResolver.resolveDeptId` |
| `getCompanyId` | Delegates to `OrgRelationResolver.resolveCompanyId` |
| `getCompanyName` | `resolveCompanyOrg` → the company entity's `name`, `""` when absent |
| `getCompanyExtendId` | `resolveCompanyOrg` → the company entity's `extendId`, `null` when absent |
| `refresh` | `loader.load()`, then atomically swaps both references (snapshot and resolver) |

### OrgRelationResolver (package-private)

A relation resolver over the full org index (`orgId → Org`); every resolution walks up the parent chain with a visited set guarding against cycles.

| Method | Resolution rule |
| --- | --- |
| `resolveDeptId(orgId)` | If the org itself is a `department`, return it; otherwise walk the parent chain for the first `department` ancestor; when none exists, return the top-level ancestor (last node of the chain) |
| `resolveCompanyId(orgId)` | Delegates to `resolveCompanyOrg` for the ID, `""` when absent |
| `resolveCompanyExtendId(orgId)` | Delegates to `resolveCompanyOrg` for the `extendId`, `null` when absent |
| `resolveCompanyOrg(orgId)` | If the org itself is a `company`, return it; otherwise take the first ancestor with `type=company` along the parent chain, **falling back to the top-level ancestor when none exists**; `null` when the org does not exist |

This implements the "company ownership" rule from [Security Model](model.md): company first, top-level fallback.

### DefaultUserProvider

Constructor takes a `UserSnapshotLoader` + `UserOrgInfoEnricher` (+ optional `UserExtInfoEnricher`); it holds an `AtomicReference<UserSnapshot>`.

| Method | Implementation |
| --- | --- |
| `getUser` | Current snapshot's `getUser` |
| `getAllUsers` | All users of the current snapshot (value-copied), consumed by `BridgeSecurityProvider` for reverse resolution |
| `getUserByExtendKey` | Normalizes via `normalizeType`, then queries the snapshot's extended-identity two-level index |
| `refresh` | `loader.load(userOrgInfoEnricher)` → per-user `enrich` via the ext enricher when present → atomically swap the snapshot reference |

### DefaultRoleProvider

Same pattern as `DefaultOrgProvider`: constructor takes a `RoleSnapshotLoader`; `getRole`/`getRoleByCode`/`getOrgRoles` query the snapshot directly, and `refresh` loads then swaps atomically.

### DefaultUserOrgInfoEnricher

Constructor takes an `OrgProvider`, relying on the org snapshot for derived resolution.

`enrich(User)`:

1. `orgId` takes `firstNonBlank(user.orgId, resolveOrgId(user))`; `resolveOrgId` picks the first attachment with `defaultOrg=true` from `userOrgs`, falling back to the first non-empty attachment.
2. Once resolved, it fills: `orgName`, `companyId`, `companyName`, `extendId`, `buId` (= companyId), `buName` (= companyName); when `deptId` is empty it fills `getDeptId`.
3. `defaultOrgId` is resolved the same way (`defaultOrg=true` first) and `defaultOrgName` is filled.

`enrich(UserOrg)`: when `orgId` is non-empty, fills `name` (when empty), `deptId`, `companyId`, `extendId`; `fullName` falls back to `name` when empty.

### DefaultUserExtInfoEnricher

The default no-op: when the upper layer provides no filler, `extendInfo` stays empty (no extension info bound).

### BridgeSecurityProvider

The local-DB bridging implementation of the `SecurityProvider` contract: it combines the data planes of the existing `OrgProvider`/`UserProvider`/`RoleProvider` and derives contract queries (including people resolution and the org tree) locally, without any remote calls — for subsystems sharing the platform DB or network, where the auth center is unnecessary.

Data-plane requirements:

| Capability | Depends on |
| --- | --- |
| Forward single/batch queries | The single-query capability of the three providers |
| People resolution (people under a department/role/department role) | `UserProvider.getAllUsers()` (snapshot implementations support it natively; direct-lookup implementations returning empty make people resolution return empty too) |
| Org tree (sub-orgs / ancestor chain) | `OrgProvider.getAllOrgs()`; the ancestor chain only needs single queries (walking up by pid), no full list required |
| Role-related queries | `RoleProvider`; degrades to empty/null when absent (null) |

Implementation matrix:

| Contract method group | Implementation |
| --- | --- |
| Single-entity queries | Fully implemented; `getUserByLoginName` goes through the `loginName` extended key; `getOrgByCode` scans all orgs matching by `code` |
| Batch queries | Per-ID single queries aggregated; missing IDs are skipped |
| People resolution | In-memory filtering over `getAllUsers`: departments by membership (`userOrgs.orgId` hits the target org set), roles by direct role `code`, department roles by `userOrgRoles` `orgId`+`code` (the user need not be a member); results deduplicated by user |
| Paging | Results sorted by `userId` then sliced, keeping pagination stable under the same data snapshot |
| `querySubOrgs` | Builds a pid → direct-children index from all orgs, expands level by level with BFS, visited set guarding cycles |
| `queryAncestorOrgs` | Walks up the pid chain, excluding itself |
| `queryOrgRoles` | Aggregates `RoleProvider.getOrgRoles` over each target org (optionally including descendants) |
| Permission queries | **Not implemented**, keeping the default empty return — permission-domain data belongs to the auth center; use its `AuthCenterSecurityProvider` |

Every call derives from the providers' current snapshots immediately, keeping no cache of its own — naturally consistent with provider refresh semantics.

### SecurityDataRefreshCoordinator

The unified refresh coordinator for the three snapshots; the method semantics and refresh chain are in [Snapshots & Loading](snapshot.md). Assembly note here: `UserProvider`/`RoleProvider` are `@Autowired(required = false)`, so direct-lookup implementations managing their own data plane can omit them.

## Platform-Layer JDBC Loading (geelato-web-platform)

All three DB loaders take the `@Qualifier("primaryJdbcTemplate")` `JdbcTemplate` in their constructors and read only rows with `del_status = 0`.

### DBOrgSnapshotLoader

| Item | Content |
| --- | --- |
| Source | `platform_org` |
| Query | `select id, pid, name, tenant_code, type, code, extend_id from platform_org where del_status = 0` |
| Assembly | Skips rows with empty `id`, builds `orgById` in load order (`LinkedHashMap`), and publishes via `OrgSnapshot.from` |

### DBUserSnapshotLoader

Four query stages, assembled into `userById` stage by stage:

| Stage | Source | Assembly |
| --- | --- | --- |
| 1. User basics | `platform_user where del_status = 0`: `id`/`login_name`/`name`/`tenant_code`/`weixin_unionId`/`weixin_work_userId` | User objects; also builds the three extended-identity indexes `loginName`/`weixinUnionId`/`weixinWorkUserId` |
| 2. Direct roles | `platform_role` join `platform_role_r_user` (both `del_status = 0`) | `UserRole` (id/code/name/type/tenantCode) |
| 3. Org-attached roles | `platform_user_r_org_role` join `platform_role` (both `del_status = 0`) | `UserOrgRole` (role fields + `org_id`) |
| 4. Org attachments | `platform_org_r_user where del_status = 0`: `user_id`/`org_id`/`org_name`/`default_org` | `UserOrg`; `default_org` judged as `"1"` or `true`; each row passes through `userOrgInfoEnricher.enrich(uo)`; when `defaultOrg=true`, fills back `user.orgId`/`user.defaultOrgId` |

Finale: per-user `enrich(user)` fills derived fields; `userRoles`/`userOrgs`/`userOrgRoles` become empty lists when null, otherwise `List.copyOf` immutabilized; finally `UserSnapshot.from(userById, extendIndex)` publishes.

### DBRoleSnapshotLoader

| Item | Content |
| --- | --- |
| Role entities | `SELECT id, code, name, type, tenant_code FROM platform_role WHERE del_status = 0` → `roleById`; fields normalized with `trimToNull` |
| Org-attached roles | `SELECT org_id, role_id FROM platform_org_r_role WHERE del_status = 0` → copies the role into an `OrgRole` (copyRole) with `orgId`; joins whose role is missing are skipped |
| Publish | `RoleSnapshot.from(roleById, orgRolesByOrgId)` |

## Spring Assembly: SecurityProviderConfiguration

`cn.geelato.web.platform.boot.SecurityProviderConfiguration` in `geelato-web-platform` provides 10 default beans, **all `@ConditionalOnMissingBean`** — as soon as the host defines a bean of the same type, the default assembly steps aside:

| Bean | Default class | Dependencies |
| --- | --- | --- |
| `OrgSnapshotLoader` | `DBOrgSnapshotLoader` | `primaryJdbcTemplate` |
| `OrgProvider` | `DefaultOrgProvider` | orgSnapshotLoader |
| `UserOrgInfoEnricher` | `DefaultUserOrgInfoEnricher` | orgProvider |
| `UserExtInfoEnricher` | `DefaultUserExtInfoEnricher` | — |
| `UserSnapshotLoader` | `DBUserSnapshotLoader` | `primaryJdbcTemplate` |
| `UserProvider` | `DefaultUserProvider` | userSnapshotLoader + userOrgInfoEnricher + userExtInfoEnricher |
| `RoleSnapshotLoader` | `DBRoleSnapshotLoader` | `primaryJdbcTemplate` |
| `RoleProvider` | `DefaultRoleProvider` | roleSnapshotLoader |
| `SecurityProvider` | `BridgeSecurityProvider` | orgProvider + userProvider + roleProvider |
| `SecurityDataRefreshCoordinator` | `SecurityDataRefreshCoordinator` | orgProvider + userProvider + roleProvider |

Notes:

- Platform applications do not depend on `geelato-auth-client`, so there is no remote-implementation competition; subsystems integrated with the auth center get `AuthCenterSecurityProvider` (an open-API implementation, not in this repository) auto-assembled by auth-client, which does not interfere with this bean.
- At startup, `SecurityDataRefreshCoordinator`'s `@PostConstruct` completes the first load of the three snapshots; at runtime `OrgController.refreshOrg()`/`UserController.refreshUser()` trigger refreshes after directory changes.

## How to Replace

Three replacement options, from smallest to largest change:

### Option 1: Replace an Enricher (minimal)

The user snapshot is incomplete, or org ownership needs extra derivation: provide a custom `UserOrgInfoEnricher` (org derivations) or `UserExtInfoEnricher` (`extendInfo` filling) bean — no need to touch the whole provider set. Fits feeding `extendInfo` with business binding data (git binding and other agreed keys).

### Option 2: Keep Providers, Replace Loaders (recommended)

You accept the default "snapshot + read-only query" model but the data source is not the platform DB: provide custom `OrgSnapshotLoader`/`UserSnapshotLoader`/`RoleSnapshotLoader` beans. You keep reusing `Default*Provider`, `BridgeSecurityProvider`, and `SecurityDataRefreshCoordinator`, while snapshot data can come from external services, a config center, Redis, or aggregation APIs. This is usually the more robust path.

### Option 3: Replace Providers Wholly (data plane does not fit snapshots)

You already have a complete org/user/role access model (external IAM, org-center API, no snapshot model): provide custom `OrgProvider`/`UserProvider`/`RoleProvider` (or `SecurityProvider`) beans directly. When replacing `UserProvider`, the extended identity mapping (`getUserByExtendKey`) must remain available, or WeChat/WeCom extended-key authentication breaks.

### Minimal Implementation Checklist

A custom `UserProvider` minimally implements: `getUser(String)`, `getUserByExtendKey(String, String)`, `refresh()`. If the returned `User` already carries `userRoles`/`userOrgs`/`userOrgRoles`, the three relation queries' default implementations can be reused as-is.

A custom `OrgProvider` minimally implements: `getOrg(String)`, `getDeptId(String)`, `getCompanyId(String)`, `refresh()`. The runtime needs not only org names but also department and company/BU derivations — an incomplete relation resolution directly degrades the user's org-dimension context after authentication.

### Semantics to Keep Stable

- `refresh()` reloads the latest snapshot; never implement it as a side-effectful heavy transaction.
- Org resolution (`getDeptId`/`getCompanyId`/`getCompanyName`) keeps stable semantics; consumers include post-auth org-dimension backfill and snapshot loading.
- The three extended identity mappings (`loginName`/`weixinUnionId`/`weixinWorkUserId`) of `getUserByExtendKey` remain available.

## Summary

- Defaults come in two layers: the framework layer (`geelato-core`) provides snapshot providers, relation resolution, bridged queries, and refresh coordination; the platform layer (`geelato-web-platform`) provides the three JDBC loaders and the unified assembly.
- Company-ownership resolution converges in `OrgRelationResolver`: the first ancestor with `type=company` up the parent chain, top-level fallback when none exists.
- All assembly is `@ConditionalOnMissingBean`: enricher → loader → whole provider, an increasing scope of change; replacing loaders is the recommended integration path.

## Suggested Reading

- [Loading Abstractions](loader.md) — the interfaces you implement when replacing loaders
- [Snapshots & Loading](snapshot.md) — refresh chain and immutable semantics
- [Permission Integration](../authentication/permission-integration.md) — unified permission management on the AuthServer side
