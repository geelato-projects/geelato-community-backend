# 快速开始

这是面向框架使用者的推荐起点，用来帮助你基于 Geelato 的基础能力搭建新的 Web 工程骨架。

## 推荐接入路径

1. 引入 `geelato-framework-bom`
2. 增加 `geelato-framework-starter`
3. 从 `geelato-sample-quickstart` 起步
4. 在最小骨架跑通后，再逐步叠加业务扩展模块

## 基础准备

- Java 17
- Maven 构建环境
- Spring Boot 兼容运行环境
- 如果超出最小 H2 样例，再按需准备外部数据库与中间件

## 最小成功标准

最短成功路径应满足：

- 应用可以正常启动
- 主数据源可用
- `JdbcTemplate`、`Dao`、ORM、Web 自动装配可用
- 能成功调用一个示例接口

## 推荐继续阅读

- [Sample Quickstart](sample-quickstart.md)
- [基于 app-scaffold-starter 创建业务项目](app-scaffold-starter-project-guide.md)
- [BOM 与 Starter](../reference/bom-and-starter.md)
- [核心模块说明](../reference/core-modules.md)
- [PlatformWebRuntime](../runtime/platform-web-runtime.md)

## 最小启动阶段不包含什么

最小启动阶段刻意不要求：

- 平台化上传运行时实现
- `market`、`message`、`schedule`、`auth` 等扩展模块
- 设计时元数据管理能力
- 完整 OpenAPI 治理体系

如果你的项目已经明确需要登录、MQL、组织用户、字典、上传这些基础后台能力，不建议继续停留在最小样例阶段，而应直接切换到：

- [基于 app-scaffold-starter 创建业务项目](app-scaffold-starter-project-guide.md)
