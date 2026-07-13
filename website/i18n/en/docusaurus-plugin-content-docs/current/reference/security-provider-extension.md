# Security Provider Extension

This page explains two important security abstractions in `geelato-security`:

- `UserProvider`
- `OrgProvider`

It covers why they exist, how the default implementation works, and how a host application can replace the default security data source.

## What Problem They Solve

The runtime security chain should not hard-code where user and organization data comes from.

So the framework exposes two provider boundaries:

- `UserProvider`
- `OrgProvider`

This lets consumers depend on a stable read interface while the actual source can come from:

- JDBC snapshots
- external IAM
- enterprise WeChat
- an organization center
- cache infrastructure
- an aggregation service

## What `UserProvider` Does

`UserProvider` exposes user-side read access such as:

- `getUser(String userId)`
- `getUserById(String userId)`
- `getUserByExtendKey(String extendKey, String type)`
- `getUserRoles(String userId)`
- `getUserOrgs(String userId)`
- `refresh()`

It also provides helper defaults such as:

- `getUserName(...)`
- `containsUser(...)`
- `normalizeType(...)`

The type normalization already aligns common identity names such as:

- `loginName`
- `weixinUnionId`
- `weixinWorkUserId`

## What `OrgProvider` Does

`OrgProvider` exposes organization-side read access such as:

- `getOrg(String orgId)`
- `getOrgName(String orgId)`
- `getDeptId(String orgId)`
- `getCompanyId(String orgId)`
- `getBuId(String orgId)`
- `refresh()`

So it is not just a simple organization lookup interface. It is also a relationship-resolution entry for department and company/BU derivation.

## Where They Are Used

They are key dependencies in the runtime security chain, including:

- `DefaultSecurityInterceptor`
- `InterceptorConfiguration`
- `SecurityDataRefreshCoordinator`
- `User.setupOrgInfo(...)`

So they are not optional helpers. They are the data-provider boundary of runtime security.

## How the Default Wiring Works

The default wiring is created in:

- `SecurityProviderConfiguration`

through:

- `@ConditionalOnMissingBean(OrgProvider.class)`
- `@ConditionalOnMissingBean(UserProvider.class)`

The default beans are:

- `DefaultOrgProvider`
- `DefaultUserProvider`

That means they are intentionally designed as replaceable extension points.

## Default Model

The current default model is:

1. loaders build snapshots
2. providers expose read access over those snapshots
3. upper security logic depends only on providers

The default loaders are:

- `JdbcOrgSnapshotLoader`
- `JdbcUserSnapshotLoader`

The default providers are:

- `DefaultOrgProvider`
- `DefaultUserProvider`

and refresh is coordinated by:

- `SecurityDataRefreshCoordinator`

## How To Extend

### Option 1: Replace the Provider Directly

If you already have a complete user or organization access model, provide your own:

- `OrgProvider`
- `UserProvider`

Because the defaults are protected by `@ConditionalOnMissingBean`, your beans will replace them naturally.

### Option 2: Keep the Provider, Replace the Loader

If you like the default snapshot-based provider model but want a different source, replace:

- `OrgSnapshotLoader`
- `UserSnapshotLoader`

This lets you keep:

- `DefaultOrgProvider`
- `DefaultUserProvider`
- `SecurityDataRefreshCoordinator`

while moving the actual data source to another backend.

### Option 3: Replace the User Org Enricher

For user organization enrichment, the framework also keeps:

- `UserOrgInfoEnricher`

If your user snapshot is incomplete or organization binding needs custom derivation, replacing this layer can be enough.

## Runtime Considerations

When replacing providers or loaders, keep these behaviors stable:

- `refresh()` really reloads the latest snapshot
- org resolution still supports department and company/BU derivation
- `getUserByExtendKey(...)` still supports external identity mapping when required

This is especially important because `UserProvider.getUserByExtendKey(...)` directly supports non-standard auth entries such as:

- `WeixinUnionId`
- `WeixinWorkUserId`

## Recommended Strategy

The usual order is:

1. decide whether you need to change the source only or the whole provider behavior
2. if only the source changes, replace the snapshot loader first
3. if the snapshot model itself does not fit, replace the provider directly
4. keep refresh and identity mapping semantics stable

## Summary

`UserProvider` and `OrgProvider` are two of the most important security extension abstractions in the current framework:

- `UserProvider` handles user access, role lookup, organization binding, and extended identity mapping
- `OrgProvider` handles organization lookup and organization relationship resolution
- the default model is `Loader -> Snapshot -> Provider`
- the default beans are protected by `@ConditionalOnMissingBean`, so host applications can override them cleanly

This makes them suitable as:

- unified identity-source integration points
- organization-center integration points
- extension boundaries for external IAM, enterprise WeChat, or custom account systems

## Suggested Reading

- [Authentication and Authorization](../authentication/security-authentication.md)
- [Override Default Implementations](override-default-implementations.md)
