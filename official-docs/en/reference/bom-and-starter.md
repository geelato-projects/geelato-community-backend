# BOM and Starter

The framework delivery entry is based on `BOM + Starter`.

## Official Consumption Entry

- `geelato-framework-bom`
- `geelato-framework-starter`

## Why This Delivery Model Exists

Framework consumers should not have to manually reason about all foundation modules and their wiring order.

The starter provides a single recommended entry point, while the BOM centralizes version alignment.

## Starter Scope

`geelato-framework-starter` currently aligns the minimal foundation modules:

- `geelato-lang`
- `geelato-utils`
- `geelato-security`
- `geelato-core`
- `geelato-web-common`
- `geelato-dynamic-datasource`
- `geelato-orm`

## What the Starter Is Not

The starter is not the place for implementation-heavy platform defaults such as:

- platform-specific upload behavior
- designer-only metadata management
- sample-only extension dependencies

## Recommended Usage Rule

Consumers should start from the starter, then add:

- runtime modules when needed
- designer modules when needed
- sample or business extension modules only after the base skeleton is stable
