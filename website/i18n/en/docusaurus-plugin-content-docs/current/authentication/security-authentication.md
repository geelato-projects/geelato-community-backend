---
title: Authentication and Authorization
sidebar_label: Authentication and Authorization
---
# Authentication and Authorization

This page focuses on the runtime entry that actually performs backend authentication inside the platform:

- `DefaultSecurityInterceptor`

It is not an architecture page for the unified authentication center. It explains how one request is authenticated inside the platform runtime, how the security context is established, and how the request is finally allowed through.

The interceptor itself knows nothing about concrete authentication schemes: all schemes (`JWTBearer`, `Anonymous`, extended keys, `SystemToken`, `Bearer`, `WeixinCode`) are plugged in through the `TokenAuthenticator` SPI. The interceptor routes by the `Authorization` prefix to the matching authenticator and uniformly owns session establishment after authentication. How to add a new scheme is covered in [Authenticator Extension (SPI)](authenticator-spi.md).

## What It Solves

`DefaultSecurityInterceptor` does not issue tokens. Its job is to consume the credential already attached to the request and build the current request security state, including:

- current user
- current tenant
- current password or authentication credential context
- Shiro `Subject`
- online user activity state
- request-level traffic tagging context

In other words:

- the unified authentication chapter explains where the token comes from
- this chapter explains how the backend authenticates after it receives the token

## Where It Sits

The main runtime authentication interceptor is located in:

- `geelato-web-common`
- `cn.geelato.web.common.interceptor.DefaultSecurityInterceptor`

It implements Spring MVC:

- `HandlerInterceptor`

So it runs before controller execution, rather than trusting the subject directly from the Servlet Filter layer.

## Main Flow

When a request enters the platform, the execution order of `DefaultSecurityInterceptor` can be summarized as:

1. apply traffic tagging
2. check whether the endpoint skips authentication
3. read `Authorization`
4. route by the `Authorization` prefix through the registry to the matching authenticator (`TokenAuthenticator`)
5. run the authenticator's scheme gate (`preAuthCheck`, e.g. the `@AllowSystemAccess` check of `SystemToken`)
6. try restoring the user context from local cache; on a miss, run the authentication
7. uniformly establish `SecurityContext` and the Shiro session after success
8. apply delegation and update online user activity
9. clean up request-level context at request completion

## Step 1: Apply Traffic Tagging First

At the beginning of `preHandle()`, the interceptor executes:

- `applyTrafficTag(request, response)`

This happens before the `@IgnoreVerify` check, so even endpoints that do not require authentication can still receive:

- `trafficTag`
- MDC logging context
- request-level traffic marker

The design rule here is:

- traffic-tagging failure only degrades gracefully and must not block the authentication flow

## Step 2: Check Whether Authentication Is Skipped

If the current handler is not a `HandlerMethod`, or the method is annotated with:

- `@IgnoreVerify`

the interceptor returns:

- `return true`

Such endpoints do not enter authentication logic, but they still keep the traffic-tagging work already done in step 1.

## Step 3: Read Authorization

For authenticated endpoints, the interceptor reads:

- `Authorization`

from the request header.

If the header is missing, it throws:

- `UnauthorizedException`

This means the platform runtime treats `Authorization` as the unified authentication entry, rather than building the subject from business parameters or a generic filter.

## Step 4: Scheme Gate and Cache Restore

With `Authorization` in hand, the interceptor first routes by prefix:

- `TokenAuthenticatorRegistry.resolve(token)` finds the single matching authenticator
- a header prefix unknown to the registry is rejected with `UnauthorizedException` — no fallback

After routing, the authenticator's scheme gate runs first:

- `authenticator.preAuthCheck(...)`

The gate **must run before cache restoration**: a cache hit short-circuits authentication, so a gate placed inside authentication would be bypassed by cached credentials. The typical example is the `@AllowSystemAccess` annotation check of the `SystemToken` scheme.

Only after the gate passes does the interceptor attempt cache restore:

- `tryRestoreFromCache(token, request, response)`

The main cache here is:

- `tokenContextCache`

It stores:

- user
- tenant
- current password
- authentication token object

If the cache hits, it restores:

- `SecurityContext.setCurrentUser(...)`
- `SecurityContext.setCurrentTenant(...)`
- `SecurityContext.setCurrentPassword(...)`
- `Subject.login(...)`

and then continues with:

- post-auth traffic tagging
- online-user touch update

The purpose is to avoid repeated token parsing and remote validation for the same token within a short time window. If the cached credential has become invalid (e.g. a token issued with an old password no longer passes the Shiro check), the cache entry is removed and the request falls back to the full authentication chain.

## Step 5: Run the Matched Authenticator

