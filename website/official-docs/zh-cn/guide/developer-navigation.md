---
id: developer-navigation
title: 开发者与 AI 导航手册
sidebar_label: 开发者与 AI 导航手册
---

# 开发者导航手册

欢迎来到 Geelato Framework！本文档专为 **人类开发者** 与 **AI 编码助手（如 Trae, Copilot 等）** 设计，提供了一目了然的框架能力矩阵、快速查询地图以及与 AI 协作的最佳实践，帮助你迅速定位需求并开始编码。

## 🎯 快速启动决策树

不同的接入场景对应不同的入口，请根据你的需求选择：

| 我的目标是...                                    | 推荐路径 / 文档锚点                                                                        |
| ------------------------------------------- | ---------------------------------------------------------------------------------- |
| **我想从零开始，快速生成一个完整的后台增删改查项目**                | 推荐使用 **App Scaffold** 脚手架。详见：[Scaffold 项目接入指南](app-scaffold-starter-project-guide) |
| **我有一个现有的 Spring Boot 项目，想接入 Geelato 核心能力** | 推荐 **最小化接入**。详见：[新项目最小接入](minimal-integration)                                     |
| **我想学习框架的架构设计，了解 Runtime 和 Designer 的关系**   | 推荐阅读架构说明。详见：[PlatformWebRuntime](../runtime/platform-web-runtime)                  |
| **我想快速对接单点登录 (SSO) 与认证**                    | 推荐了解统一认证方案。详见：[统一认证总览](../authentication/overview)                                 |

***

## 🛠️ 能力速查矩阵 (Capability Matrix)

当你在开发过程中需要使用特定功能时，可以通过下表快速跳转到对应的开发指南。

### 数据与存储

| 功能意图                   | 核心组件 / 技术栈                       | 查阅文档                                      |
| ---------------------- | -------------------------------- | ----------------------------------------- |
| 编写后端的增删改查代码            | **MetaFactory**, **Fluent DSL**  | [ORM: Fluent DSL](../orm/fluent-dsl)      |
| 在实体类上配置表名和字段映射         | **@Title**, **@Col**, **@Model** | [ORM: 注解说明](../orm/annotations)           |
| 通过 JSON 格式在前端或网关进行复杂查询 | **MQL (Meta Query Language)**    | [MQL: 语法与用法](../mql/usage)                |
| 配置或连接多个数据库             | **Dynamic Datasource**           | [动态数据源能力](../dynamic-datasource/overview) |

### 业务与扩展

| 功能意图                  | 核心组件 / 技术栈                              | 查阅文档                                                    |
| --------------------- | --------------------------------------- | ------------------------------------------------------- |
| 获取当前登录用户、租户或请求上下文     | **Global Context**, **SecurityContext** | [平台能力: 全局上下文](../platform-capabilities/global-context)  |
| 拦截数据保存/更新前后的逻辑        | **Entity Events**, **Event Bus**        | [ORM: 事件特性](../orm/event-features)                      |
| 替换框架的默认实现（如自定义主键生成策略） | **SPI**, **Spring @Primary**            | [覆盖默认实现](../reference/override-default-implementations) |
| 开发、加载和卸载业务插件          | **Plugin Mechanism**                    | [插件机制: 定义与开发](../plugin-mechanism/development)          |

### 文件与接口

| 功能意图                     | 核心组件 / 技术栈                     | 查阅文档                              |
| ------------------------ | ------------------------------ | --------------------------------- |
| 处理附件的上传与下载               | **FileController**, **OSS 模块** | [文件上传](../file-processing/upload) |
| 查询后端提供的 RESTful API 接口契约 | **SrvExplain**, **OpenAPI**    | [API 参考](../api/reference)        |

***

## 💡 AI 辅助开发上下文 (AI Prompts & Context)

> **开发者提示**：在向 AI 编程助手（如 Trae, GitHub Copilot, ChatGPT）提问时，附带以下上下文，可以让 AI 瞬间理解 Geelato 的专有“黑话”，生成高标准、高质量的业务代码。

### 框架基础认知设定 (System Prompt)

```text
你现在是一个熟练掌握 Geelato 框架的高级 Java 工程师。
Geelato 是一个企业级低代码与全代码混合框架，支持 MySQL、PostgreSQL、Oracle 等多数据库的自适应无缝切换。请在生成代码时严格遵循以下规范：
1. 【ORM 规范】禁止写原生 SQL 或 MyBatis XML，所有的 CRUD 必须使用 `MetaFactory` 的 Fluent DSL。
2. 【API 规范】对外暴露的 Controller 必须使用 `@ApiRestController`，并统一返回 JSON 格式。
3. 【权限规范】所有获取当前登录用户的操作，必须通过注入 `SecurityHelper` 调用其内部方法获取。
4. 【动态查询】在前端发起的多表联合查询，建议优先指导使用 MQL 语法，而不是在后端堆砌接口。
```

***

**下一步**：如果你是初次接触，建议直接跳转到 [👉 快速开始](quick-start)。
