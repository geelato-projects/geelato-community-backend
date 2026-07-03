# Unified Authentication Architecture

This page describes the overall architecture of the unified authentication center rather than the local integration details of one single consumer application.

Here, the unified authentication center refers to the combination of:

- `geelato-auth-server`: the backend authentication core and unified token issuer
- `gl-admin-sso`: the authentication facade for platform-internal sites
- `gl-lite-sso`: the lightweight embedded authentication facade for third-party applications
- `geelato-web-quickstart`: the platform backend host that can embed unified authentication capabilities
- platform sites such as `gl-admin-arco-rt-std` and `gl-admin-arco-dt-std`
- third-party applications such as `freight-portal`

This document answers four questions:

- what the overall boundary of the unified authentication center is
- how platform applications and third-party applications integrate differently
- how the unified token flows across systems
- how merged deployment and independent deployment can both be supported later

## Overall Design Conclusion

### Core Positioning

- `auth-server` is the backend core of the unified authentication center
- `auth-server` is the only issuer of the shared token used by all applications
- `gl-admin-sso` is the authentication frontend facade for platform sites
- `gl-lite-sso` is the lightweight authentication frontend facade for third-party applications
- platform applications and third-party applications no longer maintain separate primary token systems

### Two Integration Targets

The unified authentication center serves two kinds of applications:

- platform-internal applications such as `rt` and `dt`
- third-party applications such as `freight-portal`

The difference is not the authentication center itself, but the frontend facade used for integration:

- platform applications connect through `gl-admin-sso`
- third-party applications connect through `gl-lite-sso`

Both types finally return to the same `auth-server` authentication and token model.

### Deployment Conclusion

In the short term, merged deployment is acceptable:

- frontend sites can be hosted inside one platform host site
- backend capabilities can be converged into `geelato-web-quickstart`

In the long term, independent deployment must be supported:

- `admin-sso` can be standalone
- `lite-sso` can be standalone
- `rt` and `dt` can be standalone
- `auth-server` can be standalone

Regardless of the deployment topology, the authentication model remains unchanged:

- there is only one source of truth for backend authentication
- there is only one unified token model

## Overall Architecture

### Frontend Entry Layer

- platform sites: `gl-admin-arco-rt-std`, `gl-admin-arco-dt-std`
- platform authentication facade: `gl-admin-sso`
- third-party authentication facade: `gl-lite-sso`
- third-party applications such as `freight-portal`

### Unified Authentication Backend Layer

- backend host: `geelato-web-quickstart`
- authentication core: `geelato-auth-server`

### Runtime Dependencies

- site configuration: `.config`
- database: authentication and platform databases
- Redis: authentication state, cache, and runtime coordination

### Logical Relationships

- platform sites integrate with unified authentication through `gl-admin-sso`
- third-party applications integrate with unified authentication through `gl-lite-sso`
- both `gl-admin-sso` and `gl-lite-sso` eventually call `auth-server`
- both platform backends and third-party backends directly consume the unified token issued by `auth-server`

## Module Boundaries

### Unified Authentication Center

- `auth-server`: owns authentication protocols, login verification, unified token issuance, and unified user identity
- `admin-sso`: the platform login facade, not the source of truth for authentication
- `lite-sso`: the third-party lightweight login facade, not the source of truth for authentication

### Platform Applications

- `rt`
- `dt`
- the `quickstart` backend host

### Third-Party Applications

- `freight-portal`
- other external sites

### Boundary Notes

- platform and third-party applications are consumers of the authentication center and should no longer define their own primary token
- `freight-portal` is only an example consumer, not part of the authentication-center core itself
- frontend facades may change, but the unified token model must stay stable

## Unified Token Model

### Unified Principle

- the primary credential returned after login is always the `access_token` issued by `auth-server`
- all applications use this token as the unified login credential
- business backends trust and consume this token directly

### Meaning of the Unified Token

This means:

- `admin-sso` does not mint its own token
- `lite-sso` does not mint its own token
- platform sites do not mint their own primary token
- third-party applications do not mint their own primary token

The whole system has only one primary authentication credential model.

## Platform Application Sequence

Platform applications refer to platform sites such as `rt` and `dt`.

### Platform Main Sequence

1. the user opens a platform site
2. the platform site reads runtime configuration and authentication mode
3. the platform site redirects to or embeds `gl-admin-sso`
4. the user completes login in `gl-admin-sso`
5. `gl-admin-sso` calls `auth-server` for authentication
6. `auth-server` issues the unified token
7. the platform site receives the unified token
8. the platform backend builds its business user context from that token
9. the frontend enters the platform home page

### Platform Chain Roles

- platform site: receives login state and handles page transitions
- `gl-admin-sso`: handles platform login interaction
- `auth-server`: handles authentication and token issuance
- platform backend: builds the business user context from the token

## Third-Party Application Sequence

