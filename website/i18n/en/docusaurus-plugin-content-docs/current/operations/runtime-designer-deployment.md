# Runtime / Designer Deployment and Dependencies

The web platform is delivered as one shared base module plus two runnable boot modules:

- `geelato-web-platform` (shared base: controllers/services/boot)
- `geelato-web-runtime` (runtime boot)
- `geelato-web-designer` (designer boot)

This page explains their relationship, deployment pattern, and minimal configuration differences.

## Module Relationship

The current relationship is:

- `geelato-web-runtime` depends on `geelato-web-platform`
- `geelato-web-designer` depends on `geelato-web-platform`
- no dependency between `runtime` and `designer`

Both boot modules currently expose the full endpoint set from the shared base. The endpoint surface is not yet split at the shared-base level.

## Current Application Entries

The two current application entries are:

- `PlatformWebRuntime`
- `PlatformDesginer`

Both reuse the current `BootApplication` startup base class.

The official business-facing scaffold is:

- `geelato-app-scaffold`

It depends on runtime capabilities. Endpoint boundary will be reflected after the module-level endpoint split.

## Key Configuration Difference

Runtime currently defaults to:

```properties
spring.application.name=geelato-web-runtime
```

Designer currently defaults to:

```properties
spring.application.name=geelato-web-designer
```

## Why There Is No Switch

Design-time / runtime exposure is not controlled via a switch. It is controlled by module dependency. In the current phase, both boot modules expose the same endpoints.

## Recommended Deployment

### Runtime-only deployment

Use this for:

- business execution environments
- end-user facing host systems
- environments that must not expose metadata design, script management, or package publishing APIs

### Designer deployment

Use this for:

- low-code designer environments
- metadata, model, script, and publishing governance
- platform management environments

## Suggested Reading

- [PlatformWebRuntime](../runtime/platform-web-runtime.md)
- [PlatformDesginer](../designer/platform-desginer.md)
- [App Scaffold](../guide/app-scaffold-starter-project-guide.md)
- [SecurityContext Lifecycle](../runtime/security-context-lifecycle.md)
