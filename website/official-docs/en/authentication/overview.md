# Independent Service: Unified Authentication Center

> **Note: Unified Authentication is not considered a built-in core framework capability. It is essentially an independent Unified Authentication Center (Auth Server) service.**
>
> It primarily provides two integration capabilities:
> 1. **Standardized OAuth2 integration**
> 2. **Lightweight lite-login integration**
>
> This chapter focuses on how internal and external business systems integrate with this independent auth center, delegating user login and identity recognition to it.

## Integration Methods: Comparison & Selection

The Auth Center provides two integration methods. External business systems can choose flexibly based on their tech stack and frontend interaction requirements:

| Dimension | Method 1: Lightweight `lite-login` | Method 2: Standard OAuth2 |
| --- | --- | --- |
| **Core Mechanism** | The Auth Center provides a ready-to-use frontend login facade (`lite-login`), pushing the token to the business frontend via cross-domain `postMessage`. | Uses the standard OAuth2 Authorization Code Flow, exchanging tokens via server-to-server redirects. |
| **Frontend Interaction** | The business system embeds `lite-login` via iframe or opens it in a new window. **No need** for the business system to write a login UI; the UX is seamless. | A **full-page browser redirect** occurs, jumping to the Auth Center's unified login page, then redirecting back to the business system. |
| **Integration Complexity** | **Very Low**. Primarily frontend integration. The business backend only needs an interceptor to validate the Bearer token. | **Medium**. Requires the business backend to support a complete OAuth2 client protocol stack. |
| **Use Cases** | 1. Modern frontend-backend separated architectures (Vue/React, etc.)<br/>2. Wanting to pop up a login box directly within the business system (without leaving the page)<br/>3. Pure frontend SPA applications | 1. **Any application with an independent backend**<br/>2. Strict security requirements where tokens must never be exposed to the browser<br/>3. Existing external systems with built-in standard OAuth2 Client modules |
| **How to Integrate** | 👉 [Read the lite-login Integration Guide](lite-login-integration.md) | 👉 [Read the Standard OAuth2 Integration Guide](oauth2-integration.md) |

## What Unified Authentication Solves

The main goal is to decouple authentication from each business system's own session model:

- `auth-server` issues tokens independently and centrally, acting as the only trusted identity source.
- `lite-login` provides the lightweight, cross-domain embeddable login frontend facade.
- The business system frontend (third-party application) receives the token and passes it to its own backend business service.
- The business system backend confirms the real user identity against the independent authentication center using the token.

With this model, whenever a new portal, SaaS application, or customer-owned system is added, there is no need for each system to repeat a "username + password" login flow. It can simply delegate this to the independent authentication center service.

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

## Boundary between Independent Service and Framework Capabilities

Recommended boundaries between the independent Auth Server and the Geelato framework itself:

- **Auth Server (Independent Service)**: How the business system integrates, gets the central token, and confirms user identity.
- **Security Authentication (Framework Capability)**: How the business system consumes the token in the platform runtime and establishes the authenticated subject for the current request.
- **Runtime Security Chain (Framework Capability)**: How to establish backend security context after authentication succeeds.
- **MQL / ORM (Framework Capability)**: How to access business data after identity is confirmed.

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

## Recommended Reading Order

1. [lite-login Integration Guide](lite-login-integration.md)
2. [Standard OAuth2 Integration Guide](oauth2-integration.md)
3. To understand how the framework consumes Tokens, see [Platform Capabilities: Authentication](security-authentication.md)
4. To understand the security context, see [Platform Capabilities: SecurityContext Lifecycle](../runtime/security-context-lifecycle.md)