Third-party applications refer to `freight-portal` or other external business sites.

### Third-Party Main Sequence

1. the user opens the third-party application
2. the third-party application loads `gl-lite-sso` through iframe, popup, or page redirect
3. the user completes login in `gl-lite-sso`
4. `gl-lite-sso` calls `auth-server` for authentication
5. `auth-server` returns the unified `access_token`
6. `gl-lite-sso` sends `LOGIN_SUCCESS` back through `postMessage`
7. the third-party frontend passes the Bearer token to its own backend
8. the third-party backend validates the token with `auth-server` and resolves the unified identity
9. the third-party application builds its own business user context

### Third-Party Chain Roles

- third-party frontend: embeds the login facade, receives the token, and forwards it to its own backend
- `gl-lite-sso`: handles lightweight login interaction
- `auth-server`: handles authentication and token issuance
- third-party backend: handles token validation and identity mapping

## Two Primary Chains

### Platform Primary Chain

- platform site
- `gl-admin-sso`
- `auth-server`
- platform backend

Typical scenarios:

- platform-internal applications
- platform administration sites
- applications deeply coupled with platform site configuration

### Third-Party Primary Chain

- third-party application
- `gl-lite-sso`
- `auth-server`
- third-party backend

Typical scenarios:

- lightweight integration
- iframe or popup integration
- external applications that do not want to adopt the full platform login-page system

## Merged Deployment and Independent Deployment

### Merged Deployment

In merged deployment, a practical arrangement is:

- host `admin-sso` and `lite-sso` inside one host site
- converge backend capabilities into `geelato-web-quickstart`
- host `auth-server` capabilities in an embedded or same-service mode

Notes:

- this is only deployment merging, not authentication-model merging
- merged deployment changes topology only, not the source of truth of the unified token

### Independent Deployment

In independent deployment, the following parts can be split out:

- `rt`
- `dt`
- `admin-sso`
- `lite-sso`
- `auth-server`

Notes:

- splitting changes deployment relationships only, not the unified token model
- the redirect contract and message contract of frontend facades must remain stable

## Key Constraints

### `admin-sso`

- `admin-sso` is a platform authentication facade and does not need to stay forever inside `auth-server/templates`
- if it is currently hosted in templates, that is only one same-origin template mode
- it can later evolve into a standalone frontend deployment as long as the login entry and redirect contract stay stable

### `lite-sso`

- `lite-sso` must remain a lightweight facade and must not evolve into a second authentication center
- there must be an explicit `postMessage` protocol and origin allowlist between `lite-sso` and third-party applications

### Business Backends

- business backends must directly trust and validate the unified token
- business backends should not issue a new primary token again

## Recommended Unified Message Contract

In embeddable scenarios, it is recommended that both `lite-sso` and `admin-sso` use a unified success message shape:

```json
{
  "type": "LOGIN_SUCCESS",
  "data": {
    "accessToken": "xxxx",
    "refreshToken": "xxxx",
    "expireInSeconds": 7200,
    "tokenType": "Bearer",
    "issuer": "auth-server"
  }
}
```

Recommended principles:

- keep token as the center of the message contract
- a `user` field may exist as auxiliary data, but it is not the final trusted object
- business sites should always trust the backend-confirmed user result

## Security and Governance Requirements

### Frontend Facades

- both `admin-sso` and `lite-sso` must enforce explicit origin control
- production environments must not use permissive `postMessage` wildcard `*`
- login success callbacks must use a strict `targetOrigin`

### Backend Requirements

- all business backends must use the unified token as the authentication entry
- token expiration, revocation, or invalidation must consistently result in unauthenticated responses
- business backends should provide a current-user endpoint for frontend initialization

### Configuration Requirements

- platform sites choose between `admin-sso` and local mode through runtime configuration
- third-party applications configure the `lite-sso` address, trusted origin, and callback strategy through runtime configuration

## Benefits

### Unified Authentication Center

- the whole system has only one authentication center
- the whole system has only one primary token model
- different application integration styles still share one source of truth

### Clear Engineering Boundaries

- `auth-server` handles authentication
- `admin-sso` handles the platform login facade
- `lite-sso` handles the lightweight third-party facade
- business systems handle their own business context

### Easier Evolution

- start with merged deployment and split later
- onboard one third-party application first and reuse the pattern for others
- adjust deployment topology without changing the authentication model

## Minimum Acceptance Criteria

- `auth-server` is the only token issuer
- platform applications integrate through `admin-sso`
- third-party applications integrate through `lite-sso`
- both platform backends and third-party backends directly consume the unified token from `auth-server`
- `freight-portal` is treated only as an example third-party consumer, not as part of the authentication-center core

## Suggested Next Reading

- [Unified Authentication Overview](overview.md)
- [lite-login Third-Party Integration](lite-login-integration.md)
- [PlatformWebRuntime](../runtime/platform-web-runtime.md)
