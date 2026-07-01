# Geelato Framework 官方文档

这是 `geelato-community` 的第一版官方文档站骨架。

它把原先分散在仓库里的框架说明、样例说明和静态接口说明，收敛为围绕 `BOM + Starter + Sample + Runtime + Designer` 的统一入口。

## 这套站点覆盖什么

- 面向框架使用者的快速开始
- `geelato-framework-bom` 与 `geelato-framework-starter` 参考说明
- `PlatformWebRuntime` / `PlatformDesginer` 的运行时与设计时分层说明
- 统一认证与 `lite-login` 接入指引
- OpenAPI 与 `SrvExplain` 双轨 API 入口
- 面向长期演进的文档治理规范

## 主要服务对象

- 需要接入 Geelato Framework 的新项目开发者
- 负责维护平台模块边界、生命周期和治理规则的维护者
- 需要查看接口契约和集成示例的 API 集成方

## 当前复用的文档来源

当前这一版官方文档骨架主要复用了现有项目资产：

- `docs/` 中的专题说明
- `SrvExplain/` 中生成的控制器级 API 说明
- `../geelato-hello-example/geelato-sample-quickstart/README.md` 中的最小样例说明

## 推荐阅读顺序

1. 先看 [快速开始](guide/quick-start.md)
2. 再看 [BOM 与 Starter](reference/bom-and-starter.md)
3. 再看 [Sample Quickstart](guide/sample-quickstart.md)
4. 如需统一登录接入，再看 [统一认证](authentication/overview.md)
5. 最后看 [API 参考](api/reference.md)

## 基本原则

仓库中的 Markdown 是事实源，官方站点是对外发布形态。

