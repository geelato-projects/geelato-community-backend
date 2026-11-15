---
title: Security Model
sidebar_label: Security Model
---

# Security Model

This page documents the security model defined by `geelato-security`, attribute by attribute: six core entities, four attachment relation classes, and the relation rules between them. All classes live in the `cn.geelato.security` package as pure data contracts (POJOs) with no source or storage details; the platform DB column mapping is covered in [Platform Defaults](platform-defaults.md).

## Model Map

| Model | Responsibility |
| --- | --- |
| `Tenant` | Tenant, the multi-tenancy isolation dimension |
| `App` | Application (subsystem), the fourth context dimension |
| `Org` | Organization node (company / department / section, etc.), tree structure |
| `User` | User (login principal), carries attachments and permissions |
| `Role` | Role, the carrier of permissions |
| `Permission` | Permission rule (data permission / element permission) |
| `UserOrg` | User-organization attachment (membership) |
| `UserRole` | Directly attached role |
| `UserOrgRole` | User-org role (department role, with source org) |
| `OrgRole` | Org-attached role (role attached to a department) |

## Tenant and App

### Tenant

| Attribute | Type | Meaning |
| --- | --- | --- |
| `code` | `String` | Tenant code, the unique identifier for tenant isolation |
| `name` | `String` | Tenant name |
| `configuration` | `Map<String, Object>` | Tenant-level configuration (reserved; an empty `HashMap` today) |
| `properties` | `Map<String, Object>` | Tenant-level extended properties (reserved; an empty `HashMap` today) |

Constructors `Tenant(String code)` and `Tenant(String code, String name)`; both Maps are initialized empty at construction.

### App

| Attribute | Type | Meaning |
| --- | --- | --- |
| `id` | `String` | Application identifier |
| `name` | `String` | Application name |
| `version` | `String` | Application version |

`App` is the fourth `SecurityContext` thread dimension for subsystem identification; see [Security Context](context.md).

## Organization: OrgCore and Org

The org model has two layers: `OrgCore` carries the tree structure and tenant ownership; `Org` adds company ownership and node type.

### OrgCore

| Attribute | Type | Meaning |
| --- | --- | --- |
| `orgId` | `String` | Org node ID; organizations are always referenced by it |
| `code` | `String` | Org code, the stable business identifier |
| `pid` | `String` | Parent node ID; the org tree is built on it, empty at the root |
| `name` | `String` | Org name |
| `tenantCode` | `String` | Owning tenant code |

### Org (extends OrgCore)

| Attribute | Type | Meaning |
| --- | --- | --- |
| (inherited) | — | `orgId`/`code`/`pid`/`name`/`tenantCode`, see above |
| `companyId` | `String` | Owning company ID (derived from the tree, filled during loading by relation resolution) |
| `type` | `String` | Org node type, see table below; the key field for company resolution |
| `extendId` | `String` | Company extension ID, carried on the company node, derived with company ownership |

Platform values of `type` (`OrgTypeEnum`):

| Value | Meaning |
| --- | --- |
| `root` | Root org |
| `company` | Company |
| `department` | Department |
| `section` | Section |
| `factory` | Factory |
| `office` | Office |

## User: UserCore and User

### UserCore

| Attribute | Type | Meaning |
| --- | --- | --- |
| `userId` | `String` | Unique user ID (platform DB `platform_user.id`); users are always referenced by it |
| `userName` | `String` | User display name |
| `loginName` | `String` | Login name, one of the extended identity keys |
| `tenantCode` | `String` | Owning tenant code, the isolation dimension |

### User (extends UserCore)

`User` is the core of the security model; its attributes are grouped by purpose below.

**Identity and profile:**

| Attribute | Type | Meaning |
| --- | --- | --- |
| (inherited) | — | `userId`/`userName`/`loginName`/`tenantCode`, see `UserCore` |
| `jobNumber` | `String` | Job number |
| `description` | `String` | Description |
| `enName` | `String` | English name |
| `sex` | `int` | Gender; `0` female, `1` male (`UserSexEnum`) |
| `avatar` | `String` | Avatar |
| `mobilePrefix` | `String` | Mobile country dialing prefix |
| `mobilePhone` | `String` | Mobile number |
| `telephone` | `String` | Landline number |
| `email` | `String` | Email |
| `post` | `String` | Position |
| `nationCode` | `String` | Country/region code |
| `provinceCode` | `String` | Province code |
| `cityCode` | `String` | City code |
| `address` | `String` | Detailed address |

