---
title: 安全上下文
sidebar_label: 安全上下文
---

# 安全上下文

`SecurityContext` 是线程级安全上下文:以 `ThreadLocal` 持有当前线程的用户、租户、密码与应用四个维度,是运行时读取"当前是谁"的唯一入口。本页说明四个维度、判定方法、写入边界与跨线程传播方式。

## 四个上下文维度

| 维度 | 持有类型 | 意义 | 写入方 |
| --- | --- | --- | --- |
| `threadUser` | `User` | 当前用户主体 | 鉴权成功后的安全链路 |
| `threadTenant` | `Tenant` | 当前租户 | 鉴权成功后的安全链路 |
| `threadPassword` | `String` | 当前请求凭证 | 鉴权成功后的安全链路 |
| `threadApp` | `App` | 当前应用(子系统) | 子系统标识场景 |

## 读写方法

| 方法 | 语义 |
| --- | --- |
| `setCurrentUser(User)` / `getCurrentUser()` | 写入/读取当前用户 |
| `setCurrentTenant(Tenant)` / `getCurrentTenant()` | 写入/读取当前租户 |
| `setCurrentPassword(String)` / `getCurrentPassword()` | 写入/读取当前凭证 |
| `setCurrentApp(App)` / `getCurrentApp()` | 写入/读取当前应用 |
| `clear()` | 清空全部四个维度 |

## 判定方法

| 方法 | 语义 |
| --- | --- |
| `isDelegated()` | 当前操作是否为委托代办:当前用户 `delegateUserId` 非空 |
| `getDelegateUserId()` | 被委托人 ID,当前用户为空时返回 `null` |
| `isAdmin()` | 遍历当前用户**直挂角色**(`userRoles`),`code` 含 `"admin"` 即 `true` |
| `isSystemPrincipal()` | 当前线程是否以系统后台主体身份运行:判定依据是 `currentUser.systemPrincipal` 标志,由后台任务在建立上下文时显式设置(如 `ScheduleHelper.wrapWithContext`) |

:::warning isAdmin 的历史语义
`isAdmin()` 只看直挂角色,不含组织挂靠角色——与 `User.getEffectiveRoles()` 的并集语义不同。源码注释已声明该差异为契约保留(历史消费方行为不变),新代码判断生效角色请使用 `getEffectiveRoles()`。
:::

## 写入边界

`SecurityContext` 遵循两条硬约束:

- `SecurityContext` 是线程级上下文,生命周期与请求一致。
- 安全主体只能在鉴权成功后由安全链路内部设置。

当前真正负责认证与主体写入的是平台侧的 `DefaultSecurityInterceptor`:它在 `preHandle()` 中按 `Authorization` 前缀路由到对应的 `TokenAuthenticator` 认证器,经方案门禁、缓存恢复与凭证校验后,统一把当前用户、租户和密码写入 `SecurityContext`(链路细节见[认证鉴权](../authentication/security-authentication.md))。

通用 `SecurityContextFilter` 不设置主体,只在请求结束的 `finally` 中执行 `SecurityContext.clear()`。原因:

- Filter 处于通用请求入口层,不应该相信前端可以直接声明的主体信息;
- 主体只能来自鉴权成功后的内部链路;
- 请求结束统一清理,避免线程复用导致主体串用、异常请求遗留脏上下文。

使用约束:

- 控制器和服务层只读取 `SecurityContext`,不要在业务代码中自行拼装伪主体塞入;
- 不要在通用 Filter 中解析 header 后直接写入上下文;
- 如需新增认证方式,实现 `TokenAuthenticator` SPI 即可,见[认证器扩展(SPI)](../authentication/authenticator-spi.md)。

## 跨线程传播

四个维度都是普通 `ThreadLocal`,不随线程切换传递(框架未使用 `InheritableThreadLocal` 或 `TransmittableThreadLocal`)。异步任务需要当前主体时,必须**显式**使用 `SecurityContextRunnable` 包装——捕获-重放模式:

| 工厂方法 | 语义 |
| --- | --- |
| `wrap(Runnable)` | 自动从当前线程捕获 `User`/`Tenant`(提交任务时捕获) |
| `wrap(Runnable, User, Tenant)` | 显式指定,适用于定时任务等无 HTTP 上下文的场景 |
| `wrap(Runnable, User)` | 显式指定 `User`,`Tenant` 从当前线程捕获 |

`run()` 时序:进入时 `setCurrentUser`/`setCurrentTenant` → 执行 `delegate.run()` → `finally` 中 `SecurityContext.clear()`(任务完成后清理,不污染线程池线程)。

```java
// 自动捕获当前线程上下文
CompletableFuture.runAsync(SecurityContextRunnable.wrap(() -> {
    // 此处可正常使用 SecurityContext.getCurrentUser()
}));

// 显式指定用户(适用于定时任务等无 HTTP 上下文的场景)
CompletableFuture.runAsync(SecurityContextRunnable.wrap(() -> { ... }, overrideUser, overrideTenant));
```

两点注意:

- 传播的是 `User`/`Tenant` 两个维度;`password`/`app` 不传播。
- 包装对象是 `Runnable`,凡接收 `Runnable` 的执行器(`CompletableFuture`、自建线程池等)均可使用。

### 同范式的上下文设施

| 设施 | 位置 | 模式 |
| --- | --- | --- |
| `EngineContextExecutor` | `geelato-exception` | 保存原上下文 → 替换 → `finally` 恢复;提供 `withContext`/`withSystemContext`(跨租户)/`withEngineContext`/`withParticipantPrincipal`(参与者身份让渡) |
| `AlertContextExecutor` | `geelato-alert` | `withContext`(无会话时注入系统主体)/`withParticipantPrincipal` |
| `ScheduleHelper.wrapWithContext` | 宿主侧调度 | 后台任务建立上下文时设置系统主体(`systemPrincipal=true`) |

## 总结

- `SecurityContext` 是四个 `ThreadLocal` 维度的线程级上下文:用户/租户/密码/应用;`clear()` 一次性清空。
- 写入边界是硬约束:主体只能由鉴权成功后的安全链路写入,Filter 只负责请求结束清理。
- 跨线程不自动传播,异步任务必须显式 `SecurityContextRunnable.wrap(...)`(捕获-重放,finally 清理);判定方法中 `isAdmin` 只看直挂角色属历史语义,生效角色请用 `getEffectiveRoles()`。

## 推荐继续阅读

- [认证鉴权](../authentication/security-authentication.md)——主体是谁写入的
- [安全模型](model.md)——上下文中的 `User`/`Tenant` 对象
- [全局上下文](../platform-capabilities/global-context.md)——请求级通用上下文
