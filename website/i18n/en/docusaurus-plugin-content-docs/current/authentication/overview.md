---
id: overview
title: Independent Service — Unified Authentication Center
sidebar_label: Unified Auth Center
---

# Independent Service: Unified Authentication Center

> **Note: Unified Authentication is not considered a built-in core framework capability. It is essentially an independent Unified Authentication Center (Auth Server) service.**
>
> It provides three integration blocks to the outside:
> 1. **Login Integration** — business systems adopt unified login: Lite lightweight integration, OAuth2 protocol integration (including machine-to-machine), and Data Integration (org / user / role sync)
> 2. **Permission Integration** — adopt unified permission management (five-type permission distribution, mirrors, data-permission injection)
> 3. **Management Integration** — integrators self-manage their own business data via the Open API
>
> This chapter focuses on how internal and external business systems integrate with this independent auth center, delegating user login, identity recognition, and permission data to it.

## Login Integration: comparison of the three methods

Login integration offers three methods. External business systems can choose flexibly based on their tech stack and frontend interaction requirements:

| Dimension | Method 1: Lite lightweight integration | Method 2: OAuth2 protocol integration (incl. M2M) | Method 3: Data integration |
| --- | --- | --- | --- |
| **Core Mechanism** | The Auth Center provides a ready-to-use frontend login facade (`lite-login`), pushing the token to the business frontend via cross-domain `postMessage`. | Standard OAuth2: with user involvement use the Authorization Code Flow (token exchange via server-side redirects); without user involvement use `client_credentials` (machine-to-machine) to obtain a machine token directly. | Sync org, user, role and relationship data into your system — or provision user data into the auth center — via the Open API / SDK / account-provision API. |
| **Frontend Interaction** | The business system embeds `lite-login` via iframe or opens it in a new window. **No need** for the business system to write a login UI; the UX is seamless; in embedded mode the host page can control the display language. | Authorization code: a **full-page browser redirect** to the unified login page, then redirecting back. Machine-to-machine: no frontend involvement; pure server-side calls. | No login interaction involved. |
| **Integration Complexity** | **Very Low**. Primarily frontend integration. The business backend only needs an interceptor to validate the Bearer token. | Authorization code: **Medium**, requires a standard OAuth2 client protocol stack in the backend; machine-to-machine: **Very Low**, a single HTTP call to obtain a token. | **Low**. REST APIs or a Java SDK, ready to use. |
| **Use Cases** | 1. Modern frontend-backend separated architectures (Vue/React, etc.)<br/>2. Wanting to pop up a login box directly within the business system (without leaving the page)<br/>3. Pure frontend SPA applications | Authorization code: 1. **Any application with an independent backend** 2. Strict security where tokens must never be exposed to the browser 3. Existing systems with built-in standard OAuth2 Client modules. Machine-to-machine: service-to-service integration, scheduled jobs, Open API calls. | 1. Initializing org / user / role data for a new system<br/>2. Business backends querying user / org data (picker components)<br/>3. Approval flows auto-provisioning accounts per request |
| **Token Identity** | Logged-in user | Logged-in user (authorization code) / the application itself (machine token, `client_id`) | The calling application itself (machine token) |
| **How to Integrate** | [Read the Lite Login Integration Guide](lite-login-integration.md) | [Read the OAuth2 Protocol Integration Guide](oauth2-integration.md) | [Read the Data Integration Guide](data-integration.md) |

Login capability checklist (supported by both the unified login page and the Lite API): username-password login, SMS verification-code login, graphic / slider / rotate captchas, forgot-password, third-party login (WeChat, WeCom, Feishu, DingTalk), first-login forced password change guidance.

## What Unified Authentication Solves

The main goal is to decouple authentication from each business system's own session model:

- `auth-server` issues tokens independently and centrally, acting as the only trusted identity source.
- `lite-login` provides the lightweight, cross-domain embeddable login frontend facade.
- The business system frontend (the third-party application) obtains the token and passes it to its own backend.
- The business system backend takes the token and confirms the user's real identity with the independent auth center.

