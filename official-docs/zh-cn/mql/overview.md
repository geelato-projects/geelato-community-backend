# MQL

MQL，完整名称为 Meta Query Language，是 Geelato Framework 在平台侧提供的一套基于 JSON 的元数据查询与操作协议。

它的目标不是替代后端 Java 服务中的 ORM Fluent DSL，而是为前端页面、低代码界面和平台通用数据接口提供统一的数据访问表达方式。

## MQL 解决什么问题

MQL 主要用于这些场景：

- 前端直接按实体发起列表查询、保存、删除
- 平台通用数据接口不希望为每个实体单独编写 Controller
- 查询条件需要以 JSON 协议方式在页面和服务端之间传递
- 需要复用平台元数据、默认字段填充、嵌套保存等内核能力

## MQL 的协议定位

MQL 的典型入口是 `MetaController` 提供的一组通用接口。

它更接近：

- 平台内部通用数据协议
- 前端到平台运行时之间的元数据访问语言
- 对实体 CRUD 的统一 JSON 描述

它不等同于：

- 标准 OpenAPI 外部契约
- 后端 Java 服务里的 ORM Fluent DSL
- 强类型的 MyBatis Mapper 接口

## 与 ORM Fluent DSL 的边界

两者解决的是不同层级的问题：

- MQL：优先面向前端和平台协议侧，使用 JSON 描述查询与写入
- Fluent DSL：优先面向后端 Java 服务，使用 `MetaFactory` 链式调用数据访问能力

推荐边界：

- 页面、平台通用控制器、低代码配置场景，优先使用 MQL
- 后端服务内部标准 CRUD、轻量 join、过程调用，优先使用 Fluent DSL
- 超复杂 SQL、复杂结果映射，继续使用原生 SQL / MyBatis

## 当前核心入口

MQL 的核心处理类为 `MetaController`，常见接口包括：

- `/api/meta/list`
- `/api/meta/multiList`
- `/api/meta/save/{biz}`
- `/api/meta/batchSave`
- `/api/meta/multiSave`
- `/api/meta/delete/{biz}/{id}`
- `/api/meta/delete2/{biz}`

## 你会在 MQL 中看到什么

MQL 的主体是一段 JSON，通常围绕“实体名 + 关键字 + 条件表达式”展开。

你会频繁看到这些能力：

- `@fs` 字段选择
- `@p` 分页
- `@order` 排序
- `@group` 分组
- `@b` 复杂括号逻辑
- `ref(...)` 关联字段引用
- `increment(...)`、`findinset(...)`、`fuzzymatch(...)` 等函数
- `$ctx.*`、`$fn.*`、`$parent.*` 这类内置变量

## 推荐阅读顺序

1. 先看 [MQL 使用指引](usage.md)
2. 再看 [API 参考](../api/reference.md)，理解 OpenAPI 与 `SrvExplain` 的双轨关系
3. 如需服务端 Java 侧写法，再看 [ORM Fluent DSL 指引](../orm/fluent-dsl.md)