When cache restore misses, the interceptor calls the routed authenticator to perform "credential check + user resolution":

- `authenticator.authenticate(...)`

The authenticator returns a unified `TokenAuthentication` (user + credential + session-establishment descriptor); the remaining steps are handled uniformly by the interceptor. The platform ships with these schemes:

| Prefix | Authenticator | Description |
|---|---|---|
| `JWTBearer <token>` | `JwtTokenAuthenticator` | platform-issued JWT, verified locally |
| `Anonymous <token>` | `JwtTokenAuthenticator` | anonymous JWT with an extra anonymous-passphrase claim check |
| `WeixinUnionId <key>` | `WeixinTokenAuthenticator` | WeChat UnionId extended key |
| `WeixinWorkUserId <key>` | `WeixinTokenAuthenticator` | WeCom userId extended key |
| `SystemToken <key>` | `SystemTokenAuthenticator` | external-system fixed token, only for `@AllowSystemAccess` endpoints |
| `Bearer <token>` | `OAuth2TokenAuthenticator` | unified authentication center OAuth2 token |
| `WeixinCode <code>` | `WeixinCodeTokenAuthenticator` | WeChat mini-program login credential (fallback channel) |

### 5.1 Anonymous Authentication

Prefix:

- `Authorization: Anonymous <token>`

Handled by `JwtTokenAuthenticator` (the same authenticator as `JWTBearer`, sharing verification and user loading):

- verify the anonymous JWT
- check that the `anonymous` claim matches the global anonymous passphrase
- read `loginName`, `orgId`, and `tenantCode`, then load the current user
- return the anonymous fixed passphrase as credential; the interceptor establishes the session

Typical scenarios:

- controlled anonymous access inside the platform
- lightweight authenticated-anonymous flows with a known subject identity

Whether the anonymous login channel is enabled is decided by the global context: the environment variable `GEELATO_ANONYMOUS_ENABLED` (true/false) takes precedence when set; otherwise it follows the security level (on when level < 10, off when level ≥ 10). See [Global Context](../platform-capabilities/global-context).

### 5.2 JWTBearer Authentication

Prefix:

- `Authorization: JWTBearer <token>`

Handled by `JwtTokenAuthenticator`:

- local verification (HMAC256 + payload de-obfuscation, same structure as tokens issued by the platform login endpoints, see `cn.geelato.web.common.security.JWTUtil`)
- read `loginName`, `passWord`, `orgId`, and `tenantCode`
- load the current user with organization, department, and business-unit information
- return the `loginName/passWord` credential descriptor; the interceptor establishes the session

This is the classic path that turns a local JWT into a runtime security context.

### 5.3 Extended-Key Authentication

The current implementation supports two extended-key types:

- `WeixinUnionId`
- `WeixinWorkUserId`

Their header prefixes are:

- `Authorization: WeixinUnionId <key>`
- `Authorization: WeixinWorkUserId <key>`

Handled by `WeixinTokenAuthenticator`:

- resolve the local user by extended key through `UserProvider.getUserByExtendKey(key, type)`
- load the platform current user
- return the credential descriptor by key type; the interceptor routes to the corresponding Shiro Realm

This path is suitable for:

- Enterprise WeChat
- WeChat UnionId
- other external identities mapped into the local user system

### 5.4 SystemToken Authentication

Prefix:

- `Authorization: SystemToken <key>`

Handled by `SystemTokenAuthenticator`, for endpoints called by external systems that cannot carry a user token (e.g. sending in-app notifications from the dyn module):

- scheme gate (`preAuthCheck`): only endpoints annotated with `@AllowSystemAccess` accept this scheme, otherwise 401
- constant-time comparison against the configured fixed secret
- on success the request runs as a virtual system principal (`userId=system`, `systemPrincipal=true`), tied to no platform user and excluded from online tracking

### 5.5 OAuth2 Bearer Authentication

Prefix:

- `Authorization: Bearer <token>`

This is the key integration path with the unified authentication center, handled by `OAuth2TokenAuthenticator`.

Its processing uses a two-layer cache:

1. the authenticator maintains `tokenUserCache` (token → authentication-center user info)
2. on a cache miss, it calls `OAuth2Helper.getUserInfo(...)` to fetch user info from the authentication center

After the user is resolved:

- the local current user is loaded with organization information
- the result is returned, and the interceptor establishes the session and writes the context cache

So this path effectively means:

- the backend consumes the Bearer token issued by the unified authentication center
- then projects that unified identity into the current platform runtime security context

### 5.6 WeixinCode Authentication

Prefix:

- `Authorization: WeixinCode <code>`