**Org ownership and derivations** (filled during loading by `UserOrgInfoEnricher`, see [Loading Abstractions](loader.md)):

| Attribute | Type | Meaning |
| --- | --- | --- |
| `orgId` | `String` | The department or section the user is directly attached to; resolved from attachments when empty (default org first) |
| `orgName` | `String` | Name of the org referenced by `orgId` |
| `defaultOrgId` | `String` | Default department ID |
| `defaultOrgName` | `String` | Default department name |
| `cooperatingOrgId` | `String` | Cooperating partner org ID |
| `deptId` | `String` | When the user is attached to a section, the ID of that section's parent department |
| `buId` | `String` | Business unit ID, filled from company ownership (same value as `companyId`) |
| `buName` | `String` | Business unit name |
| `companyId` | `String` | Owning company ID (the company node resolved from the tree) |
| `companyName` | `String` | Company name |
| `extendId` | `String` | Company extension ID, taken from the company node's `extendId` |

**Status:**

| Attribute | Type | Meaning |
| --- | --- | --- |
| `type` | `int` | Account type, see table below |
| `source` | `int` | Account source, see table below |
| `enableStatus` | `int` | Enabled flag: `0` disabled, `1` enabled |

Platform values of `type` (`UserTypeEnum`):

| Value | Meaning |
| --- | --- |
| `0` | Employee account |
| `1` | System account |
| `2` | External person |
| `3` | Virtual employee account |
| `999` | Tenant administrator |

Platform values of `source` (`UserSourceEnum`):

| Value | Meaning |
| --- | --- |
| `0` | Local user |
| `1` | System sync |

**Extended identity keys:**

| Attribute | Type | Meaning |
| --- | --- | --- |
| `weixinUnionId` | `String` | WeChat UnionID, an extended identity key |
| `weixinWorkUserId` | `String` | WeCom (WeChat Work) user ID, an extended identity key |

`loginName`, `weixinUnionId`, and `weixinWorkUserId` form the three extended identity entrances for extended-key authentication (see [Authentication & Authorization](../authentication/security-authentication.md)); `UserProvider.getUserByExtendKey` looks users up by key type.

**Delegation (acting on behalf):**

| Attribute | Type | Meaning |
| --- | --- | --- |
| `delegateUserId` | `String` | The delegate (actual executor) ID; empty means the user acted personally |
| `delegateUserName` | `String` | The delegate's name |

Semantics: when an operation is executed by a delegate, `creator`/`updater` still record the effective identity (the delegating user); `delegateUserId`/`delegateUserName` exist only for audit — "who acted for whom". The same semantics apply when an engine (system principal) writes on a user's behalf: effective identity = owning user, delegate fields = engine system identity.

**Generic extension:**

| Attribute | Type | Meaning |
| --- | --- | --- |
| `extendInfo` | `Map<String, Object>` | Generic extension attributes, filled by the upper layer via `UserExtInfoEnricher` at snapshot loading; concrete features (e.g. git binding) store values under agreed keys — the `User` layer never knows key meanings; values should be immutable objects (mostly strings) |

:::note Two similar names
`extendId` is the company extension ID (a `String`, taken from the company node); `extendInfo` is the generic extension map. They are different concepts.
:::

**Principal flag:**

| Attribute | Type | Meaning |
| --- | --- | --- |
| `systemPrincipal` | `boolean` | Whether this is a system background principal (scheduled jobs and other contexts without HTTP). When `true`, data-permission filtering skips the default `creator='<userId>'` condition so the job can query all data of the current tenant; tenant isolation (`tenantCode` filtering) still applies |

**Relations and permissions:**

| Attribute | Type | Meaning |
| --- | --- | --- |
| `userOrgs` | `List<UserOrg>` | Org attachments (membership); multiple attachments returned together |
| `userRoles` | `List<UserRole>` | Directly attached roles |
| `userOrgRoles` | `List<UserOrgRole>` | Org-attached roles (with source org) |
| `defaultOrg` | `UserOrg` | Default org object (the attachment with `defaultOrg=true`) |
| `tenant` | `Tenant` | Owning tenant object |
| `dataPermissions` | `List<Permission>` | Data permission rules |
| `elementPermissions` | `List<Permission>` | Element permissions |

