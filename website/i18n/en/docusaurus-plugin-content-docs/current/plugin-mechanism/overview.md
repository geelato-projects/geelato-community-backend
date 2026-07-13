# Plugin Mechanism Overview

This page describes the current Geelato plugin mechanism, its main runtime roles, and the difference between source modules and deployed plugin directories.

The mechanism currently consists of:

1. runtime plugin management
2. extension point contracts
3. plugin implementation projects

In the current repository these are represented by:

- runtime plugin integration in `geelato-web-runtime/.../plugin`
- shared plugin contracts in `geelato-plugins/geelato-plugin-all`
- concrete plugins such as `geelato-example-plugin`, `geelato-logging-plugin`, and `geelato-ocr-plugin`

## Runtime Components

### `PluginConfiguration`

Creates:

- `SpringPluginManager`
- `UpdateManager`

and initializes plugin paths from:

- `geelato.plugin.pluginDirectory`
- `geelato.plugin.pluginRepository`

with defaults:

- `plugins`
- `plugins/repository`

### `PluginBeanProvider`

Business code uses `PluginBeanProvider` to resolve an extension by:

- extension interface type
- `pluginId`

### `PluginManagerController`

The current management APIs are exposed under:

- `/api/pm/*`

including:

- `GET /api/pm/list`
- `GET /api/pm/switchStatus`
- `GET /api/pm/log`
- `GET /api/pm/clearLog`

## Source Directory vs Runtime Directory

### Plugin Source Workspace

Plugin source modules live in:

- `geelato-plugins`

This is the build workspace, not the runtime deployment directory.

### Runtime Plugin Directory

Actual deployed plugins live in:

- `plugins`

This directory may contain:

- plugin jars
- directory-style plugins
- repository metadata files
- plugin logs

## Extension Point Design

Shared extension contracts are defined through:

- `PluginExtensionPoint`

which extends:

- `org.pf4j.ExtensionPoint`

Concrete plugin APIs then extend `PluginExtensionPoint`, such as:

- `Greeting`

This keeps the host application coupled to contracts instead of plugin implementation classes.

## Suggested Reading

- [Definition and Development](development.md)
- [Loading, Start/Stop and Uninstall](lifecycle.md)
- [Plugin Repository](repository.md)
