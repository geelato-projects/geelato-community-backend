---
id: overview
title: Application Packaging
sidebar_label: Application Packaging
---

# Application Packaging and Deployment

Low-code applications are delivered through three parallel paths, covering different granularities:

| Mechanism | Artifact | Granularity | Description |
| --- | --- | --- | --- |
| **Application package** (this page) | `.gdp` / `.zgdp` | Whole app (metadata + resources) | Designer-produced full data package: pack → transfer → deploy → rollback; module `geelato-package` + the `/package` service in web-platform |
| Plugin package (pf4j) | plugin jar | Code-level extension | Hot-start/stop Java plugins; see [Plugin Mechanism](../plugin-mechanism/overview.md) |
| Scaffold / launcher artifacts | Spring Boot fat jar | Host project | Regular build & deploy of scaffold projects or the runtime/designer launchers; see [Deployment Guide](../operations/runtime-designer-deployment.md) |

They combine naturally: the host ships via scaffold/launcher, low-code configuration migrates between environments via application packages, and code-level extensions hot-plug via plugin packages.

## Package Structure

- Package data `AppPackData`: `sourceAppId / targetAppId / version / appCode / appName` + `appMetaList` (application metadata rows: pages, models, processes…) + `appResourceList` (static resources) + `basePlatformVersion` (platform baseline at pack time).
- Artifacts: `.gdp` (full JSON package, default name `geelatoApp`) or compressed `.zgdp`.
- Module: `cn.geelato:geelato-package`; config prefix `geelato.package`.

## Configuration

| Key (`geelato.package.*`) | Default | Description |
| --- | --- | --- |
| `env` | — | Environment tag |
| `path` | — | Root directory for package files |
| `uploadFolder` | — | Upload sub-directory |
| `engine` | `v1` | Pack/deploy engine version: `v1` (original) or `v2` (evolved, below); rollback is v2-only |

## Operation Flow

`PackageController` is a plain `@Controller` (**no `/api` prefix**); `AppVersionController` uses `/api/app/version`:

| Step | Endpoint | Description |
| --- | --- | --- |
| 1. Pack | `GET/POST /package/packet/{appId}` | Build the package and register a version; `engine=v2` routes to `packetV2` |
| 2. Merge pack | `GET/POST /package/packet/merge` | Merge multiple apps into one package |
| 3. Download | `GET /package/downloadPackage/{versionId}` | Download the `.zgdp` |
| 4. Upload | `POST /package/uploadPackage/{appId}` | Upload to `geelato.package.path/uploadFolder`, register `AppVersion` |
| 5. Deploy | `GET /package/deploy/{versionId}` | Validate → backup → persist → refresh cache |
| 6. Rollback | `GET /package/rollback/{appId}` | Roll back to the last backup (**v2 only**) |

Version management: `POST /api/app/version/pageQuery`, `GET /api/app/version/get/{id}`, `POST /createOrUpdate`, `POST /validate`, `DELETE /isDelete/{id}`.

## Deployment Validation & Safety Boundaries

1. **Platform version match**: the package's `basePlatformVersion` must match the target platform app `geelato_admin`'s `platform_app.version_info`, otherwise a "please update geelato_admin to version X" error is raised;
2. **Pre-deploy backup**: v2 backs up the current version automatically for rollback;
3. **Platform-table whitelist**: `PlatformTableConstant.isPlatformTable` cleansing — application packages cannot rewrite platform tables;
4. **Table-name injection guard**: table names must match `^[a-zA-Z_][a-zA-Z0-9_]*$`;
5. **Batched persistence**: metadata rows are written in batches of 500 (MQL SaveCommand / `JsonTextSaveParser`);
6. **Environment protection**: deployment is forbidden in `init_source` environments;
7. **Cache refresh**: application metadata caches refresh after deployment.

## v1 vs v2 Engine

`geelato.package.engine` selects the engine (strangler pattern: v1 is frozen, new capabilities land in v2 only):

| Dimension | v1 | v2 |
| --- | --- | --- |
| Entry points | `/package/packet`, `/deploy` (same endpoints branch by engine) | same |
| State management | singleton mutable state | no singleton mutable state |
| Deploy transaction | programmatic | declarative `@Transactional` |
| Backup/rollback | none | auto backup + `GET /package/rollback/{appId}` |
| Data cleansing | — | platform-table whitelist + table-name guard + batch 500 |

## Boundary with Plugin Packages

Application packages move **data-form** low-code configuration (page/model/process metadata and resources); plugin packages deploy **code-form** extensions (Java jars implementing `PluginExtensionPoint`). To sync code-level capabilities across environments, build a plugin package and copy it into the plugin directory (no HTTP upload/install endpoints yet — see the plugin mechanism docs); low-code configuration changes travel via application packages.
