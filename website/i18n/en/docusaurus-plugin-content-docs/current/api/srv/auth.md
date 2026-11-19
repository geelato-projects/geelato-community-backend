---
title: API · Authentication
sidebar_label: Authentication
---
# Authentication (auth)

2 controllers with 18 endpoints in total. Controllers marked with ⚙ are design-time APIs.

## JWTAuthController — `/user`

Core of login and current-user self-service operations: account/anonymous login, identity and tenant switching, delegate-on-behalf sessions, current user info and menus, avatar and profile maintenance, password reset and recovery, account verification and binding.

### login Log in

| Item | Content |
| --- | --- |
| URL | `POST /api/user/login` |
| Params | `username`: body, string, required, login name (max 64).<br />`password`: body, string, required, login password (max 128).<br />`tenant`: body, string, optional, tenant code.<br />`suffix`: body, string, optional, login-name suffix (e.g. `@tenant`; when non-empty it takes precedence over `tenant` for tenant resolution).<br />`org`: body, string, optional, reserved field (not used by the current login logic). |
| Returns | `ApiResult<LoginResult>`: on success returns basic user info plus a `token`; the JWT payload fields are `id`, `loginName`, `passWord`, `orgId`, `tenantCode`. When the login name exists under multiple tenants, the call fails with a multi-tenant exception code and `data.tenants` carries the candidate tenant list for you to choose from. |

### loginAnonymous Anonymous login

| Item | Content |
| --- | --- |
| URL | `POST /api/user/login/anonymous` |
| Params | `username`: body, string, required, anonymous account login name.<br />`password`: body, string, required, must equal the platform-configured anonymous fixed password.<br />Other fields are the same as `login`. |
| Returns | `ApiResult<LoginResult>`: same as `login`; the JWT payload additionally carries an `anonymous` field. The platform anonymous-login option must be enabled, otherwise the call fails.<br />The anonymous channel is decided by the global context: the environment variable `GEELATO_ANONYMOUS_ENABLED` (true/false) takes precedence when set; otherwise it follows the security level (on when level < 10, off when level ≥ 10). See [Global Context](../../platform-capabilities/global-context). |

### switchIdentity Switch identity

| Item | Content |
| --- | --- |
| URL | `GET /api/user/switchIdentity` |
| Params | `org`: query, string, optional, target org ID.<br />`tenant`: query, string, optional, target tenant code. |
| Returns | `ApiResult<LoginResult>`: re-issues the JWT with the new `orgId`/`tenantCode` and returns the login result; if the given org or tenant is not among your options, it falls back to the user's default value. |

### delegators Delegatable principals

| Item | Content |
| --- | --- |
| URL | `GET /api/user/delegators` |
| Params | None |
| Returns | `ApiResult<List<UserDelegateMap>>`: list of principals you (as the delegate) may act on behalf of (records with `scope=mentor_assist`, enabled and valid, deduplicated by principal ID), for the top-right delegate switcher. Fails when you are not logged in. |

### delegateAs Act as delegate

| Item | Content |
| --- | --- |
| URL | `GET /api/user/delegateAs` |
| Params | `targetUserId`: query, string, target principal user ID. |
| Returns | `ApiResult<User>`: does not re-issue any token; it only writes a server-side "credential → delegation target" session mapping and returns the target principal's info. Checks: a valid delegation relationship must exist; nested delegation and delegating to yourself are forbidden. |

### exitDelegate Exit delegate mode

| Item | Content |
| --- | --- |
| URL | `GET /api/user/exitDelegate` |
| Params | None |
| Returns | `ApiResult<NullResult>`: `data` is empty; removes the delegation session for the current credential and clears the delegate-mode context cache, restoring your own identity. |

### getUserInfo Current user info

| Item | Content |
| --- | --- |
| URL | `GET\|POST /api/user/info` |
| Params | None |
| Returns | `ApiResult<LoginResult>`: full current-user info, including `token`, org list `orgs`, tenant list `tenants`, role info `roles`/`roleCodes`/`roleIds`, and `delegateUserId`/`delegateUserName` when in delegate mode. |

### logout Log out

