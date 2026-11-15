---
title: Snapshots & Loading
sidebar_label: Snapshots
---

# Snapshots & Loading

This page documents the three snapshot classes (`OrgSnapshot`/`UserSnapshot`/`RoleSnapshot`) — their index structures and immutability guarantees — plus the unified loading and refresh chain. Snapshots are produced by the [loader SPI](loader.md) and cached behind providers; they are the foundation of the lock-free read side.

## Snapshot Map

| Snapshot | Index structure | Produced by |
| --- | --- | --- |
| `OrgSnapshot` | `orgById` | `OrgSnapshotLoader.load()` |
| `UserSnapshot` | `userById` + a two-level `userByExtendType` index | `UserSnapshotLoader.load(enricher)` |
| `RoleSnapshot` | `roleById` + `roleByCode` + `orgRolesByOrgId` | `RoleSnapshotLoader.load()` |

All three snapshots are `final` classes with private constructors; the only entry is the static `from(...)` factory. Query methods are package-private — snapshots are never exposed directly; they are always surfaced through providers.

## OrgSnapshot

| Member | Meaning |
| --- | --- |
| `orgById` | Org ID → `Org` index |
| `from(Map<String, Org>)` | Static factory: copies into a `LinkedHashMap` (preserving load order) and wraps it with `Collections.unmodifiableMap` |
| `EMPTY` | Empty-snapshot singleton, the provider's initial state |
| `getOrg(String orgId)` | Package-private lookup, `null` when absent |

## UserSnapshot

| Member | Meaning |
| --- | --- |
| `userById` | User ID → `User` index |
| `userByExtendType` | Two-level extended-identity index: `type → extendKey → User`; `type` is one of the three extended keys `loginName`/`weixinUnionId`/`weixinWorkUserId` |
| `from(Map, Map)` | Static factory: both the outer map and every inner map are wrapped immutably (deep immutability) |
| `EMPTY` | Empty-snapshot singleton |
| `getUser(String userId)` | Package-private lookup |
| `getUserByExtendKey(String type, String extendKey)` | Package-private lookup: fetch the pool for `type`, then the user by key; `null` when the pool is absent |

The two-level index directly supports extended-key authentication (WeChat login, WeCom login): `UserProvider.getUserByExtendKey` normalizes `type` and lands on this index.

## RoleSnapshot

| Member | Meaning |
| --- | --- |
| `roleById` | Role ID → `Role` index |
| `roleByCode` | Role code → `Role` index, built by `from()` from `roleById`; duplicate codes resolve putIfAbsent-style — first wins, never overwritten |
| `orgRolesByOrgId` | Org ID → `List<OrgRole>` index (roles attached to the department); every list is made immutable via `List.copyOf` |
| `EMPTY` | Empty-snapshot singleton |
| `getRole(String roleId)` / `getRoleByCode(String roleCode)` | Package-private lookups |
| `getOrgRoles(String orgId)` | Package-private lookup, an empty list when absent |

## Immutability and the Concurrency Model

- **final + private constructor + immutable wrapping**: once published, a snapshot's contents never change; `from()` performs the copy and wrapping before publication.
- **Load as a whole, swap atomically**: providers hold the snapshot in an `AtomicReference`; `refresh()` builds the new snapshot and replaces the reference in one step. The read side is lock-free, and every read sees a complete snapshot — never a half-new, half-old mix.
- **Each query takes the current reference**: every provider method first reads `snapshotRef.get()` and then queries, which aligns naturally with refresh semantics; `BridgeSecurityProvider` keeps no cache of its own for the same reason.

## Refresh Chain: SecurityDataRefreshCoordinator

The unified loading and cascading-refresh coordinator for the three snapshots, living in the framework defaults (`geelato-core`).

| Method | Semantics |
| --- | --- |
| `init()` (`@PostConstruct`) | Runs `refreshAll()` at startup, completing the first load |
| `refreshAll()` | `orgProvider.refresh()` → `userProvider.refresh()` → role snapshot refresh |
| `refreshOrg()` | Refreshes the org snapshot and **cascades a user snapshot refresh** (org changes affect the user's derived fields: company / department / BU) |
| `refreshUser()` | Refreshes only the user snapshot |
| `refreshRole()` | Refreshes only the role snapshot |

Key points:

- All refresh methods are `synchronized`, preventing interleaved concurrent refreshes.
- `UserProvider`/`RoleProvider` may be absent (`@Autowired(required = false)`, when a direct-lookup implementation manages its own data plane); absent providers are skipped.
- Runtime trigger points: after directory changes, platform controllers call `OrgController.refreshOrg()` and `UserController.refreshUser()` (see the assembly table in [Platform Defaults](platform-defaults.md)).

## Summary

- The three snapshots are immutable index containers: org has a single index, user has two (including the extended-identity two-level index), role has three (ID / code / org attachments).
- Immutability + atomic replacement make the read side lock-free: the loading thread publishes only after the build completes, and every read sees a fully consistent snapshot.
- `SecurityDataRefreshCoordinator` unifies refresh: full load at startup, org changes cascade to users, and user/role can refresh independently.

## Suggested Reading

- [Loading Abstractions](loader.md) — where snapshots come from
- [Platform Defaults](platform-defaults.md) — the default JDBC loaders and assembly
- [Provider Contracts](provider.md) — query semantics on top of snapshots
