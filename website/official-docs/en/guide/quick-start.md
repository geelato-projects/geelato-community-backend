---
id: quick-start
title: Overview & Start Methods
sidebar_label: Overview & Comparison
---

# Quick Start: Two Ways to Bootstrap

Geelato Framework provides two standard approaches to start a project. Whether you are building a new application from scratch or integrating Geelato into an existing Spring Boot project, you can choose the path that best fits your needs.

## Comparison of Methods

| Dimension | Method 1: App Scaffold (Recommended) | Method 2: Minimal Integration |
| --- | --- | --- |
| **Positioning** | An out-of-the-box "fat scaffold" that provides a production-ready backend skeleton. | A "thin starter" that only introduces foundational dependencies with minimal intrusion. |
| **Use Case** | **New projects starting from scratch**, needing a complete backend management capability instantly. | **Existing Spring Boot projects**, or when you only need ORM/MQL capabilities. |
| **Capabilities Included** | Base foundation + Login, Org, User, Role, Dictionary, File Upload, MQL, and auto table initialization. | Only base Web configuration, primary DB setup, dynamic datasource entry, `JdbcTemplate`, and ORM base setup. |
| **Dependency** | `geelato-app-scaffold-starter` | `geelato-framework-starter` |
| **Database Tables** | Automatically detects and initializes about 17 platform tables (e.g., dict, user, role) on startup. | **Zero table requirements**. You decide what tables to create. |
| **Dev Rhythm** | Minimal configuration. You can start writing business entities and CRUD right away. | You need to build your own login interceptors, permission checks, attachment logic, etc. |

## Which One Should I Choose?

1. **If you are building a brand new backend system**:
   👉 **Strongly recommended: [App Scaffold Quickstart](app-scaffold-starter-project-guide.md)**. It saves you the time of building foundational modules so you can focus on business logic on day one.
   
2. **If you are refactoring an old project, or just want to use a specific Geelato feature (like FluentDSL)**:
   👉 **Choose [Minimal Integration](minimal-integration.md)**. This method won't force any platform tables into your database, nor will it hijack your login system.

---

## Next Steps

Please choose the corresponding guide based on your decision:

- 🚀 **[Method 1: App Scaffold Quickstart](app-scaffold-starter-project-guide.md)**
- 🌱 **[Method 2: Minimal Integration](minimal-integration.md)**
