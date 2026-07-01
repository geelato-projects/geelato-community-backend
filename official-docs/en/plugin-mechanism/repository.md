# Plugin Repository

This page explains the current repository-related configuration and the meaning of:

- `geelato.plugin.pluginDirectory`
- `geelato.plugin.pluginRepository`

## Two Different Paths

The defaults are:

- `pluginDirectory = plugins`
- `pluginRepository = plugins/repository`

They serve different purposes.

### `pluginDirectory`

This is the actual runtime plugin root directory used for loading and managing deployed plugins.

### `pluginRepository`

This is the repository metadata directory used by the update manager.

## Current Repository Metadata Files

The current workspace already contains:

- `plugins/repositories.json`
- `plugins/plugins.json`

These files describe:

- repository entries
- plugin release lists

## `repositories.json`

The current sample defines a folder repository with:

- `id`
- `url`
- `pluginsJsonFileName`

## `plugins.json`

The current sample defines plugin releases with:

- `id`
- `description`
- `releases`

and each release includes:

- `version`
- `date`
- `url`

## Current Capability Boundary

The runtime already creates:

- `UpdateManager`

so repository support is reserved in the current implementation.

However, the code does not yet expose a full public management workflow for:

- browsing repositories
- installing from repository
- updating plugin versions online

So the safest current interpretation is:

- repository metadata and update-manager infrastructure exist
- practical deployment still mainly relies on packaging and copying plugins into the plugin directory

## Suggested Reading

- [Overview](overview.md)
- [Definition and Development](development.md)
- [Loading, Start/Stop and Uninstall](lifecycle.md)