:::note Default loading of permission collections
The default platform snapshot does not fill the permission collections — permission-domain data belongs to the auth center, queried on demand via `SecurityProvider.queryPermissionsOfUser` (see [Provider Contracts](provider.md)). Full-snapshot permission loading belongs to the auth-center implementation (`AuthCenterSecurityProvider`).
:::

### User behavior methods

| Method | Semantics |
| --- | --- |
| `getEffectiveRoles()` | Effective roles = direct roles (`userRoles`) ∪ org-attached roles (`userOrgRoles`), deduplicated by role `code`. The unified entry for effective-role semantics; historical consumers (e.g. `SecurityContext.isAdmin`) keep reading direct roles only, unchanged |
| `getDataPermissionByEntity(String entity)` | Per-entity data permission: filters `dataPermissions` by matching `entity` and takes the rule with the largest `weight` (first one on ties); returns `null` when the collection is missing or nothing matches |
| `setupOrgInfo(OrgProvider orgProvider)` | Legacy compatibility entry: fills `orgName`/`companyId`/`companyName`/`extendId`/`buId`/`buName`/`deptId` (when empty)/`defaultOrgName` via `OrgProvider`. New code should prefer `UserOrgInfoEnricher` |

## Role

| Attribute | Type | Meaning |
| --- | --- | --- |
| `Id` | `String` | Role ID. Note the field name starts with a capital letter; the getters/setters are `getId`/`setId` |
| `code` | `String` | Role code; business code and data-permission rules always reference roles by `code` (stable across environments), never by ID |
| `name` | `String` | Role name |
| `type` | `String` | Role type, see table below |
| `tenantCode` | `String` | Owning tenant code |

Platform values of `type` (`RoleTypeEnum`):

| Value | Meaning |
| --- | --- |
| `platform` | Platform-level role |
| `app` | Application-level role |

## Permission

| Attribute | Type | Meaning |
| --- | --- | --- |
| `code` | `String` | Permission code, the reference identifier (`hasPermission` matches on `code`) |
| `entity` | `String` | Entity the permission belongs to; data permissions take effect per entity |
| `name` | `String` | Permission name |
| `rule` | `String` | Permission rule expression. For data permissions it is a filter condition that may contain `#currentUser.xxx#` variables; for element permissions it is the element identifier |
| `weight` | `Integer` | Data-permission weight; among multiple rules on one entity the largest `weight` wins |
| `roleWeight` | `Integer` | Weight of the source role (carried when the permission is inherited through a role) |

### User variables in rules

`PermissionRuleUtils.replaceRuleVariable(Permission, User)` replaces user variables in `rule` with the current user's field values before querying. Syntax: `#currentUser.field#`, with `#currentUser.defaultOrg.field#` supported to reach fields of the default org object.

Allowed field whitelist (strict camelCase; anything outside it throws `IllegalArgumentException`):

| Category | Allowed fields |
| --- | --- |
| Basic user fields | `userId`, `orgId`, `defaultOrgId`, `cooperatingOrgId`, `companyId`, `extendId`, `deptId`, `buId`, `weixinUnionId`, `weixinWorkUserId`, `tenantCode` |
| `defaultOrg` object fields | `defaultOrg.orgId`, `defaultOrg.deptId`, `defaultOrg.companyId`, `defaultOrg.extendId` |

Replacement behavior:

- A non-empty field value is replaced with a quoted string literal (e.g. `#currentUser.userId#` → `'123456'`); empty values are replaced with `null`.
- When merging multiple rules (`replaceRuleVariable(List<Permission>, User)`), rules are deduplicated, whitespace-normalized, wrapped in parentheses, and joined with ` OR `.
- After replacement, any remaining `#...#` placeholder throws an exception (carrying entity name, original rule, and the placeholder list) — unresolved variables never reach a query.

## Attachment Relation Classes

### UserOrg (user-org attachment, extends OrgCore)

The membership relation between a user and an organization; one user may attach to multiple orgs.

| Attribute | Type | Meaning |
| --- | --- | --- |
| (inherited) | — | `orgId` (attached org ID)/`code`/`pid`/`name`/`tenantCode` |
| `fullName` | `String` | Full org name (full path; filled from `name` by the enricher when empty) |
| `deptId` | `String` | Parent department ID of this org (derived) |
| `companyId` | `String` | Owning company ID (derived) |
| `extendId` | `String` | Company extension ID of the owning company (derived) |
| `defaultOrg` | `Boolean` | Whether this is the default org; `true` means this attachment is the user's default department |
| `type` | `String` | Org type, same values as `Org.type` |
| `category` | `String` | Org category, see table below |
| `description` | `String` | Description |

