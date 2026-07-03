# MetaStore Extension

The framework now exposes metadata definition sources through the `MetaStore` SPI, so the platform-table implementation is no longer the only possible metadata source.

## Available Extension Points

The current metadata SPI surface includes:

- `MetaStore`
- `MetaStoreProvider`
- `MetaResourceProvider`
- `MetaBootstrap`

These contracts are located under `cn.geelato.core.meta.spi` in `geelato-core`.

## Default Implementation

The current module-internal default implementation is `DefaultMetaStore`.

Its responsibility is to:

- load table definitions from the current platform table structure
- load columns, views, checks, and foreign keys
- return metadata definition bundles by entity name or view name

This keeps compatibility with the current platform schema while opening the door for replacement.

## How It Is Wired

`MetaConfiguration` now carried by `geelato-web-runtime` supports optional bean injection for:

- `MetaStore`
- `MetaResourceProvider`
- `MetaBootstrap`

If the host application provides those beans, `MetaManager` uses them before continuing with package scanning, database metadata parsing, and optional bootstrap logic.

## Typical Replacement Scenarios

Custom `MetaStore` implementations are appropriate when:

- the project does not use the default platform metadata tables
- metadata comes from a configuration center
- metadata comes from JSON, YAML, or the file system
- metadata must be composed from multiple sources at startup

## Suggested Boundary

Keep responsibilities separate:

- `MetaStore`: metadata definition source
- `MetaResourceProvider`: resource source
- `MetaBootstrap`: startup-time metadata initialization

## Suggested Reading

- [Core Modules](core-modules.md)
- [Override Default Implementations](override-default-implementations.md)
- [Minimal Integration](../guide/minimal-integration.md)
