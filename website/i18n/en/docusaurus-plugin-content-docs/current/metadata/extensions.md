---
title: Where Metadata Extends
sidebar_label: Where Metadata Extends
---

# Where Metadata Extends

This page answers one question: **with metadata in place, what does the platform extend from it?** Every subsystem treats the MetaManager registry as the single source of truth; each section below lists the consumption entry (class#method), the metadata objects consumed, and the purpose.

:::info How to Read This
Sections run from "closest to metadata" to "furthest from it". Every entry path can be located in the corresponding module's source; class names are the last segment of the full path.
:::

## ORM CRUD: Everything SQL Generation Relies On

All SQL providers call `MetaBaseSqlProvider#getEntityMeta` first and only then start generating SQL — when metadata is absent they fail hard; there is no "degraded SQL without metadata".

| Entry | What it reads | What it does |
| --- | --- | --- |
| `MetaInsertSqlProvider#buildOneSql` | tableName, `getColumnName(field)`, ColumnMeta.dataType | Builds INSERT structure/column lists, binds JDBC types per column type |
| `MetaUpdateSqlProvider#buildOneSql` | tableName, `isIgnoreUpdateField()` | Builds the UPDATE SET clause, skipping creation fields and the primary key |
| `MetaDeleteSqlProvider#buildOneSql` | tableName, deleteMode (via `DeleteCommands.resolve`) | Emits an update for logic delete, a delete for physical delete |
| `MetaQuerySqlProvider#buildOneSql/buildForeignJoins` | field column mapping, TableForeign, ViewMeta.viewConstruct, dbType | Builds SELECT, auto left joins from foreign-key metadata with display columns, dialect quoting per dbType |
| `MetaBaseSqlProvider#requireFilterFieldMeta` | FieldMeta + dataType | Filter-field validation (throws InvalidFilterFieldException when absent), parameter SQL type binding, comparison disabled for JSON columns |
| `EntitySaveParser#parse` | all fieldMetas, primary key, newDefaultEntityMap | Entity object → SaveCommand: insert/update decided by the primary key, key generation, field-fill SPI trigger |
| `Dao#convert` / `CommonRowMapper` | getJsonFieldKeys, field column mapping | Row data → entity/Map mapping + JSON column deserialization + encrypted column decryption |
| `EncryptedColumns#resolve` | getEncryptedColumnNames | Query-column decryption (including the column union of joined entities) |
| `PageTotalStrategy#uniqueKeyBounded` | `getId()` primary key | Skips the count SQL when a primary-key equality condition hits |
| `SaveCommandAdapter` / `DeleteCommandAdapter` (Fluent API) | primary key, defaults, deleteMode | Adapts MetaInsert/MetaUpdate/MetaDelete → SaveCommand/DeleteCommand |

## MQL: The Validity Source of the Platform Query Language

Entity and field names written in MQL payloads must pass metadata validation before they can become SQL:

| Entry | What it reads | What it does |
| --- | --- | --- |
| `CommandValidator#validateEntity/validateField` | containsEntity, containsField, getColumnName, tableForeigns | Entity existence, field existence, `ref(xxx->yyy)` foreign-key judgment |
| `JsonTextQueryParser` | isMaterializedEntity | Strips fields a materialized view does not have (stripNonExistentFields) |
| `JsonTextSaveParser` | EntityMeta, newDefaultEntityMap, encryptedColumnNames | Save payload → SaveCommand, default fallback, encrypted column detection |
| `JsonTextDeleteParser` | EntityMeta | Parses `@physicalDelete`, fills logic-delete values per entity fields |
| `MqlQueryProcessor#applyViewTemplateParams` | ViewMeta.viewType | Only VIRTUAL views accept `@pf` template parameters |
| `MqlFuzzymatchRouteResolver` | getFieldMetaByColumn | Column↔field conversion when fuzzy search routes to Lucene |

## Datasource Routing: Entity→Datasource Ownership

| Entry | What it reads | What it does |
| --- | --- | --- |
| `DataSourceInterceptor#aroundDaoMethod` | entityName parsed from Dao arguments | Intercepts every Dao method and switches datasources per entity |
| `EntityDataSourceResolver#resolveFromMetaData` | `MetaManager.resolveConnectId(entityName)` | Routing key: TableMeta.connectId first, catalog mapping second; fails hard when the owning datasource is not registered — no silent fallback to primary |
| `MetaBaseSqlProvider#resolveEffectiveDbType` | tableMeta.dbType → connectId → default DB | Resolves the SQL dialect (identifier quoting etc.) per entity |
| `MaterializedViewGuard#assertWritable` | isMaterializedEntity | Read-only guard intercepting writes to materialized-view entities |

The full routing rules are in [Entity-Datasource Binding](../dynamic-datasource/entity-binding.md).

## Row-Level Permission and Tenant Filtering: Rules Attached by Entity Name

| Entry | What it reads | What it does |
| --- | --- | --- |
| `PlatformQueryFilterSupport#applyTenantFilter/applyDataPermissionFilter` | command.getEntityName() | Injects tenantCode filtering; fetches the user's data-permission rule for the entity (a SQL fragment defaulting to `creator='current user'`) and writes it into originalWhere |
| `MetaQuerySqlProvider#decorateOriginalWhere` | fieldMetas | Qualifies bare column names in permission rules as `alias.column` so they land correctly in join-bearing SQL |

Permission rules are organized by entity name — without the stable entity names metadata provides, row-level permission has nothing to attach to.

## Delete Mode and Audit Filling: Switches Driven by Field Existence

| Mechanism | Entry | Metadata dependency |
| --- | --- | --- |
| Logic delete | `DeleteCommands#resolve/fillLogicDeleteValues` | Mode priority: call-site explicit > `EntityMeta.deleteMode` > global default; LOGIC requires the entity to have delStatus, and fills delStatus/deleteAt/updateAt/updater/updaterName according to the fields the entity actually has |
| Audit filling | `PlatformFieldValueFillSupport#applyMqlDefaults/applyFluentDefaults/applyEntityDefaults` | `containsField` decides writability: inserts fill createAt/creator/creatorName/tenantCode/buId/deptId; updates fill updateAt/updater/updaterName and strip creation fields to prevent forgery |

## Views and Materialized Views: Dialect and Guards

| Entry | What it reads | What it does |
| --- | --- | --- |
| `MetaReflex#getEntityMetaByView` | the subject entity's TableMeta.dbType | A view without its own connection inherits the subject's dbType (dialect resolved by the subject); a materialized copy with its own connection does not inherit |
| `MetaQuerySqlProvider#buildFromSql` | ViewMeta.viewConstruct + viewType | FROM is dispatched by viewType: default queries the physical view name directly (must exist in the database); custom wraps viewConstruct verbatim as a subquery; virtual views render viewConstruct + `@pf` as a derived table; materialized views query the physical name directly |

See [Materialized View (Platform)](../platform-capabilities/materialized-view.md).

## Script Engine and AI Channels

The script engine has no `$gl.meta` kit; metadata reaches scripts and external tools through these channels:

| Channel | Entry | Notes |
| --- | --- | --- |
| `$gl.mql` | MqlKit (`list/multiList/save/batchSave/multiSave/delete/deleteByGql/explain`) | The metadata query channel driven by MQL payloads; internally MqlQueryProcessor/MetaQLManager consume metadata to build SQL; `entityConnectId(entityName)` reads `TableMeta.connectId` for datasource routing; write methods are gated by `dao.write-enabled` |
| `$gl.sql` cross-database locating | MetaTableLocationResolver | Walks the registry by structure name (exact → case-insensitive) to find EntityMeta, then locates the physical database via `resolveConnectId` + the connection definition (`ConnectMeta`); results cached in LocalBoundedCache (60s/4096) |
| MQL function parameters | GFunction (increment/findinset/fuzzymatch) | `$entity.field` is translated to the real column name via MetaManager, producing `gfn_xxx(...)` functions |
| MCP tool | MetaModelTool (geelato-mcp) | Exposes getAllEntityNames/getEntityMeta (field lists) to AI so models can understand the data model |

## Archive: Metadata as a Precondition

`ArchivePolicyService#enableArchiveForEntity` goes through `MetaManager.get(entityName)` to obtain the structure name and title for the archive policy — **a bare structure without metadata is refused archiving**. In archive query mode, entity ownership decides the redirection to the generic metadata query channel. See [Archive](../archive/overview.md).

## Engineering Chain: Design Time and Operations

| Entry | What it reads | What it does |
| --- | --- | --- |
| Table/column/view designers (`DevTableController` / `DevTableColumnController` / `DevViewController`) | refreshDBMeta, getTableUpgradeList, getClassSourceEntity | Refresh metadata after designer saves, generate structure upgrade lists, dual-source conflict checks |
| `MetaDdlService#createOrUpdateTableByEntityMeta` | EntityMeta | Creates or upgrades structures (DDL) from entity metadata |
| `EntityJavaSourceGenerator` | entity name/title/fields/columns/primary key | Reverse-generates Java source annotated with `@Entity/@Col/@Title/@Id` |
| `PackageService#assertColumnsConsistent` + `PackageUtils.validatePackageData` | getAll() | Before packaging, "physical structure columns ⊆ entity FieldMeta columns" is enforced; inconsistency blocks packaging |
| `ImportExcelService` | getFieldMeta/ColumnMeta | Excel import column locating, type conversion, value validation, unique-column detection |
| `AuditBusinessNamer` / `DictDisplayResolver` | entityTitle, FieldMeta.title, dictDataSourceMap | Translates audit-log field values into "entity.field (dictionary label)" |
| `OcrUtils` | EntityMeta column mapping | Lands OCR results by metadata |
| `ConsistencyChecker` (geelato-maintenance) | three-way EntityMeta | Field-by-field diff and repair across ORM annotation source / online definition source / physical DB source |
| `MqlTestModelRegistrar` / `MqlExplainService` | MetaManager | Registers test models into the registry for MQL explain/trial runs |
| `PlatformMetaMonitorEndPoint` | getAll() | Actuator endpoint emitting a full entity-metadata snapshot |

## Summary: What You Lose Without Metadata

| Without… | You lose… |
| --- | --- |
| Structure name / column mapping | ORM cannot generate any insert/update/delete/query SQL |
| Field validation | MQL cannot judge entity or field validity; the generic data APIs have no entry |
| connectId / dbType | Multi-datasource routing and per-entity dialect resolution fail; dynamic datasources lose the "entity→datasource" basis |
| Entity-name attachment point | Row-level data permission and tenant filtering have nowhere to inject |
| containsField | Logic delete and audit filling lose their per-entity switches |
| SimpleFieldMeta | The low-code frontend loses its form-rendering contract; generic APIs degrade to a raw SQL channel |
| Designer write-back + refreshDBMeta | Model changes require a restart; design time and runtime split apart |
| Packaging structure consistency check | unfound FieldMeta surfaces only at deploy time — the worst possible moment |

## Further Reading

- [Metadata Model](./model.md) — where the attributes consumers read come from
- [Metadata Loading](./loading.md) — how the registry consumers query is built
- [Runtime Operations](./operations.md) — the query and refresh APIs consumers call
