---
title: 角色与权限
sidebar_label: 角色与权限
---

# 角色与权限

本页覆盖 Role、Permission 与 9 个权限类关联表，共 11 个实体。关联表全部继承 `BaseEntity`，公共字段见[概览](overview.md#公共基类字段)。

挂靠语义（与安全契约 `cn.geelato.security` 对齐，详见[安全契约概览](../security/overview.md)）：

- 角色-用户直挂：RoleUserMap；角色-组织挂靠：OrgRoleMap（仅直属部门生效，不沿组织树递归）。
- 部门角色对用户生效的唯一途径是 UserOrgRoleMap 直挂（userId × orgId × roleId）；组织挂靠本身不使部门成员自动获得角色，成员身份（OrgUserMap）与角色直挂相互独立。
- 权限有两级：角色-权限（RolePermissionMap）与用户直授权限（UserPermissionMap）。

## Role 角色

角色定义，分应用级与平台级。继承 `BaseSortableEntity`，实现 `EntityEnableAble`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 |
| name | name | String | 名称 |
| code | code | String | 编码 |
| type | type | String | 类型：`app` 应用级角色、`platform` 平台级角色 |
| enableStatus | enable_status | int | 启用状态：1 启用、0 未启用，tinyint 非空 |
| description | description | String | 描述 |
| weight | weight | Integer | 权重 |
| usedApp | used_app | int | 是否用于应用 |
| appName | — | String | `@Transient` 非落库 |
| appIds | — | String | `@Transient` 非落库 |
| usedApp（布尔视图） | — | boolean | `@Transient` 方法级派生属性，`usedApp > 0` |

## Permission 权限项

权限点定义。该实体无类级 @Title，按字段语义撰写为权限项。继承 `BaseSortableEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 Id |
| name | name | String | 名称 |
| code | code | String | 编码 |
| type | type | String | 类型 |
| object | object | String | 对象，权限作用的对象 |
| parentObject | parent_object | String | 父对象，构成权限对象层级 |
| rule | rule | String | 规则，长度 1024；保存时若为 base64 data URI 则自动解码（`afterSet`） |
| description | description | String | 描述 |
| perDefault | — | boolean | `@Transient` 非落库 |

## RoleUserMap 角色用户关系表

角色-用户直挂关联。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| roleId | role_id | String | 角色 ID，`@ForeignKey` 指向 Role |
| roleName | role_name | String | 角色名称，引用列（isRefColumn，下同，随外键取主表名称列） |
| userId | user_id | String | 用户 ID，`@ForeignKey` 指向 User |
| userName | user_name | String | 用户名称，引用列 |

## OrgRoleMap 组织角色关系表

角色挂靠到组织。语义：角色挂靠到直属部门，仅直属部门生效，不沿组织树向上递归；组织挂靠本身不使部门成员自动获得角色，成员获得部门角色须经 UserOrgRoleMap 直挂。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| orgId | org_id | String | 组织 ID，`@ForeignKey` 指向 Org |
| orgName | org_name | String | 组织名称，引用列 |
| roleId | role_id | String | 角色 ID，`@ForeignKey` 指向 Role |
| roleName | role_name | String | 角色名称，引用列 |

## OrgUserMap 组织用户关系表

用户与组织的成员关系（归属/兼职）。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| orgId | org_id | String | 组织 ID，`@ForeignKey` 指向 Org |
| orgName | org_name | String | 组织名称 |
| userId | user_id | String | 用户 ID，`@ForeignKey` 指向 User |
| userName | user_name | String | 用户名称 |
| defaultOrg | default_org | int | 1 默认组织、0 兼职 |
| post | post | String | 职务 |

## UserOrgRoleMap 用户组织角色关系表

用户在某组织下的角色直挂：部门角色对用户生效的唯一途径。语义：userId × orgId × roleId 组合；orgId 记录角色来源部门；用户可以不是该部门成员（成员身份见 OrgUserMap，与本表独立，不产生角色继承）。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| userId | user_id | String | 用户 ID，`@ForeignKey` 指向 User |
| userName | user_name | String | 用户名称，引用列 |
| orgId | org_id | String | 来源组织 ID（角色挂靠的部门），`@ForeignKey` 指向 Org |
| orgName | org_name | String | 来源组织名称，引用列 |
| roleId | role_id | String | 角色 ID，`@ForeignKey` 指向 Role |
| roleName | role_name | String | 角色名称，引用列 |

## RolePermissionMap 角色权限关系表

角色-权限关联。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 Id |
| roleId | role_id | String | 角色 ID，`@ForeignKey` 指向 Role |
| permissionId | permission_id | String | 权限 ID，`@ForeignKey` 指向 Permission |
| permissionIds | — | String | `@Transient` 非落库，角色所有的权限 id 集合 |
| roleName | role_name | String | 角色名称，引用列 |
| permissionName | permission_name | String | 权限名称 |

## UserPermissionMap 用户权限关系表

用户直授权限（不经角色）。继承 `BaseEntity`。注意：源码字段级 @Title 有复制粘贴错位（userId 的标题写成"角色ID"、roleName 的标题写成"角色名称"），下表按实际语义撰写。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 Id |
| userId | user_id | String | 用户 ID，`@ForeignKey` 指向 User |
| permissionId | permission_id | String | 权限 ID，`@ForeignKey` 指向 Permission |
| permissionIds | — | String | `@Transient` 非落库，权限 id 集合 |
| roleName | user_name | String | 用户名称，引用列（属性名沿用 roleName，列名 `user_name`） |
| permissionName | permission_name | String | 权限名称 |

## RoleAppMap 角色APP关系表

角色-应用（可访问应用）关联。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| roleId | role_id | String | 角色 ID，`@ForeignKey` 指向 Role |
| roleName | role_name | String | 角色名称，引用列 |
| appId | app_id | String | 应用 ID，`@ForeignKey` 指向 App |
| appName | app_name | String | 应用名称，引用列 |

## RoleTreeNodeMap 角色菜单关系表

角色-菜单树节点关联。菜单节点存在 platform_tree_node 中（flag 为 `menuItem`）。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 Id |
| treeId | tree_id | String | 树 ID，非空 |
| roleId | role_id | String | 角色 ID，`@ForeignKey` 指向 Role |
| treeNodeId | tree_node_id | String | 菜单 ID，`@ForeignKey` 指向 TreeNode |
| treeNodeText | tree_node_text | String | 菜单名称，引用列（取 platform_tree_node.text） |
| roleName | role_name | String | 角色名称，引用列 |
| title | title | String | 名称 |

## UserDelegateMap 用户委托关系表

用户之间的委托/助理关系：委托人可配置代理人代为处理事务。一个委托人可配置多个代理人；同一委托人为同一代理人配置多个业务范围时存为多行（每行业务范围单值）。业务唯一性：委托人 + 代理人 + 业务范围三者组合不重复。其他业务模块可按 `user_id + scope + enable_status=1` 查询有效代理人。继承 `BaseEntity`，实现 `EntityEnableAble`。生效身份语义（引擎代办的 creator 归属委托人）见平台事件审计相关约定。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| userId | user_id | String | 委托人 ID，发起委托的用户，`@ForeignKey` 指向 User |
| userName | user_name | String | 委托人名称，引用列 |
| delegateUserId | delegate_user_id | String | 代理人 ID，被委托的用户，`@ForeignKey` 指向 User |
| delegateUserName | delegate_user_name | String | 代理人名称，引用列 |
| delegateUserEnName | delegate_user_en_name | String | 代理人英文名，引用列（取 platform_user.en_name） |
| relationType | relation_type | String | 关系类型，默认 `delegate`，预留扩展，长度 32 |
| scope | scope | String | 业务范围，业务标识单值（如 `periodic_report`），一行一个业务范围，长度 64 |
| enableStatus | enable_status | int | 启用状态：1 启用、0 未启用 |

## 推荐继续阅读

- [组织与用户](org-tenant-user.md)——关联表两端的主实体
- [安全契约概览](../security/overview.md)——这些挂靠关系如何被装进安全上下文
- [应用与设计器](app-and-designer.md)——RoleAppMap/RoleTreeNodeMap 关联的应用与菜单