Platform values of `category` (`OrgCategoryEnum`):

| Value | Meaning |
| --- | --- |
| `inside` | Internal |
| `outside` | External |
| `virtual` | Virtual |

### UserRole (direct role, extends Role)

A role attached directly to the user; no extra fields. The role is granted to the user without going through an org.

### UserOrgRole (user-org role, extends Role)

The user-org role contract model: a userId × orgId × roleId triple (userId is implicit when embedded in a `User` snapshot).

| Attribute | Type | Meaning |
| --- | --- | --- |
| (inherited) | — | Role fields, see `Role` |
| `orgId` | `String` | Source org of the role (the org the role is attached to) |

Semantics: the **only** way a department role takes effect for a user — the user is attached through this relation. The user does not have to be a member of the source org (membership is `UserOrg`, an independent relation that grants no role inheritance).

### OrgRole (org-attached role, extends Role)

The org-attached role contract model (a role attached to an org).

| Attribute | Type | Meaning |
| --- | --- | --- |
| (inherited) | — | Role fields, see `Role` |
| `orgId` | `String` | The org (department) the role is attached to |

Semantics: the role is attached to an org (department) and provided by that department. **Only direct departments take effect — no upward recursion along the tree.** The attachment itself never grants the role to members automatically; for a user it takes effect only via a direct `UserOrgRole`.

### UserRoles (aggregate of the two attachment results)

| Attribute | Type | Meaning |
| --- | --- | --- |
| `userRoles` | `List<UserRole>` | Direct roles |
| `userOrgRoles` | `List<UserOrgRole>` | Org-attached roles (department roles, `orgId` = source org) |

`getEffectiveRoles()` shares the semantics of `User.getEffectiveRoles()`: direct ∪ org-attached, deduplicated by `code`.

## Relation Rules

1. **Org tree**: `Org` nodes link through `pid`, empty at the root; `type` marks the node type. Relation resolution (department/company ownership) walks up the parent chain with a visited set guarding against cycles.
2. **User-org**: many-to-many attachments (`UserOrg`), `defaultOrg=true` marks the default; when `User.orgId` is empty it is resolved from attachments (default org first, then the first attachment).
3. **Two role grant shapes**: direct (`UserRole`) and department role (`UserOrgRole`, userId × orgId × roleId — the user need not be a member of that org). Both are user-side relations.
4. **Role-org attachment** (`OrgRole`): a role attached to a department; only direct departments take effect, no recursion. The attachment never auto-grants members — user-side effect requires a direct `UserOrgRole`.
5. **Effective roles**: direct ∪ org-attached, deduplicated by `code`, always taken from `getEffectiveRoles()`.
6. **Company ownership**: take the first ancestor with `type=company` along the parent chain, falling back to the top-level ancestor when none exists; `companyId`/`companyName`/`extendId`/`buId`/`buName` derive from it.
7. **Data permission effect**: grouped by `entity`, the rule with the largest `weight` wins (`getDataPermissionByEntity`); multiple rules are merged with ` OR ` after variable replacement.
8. **System principal**: `systemPrincipal=true` skips data-permission filtering but keeps tenant isolation — for scheduled jobs and other background scenarios.
9. **Delegation**: the effective identity is the delegating user (written to `creator`/`updater`); `delegateUserId`/`delegateUserName` record the actual executor for audit only.

## Summary

- The model layer is a pure data contract: entities (tenant / app / org / user / role / permission) + relation classes (org attachment and the three role attachment shapes), with no source details.
- The user is the center: org- and company-ownership fields are all derived values filled by enrichers at loading; permission collections are not loaded by default (auth-center domain).
- Effective-role semantics converge on `getEffectiveRoles()`: direct ∪ org-attached deduplicated by code; the only way a department role takes effect for a user is a direct `UserOrgRole`.
- Data permissions take effect per `entity` by the largest `weight`; rule variables support a strict whitelist and unresolved placeholders hard-fail.

## Suggested Reading

- [Provider Contracts](provider.md) — how these models are read
- [Snapshots & Loading](snapshot.md) — how models are loaded into snapshots
- [Security Context](context.md) — how models enter the thread context
