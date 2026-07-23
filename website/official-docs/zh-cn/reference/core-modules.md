---
title: 核心模块说明
sidebar_label: 核心模块说明
---

# 核心模块说明

本页说明对框架使用者具有复用价值的基础模块及其职责。

## Foundation 模块

### `geelato-lang`

承载基础契约、API 结果模型与低层注解。

### `geelato-utils`

承载通用工具能力与跨模块辅助能力。

### `geelato-security`

承载安全契约与上下文模型（User、Org、Role、Permission、Tenant 等）。

### `geelato-core`

承载 Meta、DAO、脚本与核心运行管理能力，是平台的核心。包含元数据模型、MQL 引擎、SQL 构建器与 ORM 运行时。

### `geelato-orm`

承载 Fluent DSL 与 ORM 侧执行能力，对后端开发者暴露 `MetaFactory` 链式 CRUD。

### `geelato-dynamic-datasource`

承载可选的动态数据源能力，提供多数据源路由与连接管理。

## 关键架构约束

- `SecurityContext` 只能在鉴权成功后由安全链路写入。
- 兼容回退时 `dynamicDao` 仍优先于 `primaryDao`。
- 带强实现偏好的上传能力不进入最小 Starter 底座。

## 继续阅读

- [ORM 总览](../orm/overview.md)
- [ORM 注解说明](../orm/annotations.md)
- [Fluent DSL 指引](../orm/fluent-dsl.md)
- 最小样例：`geelato-sample-quickstart`（在 geelato-hello-example 中）
- 运行时 / 设计时拆分：[PlatformWebRuntime](../runtime/platform-web-runtime.md)、[PlatformDesginer](../designer/platform-desginer.md)
