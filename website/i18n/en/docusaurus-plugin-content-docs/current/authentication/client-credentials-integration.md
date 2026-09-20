---
title: Machine-to-Machine Integration (client_credentials)
sidebar_label: Machine-to-Machine Integration
---

# Machine-to-Machine Integration (client_credentials)

If your scenario involves **no user participation** — for example server-to-server integration, scheduled jobs, background batch processing, or calling the Auth Center's **Open API** (`/api/open/v1/**`) — you do not need the OAuth2 redirect flow. You can directly exchange `client_id` + `client_secret` for a **machine token** (client_credentials grant).

## Use Cases

- Backend service-to-service integration without any user login interaction.
- Unattended programmatic calls such as scheduled jobs and batch processing.
- Integrators self-managing their own roles, users, organizations and permissions through the **Open API** (a machine token is the only valid credential for the Open API; user tokens are not accepted). See the [Open Management API](open-api-management.md) for the full endpoint reference.

## Prerequisites

Before integrating, apply for application credentials from the Auth Center administrator:

- **`client_id`**: the unique identifier of your application.
- **`client_secret`**: the application secret. It must be kept on the server side (environment variables, secret management services, etc.) and must **never** be placed in frontend code or committed to source control.

In addition, the client's **grant types must include `client_credentials`** (included by default for newly registered clients). If calls are rejected with a 403, contact the administrator to confirm the client is enabled and has this grant type enabled.

## Obtaining a Token

**Method**: `POST`
**URL**: `https://<auth-host>/oauth2/client_token`
**Content-Type**: `application/x-www-form-urlencoded`

**Request parameters (form data)**:
- `grant_type`: fixed value `client_credentials`
- `client_id`: your `client_id`
- `client_secret`: your `client_secret`

**Example**:

```bash
curl -X POST "https://<auth-host>/oauth2/client_token" \
  -d "grant_type=client_credentials" \
  -d "client_id=<your_client_id>" \
  -d "client_secret=<your_client_secret>"
```

**Success response example**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "client_token": "...",
  "token_type": "bearer",
  "expires_in": 7200
}
```

> **Note**: `client_credentials` must use the dedicated endpoint `/oauth2/client_token`. The standard `/oauth2/token` endpoint does **not** support this grant type.

## Calling APIs with the Token

Pass the Bearer token in the header of subsequent requests:

```bash
curl -H "Authorization: Bearer <access_token>" \
  "https://<auth-host>/api/open/v1/roles/page?pageNum=1&pageSize=10"
```

(The `?access_token=` query parameter is also accepted, but the header is recommended.)

## Error Codes

| HTTP Status | Meaning |
| --- | --- |
| `401` | Missing, invalid, or expired token. |
| `403` | The token is not a client_credentials machine token (user tokens cannot call the Open API), or the client is disabled / does not have the client_credentials grant enabled. |

## FAQ

- **Why can't I use `/oauth2/token` for client_credentials?**
  The Auth Center is built on sa-token 1.46+, where `client_credentials` is a built-in dedicated endpoint `/oauth2/client_token`; the standard `/oauth2/token` endpoint does not support this grant type.

- **What if the token expires?**
  Machine tokens do not provide a refresh_token. Simply call `/oauth2/client_token` again. It is recommended to cache the token and refresh it ahead of expiry based on `expires_in`, rather than fetching a new one for every request.

- **What if `client_secret` is leaked?**
  Contact the Auth Center administrator immediately to reset the secret. A machine token represents the calling application's identity; a leaked secret allows a third party to impersonate your application on the Open API.

- **Can a machine token act as a user identity?**
  No. A machine token's subject is the `client_id` (application identity) and it is only for machine-to-machine scenarios such as the Open API. To identify a specific logged-in user, use the [OAuth2 Authorization Code Flow](oauth2-integration.md) or [lite-login integration](lite-login-integration.md).
