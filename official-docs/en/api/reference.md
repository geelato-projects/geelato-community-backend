# API Reference

The official API documentation follows a dual-track model.

## Standard Contract

**OpenAPI** is the official external contract standard.

It should become the default entry for:

- external integrators
- runtime API consumers
- interface contract review
- future gateway or client generation

## Static Supplement

**SrvExplain** remains valuable as a generated static supplement because it can:

- scan current controllers directly from source
- expose undocumented controller endpoints during transition
- provide controller-level markdown references

## Current State

At the moment:

- OpenAPI support exists in the quickstart side through `springdoc`
- `SrvExplain` is already generated and available in the repository
- the platform still relies heavily on `MetaController + MQL` for internal generic data access

## Relationship with MQL

`MQL` is not the official external contract. It is the platform's internal metadata access protocol.

It is mainly used for:

- frontend pages that access entities through JSON payloads
- generic platform data endpoints
- low-code or metadata-driven configuration scenarios

Recommended interpretation:

- OpenAPI: official external contract
- `SrvExplain`: source-derived static supplement
- MQL: internal platform data protocol

## Recommended Official Presentation

- Primary entry: OpenAPI
- Supplementary entry: `SrvExplain/index.md`
- Platform protocol entry: see [MQL](../mql/overview.md)
- Static controller catalog: see [SrvExplain API Catalog](srvexplain-catalog.md)

## Repository Entry Points

- `SrvExplain/README.md`
- `SrvExplain/index.md`
- `geelato-web-quickstart/src/main/java/cn/geelato/web/swagger/SwaggerConfig.java`

## Full SrvExplain Catalog

The official site now includes a static API catalog grouped by module:

- [SrvExplain API Catalog](srvexplain-catalog.md)

This catalog pulls the currently generated controller documents from `SrvExplain/` into the API chapter for easier browsing and navigation.

## Migration Direction

The long-term direction is not to remove `SrvExplain`, but to reposition it as:

- static source-derived supplement
- migration aid
- internal completeness checker
