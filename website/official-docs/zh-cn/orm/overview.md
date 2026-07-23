---
title: ORM
sidebar_label: ORM
---

# ORM

ORM，全称 Object-Relational Mapping（对象关系映射），是编程语言对象与关系型数据库之间的操作层。

在 Geelato Framework 中，该能力并非单点 API，而是一组围绕"元数据 + 查询协议 + Java DSL + 扩展机制"组织起来的体系：

- 注解层：描述 Java 对象如何映射到表、列和元数据标题
- 协议层：通过 `MetaController + MQL` 承载前端和平台通用数据访问
- Java API 层：通过 `MetaFactory + Fluent DSL` 承载后端服务代码中的 CRUD 与轻量高级查询
- 扩展层：通过事件、动态数据源、查询过滤/字段填充 SPI 等机制承载平台级规则与扩展能力

## ORM 能力现状

Geelato ORM 的能力特点：

- 不是 JPA/Hibernate 那种"实体状态管理优先"的重量级 ORM。
- 也不是只做简单表映射的轻量工具类集合。
- 核心是"以元数据为中心"的统一数据访问体系。
- 查询与写入能力拆分为不同入口，分别服务不同层级。

可将这套能力理解为分层协作：

- `@Entity / @Col / @Title / @Transient` 解决"对象与表结构如何声明"。
- `MQL` 解决"前端、平台协议侧如何用 JSON 描述查询与写入"。
- `Fluent DSL` 解决"后端 Java 服务如何以链式 API 发起查询、保存、更新、删除"。
- 事件、动态数据源、SPI 扩展解决"主链路之外的平台规则如何注入"。

Geelato ORM 并非仅有 `geelato-orm` 一个模块，而是多模块协作：

- `geelato-orm`：后端 Java 侧的 ORM 主入口与执行链路。
- `geelato-core`、`geelato-lang`：元数据与部分基础契约。
- `MQL`：平台协议侧的数据访问表达。
- `geelato-web-platform`：默认平台规则的落地实现。

## ORM 的目的

这套 ORM 体系主要为了解决这些问题：

- 让 Java 对象、数据库表字段、元数据标题保持统一声明
- 让平台协议侧和后端 Java 侧都可以复用同一套元数据模型
- 避免在服务层频繁手写 MQL JSON、DAO 样板代码或散落的 SQL 拼装
- 把动态数据源、视图参数、上下文取值、默认字段填充、权限过滤等框架能力统一收口
- 把平台默认规则从底层 CRUD 里解耦出来，改为通过事件或 SPI 注入

## 四类入口如何分工

### 1. ORM 注解

注解层回答的是“实体是什么”。

它主要负责：

- 一个 Java 类对应哪个实体名、哪张表
- 一个 Java 字段对应哪一列、有哪些列级约束
- 哪些字段只是运行时属性，不参与持久化
- 哪些实体和字段需要补充标题、描述等元数据信息

典型使用方式：

```java
@Entity(name = "User", table = "platform_user")
@Title(title = "用户")
public class User extends BaseEntity {
    @Title(title = "用户名")
    private String name;

    @Col(name = "login_name")
    @Title(title = "登录名")
    private String loginName;

    @Transient
    private List<Role> roles;
}
```

对应文档见 [ORM 注解说明](annotations.md)。

### 2. MQL

MQL 回答的是“平台协议侧如何访问数据”。

它主要面向：

- 前端页面
- 平台通用数据接口
- 低代码配置或 JSON 协议场景

典型能力包括：

- `@fs` 字段选择
- `@p` 分页
- `@order` 排序
- `@group` 分组
- `@b` 复杂括号逻辑
- `@pf` 视图模板参数
- `ref(...)` 关联字段
- `$ctx.* / $fn.* / $parent.*` 内置变量

典型使用方式：

```json
{
  "platform_user": {
    "@fs": "id,name,loginName",
    "@p": "1,10",
    "@order": "updateAt|-",
    "delStatus": 0
  }
}
```

MQL 与 ORM 强相关，但它的定位不是 Java 侧 DSL，而是平台通用 JSON 协议。

对应文档见 [MQL 总览](../mql/overview.md) 和 [MQL 使用指引](../mql/usage.md)。

### 3. Fluent DSL

Fluent DSL 回答的是“后端 Java 服务如何访问数据”。

它主要面向：

- 服务端业务代码
- Java 里的标准 CRUD
- 轻量 join、分页、聚合、过程调用
- 需要复用动态数据源、视图参数、值引用等框架能力的场景

典型能力包括：

- `MetaFactory.query(...)`
- `MetaFactory.insert(...)`
- `MetaFactory.update(...)`
- `MetaFactory.delete(...)`
- `MetaFactory.procedure(...)`
- `MetaFactory.sql(...)`
- `selectRef(...)`
- `leftJoin/innerJoin/rightJoin`
- `useDataSource(...)`
- `viewParams(...)`
- `ValueRefs.ctx/fn/parent`

典型使用方式：

