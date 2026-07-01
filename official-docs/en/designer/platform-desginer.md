# PlatformDesginer

`PlatformDesginer` is the designer-focused application layer in the web platform split.

## Positioning

It extends the runtime subset with design-time and governance capabilities.

## Typical Design-Time Scope

- metadata management
- model and table design
- script and API definition management
- package and release tooling
- administration-oriented security and governance interfaces

## Relationship with Runtime

- the designer side includes the runtime baseline
- the runtime side must not depend on design-time editing and governance capabilities

## Documentation Importance

This split is now a first-class concept in the official documentation because it affects:

- module boundaries
- deployment model
- API grouping
- future OpenAPI governance
