---
title: Roles & Permissions
sidebar_label: Roles & Permissions
---

# Roles & Permissions

This page covers Role, Permission and 9 permission-related relation tables — 11 entities in total. All relation tables extend `BaseEntity`; common fields are listed in [Overview](overview.md#common-base-class-fields).

Attachment semantics (aligned with the security contract `cn.geelato.security`, see [Security Contract Overview](../security/overview.md)):

- Direct user-role attachment: RoleUserMap; role attachment to organizations: OrgRoleMap (effective only for the direct department, no recursion up the organization tree).
- The only way a department role takes effect for a user is a direct UserOrgRoleMap attachment (userId × orgId × roleId); attaching a role to an organization does not automatically grant the role to its members; membership (OrgUserMap) and direct role attachment are independent.
- Permissions come in two levels: role-permission (RolePermissionMap) and direct user permission (UserPermissionMap).

## Role

Role definition, application-level or platform-level. Extends `BaseSortableEntity`, implements `EntityEnableAble`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application |
| name | name | String | Name |
| code | code | String | Code |
| type | type | String | Type: `app` (application-level role) or `platform` (platform-level role) |
| enableStatus | enable_status | int | Enable status: 1 enabled, 0 disabled; tinyint, non-null |
| description | description | String | Description |
| weight | weight | Integer | Weight |
| usedApp | used_app | int | Whether used by applications |
| appName | — | String | `@Transient`, not persisted |
| appIds | — | String | `@Transient`, not persisted |
| usedApp (boolean view) | — | boolean | `@Transient` method-level derived property, `usedApp > 0` |

## Permission

Permission point definition. This entity has no class-level @Title; documented here as a permission point by field semantics. Extends `BaseSortableEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| name | name | String | Name |
| code | code | String | Code |
| type | type | String | Type |
| object | object | String | Object, the target the permission acts on |
| parentObject | parent_object | String | Parent object, forming the permission object hierarchy |
| rule | rule | String | Rule, length 1024; auto-decoded on save when it is a base64 data URI (`afterSet`) |
| description | description | String | Description |
| perDefault | — | boolean | `@Transient`, not persisted |

## RoleUserMap

Direct role-user attachment relation. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| roleId | role_id | String | Role id, `@ForeignKey` to Role |
| roleName | role_name | String | Role name, reference column (isRefColumn; same below, fetched from the main table's name column) |
| userId | user_id | String | User id, `@ForeignKey` to User |
| userName | user_name | String | User name, reference column |

## OrgRoleMap

Role attachment to an organization. Semantics: the role is attached to the direct department and is effective only there, without recursion up the organization tree; attaching a role to an organization does not automatically grant it to department members — members obtain the department role only through a direct UserOrgRoleMap attachment. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| orgId | org_id | String | Organization id, `@ForeignKey` to Org |
| orgName | org_name | String | Organization name, reference column |
| roleId | role_id | String | Role id, `@ForeignKey` to Role |
| roleName | role_name | String | Role name, reference column |

## OrgUserMap

Membership relation between users and organizations (belonging / part-time). Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| orgId | org_id | String | Organization id, `@ForeignKey` to Org |
| orgName | org_name | String | Organization name |
| userId | user_id | String | User id, `@ForeignKey` to User |
| userName | user_name | String | User name |
| defaultOrg | default_org | int | 1 default organization, 0 part-time |
| post | post | String | Position |

## UserOrgRoleMap

Direct attachment of a role to a user within an organization: the only way a department role takes effect for a user. Semantics: the userId × orgId × roleId combination; orgId records the department the role comes from; the user need not be a member of that department (membership is in OrgUserMap, independent of this table, with no role inheritance). Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| userId | user_id | String | User id, `@ForeignKey` to User |
| userName | user_name | String | User name, reference column |
| orgId | org_id | String | Source organization id (the department the role is attached to), `@ForeignKey` to Org |
| orgName | org_name | String | Source organization name, reference column |
| roleId | role_id | String | Role id, `@ForeignKey` to Role |
| roleName | role_name | String | Role name, reference column |

## RolePermissionMap

Role-permission relation. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| roleId | role_id | String | Role id, `@ForeignKey` to Role |
| permissionId | permission_id | String | Permission id, `@ForeignKey` to Permission |
| permissionIds | — | String | `@Transient`, not persisted; the set of permission ids of the role |
| roleName | role_name | String | Role name, reference column |
| permissionName | permission_name | String | Permission name |

## UserPermissionMap

Permissions granted directly to a user (bypassing roles). Extends `BaseEntity`. Note: field-level @Title in the source is misaligned by copy-paste (userId's title says "role id", roleName's title says "role name"); the table below documents actual semantics.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| userId | user_id | String | User id, `@ForeignKey` to User |
| permissionId | permission_id | String | Permission id, `@ForeignKey` to Permission |
| permissionIds | — | String | `@Transient`, not persisted; set of permission ids |
| roleName | user_name | String | User name, reference column (the property keeps the name roleName, the column is `user_name`) |
| permissionName | permission_name | String | Permission name |

## RoleAppMap

Role-application (accessible applications) relation. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| roleId | role_id | String | Role id, `@ForeignKey` to Role |
| roleName | role_name | String | Role name, reference column |
| appId | app_id | String | Application id, `@ForeignKey` to App |
| appName | app_name | String | Application name, reference column |

## RoleTreeNodeMap

Role-menu tree node relation. Menu nodes live in platform_tree_node (flag = `menuItem`). Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| treeId | tree_id | String | Tree id, non-null |
| roleId | role_id | String | Role id, `@ForeignKey` to Role |
| treeNodeId | tree_node_id | String | Menu id, `@ForeignKey` to TreeNode |
| treeNodeText | tree_node_text | String | Menu name, reference column (fetched from platform_tree_node.text) |
| roleName | role_name | String | Role name, reference column |
| title | title | String | Name |

## UserDelegateMap

Delegation/assistant relation between users: a delegating user (user) can configure a delegate (delegate_user) to handle affairs on their behalf. One delegator may configure multiple delegates; multiple business scopes for the same pair are stored as multiple rows (one scope value per row). Business uniqueness: the combination of delegator (user_id) + delegate (delegate_user_id) + scope must not repeat. Other modules can query valid delegates by `user_id + scope + enable_status=1`. Extends `BaseEntity`, implements `EntityEnableAble`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| userId | user_id | String | Delegator id, the user initiating the delegation, `@ForeignKey` to User |
| userName | user_name | String | Delegator name, reference column |
| delegateUserId | delegate_user_id | String | Delegate id, the user handling affairs on behalf, `@ForeignKey` to User |
| delegateUserName | delegate_user_name | String | Delegate name, reference column |
| delegateUserEnName | delegate_user_en_name | String | Delegate English name, reference column (fetched from platform_user.en_name) |
| relationType | relation_type | String | Relation type, defaults to `delegate`, reserved for extension, length 32 |
| scope | scope | String | Business scope, a single business identifier (e.g. `periodic_report`); one scope per row, length 64 |
| enableStatus | enable_status | int | Enable status: 1 enabled, 0 disabled |

## Recommended Reading

- [Organization & Users](org-tenant-user.md) — the main entities on both ends of the relation tables
- [Security Contract Overview](../security/overview.md) — how these attachments are loaded into the security context
- [Applications & Designer](app-and-designer.md) — the applications and menus referenced by RoleAppMap/RoleTreeNodeMap
