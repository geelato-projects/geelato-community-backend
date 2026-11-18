---
id: packaging-flow
title: Packaging Flow
sidebar_label: Packaging Flow
---

# Packaging Flow

This page describes the complete flow of packing an application into a `.zgdp`: pre-pack gates, the three packing modes, the artifact pipeline, and download & upload. The UI entry is the app version management page in the IDE; the endpoints below can also be called directly.

`PackageController` is a plain `@Controller` (**no `/api` prefix**) branching between v1/v2 by `geelato.package.engine`; the version management API `AppVersionController` lives under `/api/app/version`.

## Prerequisites & Gates

Before writing the package, two checks run after data query; either failing aborts hard and no package is produced:

1. **App exists**: `appId` must resolve to a `platform_app` row, otherwise error 60001.
2. **Column consistency**: the physical columns of each table (the column set of `select *`) must match the entity definition (`EntityMeta` fields). Physical columns missing from the entity definition raise 60002 "pack validation failed: physical table and entity definition are inconsistent, packing is blocked".

:::note Why the gate exists
Rows packed with `select *` that carry columns absent from the target platform's entity definition would always fail deployment validation. Intercepting at pack time surfaces the problem in the source environment — sync the table structure in the model designer (or drop the redundant physical columns) and pack again.
:::

A version name may be passed or omitted; when omitted it is generated as `<appCode>_versionyyyyMMddHHmmss`. Version uniqueness per app+tenant+version applies (`POST /api/app/version/validate`, called automatically by the pack dialog).

## Three Packing Modes

### Whole-App Packing

`GET/POST /package/packet/{appId}`, optional `version`, `description`:

- Carries all rows of this app from the 19 whitelisted platform tables plus all rows of business tables with `pack_bus_data` > 0.
- Calling without extra parameters is whole-app packing — the regular way to migrate an environment.

### Partial Packing

Same endpoint; pass `appointMetas` in the body (`{table: "rowId1,rowId2"}`) to pick metadata rows:

:::warning v1 and v2 differ
- **v1 (default engine)**: `appointMetas` is a whitelist — **listed tables are packed by the selected rows, unlisted tables are excluded entirely** (only the package header remains).
- **v2**: `appointMetas` selects rows — listed tables are packed by the selected rows, and **unlisted tables are still packed in full**.

Check the engine value before a partial delivery to avoid a package that differs from expectations.
:::

`platform_app` is unaffected by `appointMetas` and always travels as the package header.

### Merge Packing (multi-version comparison merge)

`GET/POST /package/packet/merge`, body `{version: {table: "rowId1,rowId2"}}`:

1. Reads and parses the registered package files version by version.
2. Merges only entities **listed** in `appointMetas`; an empty string selects zero rows for that entity.
3. Deduplicates rows by `id` within the same entity across versions.
4. Produces a new package and registers it (default description "package produced by comparison merge").

Typical scenario: run a version comparison in the version management page first, then merge several incremental deliveries into one package. The merge only deduplicates by row `id` and performs no cross-entity reference consistency check.

## Artifact Pipeline

Internal execution order of a pack request:

1. Build the query map: 19 platform tables + business tables (business table names validated against `^[a-zA-Z_][a-zA-Z0-9_]*$` against injection).
2. Query this app's data table by table; the `platform_app` row feeds the package header.
3. Column-consistency check (above).
4. Serialize the whole package with fastjson2 (with the `LargeObject` feature to support large packages).
5. Write `<geelato.package.path>/<appCode>/<appCode>.gdp`.
6. Compress into `<version>.zgdp`, archive per tenant and app, and register as an attachment.
7. Write the app version record `platform_app_version`: `packagePath` (attachment id), source `packet`, status `draft`, pack time, tenant.

Date values inside the package JSON are normalized to `yyyy-MM-dd HH:mm:ss` to avoid parse failures caused by serialization differences between environments.

## Download & Upload

| Action | How | Description |
| --- | --- | --- |
| Download | The download button in the version management page (generic file download by attachment id); or `GET /package/downloadPackage/{versionId}` | Yields the `.zgdp` file |
| Upload | Upload in the version management page (accept `.zgdp`) then register via `POST /api/app/version/createOrUpdate`; or `POST /package/uploadPackage/{appId}` | The file lands in `geelato.package.path/uploadFolder` and an app version record is written |

After upload and registration the version can be deployed from the target environment's version list.

## Version Management API

| Endpoint | Description |
| --- | --- |
| `POST /api/app/version/pageQuery` | Paged version query |
| `GET /api/app/version/get/{id}` | Version detail |
| `GET /api/app/version/package/{id}` | Read the version's package data (`AppPackData`) |
| `POST /api/app/version/createOrUpdate` | Register/update a version (called after upload) |
| `POST /api/app/version/validate` | Version uniqueness check |
| `DELETE /api/app/version/isDelete/{id}` | Delete a version record |

## Packing Notes

- **Sensitive data travels as-is**: rows are packed with `select *` and no masking is applied to `platform_app.app_key/token` or datasource connection strings; treat package files as sensitive data in delivery and storage.
- **Dependencies are not pulled in**: `depend_app_code` is a declaration only; depended-on apps must be ready in the target environment (see [Packaged Contents & Runtime Roles](contents.md)).
- **File binaries are not packed**: `platform_resources` carries metadata rows only.
- **Versions start as `draft`**: status flips to `deployed` after a successful deployment; backup versions are generated automatically by v2 deployments (version suffix `_backup`).
- **Dedicated log routing**: pack/merge/deploy logs are routed by logger name `cn.geelato.web.platform.srv.pack` into `${LOG_DIR}/pack/`, including per-table row counts and timings.

## Further Reading

- [Deploying Application Packages](deployment-flow.md): validation, persistence and activation on the target environment
- [Packaged Contents & Runtime Roles](contents.md): what each table in the package does at runtime
- [System Configuration: Package Module](../system-config/overview.md#package-module): the `geelato.package.*` keys
