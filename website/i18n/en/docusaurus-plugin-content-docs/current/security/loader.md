---
title: Loading Abstractions
sidebar_label: Loading Abstractions
---

# Loading Abstractions

This page documents the three snapshot loader SPIs and the two loading helper contracts of `geelato-security`. Loaders are the assembly boundary from "external security data" to "immutable snapshots": the framework and platform never read the database directly — data is assembled into [snapshots](snapshot.md) through these three interfaces and cached by providers (see [Provider Contracts](provider.md)).

## The Three Loader SPIs

| Interface | Method | Semantics |
| --- | --- | --- |
| `OrgSnapshotLoader` | `OrgSnapshot load()` | Load the org snapshot |
| `UserSnapshotLoader` | `UserSnapshot load(UserOrgInfoEnricher userOrgInfoEnricher)` | Load the user snapshot |
| `RoleSnapshotLoader` | `RoleSnapshot load()` | Load the role snapshot |

Each interface has a single `load()` method. Contract points:

- **Single responsibility**: assemble external data into a complete snapshot in one pass; every call returns a freshly built snapshot, no incremental loading.
- **Replaceable by upper layers**: the interface javadoc states "allows the upper layer to replace the default platform-table reading implementation" — the default implementation reads the platform primary DB (JDBC), and a host project can swap it entirely for an external service, config center, Redis, aggregation API, or any other source.
- **Why `UserSnapshotLoader` carries an enricher parameter**: the user's org-derived fields (department / company / extension ID) depend on org relation resolution; the loader delegates that enrichment to the injected `UserOrgInfoEnricher` instead of depending on `OrgProvider` itself. The loader calls `enrich(UserOrg)` and `enrich(User)` one by one during assembly.

## Loading Helper Contracts

Two enrichers act as fillers during loading, populating derived and extension fields of the model.

### UserOrgInfoEnricher (user org info enricher)

| Method | Semantics |
| --- | --- |
| `enrich(User user)` | Fill the user's org-derived fields: `orgName`/`companyId`/`companyName`/`extendId`/`buId`/`buName`/`deptId`/`defaultOrgName` |
| `enrich(UserOrg userOrg)` | Fill one attachment's derived fields: `name`/`deptId`/`companyId`/`extendId`/`fullName` |

The data comes from org relation resolution (it depends on org data), so the default implementation takes an `OrgProvider` in its constructor.

### UserExtInfoEnricher (user ext info enricher)

| Method | Semantics |
| --- | --- |
| `enrich(User user)` | Fill `User.extendInfo` while the user snapshot is being loaded |

The data source (binding tables / configuration / auth center) is decided by the upper layer; key conventions are defined by consumers (e.g. the git operations module) and the `User` layer never knows key meanings. The default implementation is a no-op (no extension info bound); see [Platform Defaults](platform-defaults.md).

## Loading and Refresh Contract Semantics

- `load()` returns a newly built immutable snapshot every time; the loading thread publishes only after the build completes — readers never observe the loading process.
- A provider's `refresh()` calls `load()` and then swaps the snapshot reference atomically; the refresh chain is described in [Snapshots & Loading](snapshot.md).
- When replacing a loader implementation, keep the `refresh()` semantics stable: reload the latest snapshot, and never implement `refresh()` as a side-effectful heavy transaction.

## Summary

- The loader SPI is three `load()` methods: org, user, and role; their job is assembling external sources into immutable snapshots, and the default JDBC implementation can be replaced wholesale.
- Two enrichers fill derived data: `UserOrgInfoEnricher` fills org-derived fields (resolved via `OrgProvider`), `UserExtInfoEnricher` fills `extendInfo` (source decided by the upper layer).
- The enricher parameter on `UserSnapshotLoader.load` is a deliberate dependency-direction design: loaders never depend on providers.

## Suggested Reading

- [Snapshots & Loading](snapshot.md) — what a `load()` result looks like
- [Platform Defaults](platform-defaults.md) — the default JDBC loaders and replacement options
- [Provider Contracts](provider.md) — how loading results are read
