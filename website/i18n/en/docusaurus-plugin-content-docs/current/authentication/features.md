---
id: features
title: System Features
sidebar_label: System Features
---

# System Features

> **Note: this chapter introduces the feature menus that ship with the Auth Center admin console, following the actual structure of the admin sidebar and explaining what each feature is for.**
>
> Integrators can use this chapter to understand what capabilities the Auth Center offers out of the box and the problems each one solves; administrators can use it as a menu-by-menu orientation. For how these features are consumed via the Open API, refer to the integration chapters.

## Feature Overview

The Auth Center admin console has six menu groups:

| Menu Group | Features | Purpose in One Sentence |
| --- | --- | --- |
| **Client Management** | Clients | Register and maintain OAuth2 client applications (client_id / secret / redirect URIs) |
| **Identity Management** | Organizations, Users, Roles | Maintain the org structure, accounts and roles — the source of identity and authorization data |
| **Permission Management** | Menus, Permissions | Register downstream menus and the five types of permission points for role binding and distribution |
| **Authentication** | Social Login, Token Query | Configure third-party login channels; inspect token details during integration |
| **Business Audit** | Audit Records, Session Control | Search audit events with tamper-evident verification; manage online sessions and force logout |
| **System Management** | Dictionaries, App Config | Routine maintenance of system-level dictionaries and runtime parameters |

## Client Management

Manages the **clients (applications)** registered with the Auth Center — a prerequisite for OAuth2 integration:

- Register a new client and get a `client_id` / `client_secret` pair
- Configure redirect URIs, grant types, token validity and other integration parameters
- Reset the client secret when needed

Any business system adopting [OAuth2 Protocol Integration](oauth2-integration.md) (authorization code or machine-to-machine) must be registered here first; [Lite lightweight integration](lite-login-integration.md) relies on client configuration as well.

## Identity Management

### Organizations

Maintains the **org structure** of the Auth Center: a company / department hierarchy, with users assigned to departments. Org data forms the backbone of user master data and underpins department-level authorization (departments bound to roles). To sync org data into business systems, see [Data Integration](data-integration.md).

### Users

Manages the **full lifecycle of user accounts**: create / edit accounts, enable or disable them, reset passwords, and assign each user a department and roles. Which permissions a user ends up with after login is determined by the roles and departments assigned here.

### Roles

Maintains **roles** and their authorization bindings: a role can bind users, menus and permission points at the same time. Roles are the bridge between "people" and "permissions" — permission distribution is computed from a user's effective permissions, which are aggregated from roles. For how permissions are distributed to business systems, see [Permission Integration](permission-integration.md).

## Permission Management

### Menus

Registers **menu trees for downstream applications**: menus come in three types — catalog, menu and button — and can carry a permission code. Menu data serves two purposes: it is the object of role authorization (which menus a role can bind), and it is distributed together with the user's effective permissions so business systems can render dynamic menus. Under the Management Integration mode, integrators can also read menu data via the Open API — see [Management Integration](open-api-management.md).

### Permissions

Registers and manages **permission points**. Permission points come in five types, corresponding one-to-one with the five groups of a user's effective permissions:

| Type | Meaning |
| --- | --- |
| `dp` | Data Permission |
| `mp` | Model Permission |
| `cp` | Column Permission |
| `ep` | Element Permission (element-level control such as page buttons) |
| `fp` | Function Permission (API / operation level) |

The set of valid permission types is enforced by a system whitelist; the dictionary only maintains their display metadata. Business systems consume the distributed result in a fail-closed manner via [Permission Integration](permission-integration.md).

## Authentication

### Social Login

Configures the application credentials and callback parameters of **third-party login channels** (WeChat, WeCom, Feishu, DingTalk) per tenant. Once configured, the unified login page and Lite login can present the corresponding third-party login options. See [Lite lightweight integration](lite-login-integration.md).

### Token Query

Paste an `access_token` to inspect its details: token storage mode (memory / redis), owning client, the logged-in user behind it, validity, and more. Primarily for integration debugging — for example, confirming whether a token was issued successfully and which application and user it belongs to.

## Business Audit

### Audit Records

Searches the **security audit events** of the Auth Center: filter by category, result (success / failure / denied / error), actor and keyword, and inspect event details. Audit events are recorded as a hash chain with an **integrity verification** entry point, so any tampering with records can be detected — meeting security audit requirements.

### Session Control

Views and manages **online sessions**: list all current sessions, or query all sessions of a user by login name; kick out **all sessions** of a login name, or **kick out a specific device** by device type. Used for emergency logout and session governance when an account is compromised.

## System Management

### Dictionaries

Maintains the **dictionaries and dictionary items** of the Auth Center: three built-in dictionaries — permission type (`permission_type`), org type (`org_type`) and menu type (`menu_type`) — plus custom dictionaries for business extensions. Built-in dictionaries are protected (cannot be deleted, codes cannot be modified, controlled dictionaries reject new items); dropdowns across the admin console are driven by them.

### App Config

Maintains the **runtime parameters** of the Auth Center (key-value configuration in `app_config`). Changes take effect online via hot refresh — no restart required — making it easy to toggle runtime switches of the Auth Center modules.

## How Admin Features Map to the Integration Blocks

The admin console maintains the "source data"; business systems consume it through the integration blocks:

| Admin Feature | Data Produced | How Integrators Consume It |
| --- | --- | --- |
| Client Management | OAuth2 client credentials | [OAuth2 Protocol Integration](oauth2-integration.md), [Lite lightweight integration](lite-login-integration.md) |
| Orgs / Users / Roles | Identity master data | [Data Integration](data-integration.md) (Open API / SDK / SQL scripts) |
| Menus / Permission points + Role binding | User effective permissions | [Permission Integration](permission-integration.md) (five-type distribution) |
| Social Login | Third-party login channels | Third-party login entries of both login integration methods |
| Audit Records / Session Control | Auditing and session governance | Built-in console capabilities for operational security |
| Dictionaries / App Config | System-level configuration | [Management Integration](open-api-management.md) (Open API reads dictionaries, etc.) |

## Related Resources

- Service and blocks overview: [Independent Service — Unified Authentication Center](overview.md)
- Login integration: [Lite lightweight integration](lite-login-integration.md), [OAuth2 Protocol Integration](oauth2-integration.md)
- Data integration: [Data Integration](data-integration.md)
- Permission integration: [Permission Integration](permission-integration.md)
- Open API reference: [Management Integration](open-api-management.md)
