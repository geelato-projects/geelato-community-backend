# 新项目最小接入

这篇文档说明如何从零开始接入 Geelato Framework，并在不引入平台重扩展的前提下跑通一个最小 Spring Boot Web 应用。

## 推荐接入顺序

1. 引入 `geelato-framework-bom`
2. 引入 `geelato-framework-starter`
3. 参考 `geelato-sample-quickstart` 搭建最小工程
4. 跑通主库、`JdbcTemplate`、`Dao`、ORM 和一个示例接口
5. 业务需要基础后台能力时，再切到 `geelato-app-scaffold`
6. 再按需叠加 runtime、designer 或业务扩展模块

## 最小依赖原则

最小接入阶段建议只保留：

- `geelato-framework-bom`
- `geelato-framework-starter`
- 一个主数据源驱动
- Spring Boot Web 启动工程

不建议在最小接入阶段直接引入：

- 平台化上传实现
- 设计时建模治理能力
- `message`、`market`、`schedule` 等扩展模块
- 需要额外中间件的重量级平台能力

## 最小配置面

当前 starter 自动装配的前提是存在 `spring.datasource.primary.jdbc-url`，并会默认创建：

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `dbGenerateDao`

如存在 `spring.datasource.secondary.jdbc-url`，还会额外创建 secondary 相关 Bean。

## 推荐起点

当前官方最小样例是 `geelato-sample-quickstart`，它使用：

- `geelato-framework-starter`
- H2 内存数据库
- `geelato.orm.dao-bean-name=dynamicDao`
- 默认关闭 JTA / Seata 代理

这条链路的目的不是展示平台全能力，而是验证框架底座可以被独立消费。

## 什么时候切换到脚手架

如果项目已经明确需要这些基础后台能力：

- 登录
- MQL
- 组织与用户
- 字典
- 上传

则不建议继续在最小 sample 上做胖，而应直接采用：

- `geelato-app-scaffold`

它的定位是“可以直接开工的官方脚手架”，而不是“最小样例”。

如果你的目标不是运行官方示例，而是自己新起一个业务工程并长期依赖 starter，请继续阅读：

- [基于 app-scaffold-starter 创建业务项目](app-scaffold-starter-project-guide.md)

## 最小成功标准

最短成功路径应至少满足：

- 应用可以正常启动
- 主数据源可用
- `JdbcTemplate` 和 `Dao` 可注入
- ORM 自动装配可用
- 能调用一个运行时接口

## 下一步推荐

- 先看 [Sample Quickstart](sample-quickstart.md)
- 如果需要可直接开工的起点，再看 [App Scaffold](app-scaffold.md)
- 如果要基于 starter 真正创建自己的业务工程，再看 [基于 app-scaffold-starter 创建业务项目](app-scaffold-starter-project-guide.md)
- 再看 [默认实现与 Sample 定位](default-implementation-vs-sample.md)
- 如需理解交付入口，再看 [BOM 与 Starter](../reference/bom-and-starter.md)
- 如需扩展元数据能力，再看 [MetaStore 扩展](../reference/metastore-extension.md)
