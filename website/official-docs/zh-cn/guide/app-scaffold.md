# App Scaffold

`geelato-app-scaffold` 是官方胖脚手架，用于在保留框架分层边界的前提下，让新项目开箱即具备可直接开工的运行时基础能力。

## 它解决什么问题

阶段 7 的 `sample-quickstart` 只证明框架底座可以独立启动，但它刻意不携带：

- 登录
- MQL
- 组织与用户管理
- 字典服务
- 上传

如果直接把这些能力继续往 sample 上叠加，最小样例就会失去“最小验证”的意义。

因此现在官方交付分成三层：

- `geelato-framework-starter`
  - 最小底座
- `geelato-sample-quickstart`
  - 最小样例
- `geelato-app-scaffold`
  - 可直接开工的脚手架

## 默认能力集合

脚手架默认承接 `geelato-web-runtime` 的基础运行时能力，包括：

- 登录
- MQL
- 组织
- 用户
- 字典
- 上传

设计时能力仍然留在 `geelato-web-platform`，不进入脚手架默认边界。

## 交付形态

脚手架由两层组成：

- `geelato-app-scaffold-starter`
  - 作为依赖升级入口，供后续业务项目复用
- `geelato-app-scaffold`
  - 官方可运行工程，作为项目起点

这意味着后续新项目推荐做法不是把脚手架目录复制成一次性模板，而是：

1. 参考脚手架应用结构启动项目
2. 保持对 `geelato-app-scaffold-starter` 的依赖
3. 通过依赖升级持续获得公共能力演进

## 与 Sample 的区别

`sample-quickstart` 仍然负责：

- 最小化接入验证
- H2 内存库启动
- 底座装配排障

`app-scaffold` 则负责：

- 提供真实项目更常用的运行时基础服务
- 对齐外部数据库和上传目录等默认约定
- 作为团队和 AI 开发的统一起点

## 如何开始

如果你的目标是新起一个真正的业务项目，而不是只运行官方示例，请优先阅读：

- [基于 app-scaffold-starter 创建业务项目](app-scaffold-starter-project-guide.md)

这篇专题页会完整覆盖：

- 如何自己创建业务工程
- 如何配置数据库与扫描参数
- 如何验证 starter 是否可用
- 如何新增业务实体和建表脚本
- 如何通过 MQL 做实体 CRUD
- 如何调用字典、组织用户、上传等现成能力
- 如何升级业务工程依赖

`geelato-hello-example/geelato-app-scaffold` 仍然是官方可运行示例，但它主要承担“事实源”和“参考壳子”的作用，不应替代你自己的业务工程。

## 推荐继续阅读

- [基于 app-scaffold-starter 创建业务项目](app-scaffold-starter-project-guide.md)
- [新项目最小接入](minimal-integration.md)
- [默认实现与 Sample 定位](default-implementation-vs-sample.md)
- [Runtime / Designer 部署与依赖](../operations/runtime-designer-deployment.md)

