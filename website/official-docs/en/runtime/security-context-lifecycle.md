# SecurityContext Lifecycle

The runtime `SecurityContext` now has a clear request-scoped lifecycle boundary, and this is one of the most important runtime guarantees in the framework.

## Core Rules

The current implementation follows two hard rules:

- `SecurityContext` is thread-local
- the security principal can only be written after authentication succeeds inside the security chain

A generic filter must not inject the principal directly from the request. Its job is cleanup only.

## What Happens at Request Entry

`FilterConfiguration` registers common filters, and `SecurityContextFilter` wraps the request chain.

However, it does not set the principal. It only guarantees:

- `SecurityContext.clear()` in `finally`

## Who Sets the Principal

The current authentication and context population logic lives in `DefaultSecurityInterceptor`.

In `preHandle()`, it tries authentication in this order:

- cache restore
- anonymous
- JWT
- extend-key based authentication
- OAuth2

Only after authentication succeeds does it write the current user, tenant, and password into `SecurityContext`.

## Why the Filter Must Not Set the Principal

This is a hard boundary in the current framework:

- the filter is part of the generic request entry
- it must not trust frontend-controlled principal claims
- the principal must only come from the internal authentication chain

That is why `SecurityContextFilter` is limited to cleanup.

## What Happens at Request Exit

No matter whether the request succeeds or fails, `SecurityContextFilter` clears the context in `finally`, which avoids:

- context leaks across reused threads
- dirty context after exceptions
- accidental cross-request principal reuse

## Async Propagation

If an async task must inherit the current principal, use:

- `SecurityContextRunnable.wrap(...)`

It restores the required context before execution and clears it again when the task finishes.

## Suggested Reading

- [Authentication Overview](../authentication/overview.md)
- [PlatformWebRuntime](platform-web-runtime.md)
- [Runtime / Designer Deployment and Dependencies](../operations/runtime-designer-deployment.md)
