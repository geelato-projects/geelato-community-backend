---
title: API · Tenants
sidebar_label: Tenants
---
# Tenants (tenant)

1 controller, 8 endpoints in total. This domain is a design-time API.

## TenantController ⚙ — `/tenant`

Tenant query, creation, update, initialization, invitation and password reset.

### queryTenantList Tenant list

| Item | Content |
| --- | --- |
| URL | `GET /api/tenant/list` |
| Params | `code`: query, string, tenant code, optional.<br />`companyName`: query, string, company name, optional.<br />`corpId`: query, string, corporation ID, optional. |
| Returns | `ApiResult<List<Tenant>>`: `data` is the list of matching tenants. |

### queryTenantById Query by ID

| Item | Content |
| --- | --- |
| URL | `GET /api/tenant/{id}` |
| Params | `id`: path, string, tenant ID, required. |
| Returns | `ApiResult<Tenant>`: `data` is a single tenant; fails when the ID is blank or the tenant does not exist. |

### queryTenantByCode Query by code

| Item | Content |
| --- | --- |
| URL | `GET /api/tenant/code/{code}` |
| Params | `code`: path, string, tenant code, required. |
| Returns | `ApiResult<Tenant>`: `data` is a single tenant; fails when the code is blank or the tenant does not exist. |

### initializeTenant Initialize tenant

| Item | Content |
| --- | --- |
| URL | `GET /api/tenant/initialize/{code}` |
| Params | `code`: path, string, tenant code, required. |
| Returns | `ApiResult`: `data` is a Map: `tenant` the initialized tenant, `userName` initial admin login name, `password` initial password. Initialization creates the tenant site, orgs, users, roles, etc.; fails when the tenant does not exist or is deleted (`delStatus=1`). |

### updateTenant Update tenant

| Item | Content |
| --- | --- |
| URL | `PUT /api/tenant/{id}` |
| Params | `id`: path, string, tenant ID, required.<br />`tenant`: body, `Tenant` entity, the tenant info to update. |
| Returns | `ApiResult<Boolean>`: `data` is `true` on success; fails when the tenant does not exist. Updater info comes from the current logged-in user. |

### resetPassword Reset password

| Item | Content |
| --- | --- |
| URL | `GET /api/tenant/reset/password/{id}` |
| Params | `id`: path, string, tenant ID, required. |
| Returns | `ApiResult<Map<String,String>>`: `data` is a Map: `userName` admin login name, `password` newly generated plain password. Resets the password of the tenant's admin user (creates one when missing). |

### createTenant Create tenant

| Item | Content |
| --- | --- |
| URL | `POST /api/tenant/create` |
| Params | `tenant`: body, `Tenant` entity; `companyName` (company name) and `code` (tenant code) are required. |
| Returns | `ApiResult`: `data` is the created tenant; fails when the code already exists. Creator info comes from the current logged-in user. |

### inviteTenant Invite tenant

| Item | Content |
| --- | --- |
| URL | `POST /api/tenant/invite` |
| Params | `email`: query, string, email address, required (`@RequestParam`, the param is in the URL query). |
| Returns | `ApiResult`: `data` is the created tenant. Only creates a placeholder tenant holding the email (`companyName` is temporarily "to be completed"; the tenant code is generated from the email prefix plus a random number, with an extra random suffix on conflict). |
