---
id: org-user-role
title: Organization, User and Role Definitions
sidebar_label: Organization, User and Role
---

# Organization, User and Role Definitions

Core master-data concepts of the Geelato security protocol. The platform runtime (authentication, organization completion, online presence, authorization) reads these data only through unified abstractions, never hard-coding "where the data comes from" into consumer code.

## Core Concepts

| Concept | Description |
| --- | --- |
| User | The login subject, supporting multiple extension identity keys (`loginName`, `weixinUnionId`, `weixinWorkUserId`, etc.); authorization chains can use these extension identity entries directly |
| Org | Organization unit; a user can belong to multiple organizations |
| Department / Business Unit | Hierarchical concepts under an organization, back-filled with the user context after login |
| Role | Permission carrier, queried via `getUserRoles(userId)` |
| Tenant | Multi-tenant isolation dimension, propagated with the global context |

## Reading Abstractions: UserProvider and OrgProvider

`geelato-security` abstracts "where user/org data comes from" into two read-only interfaces:

- **`UserProvider`**: read-only user queries — `getUser`, `getUserByExtendKey`, `getUserRoles`, `getUserOrgs`, `refresh`, etc.
- **`OrgProvider`**: read-only organization queries — organization tree queries, organization completion, etc.

The default implementations read from the primary database snapshot and can be replaced entirely by an external IAM, WeCom, an organization center, a caching platform, etc. See [Security Provider Extension](../reference/security-provider-extension.md).

:::info Documentation Status
This page is a skeleton page. Field-level definitions of the organization model, the role-permission relationship model, and data permission rules will be added in later versions.
:::

## Further Reading

- [Security Authentication](../authentication/security-authentication.md) — the overall authentication and authorization flow
- [SecurityContext Lifecycle](../runtime/security-context-lifecycle.md) — initialization, propagation and destruction of the user context
- [Security Provider Extension](../reference/security-provider-extension.md) — replacing user/org data sources
- [Permission Integration](../authentication/permission-integration.md) — unified permission management on the AuthServer side