```java
List<Map<String, Object>> users = MetaFactory.query("User")
        .select(new String[]{"id", "name", "mobilePhone"})
        .where(Filter.eq("delStatus", 0))
        .order(Order.desc("updateAt"))
        .page(1, 10)
        .page();
```

对应文档见 [Fluent DSL 指引](fluent-dsl.md)。

### 4. 高级特性与扩展机制

高级特性回答的是“标准 CRUD 之外的平台能力如何接入”。

当前主要包括四类：

- ORM 事件：在保存和删除前后挂接监听器
- 动态数据源：按运行时 key 切换数据源
- 查询过滤 SPI：把租户、权限等平台级过滤规则注入查询链路
- 字段填充 SPI：在保存链路自动补齐审计字段、租户字段等默认值

#### ORM 事件怎么用

适合做：

- 保存前校验
- 保存后审计、缓存刷新、镜像同步
- 删除前约束拦截
- 删除后旁路清理

对应文档见 [ORM 事件特性](event-features.md)。

#### 动态数据源怎么用

适合做：

- 同一套服务代码访问不同数据源
- 在 Fluent DSL 或 SQL 直通执行时显式切换数据源
- 在宿主工程里替换动态数据源定义来源

对应文档见 [ORM / 数据源扩展](datasource-extension.md) 和 [动态数据源](../dynamic-datasource/overview.md)。

#### 注入扩展怎么用

适合做：

- 自动注入租户过滤、权限过滤、组织隔离条件
- 自动填充创建人、更新时间、租户编码等默认字段
- 在宿主项目中替换平台默认规则，而不修改底层模块

当前推荐扩展入口：

- `FluentQueryFilterInjector`
- `MqlQueryFilterInjector`
- `FluentSaveFieldValueFiller`
- `MqlSaveFieldValueFiller`
- `EntitySaveFieldValueFiller`

对应文档见 [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)。

## 推荐使用边界

推荐优先使用 Geelato ORM 体系的场景：

- 后端服务中需要按实体名或实体类做标准 CRUD
- 页面或平台通用接口需要用 JSON 协议描述查询和保存
- 需要少量关联字段、分页、排序、轻量聚合或轻量存储过程调用
- 需要复用动态数据源、视图参数、默认字段填充、上下文取值等框架能力

不建议强行使用这套 ORM 体系的场景：

- 超复杂跨组过滤、递归 CTE、窗口函数等 SQL-first 查询
- 多结果集存储过程或高度依赖复杂 `resultMap` 的场景
- 团队已经明确持有完整 SQL，且更适合直接维护 SQL 文本的场景

## 与其他数据访问方式的关系

- `MetaFactory + Fluent DSL`：优先用于后端 Java 服务中的标准元数据 CRUD
- `MetaController + MQL`：优先用于平台通用数据接口和前端协议侧
- `MetaFactory.sql(...)`：用于业务方已经持有完整 SQL、只想复用执行链路的场景
- MyBatis / 原生 SQL：保留给超复杂查询、复杂结果映射和数据库特性较强的场景

## 最小接入（独立 Spring Boot）

这部分给出最短接入路径，让一个普通 Spring Boot 工程可以直接使用 `MetaFactory` 完成 CRUD。

最小依赖：

```xml
<dependency>
  <groupId>cn.geelato</groupId>
  <artifactId>geelato-orm</artifactId>
</dependency>
```

最小 Bean：

```java
@Configuration
public class OrmDaoConfiguration {
    @Bean
    public Dao primaryDao(JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
```

元数据准备：

- 默认会扫描 Spring Boot 启动类所在包及子包内的 `@Entity` 并注册到元数据管理器
- 可通过配置关闭或限定扫描范围：

```yaml
geelato:
  orm:
    entity-auto-scan-enabled: true
    entity-scan-base-packages:
      - com.example.demo.entity
```

## 自动装配包含什么

`geelato-orm` 在 Spring Boot 环境中当前自动装配这些能力：

- `MetaExecutionStrategy`：根据配置选择基于 `Dao` 或 `JdbcTemplate` 的执行策略
- `MetaCommandExecutor`：承接 Fluent DSL 的执行
- `SaveDefaultValueFiller`：兼容层默认字段填充 Bean
- `BeansUtils`：用于在执行路径中获取 Spring Bean
- 实体元数据自动注册：扫描 `@Entity` 并注册到元数据管理器
- 默认数据源初始化：推导并注册默认数据源 key

## 推荐阅读顺序

1. 先看 [ORM 注解说明](annotations.md)，理解对象与表结构如何映射
2. 再看 [MQL 总览](../mql/overview.md)，理解平台协议侧如何访问数据
3. 再看 [Fluent DSL 指引](fluent-dsl.md)，理解后端 Java 侧如何访问数据
4. 再看 [ORM 事件特性](event-features.md) 与 [ORM / 数据源扩展](datasource-extension.md)，理解高级扩展机制
5. 最后结合 [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md) 和 [核心模块说明](../reference/core-modules.md) 理解整体架构
