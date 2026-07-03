# App Scaffold

`geelato-app-scaffold` is the official fat scaffold for starting business applications with ready-to-use runtime services while keeping the framework layering intact.

## What Problem It Solves

Stage 7 delivered `geelato-sample-quickstart` as a minimal proof that the foundation can start independently, but that sample intentionally does not bundle:

- login
- MQL
- organization and user management
- dictionary services
- upload

If those capabilities were pushed into the sample, the sample would stop being a minimal verification point.

The public delivery model is now split into three layers:

- `geelato-framework-starter`
  - minimal foundation
- `geelato-sample-quickstart`
  - minimal sample
- `geelato-app-scaffold`
  - ready-to-start scaffold

## Default Capability Set

The scaffold assembles the runtime base services from `geelato-web-runtime`, including:

- login
- MQL
- organization
- user
- dictionary
- upload

Design-time capabilities remain in `geelato-web-platform` and are not part of the scaffold's default boundary.

## Delivery Shape

The scaffold is delivered in two layers:

- `geelato-app-scaffold-starter`
  - the reusable dependency-upgrade entry
- `geelato-app-scaffold`
  - the runnable official application

This means new business projects should prefer:

1. starting from the scaffold application structure
2. keeping a dependency on `geelato-app-scaffold-starter`
3. receiving common capability upgrades through dependency updates

## Difference from the Sample

`sample-quickstart` remains focused on:

- minimal integration verification
- H2-based startup
- troubleshooting foundation wiring

`app-scaffold` is responsible for:

- practical runtime services for real projects
- default external database and upload-path conventions
- a shared starting point for developers and AI-assisted delivery

## How to Start

The operational source of truth stays in `../geelato-hello-example/geelato-app-scaffold/README.md`, including:

- database initialization
- platform schema and resource initialization
- configuration setup
- build and startup
- extension strategy
- dependency upgrade strategy

## Suggested Reading

- [Minimal Integration](minimal-integration.md)
- [Default Implementations vs Sample](default-implementation-vs-sample.md)
- [Runtime / Designer Deployment and Dependencies](../operations/runtime-designer-deployment.md)


