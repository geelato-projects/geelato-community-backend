---
id: overview
title: Search Engine Overview
sidebar_label: Search Engine
---

# Search Engine (geelato-search)

The Geelato search engine accelerates **`fuzzymatch` multi-number fuzzy search** in MQL. A typical query — "one document's numbers are spread across multiple columns of the main table and multiple rows of child tables; hitting any number hits the document" — originally relied on the database stored function `gfn_fuzzymatch`, which degrades into a full table scan on large tables (measured 3s+ on 1.44M rows).

The search engine **equivalently offloads** such predicates to an embedded search engine while keeping result sets strictly identical to the original SQL:

| Layer | Condition | Mechanism | Status |
| --- | --- | --- | --- |
| L1 REGEXP rewrite | Default, zero config | `gfn_fuzzymatch(col,'kw')>0` rewritten at SQL level to `(col<>'' AND col REGEXP ?)` | Implemented |
| L2 Lucene routing | Domain configured + reindexed | fuzzymatch predicates routed to an embedded Lucene inverted index (NRT + NGram contains); matched ids re-injected as `id IN (...)` | Implemented, behind experiment gate |
| L3 Sync & compensation | Automatic with L2 | ORM save/delete events sync the index asynchronously; failures go to the `search_sync_fail` retry queue; daily `update_at` watermark reconciliation | Implemented |

**Boundary**: the source of truth is always the database; the engine only answers "which ids match". Joins, paging, sorting and data-permission filtering stay in SQL — the index is only an accelerator and lagging indexes affect freshness, not correctness.

## Core Concepts

| Concept | Description |
| --- | --- |
| Search domain | Retrieval model of one document: `id`, `mainEntity`, `pkField` (default `id`), `fields[]`, plus `children[]` aggregations (`entity` + FK `fkField` + `fields[]`). Child-entity changes rebuild the parent document automatically |
| Document | Document id = main-entity primary key; `fieldValues` is multi-valued — main-table fields single value, aggregated child fields multiple values |
| Query | Multi-keyword OR × multi-field contains (aligned with `gfn_fuzzymatch`); optional tenantCode/delStatus/appId filters; `maxIds` default 50000 |
| Truncation | A truncated hit set **cannot be used for equivalent routing** (re-injecting ids would miss documents); routing must fall back to SQL |

## Quick Start

1. **Dependency**: quickstart already includes `geelato-search-api` + `geelato-search-lucene`.
2. **Configure a domain** (`geelato.search.domains.*`):

```properties
geelato.search.domains[0].id=il_eco
geelato.search.domains[0].main-entity=il_eco
geelato.search.domains[0].pk-field=id
geelato.search.domains[0].fields[0]=soNo
geelato.search.domains[0].fields[1]=mblNo
geelato.search.domains[0].fields[2]=containerNo
# child-entity aggregation
#geelato.search.domains[0].children[0].entity=il_so_container_fee_detail
#geelato.search.domains[0].children[0].fk-field=orderId
#geelato.search.domains[0].children[0].fields[0]=containerNo
# view-entity routing map
#geelato.search.domains[0].route-entities.vt_eco_view.pk-column=id
#geelato.search.domains[0].route-entities.vt_eco_view.column-map.so_no=soNo
```

3. **Backfill**: `POST /api/search/reindex/{domainId}`, poll `GET /api/search/reindex/{domainId}/progress` until `finished=true`. Domains without a completed reindex never participate in routing.
4. **Automatic routing**: fuzzymatch conditions on that domain route automatically (zero business-code changes); index sync hooks ORM save/delete events.

## Management & Operations

| Endpoint | Description |
| --- | --- |
| `POST /api/search/reindex/{domainId}` | Trigger backfill (idempotent, domain-exclusive) |
| `GET /api/search/reindex/{domainId}/progress` | Backfill progress |
| `GET /api/search/domains` / `health` / `debug/{domainId}` | Domain list / engine health / debug info |
| `GET /api/search-ops/consistency` / `compensation` / `watermarks` | Read-only data for the ops page |
| `GET /search-ops.html` | Ops page (consistency / compensation queue / watermarks) |

## Configuration (`geelato.search.*`)

| Key | Default | Description |
| --- | --- | --- |
| `enabled` | true | Master switch; false keeps L1 fallback and skips index writes |
| `index-root-dir` | `data/search-index` | Index directory (one sub-directory per domain) |
| `max-ids` | 50000 | Routing hit cap; over the cap routing is abandoned |
| `reindex-batch-size` | 500 | Backfill batch size |
| `compensation-cron` | `0 * * * * ?` | Compensation retry schedule |
| `reconcile-cron` | `0 0 2 * * ?` | Daily watermark reconciliation |
| `retry-backoff-minutes` | `[1,5,30,120]` | Compensation backoff (over `max-retry-count`=16 → DEAD, manual intervention) |
| `domains[].{...}` | — | Domain definitions |

## Experiment Gate

L2 query routing (including the L1 rewrite) is gated by the **experiment feature gate**: a build-time whitelist constant `ExperimentFeatures.SEARCH` plus request header `X-Gl-Experiments: search`. **Index sync/compensation/reconciliation are not gated** (controlled by `geelato.search.enabled`), so you can warm indexes first and open query routing on your own release rhythm. See [Extending the Search Engine](extension.md).
