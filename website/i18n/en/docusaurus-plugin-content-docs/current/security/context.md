---
title: Security Context
sidebar_label: Security Context
---

# Security Context

`SecurityContext` is the thread-level security context: it holds the current thread's user, tenant, password, and application through `ThreadLocal` variables, and is the single entry for reading "who is current" at runtime. This page documents the four dimensions, the judgment methods, the write boundary, and cross-thread propagation.

## The Four Context Dimensions

| Dimension | Held type | Meaning | Written by |
| --- | --- | --- | --- |
| `threadUser` | `User` | Current user principal | The security chain after successful authentication |
| `threadTenant` | `Tenant` | Current tenant | The security chain after successful authentication |
| `threadPassword` | `String` | Current request credential | The security chain after successful authentication |
| `threadApp` | `App` | Current application (subsystem) | Subsystem identification scenarios |

## Read/Write Methods

| Method | Semantics |
| --- | --- |
| `setCurrentUser(User)` / `getCurrentUser()` | Write/read the current user |
| `setCurrentTenant(Tenant)` / `getCurrentTenant()` | Write/read the current tenant |
| `setCurrentPassword(String)` / `getCurrentPassword()` | Write/read the current credential |
| `setCurrentApp(App)` / `getCurrentApp()` | Write/read the current application |
| `clear()` | Clears all four dimensions |

## Judgment Methods

| Method | Semantics |
| --- | --- |
| `isDelegated()` | Whether the current operation is a delegation: the current user's `delegateUserId` is non-empty |
| `getDelegateUserId()` | The delegate's ID, `null` when there is no current user |
| `isAdmin()` | Iterates the current user's **direct roles** (`userRoles`); `true` if any `code` contains `"admin"` |
| `isSystemPrincipal()` | Whether the current thread runs as a system background principal: decided by the `currentUser.systemPrincipal` flag, set explicitly by background jobs when establishing the context (e.g. `ScheduleHelper.wrapWithContext`) |

:::warning isAdmin historical semantics
`isAdmin()` reads direct roles only, not org-attached roles — which differs from the union semantics of `User.getEffectiveRoles()`. Source comments declare this difference a preserved contract (historical consumer behavior unchanged); new code checking effective roles should use `getEffectiveRoles()`.
:::

## Write Boundary

`SecurityContext` follows two hard constraints:

- It is a thread-level context whose lifetime matches the request.
- The security principal can only be set inside the security chain after successful authentication.

Today, authentication and principal writing belong to the platform-side `DefaultSecurityInterceptor`: in `preHandle()` it tries cache recovery, Anonymous, JWT, extended-key, and OAuth2 authentication in order, and only after success writes the current user, tenant, and password into `SecurityContext` (flow details in [Authentication & Authorization](../authentication/security-authentication.md)).

The generic `SecurityContextFilter` never sets the principal; it only runs `SecurityContext.clear()` in a `finally` block when the request ends. Why:

- Filters sit at the generic request entrance and must not trust principal information the frontend could declare directly;
- The principal can only come from the internal chain after successful authentication;
- Clearing once at request end prevents thread-reuse principal leaks and dirty contexts left by failed requests.

Usage constraints:

- Controllers and services only read `SecurityContext`; never fabricate a pseudo-principal in business code;
- Never parse headers in a generic filter and write them straight into the context;
- To extend the authentication chain, do it inside the interceptor / security chain.

## Cross-Thread Propagation

The four dimensions are plain `ThreadLocal`s and do not travel across thread switches (the framework uses neither `InheritableThreadLocal` nor `TransmittableThreadLocal`). An async task that needs the current principal must **explicitly** wrap itself with `SecurityContextRunnable` — a capture-replay pattern:

| Factory method | Semantics |
| --- | --- |
| `wrap(Runnable)` | Captures the current thread's `User`/`Tenant` automatically (captured at submission time) |
| `wrap(Runnable, User, Tenant)` | Explicit values, for scheduled jobs and other contexts without HTTP |
| `wrap(Runnable, User)` | Explicit `User`; `Tenant` captured from the current thread |

`run()` sequence: `setCurrentUser`/`setCurrentTenant` on entry → run `delegate.run()` → `SecurityContext.clear()` in `finally` (cleared after the task, so pool threads stay clean).

```java
// Capture the current thread's context automatically
CompletableFuture.runAsync(SecurityContextRunnable.wrap(() -> {
    // SecurityContext.getCurrentUser() works here
}));

// Explicit user (for scheduled jobs without HTTP context)
CompletableFuture.runAsync(SecurityContextRunnable.wrap(() -> { ... }, overrideUser, overrideTenant));
```

Two notes:

- Only `User`/`Tenant` propagate; `password`/`app` do not.
- The wrapper is a `Runnable`, so any executor accepting `Runnable` works (`CompletableFuture`, custom pools, etc.).

### Context Facilities with the Same Pattern

| Facility | Location | Pattern |
| --- | --- | --- |
| `EngineContextExecutor` | `geelato-exception` | Save original context → replace → restore in `finally`; offers `withContext`/`withSystemContext` (cross-tenant)/`withEngineContext`/`withParticipantPrincipal` (participant identity delegation) |
| `AlertContextExecutor` | `geelato-alert` | `withContext` (injects a system principal when no session exists)/`withParticipantPrincipal` |
| `ScheduleHelper.wrapWithContext` | Host-side scheduler | Establishes the context for background jobs with a system principal (`systemPrincipal=true`) |

## Summary

- `SecurityContext` is a thread-level context of four `ThreadLocal` dimensions: user / tenant / password / app; `clear()` empties them all at once.
- The write boundary is hard: principals are written only by the security chain after successful authentication, and filters are responsible only for cleanup at request end.
- Cross-thread propagation is never automatic — async tasks must explicitly use `SecurityContextRunnable.wrap(...)` (capture-replay with finally cleanup); among the judgment methods, `isAdmin` reading direct roles only is historical semantics — use `getEffectiveRoles()` for effective roles.

## Suggested Reading

- [Authentication & Authorization](../authentication/security-authentication.md) — who writes the principal
- [Security Model](model.md) — the `User`/`Tenant` objects inside the context
- [Global Context](../platform-capabilities/global-context.md) — the request-scoped general context
