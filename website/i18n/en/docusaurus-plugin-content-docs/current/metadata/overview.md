---
id: overview
title: Metadata Overview
sidebar_label: Metadata Overview
---

# Metadata Overview

Metadata is the foundation of the Geelato framework. It is a **mapping description of structured data**: for every entity (business object), the framework collects the complete definition — its name, its fields, the primary-key and foreign-key relations between fields, the datasource it belongs to, and its delete mode — into one unified runtime object: `EntityMeta`. Every upper-layer capability is derived from this definition; no capability carries a second copy of the mapping.

Metadata describes structured data itself and is **bound to no fixed storage**: the same metadata model can sit behind a MySQL table, an Excel worksheet, a MongoDB collection, or any other structured carrier. Likewise, where the metadata definitions themselves live is abstracted behind an SPI — the framework does not mandate a medium.

## What Metadata Describes

The complete facts about an entity are held by the `EntityMeta` aggregate root:

- **Identity**: `entityName`, `entityTitle` (Chinese title), `entityType` (Table / View / Class source forms), `catalog` (logical database grouping)
- **Structure definition**: `tableMeta` (structure name, schema, connect id `connectId`, data dialect `dbType`, cache type `cacheType`, etc.)
- **Fields and columns**: `fieldMetas` (each `FieldMeta` embeds a `ColumnMeta` carrying field name, data type, length, precision, nullability, default value, encryption flag, etc.)
- **Primary key**: `id` (the primary key `FieldMeta`)
- **Relations**: foreign keys `tableForeigns`, CHECK constraints `tableChecks`, associated views `viewMetas`
- **Behavior switches**: delete mode `deleteMode`, ignore-update field map `ignoreUpdateFieldMap`, encrypted column set `encryptedColumnNames`, JSON column set `jsonFieldKeys`

## Why It Is the Foundation

Without metadata, none of the framework's upper-layer capabilities can exist. Every subsystem treats the `MetaManager` registry as the single source of truth:

| Upper-layer capability | What it reads from metadata |
| --- | --- |
| ORM SQL generation | Structure name, field→column mapping, primary key, dialect quoting, foreign-key JOINs, encrypted/JSON column handling |
| MQL query language | Entity and field validation, logic-delete field filling, default value fallback |
| Datasource routing | `resolveConnectId` turns "entity→datasource" ownership into a routing key and resolves the SQL dialect per entity |
| Row-level permission / tenant filter | Permission rules are attached by entity name; bare column names in rules are qualified as `alias.column` |
| Generic data APIs | One entity name is enough to expose full CRUD and publish slim field metadata to the low-code frontend |
| Audit field filling | `EntityMeta.containsField` decides writability: create-time fields on insert, update-time fields on update |
| Delete mode | Entity-level `deleteMode` is the middle layer of the call-site > entity > global configuration chain |
| Archive, packaging, Excel import, code generation, designer | Structure consistency checks, value conversion and locating, reverse Java source generation, DDL upgrades |

The complete consumer map is in [Where Metadata Extends](./extensions.md).

## Panorama

```mermaid
flowchart TB
    subgraph sources["Two definition sources"]
        direction LR
        A["ORM annotation source<br/>@Entity class scanning"]
        B["Online definition source<br/>designer-maintained, medium decided by the MetaStore implementation"]
    end
    A -->|"MetaReflex reflection"| R["parseOne"]
    B -->|"MetaStore SPI read"| R2["parseDBMeta / parseTableEntity"]
    R --> M["MetaManager registry<br/>entityMetadataMap (merged view)"]
    R2 --> M
    M -->|"runtime refresh refreshDBMeta"| M
    M --> C1["ORM SQL generation"]
    M --> C2["MQL validation & parsing"]
    M --> C3["Datasource routing"]
    M --> C4["Generic data APIs"]
    M --> C5["Permission / audit / delete / archive"]
    M --> C6["Designer / codegen / packaging / monitoring"]
```

## Dual-Source Model

Metadata has two sources. Each is registered into its own source cache, then merged into the main registry:

| Source | How it is produced | Where it lives | Characteristics |
| --- | --- | --- | --- |
| **ORM annotation source** | Startup scan of ORM `@Entity` classes — the declaration form of entity mapping — reflection over annotations | `entityMetadataMapFromClass` | Has `classType`; field types derived from Java types; lengths come from annotation defaults |
| **Online definition source** | Startup and runtime reads via the `MetaStore` SPI of design-time maintained definitions | `entityMetadataMapFromDatabase` | No `classType`; fields come from the maintained structure definitions (type/length/precision/encryption); `entityType` is Table or View |
| **Merged main map** | Merged per the conflict strategy at registration | `entityMetadataMap` | The only map ORM/MQL read at runtime; same-name entities are first-come-first-served by default; entities with `catalog=platform` always take the class definition |

You can compare the two sources at any time: MetaManager provides `getClassSourceEntity` / `getDatabaseSourceEntity` / `compareEntitySources`, and the designer also performs conflict checks through this mechanism. See [Metadata Loading](./loading.md) and [Runtime Operations](./operations.md).

Read the ORM annotation source together with the ORM chapter: the `@Entity`/`@Col`/`@Title` annotations on entity classes are at once ORM's entity-mapping declarations and the declaration form of metadata — ORM's SQL generation consumes exactly the metadata parsed from these annotations; declaring is mapping. See [ORM Annotations](../orm/annotations.md) for annotation usage and [ORM](../orm/overview.md) for how ORM is metadata-driven.

## Where Metadata Definitions Come From

The framework abstracts "where metadata definitions come from" into the `MetaStore` SPI: the framework layer only knows the interface and does not mandate a storage medium — MySQL, Excel, MongoDB, config centers, and external metadata services can all be sources; implement the interface and the whole source chain is replaceable. The loading flow and the SPI contract are in [Metadata Loading](./loading.md); the full guide to a custom source is [MetaStore Extension](../reference/metastore-extension.md).

The platform ships a default implementation (`DefaultMetaStore`) whose design-time storage lives in the platform database — its table structures and the platform entity catalog are outside the scope of framework documentation; see [Platform Entities](../entities/overview.md).

## Chapter Navigation

| Page | Content |
| --- | --- |
| [Metadata Model](./model.md) | Attribute-by-attribute reference for EntityMeta/FieldMeta/ColumnMeta/TableMeta and the annotation→metadata mapping |
| [Metadata Loading](./loading.md) | Registry structure, three startup load paths, conflict strategy, SPI contract, cache responsibilities |
| [Runtime Operations](./operations.md) | Full query/registration/refresh API list of MetaManager and EntityMeta |
| [Where Metadata Extends](./extensions.md) | Consumer panorama: ORM/MQL/routing/permission/audit/archive/designer, incl. script-engine channels |
| [MetaStore Extension](../reference/metastore-extension.md) | Guide for implementing a custom metadata source |

## Further Reading

- [Platform Entities Overview](../entities/overview.md) — a field-level catalog of the 54 platform entities in the `geelato-meta` module
- [Startup Process](../reference/startup-process.md) — when metadata loading happens in the startup chain (stage two)
- [ORM Annotations](../orm/annotations.md) — how to declare entities with annotations
- [Core Modules](../reference/core-modules.md) — where `MetaManager` sits in the overall architecture
