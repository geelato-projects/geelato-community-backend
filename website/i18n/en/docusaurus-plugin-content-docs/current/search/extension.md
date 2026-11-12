---
id: extension
title: Extending the Search Engine
sidebar_label: Extension Guide
---

# Extending the Search Engine

The search engine follows a port-adapter split: `geelato-search-api` defines the contract, `geelato-search-lucene` is the reference implementation. Business systems can replace it with another engine (e.g. Elasticsearch) or extend routing without changing engines.

## The SearchEngine Port

`cn.geelato.search.api.SearchEngine` is the single port:

| Method | Description |
| --- | --- |
| `name()` | Engine name (logging and routing decisions) |
| `capabilities()` | Capability declaration (below) |
| `upsert(domainId, docs)` | Whole-document replace upsert (idempotent) |
| `delete(domainId, docIds)` | Delete documents |
| `search(domainId, query)` | Search; returns `SearchResult` (ids + truncated flag) |
| `health()` | `UP / DEGRADED / DOWN` |

The semantics-critical part of `SearchEngineCapabilities` is the **consistency declaration**: `SYNC_FAIL_FAST` for embedded engines (like Lucene; write failures fail fast with the compensation queue as safety net), `ASYNC_EVENTUAL` for remote engines (like ES; the framework adjusts reconciliation expectations accordingly).

### Contract Essentials

- **Result equivalence**: `search` means "multi-keyword OR × multi-field contains", strictly aligned with the SQL-level `gfn_fuzzymatch` multi-word OR; any mismatch causes routing to miss documents.
- **tenant/delStatus/appId filter fields** are written with each document and must be applied from `SearchQuery`.
- **Report `truncated` honestly**: when hits exceed `maxIdsLimit`, return `truncated=true`; routing will abandon the route.
- **TCK**: `SearchEngineContractTest` in `geelato-search-api` must pass fully — it guarantees implementations are interchangeable without semantic drift.

## Steps to Plug in a Custom Engine

1. Implement `SearchEngine` against `geelato-search-api`.
2. Register it as a Spring Bean (at most one engine bean is wired; use `@Primary` when several exist).
3. Pass the TCK.
4. Backfill: once the engine supports bulk loading, reuse `POST /api/search/reindex/{domainId}` — `SearchReindexService` writes via `SearchEngine.upsert`, so it works for any engine.

Index sync (ORM save/delete listeners), the compensation queue, daily reconciliation and reindex orchestration are engine-agnostic and inherited automatically.

## Routing SPI (MqlFuzzymatchRouter)

L2 routing plugs in through core's `cn.geelato.core.mql.spi.MqlFuzzymatchRouter` SPI:

- `MqlQueryProcessor` calls `MqlFuzzymatchRouteResolver`, which takes **at most one** Router bean from the context (more than one throws; none logs at debug and falls back to SQL).
- `LuceneFuzzymatchRouter` is the default implementation: ① per-condition replacement of top-level predicates; ② whole-group replacement of or-bracketed groups (all fuzzymatch conditions with identical keywords).

### Equivalence Hard Constraints

If any of the following holds, routing **must be abandoned and SQL used** (info log with reason) — this is the baseline of result equivalence:

| Constraint | Reason |
| --- | --- |
| Domain not backfilled (no marker watermark) | Incomplete index; `id IN (...)` would miss documents |
| Keyword contains regex metacharacters | Cannot be aligned with L1 REGEXP semantics |
| Token length < 2 | NGram(2,2) has no 1-gram; contains semantics diverge |
| Re-assembled tokens ≠ whole pattern | Alignment check with stored-function quirks fails |
| Column not mappable to a domain field (incl. view `column-map`) | Routing target unknown |
| Hits exceed `maxIds` (truncated) | Truncated sets cannot be used for equivalent routing |

Empty hits re-inject the always-false value `__search_no_match__`, so "no search hits = no SQL rows".

## Roadmap

The Elasticsearch implementation is **design + TCK skeleton only** (`SearchEngineCapabilities.ASYNC_EVENTUAL` is reserved); no implementation module exists yet. Implementing the contract and passing the TCK allows a seamless swap — MQL syntax and domain configuration stay unchanged.
