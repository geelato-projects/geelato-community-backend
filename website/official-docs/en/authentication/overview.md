# Unified Authentication

This chapter explains the current unified authentication integration boundary in Geelato Framework, focusing on the responsibility split between `auth-server` and `lite-login`, and on how third-party applications reuse the central authentication service.

## What Unified Authentication Solves

The main goal is to decouple authentication from each business system's own session model:

- `auth-server` issues tokens
- `lite-login` provides the lightweight login facade
- the third-party frontend receives the token and passes it to its own backend
- the third-party backend confirms the user identity against the authentication center

With this model, each new portal, SaaS application, or customer-owned system does not need to build its own username-password login flow.

## Core Components

### `auth-server`

`auth-server` is the unified authentication center and the only trusted token issuer.

It is responsible for:

- issuing access tokens
- exposing OAuth endpoints such as `/oauth2/userinfo`
- acting as the trusted identity source for third-party backends

### `lite-login`

`lite-login` is the lightweight login facade exposed by the unified authentication center.

It is responsible for:

- hosting the login interaction
- supporting iframe embedding or popup-based integration
- returning login results to the third-party frontend through `postMessage`

## Scope

Recommended for:

- third-party portals
- third-party SaaS systems
- customer-owned systems
- frontend-backend separated applications that need embedded login
- independently deployed systems that want to reuse the central authentication service

Not intended for:

- the original platform-internal `admin-sso` hosted login mode
- monolithic login pages hosted directly inside `auth-server templates`

## Responsibility Boundary

### Authentication Center

- `auth-server` is the only token issuer
- `lite-login` is the lightweight login facade
- `lite-login` returns the token after login succeeds
- the final trusted user identity still comes from backend confirmation through `/oauth2/userinfo`

### Third-Party Frontend

- opens or embeds `lite-login`
- listens to `postMessage`
- extracts `accessToken` or `token`
- stores the token temporarily
- passes `Authorization: Bearer <token>` to its own backend

### Third-Party Backend

- reads the Bearer token from request headers
- calls `/oauth2/userinfo`
- extracts `data.user`
- establishes account mapping, permission context, or local session state

## Relationship to Other Framework Capabilities

Recommended boundaries:

- unified authentication: how to get the central token and confirm identity
- security authentication: how the platform runtime consumes the token and establishes the authenticated subject for the current request
- runtime security chain: how to establish backend security context after authentication succeeds
- MQL / ORM: how to access business data after identity is confirmed

Important note:

- the `user` returned in `LOGIN_SUCCESS` is only auxiliary display data on the frontend
- the final trusted identity must be confirmed by the backend
- `SecurityContext` must only be written from the internal authenticated security flow

## Entry Constraint

Use the explicit lightweight login entry:

```text
https://<auth-host>/lite-login
```

Do not reuse:

```text
/login?display=embedded
```

Each third-party application should still keep its own `/login` page as the entry handoff page, but it should not implement username-password authentication itself.

## Suggested Reading Order

1. Read [Unified Authentication Architecture](architecture.md)
2. Read [Security Authentication](security-authentication.md) to understand the `DefaultSecurityInterceptor` auth flow
3. Read [lite-login Third-Party Integration](lite-login-integration.md)
4. Then read [PlatformWebRuntime](../runtime/platform-web-runtime.md) for the runtime security chain position
5. If you also need platform-side data access, continue with [MQL Overview](../mql/overview.md)
