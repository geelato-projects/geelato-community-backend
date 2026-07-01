# ORM

`geelato-orm` 是 Geelato Framework 在服务端提供的 ORM 模块，核心目标不是做一套重量级实体持久化框架，而是围绕元数据模型提供一条统一的后端数据访问链路。

这条链路主要由两部分组成：

- ORM 注解：负责把 Java 类声明为框架可识别的元数据实体
- Fluent DSL：负责在 Java 服务代码中以链式方式完成查询、保存、更新、删除和轻量高级查询

## ORM 在框架中的角色

`geelato-orm` 主要解决这些问题：

- 让实体类与数据库表、列、中文标题等元数据保持统一声明
- 让后端代码可以通过 `MetaFactory` 直接发起元数据驱动的 CRUD
- 复用动态数据源、视图参数、默认审计字段填充、上下文值引用等框架能力
- 避免在 Java 服务层频繁手写 MQL JSON 或重复 DAO 样板代码

## 推荐使用边界

推荐优先使用 ORM 的场景：

- 后端服务中需要按实体名或实体类做标准 CRUD
- 需要少量关联字段、分页、排序、轻量聚合查询
- 需要复用 `useDataSource(...)`、`viewParams(...)`、`ValueRefs` 等框架能力

不建议强行使用 ORM 的场景：

- 前端直连平台通用数据接口时，仍以 `MetaController + MQL` 为主
- 超复杂跨组过滤、递归 CTE、窗口函数等 SQL-first 场景
- 多结果集存储过程或高度依赖手写 `resultMap` 的 MyBatis 场景

## 两部分内容如何理解

### ORM 注解

ORM 注解负责描述“实体是什么”，例如：

- 这个类映射到哪张表
- 某个字段对应哪一列
- 哪些属性只参与内存计算、不参与落库
- 某些实体和字段在人机界面上的中文标题是什么

对应文档见 [ORM 注解说明](annotations.md)。

### Fluent DSL

Fluent DSL 负责描述“如何访问数据”，例如：

- 如何查询一条记录或一页记录
- 如何插入、更新、删除
- 如何 selectRef、join、切换数据源、调用存储过程
- 如何在写入时使用 `ValueRefs.ctx/fn/parent`

对应文档见 [Fluent DSL 指引](fluent-dsl.md)。

## 推荐阅读顺序

1. 先看 [ORM 注解说明](annotations.md)，理解实体元数据如何声明
2. 再看 [Fluent DSL 指引](fluent-dsl.md)，理解服务端如何访问数据
3. 最后结合 [核心模块说明](../reference/core-modules.md) 理解 `geelato-orm` 在整个框架中的位置

## 与其他数据访问方式的关系

- `MetaFactory + Fluent DSL`：优先用于后端 Java 服务中的标准元数据 CRUD
- `MetaController + MQL`：优先用于平台通用数据接口和前端协议侧
- `MetaFactory.sql(...)`：用于业务方已经持有完整 SQL、只想复用执行链路的场景
- MyBatis / 原生 SQL：保留给超复杂查询、复杂结果映射和数据库特性较强的场景
