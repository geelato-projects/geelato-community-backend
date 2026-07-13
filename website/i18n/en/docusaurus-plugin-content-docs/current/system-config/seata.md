# Seata Module

File:

- `properties/seata.properties`

## Purpose

This file carries reserved distributed transaction settings.

## Key Properties

- `geelato.svcp`
- `seata.enabled`
- `seata.application-id`
- `seata.tx-service-group`

## Notes

- Seata is not enabled by default
- use it only when distributed transaction behavior is explicitly required
- for deeper datasource customization, continue with [ORM / Datasource Extension](../orm/datasource-extension.md)
