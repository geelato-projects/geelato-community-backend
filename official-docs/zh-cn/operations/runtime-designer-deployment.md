# Runtime / Designer 部署与依赖

阶段 8 完成后，Web 平台已经按方案 B 拆出了两个可发布模块：

- `geelato-web-runtime`
- `geelato-web-platform`

这篇文档用于说明它们的关系、部署方式和最小配置差异。

## 模块关系

当前关系是：

- `designer` 依赖 `runtime`
- `runtime` 代表业务执行最小子集
- `designer` 在运行时子集之上叠加设计时能力

因此“运行时是设计时的子集”在当前交付形态下已经有了明确的模块表达。

## 当前启动入口

当前两个应用入口分别是：

- `PlatformWebRuntime`
- `PlatformDesginer`

它们都复用了现有的 `BootApplication` 启动基类，但通过配置把设计时能力开关区分开。

另外，面向业务项目的官方脚手架是：

- `geelato-app-scaffold`

它依赖运行时能力，但不会默认引入设计时接口。

## 关键配置差异

运行时模块当前默认配置：

```properties
spring.application.name=geelato-web-runtime
geelato.web.platform.design-time.enabled=false
```

设计时模块当前默认配置：

```properties
spring.application.name=geelato-web-platform
geelato.web.platform.design-time.enabled=true
```

## 设计时开关意味着什么

当前设计时接口主要通过设计时注解和条件装配控制。

因此：

- 部署 `runtime` 时，设计时接口默认不暴露
- 部署 `designer` 时，设计时接口默认暴露

这让两个模块即使复用大量底层实现，也具备不同的对外接口边界。

## 推荐部署方式

### 纯运行时部署

适用于：

- 业务执行环境
- 面向终端用户的宿主系统
- 不希望暴露元数据设计、脚本管理、打包发布等接口的环境

### 设计时部署

适用于：

- 低代码设计器环境
- 元数据、模型、脚本和发布治理环境
- 平台管理后台环境

## 推荐依赖顺序

如果只是运行宿主系统，优先引：

- `geelato-web-runtime`

如果需要完整设计时平台，再引：

- `geelato-web-platform`

## 推荐继续阅读

- [PlatformWebRuntime](../runtime/platform-web-runtime.md)
- [PlatformDesginer](../designer/platform-desginer.md)
- [App Scaffold](../guide/app-scaffold.md)
- [SecurityContext 生命周期](../runtime/security-context-lifecycle.md)
