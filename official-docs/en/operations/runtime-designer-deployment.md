# Runtime / Designer Deployment and Dependencies

After stage 8, the web platform has been split into two publishable modules:

- `geelato-web-runtime`
- `geelato-web-platform`

This page explains their relationship, deployment pattern, and minimal configuration differences.

## Module Relationship

The current relationship is:

- `designer` depends on `runtime`
- `runtime` is the minimum subset for business execution
- `designer` adds design-time capabilities on top of the runtime subset

This is how the rule “runtime is a subset of designer” is represented in the current delivery model.

## Current Application Entries

The two current application entries are:

- `PlatformWebRuntime`
- `PlatformDesginer`

Both reuse the current `BootApplication` startup base class, but they separate design-time exposure through configuration.

The official business-facing scaffold is:

- `geelato-app-scaffold`

It depends on runtime capabilities, but it does not expose design-time endpoints by default.

## Key Configuration Difference

Runtime currently defaults to:

```properties
spring.application.name=geelato-web-runtime
geelato.web.platform.design-time.enabled=false
```

Designer currently defaults to:

```properties
spring.application.name=geelato-web-platform
geelato.web.platform.design-time.enabled=true
```

## What the Switch Means

Design-time endpoints are currently controlled through the design-time annotation set and conditional wiring.

Therefore:

- when `runtime` is deployed, design-time endpoints are disabled by default
- when `designer` is deployed, design-time endpoints are enabled by default

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
- [App Scaffold](../guide/app-scaffold.md)
- [SecurityContext Lifecycle](../runtime/security-context-lifecycle.md)
