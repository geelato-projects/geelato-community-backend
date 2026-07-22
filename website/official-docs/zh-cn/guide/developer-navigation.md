---
title: 开发者与 AI 导航手册
sidebar_label: 开发者与 AI 导航手册
---

# 开发者与 AI 导航手册

本手册面向人类开发者与 AI 编码助手（如 Trae、Copilot 等），提供框架能力矩阵、查询地图以及与 AI 协作的上下文，便于快速定位需求并开始编码。

## 快速启动决策树

不同接入场景对应不同入口，按需求选择：

| 目标 | 推荐路径 |
| --- | --- |
| 从零开始，快速生成一个完整的后台增删改查项目 | **App Scaffold** 脚手架。详见 [Scaffold 项目接入指南](app-scaffold-starter-project-guide.md) |
| 现有 Spring Boot 项目，需接入 Geelato 核心能力 | **最小化接入**。详见 [新项目最小接入](minimal-integration.md) |
| 了解框架架构设计、Runtime 与 Designer 的关系 | 阅读架构说明。详见 [PlatformWebRuntime](../runtime/platform-web-runtime.md) |
| 对接单点登录 (SSO) 与认证 | 统一认证方案。详见 [统一认证总览](../authentication/overview.md) |

## 能力速查矩阵

开发过程中需使用特定功能时，可通过下表快速跳转到对应开发指南。

### 数据与存储

| 功能意图 | 核心组件 / 技术栈 | 查阅文档 |
| --- | --- | --- |
| 编写后端的增删改查代码 | MetaFactory、Fluent DSL | [ORM: Fluent DSL](../orm/fluent-dsl.md) |
| 在实体类上配置表名和字段映射 | `@Title`、`@Col`、`@Model` | [ORM: 注解说明](../orm/annotations.md) |
| 通过 JSON 格式在前端或网关进行复杂查询 | MQL (Meta Query Language) | [MQL: 语法与用法](../mql/usage.md) |
| 配置或连接多个数据库 | Dynamic Datasource | [动态数据源能力](../dynamic-datasource/overview.md) |

### 业务与扩展

| 功能意图 | 核心组件 / 技术栈 | 查阅文档 |
| --- | --- | --- |
| 获取当前登录用户、租户或请求上下文 | Global Context、SecurityContext | [平台能力: 全局上下文](../platform-capabilities/global-context.md) |
| 拦截数据保存/更新前后的逻辑 | Entity Events、Event Bus | [ORM: 事件特性](../orm/event-features.md) |
| 扩展平台默认规则（如查询过滤、字段填充） | SPI、Spring Bean | [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md) |
| 开发、加载和卸载业务插件 | Plugin Mechanism | [插件机制: 定义与开发](../plugin-mechanism/development.md) |

### 文件与接口

| 功能意图 | 核心组件 / 技术栈 | 查阅文档 |
| --- | --- | --- |
| 处理附件的上传与下载 | FileController、OSS 模块 | [文件上传](../file-processing/upload.md) |
| 查询后端提供的 RESTful API 接口契约 | SrvExplain、OpenAPI | [API 参考](../api/reference.md) |

## AI 辅助开发上下文

向 AI 编码助手（如 Trae、GitHub Copilot、ChatGPT）提问时，附带以下上下文有助于 AI 准确理解 Geelato 的专有术语并生成符合规范的业务代码。

### 框架基础认知设定 (System Prompt)

```text
你现在是一个熟练掌握 Geelato 框架的高级 Java 工程师。
Geelato 是一个企业级低代码与全代码混合框架，支持 MySQL、PostgreSQL、Oracle 等多数据库的自适应无缝切换。请在生成代码时严格遵循以下规范：
1. 【ORM 规范】禁止写原生 SQL 或 MyBatis XML，所有的 CRUD 必须使用 `MetaFactory` 的 Fluent DSL。
2. 【API 规范】对外暴露的 Controller 必须使用 `@ApiRestController`，并统一返回 JSON 格式。
3. 【权限规范】所有获取当前登录用户的操作，必须通过注入 `SecurityHelper` 调用其内部方法获取。
4. 【动态查询】在前端发起的多表联合查询，建议优先指导使用 MQL 语法，而不是在后端堆砌接口。
```

## 下一步

如初次接触，建议从 [快速开始](quick-start.md) 入手。
