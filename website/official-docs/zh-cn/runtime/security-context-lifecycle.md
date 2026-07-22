---
title: SecurityContext 生命周期
sidebar_label: SecurityContext 生命周期
---

# SecurityContext 生命周期

运行时 `SecurityContext` 具有明确的请求级生命周期边界。本页说明该生命周期的核心原则、写入链路与清理机制。

## 核心原则

`SecurityContext` 遵循两个约束：

- `SecurityContext` 是线程级上下文。
- 安全主体只能在鉴权成功后由安全链路内部设置。

通用 Filter 不负责从请求中直接注入主体，它只负责在请求结束后统一清理。

## 请求进入时发生什么

HTTP 请求进入后，`FilterConfiguration` 会先注册公共 Filter，其中 `SecurityContextFilter` 会包裹整个请求链路。

但它并不会设置主体，只会在 `finally` 中执行：

- `SecurityContext.clear()`

## 主体是谁来设置的

当前真正负责认证与主体写入的是 `DefaultSecurityInterceptor`。

它会在 `preHandle()` 中按顺序尝试：

- 缓存恢复
- Anonymous 认证
- JWT 认证
- 扩展键认证
- OAuth2 认证

一旦鉴权成功，才会把当前用户、租户和密码写入 `SecurityContext`。

## 为什么不能在 Filter 中设置主体

这是当前框架的硬边界：

- Filter 处于通用请求入口层
- 它不应该相信前端可以直接声明的主体信息
- 主体只能来自鉴权成功后的内部链路

因此 `SecurityContextFilter` 的职责被严格限制为清理，而不是注入。

## 请求结束时发生什么

无论请求成功还是失败，`SecurityContextFilter` 都会在 `finally` 中清理上下文，避免：

- 线程复用导致主体串用
- 异常请求遗留脏上下文
- 后续请求误读上一请求的用户信息

## 异步任务如何传播上下文

如果运行时异步任务需要继承当前主体，应显式使用：

- `SecurityContextRunnable.wrap(...)`

它会在异步任务进入时恢复必要上下文，并在任务完成后再次清理。

## 使用建议

- 控制器和服务层只读取 `SecurityContext`
- 不要在业务代码中自行拼装伪主体塞入 `SecurityContext`
- 不要在通用 Filter 中解析 header 后直接写入上下文
- 如需扩展认证链路，应在 Interceptor / 安全链路内部完成

## 推荐继续阅读

- [统一认证](../authentication/overview.md)
- [PlatformWebRuntime](platform-web-runtime.md)
- [Runtime / Designer 部署与依赖](../operations/runtime-designer-deployment.md)
