# 默认实现与 Sample 定位

这篇文档用于解释三层边界：

- 框架底座
- 模块内默认实现
- 官方 Sample
- 官方脚手架

阶段 7 和阶段 8 完成后，这个边界已经变成对外消费时必须理解的基础约束。

## 框架底座是什么

当前对外推荐消费入口是：

- `geelato-framework-bom`
- `geelato-framework-starter`

底座负责提供：

- 基础 Web 装配
- 主库 `DataSource`、`JdbcTemplate`、`Dao`
- ORM 自动装配
- 动态数据源自动装配入口
- `SecurityContext` 清理 Filter 等公共运行时基础设施

## 默认实现是什么

默认实现是“模块内部自带、但允许上层替换”的实现，而不是最小框架契约本身。

当前已经明确的例子包括：

- `DefaultMetaStore`：当前平台表结构下的默认元数据实现
- `PlatformDynamicDataSourceDefinitionLoader`：默认从平台表读取动态数据源定义
- `DefaultUserProvider` / `DefaultOrgProvider`：安全快照默认实现

这些能力可以直接复用，但不意味着外部项目必须接受同样的实现偏好。

## Sample 是什么

Sample 用来证明“框架底座能独立跑起来”，不是把平台所有能力都堆进去。

当前官方最小 Sample 是：

- `geelato-sample-quickstart`

它刻意只保留：

- `geelato-framework-starter`
- H2 主库
- 最小启动类
- 一个运行时示例接口

## 示例仓库（geelato-hello-example）

官方示例仓库：[geelato-hello-example](https://github.com/geelato-projects/geelato-hello-example)

仓库内按“从最小到可开工”的顺序提供了多个工程：

- `geelato-sample-quickstart`：框架底座最小可启动样例（H2 + 一个运行时接口）
- `geelato-sample-orm`：ORM 独立接入样例（Fluent DSL / `MetaFactory` CRUD）([geelato-sample-orm](https://github.com/geelato-projects/geelato-hello-example/tree/main/geelato-sample-orm))
- `sample-lite-login`：第三方应用接入统一认证 `lite-login` 的样例（前端拿 token + 后端调 `/oauth2/userinfo`）([sample-lite-login](https://github.com/geelato-projects/geelato-hello-example/tree/main/sample-lite-login))
- `sample-oauth2-authorize`：OAuth2 授权码模式样例（`/oauth2/authorize` + 回调 code）
- `geelato-app-scaffold`：官方脚手架工程（用于真实项目开工的默认运行时骨架）

## 脚手架是什么

官方脚手架是：

- `geelato-app-scaffold`

它的职责不是验证“底座能不能启动”，而是给业务项目一个默认可开工的运行时骨架。

它默认承接：

- 登录
- MQL
- 组织与用户
- 字典
- 上传

## 为什么不能把 Sample 当成底座

Sample 可以携带演示属性，但底座不能被演示工程反向污染。

例如：

- `sample-quickstart` 选择 H2，只是为了最小启动
- `app-scaffold` 则面向真实项目的基础后台能力起步
- `web-quickstart` 恢复了较多扩展依赖，是扩展型样例
- 平台化上传、设计时治理、打包发布等能力，不应被误认为 Starter 的强制组成部分

## 推荐理解方式

- 底座：定义最小契约与统一接入入口
- 默认实现：给出开箱可用的参考实现
- Sample：证明接入方式正确，并降低首次上手成本
- 脚手架：在不污染底座的前提下，提供可直接开工的默认业务骨架

## 推荐继续阅读

- [新项目最小接入](minimal-integration.md)
- [Sample Quickstart](sample-quickstart.md)
- [App Scaffold](app-scaffold.md)
- [覆盖默认实现](../reference/override-default-implementations.md)