| Item | Content |
| --- | --- |
| URL | `POST /api/user/logout` |
| Params | None |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### uploadAvatar Upload avatar

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/user/avatar/{userId}` |
| Params | `userId`: path, string, target user ID.<br />`file`: form, multipart file, avatar file (parameter name `file`). |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### updateUserInfo Update profile

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/user/update/{userId}` |
| Params | `userId`: path, string, target user ID.<br />`name`: body, string, optional, name (max 64).<br />`enName`: body, string, optional, English name (max 64).<br />`mobilePrefix`: body, string, optional, mobile prefix (max 16).<br />`mobilePhone`: body, string, optional, mobile number (max 16).<br />`telephone`: body, string, optional, telephone (max 64).<br />`email`: body, string, optional, email (max 128).<br />`nationCode`, `provinceCode`, `cityCode`: body, string, optional, nation/province/city codes (max 32).<br />`address`: body, string, optional, address (max 255).<br />`description`: body, string, optional, description (max 255). |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### getCurrentUserMenu Current user menus

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/user/menu` |
| Params | `flag`: body, string, optional, filter flag passed through to the menu query.<br />`appId`: body, string, optional, application ID.<br />`tenantCode`: body, string, optional, tenant code; when non-empty it must match your current tenant, otherwise validation fails. |
| Returns | `ApiResult<List<Map<String,Object>>>`: menu nodes authorized for the current user (queried by app/tenant). Returns an empty list when `appId` or the tenant code is missing. |

### resetPassword Reset current user password

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/user/resetPassword` |
| Params | `passwordLength`: query, int, optional, length of the new password; default 8, max 32. |
| Returns | `ApiResult<String>`: `data` is the newly generated random plaintext password. |

### forgetValid Locate account for recovery

| Item | Content |
| --- | --- |
| URL | `POST /api/user/forgetValid` |
| Params | `validType`: body, string, required, verification type (1 mobile, 2 email, 3 password).<br />`validBox`: body, string, required, mobile number or email.<br />`prefix`: body, string, required when `validType` is 1, mobile prefix. |
| Returns | `ApiResult<User>`: `data` contains only the `id` of the matched user (for the subsequent recovery steps); throws a business exception when no unique user matches. |

### forgetPassword Reset forgotten password

| Item | Content |
| --- | --- |
| URL | `POST /api/user/forget` |
| Params | `action`: body, string, required, business action identifier (used for auth-code validation).<br />`validType`: body, string, required, verification type (1 mobile, 2 email).<br />`validBox`: body, string, required, mobile number or email.<br />`authCode`: body, string, required, auth code.<br />`userId`: body, string, required, user ID.<br />`password`: body, string, required, new password.<br />`prefix`: body, string, optional, mobile prefix. |
| Returns | `ApiResult<NullResult>`: `data` is empty; resets the user's password after the auth code is validated. |

### validateUser Verify user

| Item | Content |
| --- | --- |
| URL | `POST /api/user/validate` |
| Params | `validType`: body, string, required, verification type (1 mobile, 2 email, 3 password).<br />`userId`: body, string, required, user ID.<br />`authCode`: body, string, required, auth code; used as the login password when `validType` is 3.<br />`prefix`: body, string, optional, mobile prefix. |
| Returns | `ApiResult<NullResult>`: `data` is empty; validates the auth code or password according to the verification type, and throws a business exception on failure. |

### bindAccount Bind account

| Item | Content |
| --- | --- |
| URL | `POST /api/user/bindAccount` |
| Params | `validType`: body, string, required, binding type (1 mobile, 2 email, 3 password).<br />`userId`: body, string, required, user ID.<br />`authCode`: body, string, required, auth code.<br />`validBox`: body, string, required, mobile number/email/new password.<br />`prefix`: body, string, optional, mobile prefix. |
| Returns | `ApiResult<NullResult>`: `data` is empty; when `validType` is 3 the `validBox` is encrypted and saved as the new login password; when it is 1/2 the mobile number or email is bound after the auth code is validated. |

## OAuth2Controller — `/oauth2`

OAuth2 integration login: exchanges an authorization code for tokens at the OAuth server, fetches user info to complete the login on this platform, and refreshes access tokens.

### login OAuth2 login

| Item | Content |
| --- | --- |
| URL | `POST /api/oauth2/login` |
| Params | `code`: query, string, OAuth2 authorization code. |
| Returns | `ApiResult<LoginResult>`: exchanges the code for tokens using the platform-configured OAuth server URL and client credentials, fetches user info, stores the access/refresh token mapping, and returns the login result (`token` is the OAuth access token). Fails when token exchange or user-info fetching fails. |

### refreshToken Refresh access token

| Item | Content |
| --- | --- |
| URL | `POST /api/oauth2/refreshToken` |
| Params | `oldToken`: query, string, old access token. |
| Returns | `ApiResult<HashMap<String,String>>`: `data` contains `access_token` and `refresh_token`; looks up the refresh token by the old token, exchanges it at the OAuth server for new tokens, and updates the token mapping on success. Fails when no refresh token is found or the exchange fails. |
