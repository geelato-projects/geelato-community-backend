---
title: API Reference
sidebar_label: API Reference
---
# API Reference

Static reference for all REST endpoints under the platform package `cn.geelato.web.platform.srv` — 90 controllers and 500+ endpoints.

This chapter is organized by business domain: one page per domain (srv sub-package), sections per controller, and one table per endpoint covering URL, parameters, and result. The information is extracted directly from source signatures and kept in sync with the code.

## Conventions

### URL prefix

- Controllers annotated with the custom `@ApiRestController` family (including `@DesignTimeApiRestController` and `@ApiRuntimeRestController`) automatically get an `/api` prefix at runtime: actual URL = `/api` + class-level path + method-level path.
- A few controllers using the plain `@Controller` / `@RestController` annotations do not get the `/api` prefix (e.g. the `/package` endpoints of app packaging); always follow the URL shown on each endpoint.
- The prefix is registered by `ApiPrefixAutoConfiguration` and only applies to the quickstart host.

### Common headers

- `appId`: application identifier; accepts `appId` / `AppId` / `App-Id`.
- `tenantCode`: tenant code; accepts `tenantCode` / `TenantCode` / `Tenant-Code`, falling back to the current session when absent.

These apply to every endpoint and are not repeated in per-endpoint parameter tables.

### Pagination parameters

Shared by list/paged endpoints:

- `pageNum`: page number, aliases `current` and `page`. When absent in a GET request it defaults to `-1`, meaning no pagination (full result); when absent in a POST body it defaults to `1`; values `< 1` fail hard.
- `pageSize`: page size, alias `limit`. When absent in a GET request it defaults to `-1` (full result); when absent in a POST body it defaults to `10`; values are clamped to `[1, 1000]`.
- `orderBy`: sorting, alias `order`; default `seq_no ASC,update_at DESC`.

### Filter conditions

Query or body keys support the `fieldName|operator` syntax (e.g. `name|like`); only keys matching existing entity fields are kept. The default operator is `eq`; `bt` (between, two values), `nil` (is null) and others are supported.

### Design-time endpoints

Controllers marked with ⚙ are design-time endpoints (`@DesignTimeApiRestController`), mainly consumed by the designer / IDE; the rest are runtime endpoints (`@ApiRestController`). Controllers annotated with `@ApiRuntimeRestController` whose category is `platform-design` are also marked as design-time.

## Unified result structure

| Field | ApiResult | ApiPagedResult | ApiMetaResult | Description |
| --- | --- | --- | --- | --- |
| `code` | ✓ | ✓ | ✓ | Result code, `200` for success |
| `msg` | ✓ | ✓ | ✓ | Message |
| `status` | ✓ | ✓ | ✓ | Status |
| `data` | ✓ | ✓ | ✓ | Data |
| `cache` | ✓ | ✓ | ✓ | Cache flag |
| `total` / `page` / `size` / `dataSize` | — | ✓ | — | Total / page / size / row count of current page |
| `meta` | — | ✓ | ✓ | Metadata |

`ApiMultiPagedResult` returns `data` as `Map<String, PageData>`, where `PageData` contains `total` / `page` / `size` / `dataSize` / `data` / `meta`, used when one request pages multiple entities.

## Domain index

| Domain | Controllers | Endpoints |
| --- | --- | --- |
| [AI Assistant](./srv/ai.md) | 1 | 1 |
| [Announcements](./srv/announcement.md) | 1 | 2 |
| [Arco Components](./srv/arco.md) ⚙ | 1 | 1 |
| [Authentication](./srv/auth.md) | 2 | 18 |
| [Platform Basics](./srv/base.md) | 15 | 46 |
| [Excel Import/Export](./srv/excel.md) | 3 | 17 |
| [Files & Attachments](./srv/file.md) | 3 | 21 |
| [Gray Release Rules](./srv/gray.md) | 1 | 2 |
| [Metadata](./srv/meta.md) | 2 | 26 |
| [Data Modeling](./srv/model.md) ⚙ | 7 | 48 |
| [Materialized Views](./srv/mv.md) ⚙ | 1 | 13 |
| [Notifications](./srv/notification.md) | 1 | 9 |
| [OCR](./srv/ocr.md) | 6 | 24 |
| [ORM Hooks](./srv/ormhook.md) | 1 | 7 |
| [App Packaging](./srv/pack.md) | 2 | 12 |
| [Apps & Platform Config](./srv/platform.md) | 13 | 80 |
| [Periodic Reports](./srv/report.md) | 1 | 5 |
| [Script & External Services](./srv/script.md) | 5 | 16 |
| [Security & Permissions](./srv/security.md) | 19 | 125 |
| [App Settings](./srv/settings.md) | 1 | 3 |
| [Static Sites](./srv/site.md) ⚙ | 1 | 13 |
| [Tenants](./srv/tenant.md) ⚙ | 1 | 8 |
| [WeCom](./srv/weixin.md) | 1 | 5 |
| [Barcode](./srv/zxing.md) | 1 | 9 |

## Standard contract and supplementary entries

- **OpenAPI** is the official standard contract for external integrations, runtime API consumption, API review, and gateway / client generation; the capability lives in the `springdoc` setup of the quickstart host.
- This static reference is extracted directly from source and serves as the supplementary entry for browsing by controller.
- `MQL` is not an external standard contract but the platform-internal universal metadata access protocol; see [MQL](../mql/overview.md).
