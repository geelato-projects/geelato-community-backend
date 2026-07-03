# API 参考

官方 API 文档采用双轨模式。

## 标准契约

**OpenAPI** 是对外官方标准契约。

它未来应作为这些场景的主入口：

- 外部集成
- 运行时 API 消费
- 接口评审
- 网关或客户端生成

## 静态补充

**SrvExplain** 继续保留价值，因为它可以：

- 直接从源码静态扫描当前控制器
- 在迁移期暴露尚未完全 OpenAPI 化的接口
- 以 Controller 维度输出 Markdown 说明

## 当前现状

目前：

- OpenAPI 能力主要存在于 quickstart 侧的 `springdoc`
- `SrvExplain` 已经可以在仓库中持续生成
- 平台内部通用数据协议仍大量依赖 `MetaController + MQL`

## 与 MQL 的关系

`MQL` 不是对外标准契约，而是平台内部的通用元数据访问协议。

它主要适用于：

- 前端页面通过 JSON 协议访问实体
- 平台通用数据接口
- 低代码或元数据驱动配置场景

推荐理解方式：

- OpenAPI：对外标准接口契约
- `SrvExplain`：静态源码扫描补充说明
- MQL：平台内部通用数据协议

## 官方展示建议

- 主入口：OpenAPI
- 补充入口：`SrvExplain/index.md`
- 平台协议入口：见 [MQL](../mql/overview.md)
- 控制器静态清单：见 [SrvExplain API 目录](srvexplain-catalog.md)

## 当前仓库入口

- `SrvExplain/README.md`
- `SrvExplain/index.md`
- `geelato-web-quickstart/src/main/java/cn/geelato/web/swagger/SwaggerConfig.java`

## SrvExplain 全量清单

官方文档站已补充一份按模块展开的静态 API 清单：

- [SrvExplain API 目录](srvexplain-catalog.md)

这份清单会把当前 `SrvExplain/` 中已经生成的全部 Controller 文档纳入 API 章节，便于按模块浏览和跳转。

## 演进方向

长期方向不是移除 `SrvExplain`，而是把它收敛为：

- 静态补充视图
- 迁移期辅助能力
- 内部完整性检查工具
