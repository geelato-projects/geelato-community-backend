# Quick Start

This page is the recommended entry for consumers who want to build a new web application on top of the Geelato framework foundation.

## Recommended Consumption Path

1. Import `geelato-framework-bom`
2. Add `geelato-framework-starter`
3. Start from `geelato-sample-quickstart`
4. Add business modules only after the minimal skeleton is running

## What You Need

- Java 17
- Maven build environment
- A Spring Boot compatible runtime
- Optional database services if you go beyond the minimal H2-based sample

## Minimal Goal

The shortest successful path is:

- The application starts successfully
- A primary datasource is available
- `JdbcTemplate`, `Dao`, ORM, and web auto-configuration are wired
- A sample endpoint can be called from the browser or curl

## Suggested Reading Order

- [Sample Quickstart](sample-quickstart.md)
- [BOM and Starter](../reference/bom-and-starter.md)
- [Core Modules](../reference/core-modules.md)
- [PlatformWebRuntime](../runtime/platform-web-runtime.md)

## What Is Out of Scope for the Minimal Start

The minimal start intentionally does not require:

- platform-specific upload runtime
- market, message, schedule, or auth extension modules
- designer-side metadata management capabilities
- full OpenAPI governance
