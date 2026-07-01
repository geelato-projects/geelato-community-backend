# Core Modules

This page explains the current framework-facing role of the main reusable modules.

## Foundation Modules

### `geelato-lang`

- Shared contracts, API result models, and low-level annotations

### `geelato-utils`

- Shared utility capabilities and cross-module helpers

### `geelato-security`

- Security contracts and context model

### `geelato-core`

- Core framework kernel including meta, DAO, scripts, and runtime managers

### `geelato-orm`

- Fluent DSL and ORM-side command execution capabilities

### `geelato-dynamic-datasource`

- Optional dynamic datasource layer with lightweight defaults

## Key Architecture Notes

- `SecurityContext` must be written only after successful authentication in the security chain
- `dynamicDao` remains higher priority than `primaryDao` for compatibility fallback
- upload-related platform defaults stay outside the minimal starter foundation

## First Consumer References

- [ORM Overview](../orm/overview.md)
- [ORM Annotations](../orm/annotations.md)
- [Fluent DSL Guide](../orm/fluent-dsl.md)
- Sample skeleton: `geelato-sample-quickstart`
- Runtime / Designer split: see the dedicated pages in this site
