---
id: lite-login-integration
title: lite-login Third-Party Integration
sidebar_label: lite-login Integration
---

# lite-login Third-Party Integration

This page explains how any third-party application can integrate with the `lite-login` lightweight login facade provided by the unified authentication center, focusing on two things:

- how the frontend obtains the token issued by `auth-server`
- how the third-party backend uses that token to call OAuth endpoints and confirm user identity

This guide does not assume that the third-party application already has fixed endpoints such as `/api/user/info`, `/session/me`, or `/auth/profile`. Those endpoints are application-specific.

Sample projects:

- Repository: [geelato-hello-example](https://github.com/geelato-projects/geelato-hello-example)
- lite-login integration sample: [sample-lite-login](https://github.com/geelato-projects/geelato-hello-example/tree/main/sample-lite-login)

## Integration Goal

The minimal acceptable result is:

- the third-party frontend can open or embed `lite-login`
- the frontend receives `LOGIN_SUCCESS` after login succeeds
- the frontend can extract and store the token
- the third-party backend can call `/oauth2/userinfo` with a Bearer token
- the third-party backend can build its own local identity mapping from the returned user

## Core Boundary

### Authentication Center

- `auth-server` is the only token issuer
- `lite-login` is the lightweight login facade
- `lite-login` handles login and returns the token
- `auth-server` confirms the identity behind that token through OAuth endpoints

### Third-Party Frontend

- opens or embeds `lite-login`
- listens to `postMessage`
- extracts `accessToken/token`
- stores the token temporarily
- passes the token to its own backend in later requests

### Third-Party Backend

- reads the Bearer token from the request header
- calls `/oauth2/userinfo`
- extracts user identity from the response
- establishes session state, account mapping, or permission context according to its own domain model

## Entry Constraint

Use the explicit lightweight login entry:

```text
https://<auth-host>/lite-login
```

Do not reuse:

```text
/login?display=embedded
```

Each third-party application should keep its own:

```text
/login
```

That page is only the third-party handoff page and should not implement username-password authentication itself.

## Login Methods

The `lite-login` page ships with the following login methods — business systems need no extra development:

- **Username-password login**: with graphic / slider / rotate captchas (as configured in the auth center).
- **SMS verification-code login**: mobile phone + code; the code channel is isolated from the forgot-password channel.
- **Third-party login**: the page automatically renders the third-party providers enabled for the current tenant (WeChat, WeCom, Feishu, DingTalk); clicking redirects to the corresponding platform for authorization; unbound users go through an "verify account-password then bind" flow.
- **Forgot / reset password**: built into the login page.
- **First-login forced password change**: users created through account provisioning (see [Data Integration](data-integration.md)) carry a forced-change flag in the login result; the frontend guides them through the change accordingly.

Underlying Lite authentication APIs (provided by the auth center's `geelato-auth-login`; you can also call them directly to build your own login UI):

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/auth/login` | Password or SMS login; body: `{username, password?, captchaToken?, scene?, loginType?, authCode?}`; `loginType=sms` switches to SMS verification |
| POST | `/api/auth/login/sms/send` | Send the login SMS code (unregistered numbers are rejected) |
| POST | `/api/auth/refresh` | Refresh the token; body: `{refreshToken}` |
| POST | `/api/auth/logout` | Log out the current session |
| GET | `/api/auth/captcha`, `/slider`, `/rotate`, etc. | Captcha image and verification endpoints |
| GET | `/api/oauth2/providers` | Third-party providers enabled for the current tenant |
| GET | `/api/oauth2/authorize/{provider}` | Start third-party authorization (302 to the platform) |
| POST | `/api/oauth2/callback` | Third-party callback (unbound users get `2001 needBind` plus a `bindToken` valid for 5 minutes) |
| POST | `/api/oauth2/bind` | Complete binding with `bindToken` + account-password, then log in |

> Login is risk-controlled: repeated failures trigger account locking (`423`) and rate limiting (`429`); surface these response codes as hints.

## Frontend Integration Flow

### Interaction Sequence Diagram

The complete interaction sequence for integrating a business system with lite-login is as follows:

```mermaid
sequenceDiagram
    participant User as User Browser
    participant Third as Business Frontend
    participant LiteSSO as lite-login (Facade)
    participant Auth as Auth Server (Backend)
    participant ThirdApi as Business Backend

    User->>Third: 1. Open business system
    Third->>LiteSSO: 2. Load login via iframe/popup
    User->>LiteSSO: 3. Enter credentials & submit
    LiteSSO->>Auth: 4. Authentication request
    Auth-->>LiteSSO: 5. Return access_token
    LiteSSO-->>Third: 6. postMessage LOGIN_SUCCESS(accessToken)
    Third->>ThirdApi: 7. Call own API with Bearer token
    ThirdApi->>Auth: 8. Request /oauth2/userinfo to validate token
    Auth-->>ThirdApi: 9. Return Unified Identity (User Info)
    ThirdApi-->>Third: 10. Return business system's user context
    Third-->>User: 11. Login complete, enter home page
```

### Standard Sequence Steps

1. The user visits the third-party application's own `/login`
2. The application loads `/lite-login?display=embedded&redirect=...`
3. `lite-login` sends `LOGIN_INIT`
4. The user completes login inside `lite-login`
5. `lite-login` sends `LOGIN_SUCCESS`
6. The frontend extracts the token
7. The frontend stores the token temporarily
8. The frontend includes the token when calling its own backend
9. The backend calls `/oauth2/userinfo`
10. The backend continues business processing with the resolved user identity

### Required Frontend Configuration

At minimum prepare:

- `liteSsoBaseUrl`
  - the base address of the lightweight login facade
  - for example: `http://app.localgl.cn`
- `liteSsoOrigin`
  - the allowed `postMessage` origin
  - for example: `http://app.localgl.cn`

### The Third-Party `/login` Page

Each application should provide its own `/login` page as the handoff page for unified authentication.

Recommended options:

- iframe embedded login
- popup or new-window fallback

The iframe URL should be built as:

```text
/lite-login?display=embedded&redirect=<business-target-url>
```

### `postMessage` Protocol

The frontend should at least handle:

- `LOGIN_INIT`
  - the login page is initialized
- `LOGIN_SUCCESS`
  - login succeeds
  - the token payload exists in `data`
- `LOGIN_CLOSE`
  - the user closes or cancels login
- `SET_LOCALE`
  - the parent page controls the display language of the embedded `lite-login`
  - mainly for host pages that manage multi-language state centrally

### `LOGIN_SUCCESS` Payload

The frontend should at least be compatible with this structure:

```json
{
  "type": "LOGIN_SUCCESS",
  "data": {
    "accessToken": "xxx",
    "token": "xxx",
    "refreshToken": null,
    "expireInSeconds": 7199,
    "tokenType": "Bearer",
    "issuer": "auth-server",
    "user": {
      "id": "123",
      "username": "zhangsan",
      "nickname": "Zhang San",
      "email": "zhangsan@example.com",
      "mustChangePassword": false,
      "mobilePrefix": "+86",
      "mobilePhone": "13800000000",
      "phone": "+8613800000000"
    }
  }
}
```

Use this priority when extracting the token:

1. `accessToken`
2. `token`

**First-login forced password change**: when `user.mustChangePassword === true`, the account was created by the account-provision flow and its initial password must be changed on first login. After receiving `LOGIN_SUCCESS`, guide the user to the change-password flow (the auth center ships a reset page at `/oauth2/lite-reset`, or build a custom one); the verification-code parameters can be taken directly from `mobilePrefix` / `mobilePhone` in the contract. Do not let the user into the business system before the change completes.

### Embedded Language Control

When `lite-login` is embedded in a third-party application via iframe with `display=embedded`, the language switcher is no longer exposed by the embedded page itself — the parent page controls it centrally.

This design has two purposes:

- the host page maintains its own language state without a second switch inside the iframe
- embedded login is usually just part of the host's login flow, so following the host's language feels natural

Current constraints:

- applies only to `/oauth2/lite-login?display=embedded`
- supported locale values are `zh-CN` and `en-US`
- invalid values fall back to the default locale via the system's existing logic

### How the Parent Page Switches the Embedded Page's Language

Depending on whether the parent page and the auth center share the same origin, use one of two approaches:

#### Approach 1: same-origin iframe direct call

If the parent page and `lite-login` are same-origin, call the global method exposed by the child page directly:

```ts
iframe.contentWindow?.setLiteLoginLocale?.('en-US')
```

This fits:

- host page and `lite-login` sharing the same domain
- or both being unified under one origin via a reverse proxy

#### Approach 2: cross-origin iframe via `postMessage`

If the parent page and `lite-login` have different origins, the parent cannot access `contentWindow.setLiteLoginLocale`; send a language-switch message via `postMessage` instead:

```ts
iframe.contentWindow?.postMessage(
  {
    type: 'SET_LOCALE',
    data: {locale: 'en-US'}
  },
  targetOrigin
)
```

Recommendations:

- write `targetOrigin` explicitly as the auth center domain — never `*`
- after the parent completes its own language switch, notify the embedded page to follow

### `SET_LOCALE` Message Contract

In cross-origin scenarios, the message the parent page sends to `lite-login` looks like:

```json
{
  "type": "SET_LOCALE",
  "data": {
    "locale": "en-US"
  }
}
```

Field notes:

- `type`
  - fixed value `SET_LOCALE`
- `data.locale`
  - target locale
  - recommended values: `zh-CN`, `en-US`

Security requirements:

- `lite-login` still validates the message origin
- the parent page must not rely on "any origin may send messages"
- for cross-origin deployments, make sure the auth center side has configured trusted origins

### Correct Action After Login Success

After receiving `LOGIN_SUCCESS`, the standard frontend actions are only:

1. extract and store the token
2. pass that token to the application's own backend in later requests

Do not hard-code a rule that login success must always be followed by a call to a fixed user-info endpoint, because different third-party applications may not expose the same endpoint shape.

### Frontend Storage Recommendation

At minimum store:

- `geelato_sso_token`
- `geelato_sso_user`
- `geelato_sso_expires`

Whether the application also stores additional local session data is application-specific.

### Request-Layer Handling

The application's shared request layer should automatically read the token and append:

```text
Authorization: Bearer <token>
```

This avoids repeating manual header wiring in every business request.

### Login-State Recovery

A lightweight startup recovery flow can be:

1. read `geelato_sso_token`
2. if the token exists and is not expired, continue using it
3. if the backend later confirms the token is invalid, clear the local token

## Backend Integration Flow

### Minimal Processing Chain

The third-party backend does not need to expose a fixed user-info confirmation endpoint of its own.

The minimal viable approach is:

1. any business endpoint reads `Authorization`
2. extract the Bearer token
3. call `/oauth2/userinfo`
4. extract the user from the response
5. build the application's own identity mapping
6. continue the current business request

### New `/oauth2/userinfo` Contract

The current `/oauth2/userinfo` response is no longer a direct `User`, but a session wrapper:

```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "accessToken": "...",
    "token": "...",
    "tokenType": "Bearer",
    "issuer": "auth-server",
    "loginId": "demo",
    "user": {
      "...": "..."
    }
  }
}
```

So the backend must:

1. parse `data`
2. then extract `data.user`

Do not parse the entire `data` block directly as a `User`.

### Recommended Compatibility Strategy

Recommended shared helper logic:

1. if `data.user` exists, parse `data.user`
2. otherwise fall back to the older `data -> User` structure

This keeps backward compatibility with historical versions.

### Backend Identity Mapping

After resolving the central-authentication user, the application decides its own follow-up logic, for example:

- use the central user directly as the current user
- map the system account from `loginId`
- bind accounts by email, phone number, or employee number
- create or update a local user mirror
- enrich the user with local tenant, role, or organization context

No unified `LoginResult` response shape is required here.

## Security Requirements

### `postMessage` Origin Validation

The frontend must validate:

```text
event.origin === liteSsoOrigin
```

Never trust arbitrary page origins by default.

### Do Not Trust Frontend `user` Alone

The `user` returned in `LOGIN_SUCCESS` is only auxiliary display information.

The final trusted identity must be confirmed by the backend through the authentication center.

### Token Expiration Handling

When the frontend or backend detects token expiration, it should:

1. clear the local token
2. clear local application session state
3. redirect back to the application's own `/login`

Do not jump directly to the authentication center page.

## Frontend Pseudocode

```ts
const handleSsoMessage = (event: MessageEvent) => {
  if (event.origin !== liteSsoOrigin) return
  if (event.data?.type !== 'LOGIN_SUCCESS') return

  const token = event.data?.data?.accessToken || event.data?.data?.token
  if (!token) return

  localStorage.setItem('geelato_sso_token', token)
  localStorage.setItem('geelato_sso_expires', String(Date.now() + 7200 * 1000))

  navigateTo('/dashboard')
}
```

If the application wants the backend to confirm identity immediately, it may call any of its own backend endpoints right after login success, but that is not a mandatory platform rule.

### Parent-Page Language Control Pseudocode

If the host page supports switching between Chinese and English, fold the iframe language sync into the same switch action:

```ts
const syncLiteLoginLocale = (iframe: HTMLIFrameElement, locale: 'zh-CN' | 'en-US') => {
  const childWindow = iframe.contentWindow
  if (!childWindow) return

  try {
    childWindow.setLiteLoginLocale?.(locale)
    return
  } catch (error) {
    // cross-origin falls into message mode
  }

  childWindow.postMessage(
    {
      type: 'SET_LOCALE',
      data: {locale}
    },
    liteSsoOrigin
  )
}
```

Recommended wiring:

1. the host page updates its own i18n state first
2. then calls `syncLiteLoginLocale(iframe, locale)`
3. if the iframe has not finished loading, re-send the current locale after its `load` event

## Backend Pseudocode

```java
public CurrentUser resolveCurrentUser(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    String accessToken = extractBearerToken(authorization);

    OAuth2ServerResult result = oauth2Service.getUserInfo(authServerUrl, accessToken);
    JSONObject session = JSON.parseObject(result.getData());
    User user = JSON.parseObject(JSON.toJSONString(session.get("user")), User.class);

    return CurrentUser.from(user);
}
```

If old-structure compatibility is required, keep the parsing logic inside a shared helper.

## Integration Checklist

### Frontend Checklist

- does the application have its own `/login`
- does it use `/lite-login` as the real authentication facade
- does it validate `event.origin`
- does it handle `LOGIN_INIT / LOGIN_SUCCESS / LOGIN_CLOSE`
- can it extract `accessToken/token` correctly
- can it attach `Authorization` automatically in later requests
- can it clear local state when the token expires
- does it handle the first-login forced password change when `user.mustChangePassword=true`

### Backend Checklist

- can it extract the Bearer token from headers correctly
- does it trust and verify the `auth-server` token
- does it parse the user through `data.user`
- is it compatible with the legacy `userinfo` structure
- has it implemented its own account-mapping logic

## FAQ

### What should I do when `mustChangePassword=true`

The user was created by the account-provision flow (approval platform) and must change the initial password on first login. Standard handling:

1. read `user.mustChangePassword` from `LOGIN_SUCCESS`
2. when it is `true`, redirect to the change-password page (the auth center ships `/oauth2/lite-reset`, or build a custom one) and complete the change using `mobilePrefix` / `mobilePhone` from the contract to send the verification code
3. only let the user into the business system after the change succeeds

For the full account-provision semantics, see [Data Integration](data-integration.md).

### Why does the app return to the login page right after successful login

Check these first:

- whether the request sent to the third-party backend actually includes the Bearer token
- whether `/oauth2/userinfo` is still parsed as the old structure
- whether the unauthenticated frontend fallback mistakenly treats `lite-login` as a normal business page

### Why is there no language switcher in embedded mode

This is expected behavior.

In `display=embedded` mode, language switching is controlled by the parent page; the embedded page no longer exposes its own switcher to avoid a language-state mismatch between host and embedded page.

### Why can't the parent call `contentWindow.setLiteLoginLocale` directly across origins

Because the browser's same-origin policy blocks the parent from accessing runtime objects of a cross-origin iframe.

In that case use:

```ts
iframe.contentWindow?.postMessage(
  {
    type: 'SET_LOCALE',
    data: {locale: 'en-US'}
  },
  liteSsoOrigin
)
```

### Why must `SET_LOCALE` also validate its origin

Because it is still a cross-window control message.

Without origin validation, any page could forge language-switch messages, making integration behavior uncontrollable. Even though such messages carry no sensitive credentials, they should satisfy the same security requirements as the login messages.

### Why can't we trust the token and user returned directly to the frontend

Because:

- `lite-login` only handles unified authentication
- the third-party application still decides how that user maps into its own system
- that decision must be made by the third-party backend

### Why keep the application's own `/login`

Because:

- the application needs its own return path
- the application needs its own wording and handoff page
- the authentication center only provides the authentication facade, not the entry experience of each application

## Recommended Landing Pattern

Use this pattern consistently:

- the authentication center provides a unified `lite-login`
- each third-party application keeps its own `/login`
- each third-party frontend follows embedded login, `postMessage`, parent-controlled embedded language, token storage, and automatic Bearer propagation
- each third-party backend follows Bearer extraction, `/oauth2/userinfo`, `data.user`, and application-specific identity mapping

With this pattern, onboarding a new third-party application only requires changing:

- the application's own `/login` UI
- the application's own user-mapping logic

The rest of the integration skeleton stays reusable.