This way, whether it is a new portal, a SaaS, or a customer-owned system, none of them needs to re-implement its own "username + password" authentication flow — everything is delegated to the independent auth center.

## Core Components

### `auth-server`

`auth-server` is the unified authentication center and the only trusted token issuer.

It is responsible for:

- Issuing access tokens (OAuth2 protocol endpoints: authorize / token / client_token / introspect / userinfo, etc.)
- Centrally managing org / user / role / menu / permission data
- Distributing users' effective permissions and visible menus
- Acting as the trusted source for third-party application backends to confirm user identity

### `lite-login`

`lite-login` is the lightweight login facade provided by the unified auth center.

It is responsible for:

- Hosting the login interaction (password / SMS / third-party login)
- Supporting embedding via iframe or a new window in third-party applications
- Returning the login result to the third-party frontend via `postMessage`
- In `display=embedded` mode, letting the parent page control language switching

## Scope of Application

Recommended for:

- Third-party portals
- Third-party SaaS
- Customer-owned systems
- Frontend-backend separated applications that need to embed the unified login page
- Externally deployed systems that want to reuse the unified auth center

Not applicable to:

- The platform's internal `admin-sso` original login page hosting mode
- Traditional all-in-one login pages hosted directly in `auth-server templates`

## Responsibility Boundaries

### Auth center responsibilities

- `auth-server` is the only token issuer
- `lite-login` is the lightweight login facade
- After login succeeds, `lite-login` returns the token
- The user's real identity is ultimately determined by the backend calling `/oauth2/userinfo`

### Third-party application frontend responsibilities

- Open or embed `lite-login`
- Listen to `postMessage`
- Extract `accessToken` or `token`
- Temporarily store the token
- Attach `Authorization: Bearer <token>` when calling its own backend

### Third-party application backend responsibilities

- Read the Bearer token from the request header
- Call the auth center's `/oauth2/userinfo`
- Extract `data.user` from the response
- Build its own account mapping, permission context, or local session

## Boundaries with the Framework and Platform Capabilities

Suggested boundaries between the independent Auth Server and Geelato framework / platform capabilities:

- **Auth Server (independent service)**: solves "how business systems integrate, obtain a unified token, confirm user identity, and sync master data & permissions".
- **Security authentication (framework capability)**: solves "how the platform runtime consumes the token and establishes the request's auth principal".
- **Runtime chain (framework capability)**: solves "how the backend establishes the security context after authentication".
- **MQL (platform capability)**: solves "how the frontend and the platform protocol side access business data after identity is confirmed".
- **ORM (framework capability)**: solves "how backend Java services access business data".

Special notes:

- The `user` obtained from `LOGIN_SUCCESS` on the frontend is display-auxiliary information only
- The trusted identity must always be the backend-confirmed result
- `SecurityContext` can only be set inside the security chain after successful authentication

## Unified Entry Constraint

The recommended unified entry is:

```text
https://<auth-host>/lite-login
```

Do not reuse:

```text
/login?display=embedded
```

Each third-party application should still keep its own `/login` page, but only as a landing page — it should not perform username-password authentication itself.

## Recommended Reading Order

1. Decide the **login integration method** and follow its guide:
   - [Lite Login Integration](lite-login-integration.md) (ready-made login page, iframe / popup embedding, postMessage return)
   - [OAuth2 Protocol Integration](oauth2-integration.md) (authorization code flow / machine-to-machine client_credentials)
2. To **sync org / user / role data**: [Data Integration](data-integration.md) (Open API / SDK / account provision / SQL scripts)
3. To adopt **unified permission management** (five-type permission distribution / mirror tables / data-permission injection): [Permission Integration](permission-integration.md)
4. To **self-manage your own system's data** and build your own admin UI: [Management Integration](open-api-management.md) (Open API endpoint reference)
5. How the framework consumes tokens: [Platform Capability: Authentication](security-authentication.md)
6. Security context: [Security Contract: Security Context](../security/context.md)
