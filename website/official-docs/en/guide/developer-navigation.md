---
id: developer-navigation
title: Developer & AI Navigation Manual
sidebar_label: Dev & AI Navigation
---

# Developer & AI Navigation Manual

Welcome to the Geelato Framework! This document is designed for both **human developers** and **AI coding assistants (e.g., Trae, Copilot)**. It provides a clear capability matrix, a quick lookup map, and best practices for collaborating with AI, helping you quickly locate requirements and start coding.

## 🎯 Quick Start Decision Tree

Different scenarios have different entry points. Choose based on your needs:

| My Goal Is... | Recommended Path / Anchor |
| --- | --- |
| **I want to start from scratch and quickly generate a complete CRUD backend project** | Recommended to use the **App Scaffold**. See: [Scaffold Project Guide](app-scaffold-starter-project-guide) |
| **I have an existing Spring Boot project and want to integrate Geelato core capabilities** | Recommended **Minimal Integration**. See: [Minimal Integration](minimal-integration) |
| **I want to learn the architecture design and the relationship between Runtime and Designer** | Read the architecture docs. See: [PlatformWebRuntime](../runtime/platform-web-runtime) |
| **I want to quickly integrate Single Sign-On (SSO) and authentication** | See the authentication overview. See: [Authentication Overview](../authentication/overview) |

---

## 🛠️ Capability Matrix

When you need a specific function during development, use the table below to quickly jump to the corresponding guide.

### Data & Storage
| Intent | Core Components / Tech Stack | Documentation |
| --- | --- | --- |
| Write backend CRUD code | **MetaFactory**, **Fluent DSL** | [ORM: Fluent DSL](../orm/fluent-dsl) |
| Configure table names and field mappings on Entity classes | **@Title**, **@Col**, **@Model** | [ORM: Annotations](../orm/annotations) |
| Perform complex queries via JSON from frontend or gateway | **MQL (Meta Query Language)** | [MQL: Syntax & Usage](../mql/usage) |
| Configure or connect multiple databases | **Dynamic Datasource** | [Dynamic Datasource](../dynamic-datasource/overview) |

### Business & Extensions
| Intent | Core Components / Tech Stack | Documentation |
| --- | --- | --- |
| Get current login user, tenant, or request context | **Global Context**, **SecurityContext** | [Capabilities: Global Context](../platform-capabilities/global-context) |
| Intercept logic before/after data save or update | **Entity Events**, **Event Bus** | [ORM: Event Features](../orm/event-features) |
| Replace default implementations (e.g., custom PK generation) | **SPI**, **Spring @Primary** | [Override Default Implementations](../reference/override-default-implementations) |
| Develop, load, and unload business plugins | **Plugin Mechanism** | [Plugin: Development](../plugin-mechanism/development) |

### Files & Interfaces
| Intent | Core Components / Tech Stack | Documentation |
| --- | --- | --- |
| Handle attachment upload and download | **FileController**, **OSS Module** | [File Upload](../file-processing/upload) |
| Query RESTful API contracts provided by the backend | **SrvExplain**, **OpenAPI** | [API Reference](../api/reference) |

---

## 🤖 AI Assistant Context & Prompts

If you are using an AI assistant (like Trae, Cursor), providing the correct "Geelato terminology" will yield higher-quality code generation.

### Recommended Prompt Templates

- 🟢 **When building queries:** 
  > "Please use Geelato's `MetaFactory` and Fluent DSL (`MetaQuery`) to write Java code that queries the top 10 records from the `dev_project` table where `status` is 1."
  
- 🟢 **When writing Entity classes:**
  > "Please use Geelato's ORM annotations (`@Title`, `@Col`, `@Model`) to define an entity class named `SysUser` with id, name, and loginName fields."

- 🟢 **When integrating frontend APIs:**
  > "Based on Geelato's MQL syntax, construct a JSON request body for paginated queries on the user table (`gl_user`) with a join on the department table (`gl_org`)."

- 🟢 **When retrieving the current user:**
  > "In a Spring Boot Controller, how do I get the current request's `tenantCode` and `userId` using Geelato's `SecurityContext` or Global Context?"

### 💡 Key Context to Feed AI
If you need the AI to better understand a Geelato project, you can paste the following context:
> "This project is built on the Geelato Framework, a metadata-driven foundation that supports adaptive seamless switching between multiple databases such as MySQL, PostgreSQL, and Oracle. Direct SQL or MyBatis XML is NOT recommended for database operations. Instead, use the built-in ORM (via `MetaFactory` for FluentDSL) or the JSON-based MQL syntax. The project's security context is usually handled by the unified authentication module. For API design, refer to the SrvExplain API specification."

---

**Next Step**: If you are new here, we suggest jumping straight to [👉 Quick Start](quick-start).
