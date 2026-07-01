# Default Implementations vs Sample

This page explains the boundary between three layers:

- framework foundation
- module-internal default implementations
- official samples
- official scaffold

After stage 7 and stage 8, this boundary is part of the public consumption model.

## What the Foundation Is

The recommended public entry today is:

- `geelato-framework-bom`
- `geelato-framework-starter`

The foundation is responsible for:

- basic web wiring
- primary `DataSource`, `JdbcTemplate`, and `Dao`
- ORM auto-configuration
- dynamic datasource auto-configuration entry
- shared runtime infrastructure such as the `SecurityContext` cleanup filter

## What a Default Implementation Is

A default implementation is an implementation shipped inside a module but still replaceable by the host project.

Current examples include:

- `DefaultMetaStore`
- `PlatformDynamicDataSourceDefinitionLoader`
- `DefaultUserProvider` / `DefaultOrgProvider`

These implementations are useful out of the box, but they are not the final framework contract.

## What a Sample Is

A sample proves that the framework foundation can run independently. It is not the place to aggregate every platform capability.

The official minimal sample is:

- `geelato-sample-quickstart`

It intentionally keeps only:

- `geelato-framework-starter`
- an H2 primary datasource
- a minimal startup class
- one runtime sample endpoint

## What the Scaffold Is

The official scaffold is:

- `geelato-app-scaffold`

Its job is not to prove that the foundation starts. Its job is to provide a default runtime-ready starting point for business projects.

It assembles:

- login
- MQL
- organization and user services
- dictionary
- upload

## Why a Sample Must Not Become the Foundation

Samples may carry demonstration choices, but the foundation must not be polluted by those choices.

Examples:

- `sample-quickstart` uses H2 only for minimal startup
- `app-scaffold` targets real-project runtime basics
- `web-quickstart` is an extended sample with more dependencies
- upload runtime, designer governance, and package publishing must not be treated as mandatory starter content

## Suggested Reading

- [Minimal Integration](minimal-integration.md)
- [Sample Quickstart](sample-quickstart.md)
- [App Scaffold](app-scaffold.md)
- [Override Default Implementations](../reference/override-default-implementations.md)
