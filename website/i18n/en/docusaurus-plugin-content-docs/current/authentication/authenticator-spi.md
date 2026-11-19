---
title: Authenticator Extension (SPI)
sidebar_label: Authenticator Extension
---
# Authenticator Extension (SPI)

This page explains how to extend the platform runtime authentication chain with a new authentication scheme (authenticator).

The runtime authentication entry point of the platform is `DefaultSecurityInterceptor`, which itself knows nothing about concrete authentication schemes. All schemes — `JWTBearer`, `Anonymous`, `WeixinUnionId`, `WeixinWorkUserId`, `SystemToken`, `Bearer` (OAuth2), `WeixinCode` — are plugged in through the same SPI interface `TokenAuthenticator`. Adding a new authentication method requires no changes to the interceptor: just implement the interface and register it as a Spring Bean.

## When to Use This Page

Read this page if:

- You want the platform to recognize a new `Authorization` header prefix (e.g. `DingtalkCode <code>`, `ApiKey <key>`)
- You want to integrate a new external identity source (DingTalk scan, third-party OAuth, device fingerprint) while reusing the platform user system and session chain
- You want an external system credential to be exchanged directly for a runtime user context

This page is not the right place if:

- You only want to change where user data is loaded from → see [Provider Contracts](../security/provider.md)
- You only want to adjust how organizations or roles are loaded → see [Provider Contracts](../security/provider.md)
- You need a brand-new Shiro credential type that no existing Realm can express → framework-level evolution, see [LoginCredential semantic boundary](#logincredential-semantic-boundary)

## Architecture and Responsibility Boundary

The responsibilities of the authentication chain are split in two halves. Make this split clear before extending:

```
Request (Authorization: <scheme> <credential>)
        │
        ▼
TokenAuthenticatorRegistry        Routes by prefix to the single authenticator
        │
        ▼
TokenAuthenticator (yours)        Does exactly two things: credential check + user resolution
        │                          — must not import any Shiro type
        ▼
DefaultSecurityInterceptor        Uniformly responsible for:
        │                          SecurityContext population
        │                          Shiro session establishment (routes to existing Realms by credential type)
        │                          Context cache / delegation / traffic tagging / online tracking
        ▼
Business request proceeds
```

This boundary is a hard constraint:

- Authenticator implementations **must not import any `org.apache.shiro` type**. Shiro's `Subject.login(...)` and all `AuthenticationToken` types are handled uniformly by the interceptor
- Authenticators should not perform context operations other than returning the result — the interceptor completes the remaining steps in a fixed order
- All schemes share the same "uniform session establishment" tail logic, so a new scheme inherits cache restoration, delegation, online tracking and other capabilities for free

## SPI Contract

The interface and data carriers live in the `cn.geelato.web.common.security.spi` package.

### TokenAuthenticator

| Method | Contract |
|---|---|
| `Collection<String> schemePrefixes()` | The `Authorization` header prefixes this authenticator owns, **with a trailing space** (e.g. `"DingtalkCode "`). Prefixes must be mutually exclusive across authenticators |
| `default boolean supports(String rawToken)` | Whether the header value starts with one of this authenticator's prefixes (default: prefix list check) |
| `default void preAuthCheck(TokenAuthenticationContext ctx)` | Method-level access gate, executed **before cache restoration** so cached credentials cannot bypass it. Default: no gate |
| `TokenAuthentication authenticate(ctx)` | Credential check + user resolution. Return the result on success; throw `UnauthorizedException` (with root cause) on failure — never swallow exceptions or return null |

### TokenAuthentication

The result carrier on success:

```java
public record TokenAuthentication(User user, String password, LoginCredential login, boolean trackOnline) {
    public TokenAuthentication(User user, String password, LoginCredential login) {
        this(user, password, login, true);   // trackOnline defaults to true
    }
}
```

| Field | Meaning |
|---|---|
| `user` | Session user (already cloned per token; used directly by the interceptor, no further cloning needed) |
| `password` | Credential written into `SecurityContext`; Shiro session establishment depends on it |
| `login` | Credential descriptor required for session establishment, see below |
| `trackOnline` | Whether to count into online users. Virtual principals (e.g. system tokens) pass `false` |

### LoginCredential

```java
public record LoginCredential(String type, String principal, String credential)
```

The interceptor routes the authentication result to an existing Shiro Realm by `type`:

| Constant | Realm | Check semantics |
|---|---|---|
| `TYPE_USERNAME_PASSWORD` | DbRealm / AnonymousRealm | Login name + password (salted hash or fixed anonymous password) |
| `TYPE_WEIXIN_UNION_ID` | WeixinUnionIdRealm | Reverse lookup on `User.weixinUnionId` column |
| `TYPE_WEIXIN_WORK_USER_ID` | WeixinWorkUserIdRealm | Reverse lookup on WeCom userId |
| `TYPE_OAUTH2` | OAuth2Realm | Reverse lookup by accessToken |
| `TYPE_SYSTEM_TOKEN` | SystemTokenRealm | Constant-time fixed secret comparison |

`principal` / `credential` values vary by type (login name and password for the password scheme, the extend key itself for extend-key schemes) — refer to the existing authenticator implementations.

### TokenAuthenticationContext

Input of a single authentication:

| Field | Meaning |
|---|---|
| `rawToken` | Raw `Authorization` header value (with prefix) |
| `request` | Current request |
| `handlerMethod` | Target handler method (for gates reading annotations; guaranteed non-null by the interceptor) |
| `stripped(prefix)` | Convenience: credential body with the scheme prefix removed |

## Key Semantics

The following semantics are enforced by the framework and must be respected:

1. **Mutually exclusive prefixes, fail fast at startup**. If two authenticators register the same prefix, application startup throws `IllegalStateException`. Confirm no conflict with existing prefixes (`JWTBearer `, `Anonymous `, `WeixinUnionId `, `WeixinWorkUserId `, `SystemToken `, `Bearer `, `WeixinCode `).
2. **Unrecognized prefixes are rejected with 401**. Requests carrying an unregistered prefix throw `UnauthorizedException` with no fallback. Only registered prefixes enter authentication.
3. **Failures must throw with root cause**. Do not `catch (Exception) { return false; }` and do not return null. The legacy implementation swallowed exceptions, making troubleshooting hard; the current contract is to throw `UnauthorizedException("...: " + cause)`. The HTTP outcome is still 401, but logs pinpoint the failing step.
4. **`preAuthCheck` runs before cache restoration**. A cache hit short-circuits authentication, so a method-level gate placed inside `authenticate` would be bypassed by cached credentials — that is exactly why the `@AllowSystemAccess` gate of `SystemToken` lives in `preAuthCheck`. If your scheme has similar per-method restrictions, the gate must be overridden in `preAuthCheck`.

## Shortest Integration Path

### Step 1: Pick a prefix

Pick a prefix that does not conflict with existing schemes. The trailing space is mandatory (matching is `startsWith`; the space separates scheme from credential).

### Step 2: Implement the authenticator and register it as a Spring Bean

```java
package com.example.auth;

import cn.geelato.core.GlobalContext;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.User;
import cn.geelato.security.UserProvider;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.auth.AbstractTokenAuthenticator;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import cn.geelato.web.common.security.spi.TokenAuthenticator;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class DingtalkCodeTokenAuthenticator extends AbstractTokenAuthenticator {

    public static final String PREFIX = "DingtalkCode ";

    private final DingtalkClient dingtalkClient;
    private final UserProvider userProvider;

    public DingtalkCodeTokenAuthenticator(OrgProvider orgProvider,
                                          UserProvider userProvider,
                                          DingtalkClient dingtalkClient) {
        super(orgProvider);
        this.userProvider = userProvider;
        this.dingtalkClient = dingtalkClient;
    }

    @Override
    public Collection<String> schemePrefixes() {
        return List.of(PREFIX);
    }

    @Override
    public TokenAuthentication authenticate(TokenAuthenticationContext ctx) {
        String code = ctx.stripped(PREFIX);
        try {
            // 1. Credential check: call the external system to exchange for an identity
            String unionId = dingtalkClient.exchange(code);
            // 2. User resolution: map to a platform user
            User user = userProvider.getUserByExtendKey(unionId, "weixinUnionId");
            if (user == null) {
                throw new UnauthorizedException("DingTalk identity not bound to any platform account");
            }
            // 3. Load the full session user (with org info), return the result — the rest is up to the interceptor
            User currentUser = loadUser(user.getLoginName(), user.getTenantCode());
            return new TokenAuthentication(currentUser, GlobalContext.getAnonymousPwd(),
                    new LoginCredential(LoginCredential.TYPE_USERNAME_PASSWORD,
                            currentUser.getLoginName(), GlobalContext.getAnonymousPwd()));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("DingTalk credential check failed: " + e.getMessage());
        }
    }
}
```

Three key points:

- Extend `cn.geelato.web.common.security.auth.AbstractTokenAuthenticator` to reuse `loadUser(loginName, tenantCode)` (load user + org info + the clone protecting the shared snapshot), `setupOrgInfo(user, orgId)` and `copyOf(user)`
- A plain `@Component` is enough. `BootApplication` scans the whole `cn.geelato` package and host projects usually scan their own packages; `TokenAuthenticatorRegistry` collects all `TokenAuthenticator` beans automatically
- `OrgProvider` and `UserProvider` required by the constructor are existing container beans — inject them directly

### Step 3: (Optional) Override preAuthCheck

Only needed when your scheme restricts which endpoints may be called with this credential — see how `SystemTokenAuthenticator` handles `@AllowSystemAccess`.

### Step 4: Verify with a real request

Do not stop at a successful Spring startup. Call a protected endpoint with the new prefix and confirm business data is returned and `SecurityContext` holds the current user; then call once with a wrong credential and once with an unbound identity, confirming 401 with the root cause in logs.

## LoginCredential Semantic Boundary

The SPI extension covers "**credential check + user resolution**": a new credential source mapped onto one of the existing Realm semantics.

- The existing 5 `type` values (table above) cover most scenarios: password-based, WeChat-based, OAuth2-based, system-token-based
- If an external identity system fits none of them (e.g. challenge-response credentials), a new Realm must be added at framework level and the routing switch of `DefaultSecurityInterceptor#createShiroToken` extended — that is framework-level evolution with the same cost as before, not part of the host-side SPI extension

## Worked Example: the WeixinCode Mini-Program Fallback Channel

A real two-layer extension case: **the platform provides a generic authenticator, the host implements only a smaller SPI**.

In WeChat mini-program scenarios, business requests may carry `Authorization: WeixinCode <js_code>` (the one-time credential of `wx.login()`) for authentication. Since each host integrates its own mini-program appid/secret, the platform-side `WeixinCodeTokenAuthenticator` only handles generic orchestration:

- code → unionid result cache (5 minutes, aligned with the code validity, covering frontend retries that reuse the same code and avoiding WeChat error 40163)
- unionid → platform user: reuses the existing `UserProvider.getUserByExtendKey(unionid, "weixinUnionId")` chain
- Session establishment: `LoginCredential(TYPE_WEIXIN_UNION_ID, ...)` → `WeixinUnionIdRealm`

How the code is exchanged for a unionid (calling WeChat `jscode2session`, which requires appid/secret configuration) is implemented by the host through a smaller SPI exposed by the platform:

```java
package cn.geelato.web.common.security.spi;

public interface WeixinCodeResolver {
    /** js_code → unionid. Throw UnauthorizedException (with root cause) on failure. */
    String resolveUnionId(String jsCode);
}
```

Host-side implementation (excerpt from customer-client):

```java
@Component
@ConditionalOnProperty(prefix = "customer-client.wx.miniapp", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class WxMiniAppCodeResolver implements WeixinCodeResolver {
    // GET https://api.weixin.qq.com/sns/jscode2session?appid=..&secret=..&js_code=..
    // errcode 40029/40163/45011 mapped to UnauthorizedException with readable messages
    // missing unionid (mini-program not bound to WeChat Open Platform) → UnauthorizedException
}
```

`WeixinCodeTokenAuthenticator` wires the resolver via `ObjectProvider`: when the host provides no implementation, the `WeixinCode` channel uniformly returns 401 (channel disabled) and all other schemes are unaffected. Combined with a `@ConditionalOnProperty` switch, the channel can be turned off in one place.

The pattern behind this example is reusable: **when the orchestration logic of a scheme family is generic but the credential exchange depends on host-specific external configuration, put a generic authenticator plus a host-implemented exchange SPI on the platform side**.

## Notes and Troubleshooting

- **Three root causes of 401**: missing `Authorization` header / unregistered prefix (including a channel switched off) / gate or credential check failure. The first two show up in interceptor and registry warn logs; the third carries the root cause in the authenticator's `UnauthorizedException` message
- **Authenticated but still 401**: check that `authenticate` returned a proper `user` (with `loginName`/`tenantCode`); `loadUser` throws "user not found" when the user cannot be loaded
- **Caching and validity**: after a successful authentication the interceptor writes `tokenContextCache` (30 minutes); subsequent requests with the same credential are restored from cache. Scheme-specific external credential caches (like WeixinCode's code→unionid) are maintained by each authenticator
- **Log hygiene**: never print credential raw values (tokens, codes, secrets) inside authenticators — record only scheme and prefix, as `OAuth2TokenAuthenticator` does

## Further Reading

- [Authentication and Authorization](security-authentication.md) — full runtime authentication chain
- [Provider Contracts](../security/provider.md) — replacing user / organization / role sources
- [Security Context](../security/context.md) — `SecurityContext` lifecycle and usage constraints
