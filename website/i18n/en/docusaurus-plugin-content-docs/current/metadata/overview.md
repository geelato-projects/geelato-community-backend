---
id: overview
title: Metadata Overview
sidebar_label: Metadata Overview
---

# Metadata Overview

Metadata (`EntityMeta`) is the foundation of the Geelato framework: ORM entity mapping, MQL query parsing, entity-to-datasource routing, and automatic field filling are all metadata-driven.

## Core Concepts

| Concept | Description |
| --- | --- |
| `EntityMeta` | Entity metadata: table name, field mapping, primary key, validation rules, view definitions, etc. |
| `MetaManager` | Metadata manager: loads, caches and detects conflicts of metadata; sits at the core of the startup chain |
| `MetaStore` | The metadata **definition source** SPI (`cn.geelato.core.meta.spi` package in `geelato-core`), decides "where metadata comes from" |
| `MetaResourceProvider` | SPI for metadata-related **static resources** (default column definitions, column option types, table upgrade columns) |

## Where Metadata Comes From

The framework abstracts "where metadata definitions come from" into the `MetaStore` SPI. The framework layer only knows the interface; concrete sources are replaceable:

- **Platform designer tables**: `DefaultMetaStore` reads definitions from the five platform designer tables (`platform_dev_*`); it is one of the default implementations.
- **Annotation declarations**: entities declare mappings directly via `@Model`, `@Col` and other annotations. Annotations and designer definitions are two sources; for entities with the same name, first-come-first-served.
- **Custom sources**: JSON files, config centers, external metadata services, etc. — implement the `MetaStore` interface and annotate `@Component @Primary` to replace the whole source chain without touching framework code.

:::info Documentation Status
This page is a skeleton page. Details such as the metadata cache structure, conflict detection rules, view metadata, and refresh mechanisms will be added in later versions.
:::

## Further Reading

- [MetaStore Extension](../reference/metastore-extension.md) — how to implement a custom metadata source
- [Core Modules](../reference/core-modules.md) — where `MetaManager` sits in the overall architecture
- [Startup Process](../reference/startup-process.md) — when metadata loading happens
- [ORM Annotations](../orm/annotations.md) — how to declare entities with annotations
