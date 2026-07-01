# Minimal Integration

This page explains how to start a new Geelato-based project from scratch and keep the first integration focused on the framework foundation instead of platform-heavy extensions.

## Recommended Order

1. Import `geelato-framework-bom`
2. Add `geelato-framework-starter`
3. Start from `geelato-sample-quickstart`
4. Verify the primary datasource, `JdbcTemplate`, `Dao`, ORM, and one sample endpoint
5. Move to `geelato-app-scaffold` once the project needs ready-to-use backend services
6. Add runtime, designer, or business extensions only after the minimal path is stable

## Minimal Dependency Rule

At the beginning, keep only:

- `geelato-framework-bom`
- `geelato-framework-starter`
- one primary datasource driver
- a Spring Boot web application

Do not start with:

- platform-specific upload runtime
- designer-side metadata governance
- `message`, `market`, `schedule`, or similar extensions
- heavy platform capabilities that require extra middleware

## Minimal Configuration Surface

The current starter wiring is triggered by `spring.datasource.primary.jdbc-url`, and it creates these default beans:

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `dbGenerateDao`

If `spring.datasource.secondary.jdbc-url` is also provided, the secondary beans are created as well.

## Recommended Starting Point

The official minimal sample is `geelato-sample-quickstart`, which uses:

- `geelato-framework-starter`
- an in-memory H2 datasource
- `geelato.orm.dao-bean-name=dynamicDao`
- JTA and Seata proxy disabled by default

Its purpose is to prove that the framework foundation can be consumed independently.

## When to Switch to the Scaffold

If the project already needs these baseline backend capabilities:

- login
- MQL
- organization and user management
- dictionary
- upload

do not keep growing the minimal sample. Move to:

- `geelato-app-scaffold`

Its role is to be the official ready-to-start scaffold, not the minimal verification sample.

## Minimal Success Criteria

The shortest successful path should verify:

- the application starts
- the primary datasource is available
- `JdbcTemplate` and `Dao` can be injected
- ORM auto-configuration is active
- one runtime endpoint can be called

## Suggested Reading

- [Sample Quickstart](sample-quickstart.md)
- [App Scaffold](app-scaffold.md)
- [Default Implementations vs Sample](default-implementation-vs-sample.md)
- [BOM and Starter](../reference/bom-and-starter.md)
- [MetaStore Extension](../reference/metastore-extension.md)
