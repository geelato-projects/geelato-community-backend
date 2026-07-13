# Plugin Loading, Start/Stop and Uninstall

This page explains how the current runtime uses the plugin directory, what is supported by the management API, and how to understand uninstall in the current implementation.

## Plugin Directory

The runtime builds `SpringPluginManager` from:

- `geelato.plugin.pluginDirectory`

The default value is:

- `plugins`

So the basic loading model is still directory-based: plugin jars or directory-style plugins must be placed into the configured plugin directory.

## Current Management APIs

The current controller exposes:

- `GET /api/pm/list`
- `GET /api/pm/switchStatus`
- `GET /api/pm/log`
- `GET /api/pm/clearLog`

## List

`/api/pm/list` returns fields such as:

- `id`
- `version`
- `description`
- `provider`
- `dependencies`
- `state`
- `enabled`

## Start and Stop

`/api/pm/switchStatus` currently supports:

- `status=enable`
- `status=disable`

Internally it uses:

- `pluginManager.startPlugin(pluginId)`
- `pluginManager.stopPlugin(pluginId)`

So current public runtime management is focused on start/stop, not full install/delete workflows.

## How to Understand Uninstall

The current runtime does not publicly expose:

- `unloadPlugin`
- `deletePlugin`
- upload-and-install APIs

So uninstall should be understood in two layers:

- runtime disable: stop the plugin
- physical removal: stop it, remove the jar or plugin directory, then restart the application

## Plugin Logs

Plugin logs are written under:

- `plugins/logs`

The runtime can:

- read plugin logs
- clear plugin logs

through `/api/pm/log` and `/api/pm/clearLog`.

## Suggested Reading

- [Overview](overview.md)
- [Definition and Development](development.md)
- [Plugin Repository](repository.md)
