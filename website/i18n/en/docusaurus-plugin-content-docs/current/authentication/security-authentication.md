# Authentication and Authorization

This page focuses on the runtime entry that actually performs backend authentication inside the platform:

- `DefaultSecurityInterceptor`

It is not an architecture page for the unified authentication center. It explains how one request is authenticated inside the platform runtime, how the security context is established, and how the request is finally allowed through.

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
4. try restoring user context from local cache
5. try multiple authentication strategies in order
6. establish `SecurityContext` after authentication succeeds
7. update online user activity
8. clean up request-level context at request completion

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

the interceptor simply returns:

- `return true`

Such endpoints do not enter authentication logic, but they still keep the traffic-tagging work already done in step 1.

## Step 3: Read Authorization

For authenticated endpoints, the interceptor reads:

- `Authorization`

from the request header.

If the header is missing, it throws:

- `UnauthorizedException`

This means the platform runtime treats `Authorization` as the unified authentication entry, rather than building the subject from business parameters or a generic filter.

## Step 4: Try Cache Restore First

Before entering the concrete authentication branches, the interceptor calls:

- `tryRestoreFromCache(token, request, response)`

The main cache here is:

- `tokenContextCache`

It stores:

- user
- tenant
- current password
- authentication token object
- expiration timestamp

If the cache hits, it restores:

- `SecurityContext.setCurrentUser(...)`
- `SecurityContext.setCurrentTenant(...)`
- `SecurityContext.setCurrentPassword(...)`
- `Subject.login(...)`

and then continues with:

- post-auth traffic tagging
- online-user touch update

The purpose is to avoid repeated token parsing and remote validation for the same token within a short time window.

## Step 5: Try Multiple Authentication Strategies in Order

If cache restore does not succeed, the interceptor tries the following authentication branches in a fixed order.

### 5.1 Anonymous Authentication

Prefix:

- `Authorization: Anonymous <token>`

Processing:

- verify the anonymous JWT
- read `loginName`, `orgId`, and `tenantCode`
- initialize the current user
- establish anonymous password context
- build `UsernamePasswordToken`
- perform Shiro login

Typical scenarios:

- controlled anonymous access inside the platform
- lightweight authenticated-anonymous flows with a known subject identity

### 5.2 JWTBearer Authentication

Prefix:

- `Authorization: JWTBearer <token>`

Processing:

- verify the JWT
- read `loginName`, `passWord`, `orgId`, and `tenantCode`
- initialize the current user
- fill organization, department, and business-unit information
- write to `SecurityContext`
- build `UsernamePasswordToken`
- perform Shiro login

This is the classic path that turns a local JWT into a runtime security context.

### 5.3 Extended-Key Authentication

The current implementation supports two extended-key types:

- `WeixinUnionId`
- `WeixinWorkUserId`

Their header prefixes are:

- `Authorization: WeixinUnionId <key>`
- `Authorization: WeixinWorkUserId <key>`

Processing:

- use `UserProvider` to find the local user by extended key
- initialize the platform current user
- write to `SecurityContext`
- build the corresponding Shiro `AuthenticationToken`
- perform Shiro login

This path is suitable for:

- Enterprise WeChat
- WeChat UnionId
- other external identities mapped into the local user system

### 5.4 OAuth2 Bearer Authentication

Prefix:

- `Authorization: Bearer <token>`

This is the key integration path with the unified authentication center.

Its processing uses two cache layers:

1. check whether `tokenContextCache` already has the full authenticated context
2. check whether `tokenUserCache` already has OAuth2 user information
3. if neither exists, call `OAuth2Helper.getUserInfo(...)` to fetch user info from the authentication center

After the user is resolved, the interceptor:

- initializes the local current user
- sets the current tenant
- builds `OAuth2Token`
- performs Shiro login
- writes the context cache

So this path effectively means:

- the backend consumes the Bearer token issued by the unified authentication center
- then projects that unified identity into the current platform runtime security context

## Step 6: What Happens After Authentication Succeeds

Regardless of which branch succeeds, the interceptor performs several post-auth actions.

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

If all authentication branches fail, the interceptor throws:

- `UnauthorizedException("未授权访问")`

So in runtime semantics, there are only two failure exits:

- no `Authorization` header
- an `Authorization` header exists, but none of the supported authentication strategies can resolve it

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
- try cache restore first
- then authenticate in order through anonymous, JWT, local extension-key, and OAuth2 Bearer flows
- establish both `SecurityContext` and Shiro `Subject` after success
- update online state and clean request-level context at the end

This design gathers credential consumption, runtime subject establishment, online-state update, and traffic tagging into one interceptor entry, while still keeping these boundaries intact:

- `SecurityContext` must only be written after authentication succeeds
- filters do not directly trust frontend-injected subject identity
- the unified authentication center and the runtime authentication chain remain separate concerns

## Suggested Reading

- [Unified Authentication](overview.md)
- [Platform Capabilities: SecurityContext Lifecycle](../runtime/security-context-lifecycle.md)
- [Security Provider Extension](../reference/security-provider-extension.md)
