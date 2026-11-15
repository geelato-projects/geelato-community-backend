---
id: deployment-flow
title: Deploying Application Packages
sidebar_label: Deployment
---

# Deploying Application Packages

This page describes what happens when an application package reaches its target environment: the entry point and environment gate, pre-deploy validation, backup & rollback, the persistence strategy, activation, and failure behavior. The UI entry is the "Deploy" action in the version management page, endpoint `GET /package/deploy/{versionId}` (branching by `geelato.package.engine`).

## Pre-Deploy Validation

Validation runs in the following order; any failure aborts with no data change:

1. **Environment gate**: deployment is forbidden in environments whose `geelato.package.env` is `init_source` (60010), protecting the initialization source environment from being overwritten.
2. **Version & package readable**: the version record exists with a valid `packagePath`; `.zgdp` files are read as archives (attachment ids resolved through the file service); a package without metadata raises 60003.
3. **Meta-model compatibility**: every `metaName` in the package must have a matching entity definition in the target platform's `MetaManager`, and every column of every row must resolve to field metadata. Any miss raises 60004 with "please update the platform app geelato_admin to version X" (X being the package's `basePlatformVersion`).

:::note What 60004 really means
The baseline of this check is the **target platform's current meta model**, not a version-string comparison. 60004 means the package carries a table or column absent from the target platform's entity definitions — usually a too-old target platform or an unsynced source table structure. Upgrade the platform app `geelato_admin` as prompted and retry.
:::

## Backup & Rollback

- **Automatic backup (v2 only)**: before deploying a non-backup version, the target app's current data is packed into a backup version (version suffix `_backup`, status `backup`). The backup commits independently and survives a failed deployment.
- **Rollback**: `GET /package/rollback/{appId}` re-deploys the latest backup version (deploying a backup version does not create another backup). The v1 engine produces no real backups, so rollback reports that no backup version was found.

## Persistence Strategy

Deployment writes each table of the package into the target database under one of two strategies. Incremental tables = the 5 platform incremental tables (`platform_dict`, `platform_dict_item`, `platform_sys_config`, `platform_encoding`, `platform_resources`) ∪ business tables with `pack_bus_data=1`; everything else is overwrite-type.

| Strategy | Tables | Delete phase | Insert phase | Semantics |
| --- | --- | --- | --- | --- |
| Overwrite | Pages, menus, models, datasources, roles/permissions, export templates… | Clear this app's old rows by `app_id` (`platform_app` by `id`, `platform_tree_node` by `tree_id`) | Insert all package rows, primary keys preserved | Package contents **replace** this app's data in the target environment |
| Incremental | The 5 platform incremental tables + `pack_bus_data=1` business tables | No deletion | Insert package rows; rows whose `id` already exists are skipped | **Idempotent merge**, safe to re-deploy |

All inserts preserve the package's original primary keys (`forceId` semantics) so cross-table references such as page–menu–model remain valid after deployment. Persistence runs in **batches of 500 rows** (the MQL `SaveCommand` pipeline, with the `LargeObject` serialization feature for large tables) and never round-trips a whole table through serialization, avoiding out-of-memory on large packages.

### Failure Behavior

- **v1**: delete and insert run inside one programmatic transaction; any metadata failure (unknown entity/column fails hard with 60005) rolls everything back, leaving the target database as before the deployment.
- **v2**: persistence runs batch-wise; unknown entities/columns are skipped with a warning; on a mid-way failure the already-written batches remain — there is no whole-package rollback. Incremental tables can be re-deployed safely (existing ids are skipped); overwrite tables simply replace their scope again on re-deploy.

:::warning Handling a failed deployment
Do not patch data manually after a v2 deployment failure: check `${LOG_DIR}/pack/` for the failing table and cause, fix it, and **re-deploy the same version** — overwrite tables replace wholesale and incremental tables skip existing ids, so repeated deployments converge to a consistent state.
:::

## Activation

After persistence succeeds, the runtime metadata of the deployed app is refreshed (`refreshDBMeta` entity by entity) and related query caches are invalidated — **hot reload, no platform restart needed**. Pages, dictionaries and config data are read on demand through caches, so the new data is served right after deployment.

If the metadata cache refresh fails, error 60009 is raised ("deployment succeeded but refreshing the app metadata cache failed"): data is persisted, the deployment is not rolled back — handle per the log and reload.

## Deployment Error Codes

Error codes are defined in `PackException` (60xxx segment):

| Code | Phase | Meaning |
| --- | --- | --- |
| 60001 | Pack | App not found |
| 60002 | Pack | Pre-pack column-consistency check failed, packing blocked |
| 60003 | Deploy | Version/package data missing or corrupted |
| 60004 | Deploy | Meta-model validation failed; upgrade the platform app `geelato_admin` first |
| 60005 | Deploy | Metadata or field missing on the target platform (hard failure in v1) |
| 60006 | Deploy | Package data write failed |
| 60007 | General | Illegal table name (injection guard) |
| 60008 | General | Package file read/write failure |
| 60009 | Deploy | Deployed successfully but metadata cache refresh failed |
| 60010 | General | Operation not allowed in this environment / no backup to roll back |

## Log Troubleshooting

All pack and deploy logs are routed by logger name `cn.geelato.web.platform.srv.pack` into `${LOG_DIR}/pack/` (date-rolled, not leaked into the main log), covering start/end and timings of each pack/deploy, per-table row counts, every delete SQL, and every persisted or skipped entity. When investigating deployment differences, compare the logged per-table row counts against the package expectations first.

## Further Reading

- [Packaging Flow](packaging-flow.md): producing, downloading and uploading packages
- [Packaged Contents & Runtime Roles](contents.md): which content categories the overwrite/incremental strategies apply to