Handled by `WeixinCodeTokenAuthenticator` — the fallback authentication channel for WeChat mini-programs (`code` is the one-time login credential of `wx.login()`, valid for 5 minutes):

- the platform maintains a code → unionid cache (5 minutes, covering frontend retries that reuse the same code)
- on a cache miss, a host-implemented `WeixinCodeResolver` SPI calls WeChat `jscode2session` to exchange the code for a unionid
- the unionid then resolves the platform user through the `WeixinUnionId` extended-key chain; unbound or disabled accounts get 401 (prompting to bind the workspace account first)

This channel calls a WeChat API on every request (when the cache misses), so it is positioned as a fallback/compatibility channel — prefer `JWTBearer` tokens for regular traffic. When the host provides no `WeixinCodeResolver` implementation, the channel uniformly returns 401. See the worked example in [Authenticator Extension (SPI)](authenticator-spi.md).

## Step 6: What Happens After Authentication Succeeds

No matter which authenticator succeeds, the interceptor runs the same uniform session-establishment flow.

### Establish SecurityContext

It writes:

- `SecurityContext.setCurrentUser(...)`
- `SecurityContext.setCurrentTenant(...)`
- `SecurityContext.setCurrentPassword(...)`

This is the main source used by business code to read user, tenant, and credential context.

### Perform Shiro Login

The interceptor also calls:

- `SecurityUtils.getSubject().login(...)`

So the platform does not rely only on a custom thread-local context. It also integrates the authenticated result into the Shiro `Subject` model.

### Apply Post-Authentication Traffic Tagging

After the user is identified, it calls:

- `applyTrafficTagAfterAuthenticated(...)`

This allows traffic-tagging strategy to refine behavior using authenticated user information.

### Update Online State

Finally, it calls:

- `touchOnline(user, request)`

If `OnlineUserTracker` is configured, the user's latest activity and request information are written into the online-user tracking system.

## Step 7: What Happens on Authentication Failure

All failure exits are `UnauthorizedException` (HTTP 401), grouped by root cause into three kinds:

- no `Authorization` header
- a prefix unknown to the registry (unregistered scheme, or a channel switched off by the host)
- a failed scheme gate, credential check, or Shiro session establishment — these carry the root cause (e.g. "anonymous passphrase check failed", "user not found", "WeChat identity not bound to a platform account") so server logs pinpoint the failing step; the frontend semantics stay unchanged (a uniform 401 → re-authentication)

## Step 8: Cleanup at Request Completion

In `afterCompletion()`, the interceptor clears:

- the traffic-tagging MDC key
- `TrafficTagContext`

The key point is:

- authenticated subject cleanup is not completed inside this interceptor
- unified `SecurityContext` cleanup is handled by the outer `SecurityContextFilter`

This matches the framework boundary:

- the filter handles final cleanup
- the interceptor builds the subject after authentication succeeds

## Key Supporting Objects

### `OrgProvider`

Used to enrich:

- organization
- department
- business unit

This means the current user has not only a login identity but also organizational context.

### `UserProvider`

Used to resolve local users by external identity extension keys, mainly for:

- `WeixinUnionId`
- `WeixinWorkUserId`
- `WeixinCode` (the WeChat mini-program fallback channel: the unionid obtained from the code resolves the user through the same `WeixinUnionId` extended-key chain)

### `OnlineUserTracker`

Used to record online activity for:

- online-user display
- latest activity observation

### `TrafficTagResolver`

Used to apply traffic tagging both when the request arrives and after authentication succeeds.

## Design Conclusion

`DefaultSecurityInterceptor` currently represents a clear runtime authentication chain:

- apply traffic tagging first
- allow no-auth endpoints to pass early
- read the unified authentication header
- route by prefix through the registry to the single authenticator, run its scheme gate, try cache restore first, and authenticate only on a miss
- establish both `SecurityContext` and Shiro `Subject` uniformly after success
- apply delegation, update online state, and clean request-level context at the end

This design gathers credential consumption, runtime subject establishment, online-state update, and traffic tagging into one interceptor entry, while still keeping these boundaries intact:

- authentication schemes are plugged in entirely through the `TokenAuthenticator` SPI — adding a new scheme never requires changing the interceptor
- `SecurityContext` must only be written after authentication succeeds
- filters do not directly trust frontend-injected subject identity
- the unified authentication center and the runtime authentication chain remain separate concerns

How to add a new authentication scheme:

- [Authenticator Extension (SPI)](authenticator-spi.md)

## Suggested Reading

- [Authenticator Extension (SPI)](authenticator-spi.md)
- [Unified Authentication](overview.md)
- [Security Contract: Security Context](../security/context.md)
- [Provider Contracts](../security/provider.md)
