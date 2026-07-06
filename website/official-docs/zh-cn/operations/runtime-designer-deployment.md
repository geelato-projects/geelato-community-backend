# Runtime / Designer 部署与依赖

Web 平台以"一个共享底座 + 两个可发布的启动壳"的方式交付：

- `geelato-web-platform`（共享底座：controllers/services/boot）
- `geelato-web-runtime`（运行时启动壳）
- `geelato-web-designer`（设计时启动壳）

这篇文档用于说明它们的关系、部署方式和最小配置差异。

## 模块关系

当前关系是：

- `geelato-web-runtime` 依赖 `geelato-web-platform`
- `geelato-web-designer` 依赖 `geelato-web-platform`
- `runtime` 与 `designer` 之间不建立依赖关系

当前两个启动壳都会暴露底座中的全量接口。共享底座的接口集合尚未拆分。

## 当前启动入口

当前两个应用入口分别是：

- `PlatformWebRuntime`
- `PlatformDesginer`

它们都复用了现有的 `BootApplication` 启动基类。

另外，面向业务项目的官方脚手架是：

- `geelato-app-scaffold`

它依赖运行时能力；是否包含设计时接口以后续模块拆分为准。

## 关键配置差异

运行时模块当前默认配置：

```properties
spring.application.name=geelato-web-runtime
```

设计时模块当前默认配置：

```properties
spring.application.name=geelato-web-designer
```

## 为什么不再使用开关

设计时 / 运行时能力的启用不通过开关控制，而通过“是否依赖模块”决定。当前阶段两个启动壳接口集合一致，后续拆分接口集合后再体现边界差异。

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

- `geelato-web-designer`

## 推荐继续阅读

- [PlatformWebRuntime](../runtime/platform-web-runtime.md)
- [PlatformDesginer](../designer/platform-desginer.md)
- [App Scaffold](../guide/app-scaffold-starter-project-guide.md)
- [SecurityContext 生命周期](../runtime/security-context-lifecycle.md)
