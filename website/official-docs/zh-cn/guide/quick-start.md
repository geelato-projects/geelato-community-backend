---
title: 快速开始与启动方式对比
sidebar_label: 概览与启动方式对比
---

# 快速开始：两种启动方式对比

Geelato Framework 提供两种标准的项目启动方式。无论是从零开发全新业务系统，还是在现有 Spring Boot 项目中引入 Geelato 的基础能力，均可根据实际需求选择接入路径。

## 启动方式对比

| 对比维度 | 方式一：基于脚手架启动 (App Scaffold) | 方式二：最小化接入 (Minimal Integration) |
| --- | --- | --- |
| **定位** | 开箱即用的"胖脚手架"，直接提供可用于生产的后台骨架。 | 仅引入底层依赖的"瘦启动"，对现有项目侵入性最小。 |
| **适用场景** | 从零开始的新项目，需要快速拥有完整的后台管理能力。 | 已有 Spring Boot 项目，或仅需使用 ORM/MQL 能力。 |
| **包含能力** | 完整底座 + 登录、组织、用户、角色、字典、文件上传、MQL 与自动化建表。 | 基础 Web 装配、主库配置、动态数据源入口、`JdbcTemplate` 与 ORM 基础装配。 |
| **依赖引入** | `geelato-app-scaffold-starter` | `geelato-framework-starter` |
| **数据库表** | 启动时自动检测并初始化约 17 张平台基础表（字典表、用户表、权限表等）。 | 零建表要求，由开发者自行决定是否建表及建什么表。 |
| **开发节奏** | 配置极少，直接上手编写业务实体与 CRUD。 | 需自行搭建登录拦截、权限校验、附件管理等周边逻辑。 |

## 如何选择启动方式

- **全新后台管理系统**：选择 [脚手架快速启动](app-scaffold-starter-project-guide.md)。可跳过基础模块配置，直接进入业务开发。
- **改造老项目，或仅需使用 Geelato 的单一功能（如 FluentDSL）**：选择 [最小化接入](minimal-integration.md)。此方式不会向数据库强制增加任何平台表，也不会接管现有登录体系。

## 下一步

根据所选方式，阅读对应的接入指南：

- [方式一：脚手架快速启动 (App Scaffold)](app-scaffold-starter-project-guide.md)
- [方式二：最小化接入 (Minimal Integration)](minimal-integration.md)
