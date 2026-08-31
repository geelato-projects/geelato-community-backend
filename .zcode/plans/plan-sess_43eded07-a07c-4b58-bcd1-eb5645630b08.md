# SystemToken 固定令牌认证：外部系统调用 community API（如 dyn 发站内信）

## 背景诊断

- 统一认证入口 `DefaultSecurityInterceptor`（geelato-web-common）拦 `/**`，对 `Authorization` 头按前缀依次尝试 4 组认证：`Anonymous `、`JWTBearer `、`WeixinUnionId `/`WeixinWorkUserId `、`Bearer `（送外部 OAuth2 服务器校验）。该"多凭证依次尝试"架构可自然扩展新凭证类型。
- 站内信发送 `POST /api/notification/send`（`NotificationController.java:181`）仅要求任一认证通过；Service 层 `resolveOperator()` 已兼容无登录上下文（回退 `system`）。
- **现状缺陷**：dyn 的 `InAppDirectChannelAdapter.java:78` 与企业版 geelato-message 的 `InAppClient.java:51-53` 已在用固定令牌调该接口，但前缀是 `Bearer `——会被送 OAuth2 服务器校验，固定串必然 401，链路实际不通。

## 方案总览

新增第 6 种凭证：`Authorization: SystemToken <固定密钥>`，配合方法级注解 `@SystemToken` 标记"该接口允许固定令牌调用"（与 `@IgnoreVerify` 同款机制）。

- 前端调用：`JWTBearer <用户JWT>` → 真实用户身份，完全不变；未标记注解的接口对 SystemToken 一律 401。
- 外部系统：`SystemToken <固定密钥>` → 虚拟系统主体（`User.systemPrincipal=true`，`userId/loginName=system`），仅能调用带 `@SystemToken` 注解的方法。
- **默认开启，无开关**：密钥为内置固定值（参照工程内 `GlobalContext.getAnonymousPwd()` 的既有风格），可由配置/环境变量覆盖；无需任何配置即可用。
- 不放行路径、不加 `@IgnoreVerify`，接口不裸奔；认证失败统一 401。

## 一、community 侧改动（D:\geelato\geelato-enterprise\geelato-community）

1. **新建注解** `geelato-web-common/.../interceptor/annotation/SystemToken.java`：仿 `@IgnoreVerify`（`@Target({METHOD}) @Retention(RUNTIME)`），语义为"允许 SystemToken 固定令牌认证访问"。
2. **新建配置类** `geelato-web-common/.../interceptor/SystemTokenProperties.java`：`@Component @ConfigurationProperties(prefix="geelato.security.system-token")`，仿 `SecurityInterceptorProperties`。
   - 仅一个字段 `token`（固定密钥），内置默认值（硬编码随机长串，可用 `geelato.security.system-token.token` 或环境变量覆盖）。
   - 密钥比对逻辑集中为一个 `matches(String)` 方法；**预留多接入方扩展**（将来加 `clients` 列表只改此类与该方法的调用方，拦截器/Realm 无需变动）。
   - 无 `enabled` 开关：token 永远有默认值，机制默认生效。
3. **新建** `geelato-web-common/.../shiro/SystemTokenToken.java`（实现 `AuthenticationToken`，principal=`system`，credentials=令牌，仿 `WeixinUnionIdToken`）。
4. **新建** `geelato-web-common/.../shiro/SystemTokenRealm.java`（仿 `AnonymousRealm`：仅 supports `SystemTokenToken`，用 `SystemTokenProperties.matches` 校验 credentials，授权信息返回空；构造注入 `SystemTokenProperties`）。
5. **修改** `DefaultSecurityInterceptor.java`：
   - 新增常量 `__SystemTokenTag__ = "SystemToken "`、`@Setter SystemTokenProperties`。
   - **注解前置校验**：在 `preHandle` 中、`tryRestoreFromCache` **之前**——若 token 以 `SystemToken ` 开头且 handler 方法无 `@SystemToken` 注解，立即 401（防止同一令牌先在已标记接口缓存成功后、被拿到未标记接口命中 `tryRestoreFromCache` 绕过注解）。
   - 新增 `trySystemTokenAuthenticate()`：前缀匹配 → `MessageDigest.isEqual` 常量时间比对密钥 → 构造虚拟主体（`new User()`，`userId/loginName/name="system"`，`tenantCode=GlobalContext.getDefaultTenantCode()`，`setSystemPrincipal(true)`）→ `SecurityContext.setCurrentUser/Tenant` → `subject.login(SystemTokenToken)` → `cacheUserContext` 复用 30 分钟缓存；不调 `touchOnline`（外部系统不算在线用户）。
   - 认证链插入位置：`tryExtendKeyAuthenticate` 之后、`tryOAuth2Authenticate` 之前。
6. **标记接口**：`NotificationController` 的 `send()`（`/api/notification/send`，181 行）加 `@SystemToken`。
7. **修改** `geelato-web-platform/.../boot/InterceptorConfiguration.java`：注入 `SystemTokenProperties` 并注入拦截器。
8. **修改** `geelato-web-platform/.../boot/ShiroConfiguration.java`：realm 列表注册 `SystemTokenRealm`。
9. **配置样例**：`geelato-web-runtime/src/main/resources/application.properties` 追加注释化样例（`geelato.security.system-token.token=${GEELATO_SYSTEM_TOKEN:内置默认值}`，默认注释即用内置值）。
10. **文档**：`docs/notification/integration-guide.md` 增补"外部系统 SystemToken 接入"一节（默认密钥与覆盖方法、curl 示例、安全建议：生产环境用环境变量覆盖并走 HTTPS）。

## 二、dyn 侧改动（D:\geelato\geelato-enterprise\geelato-dyn）

11. `geelato-dyn-common/.../InAppDirectChannelAdapter.java:78`：`"Bearer "` → `"SystemToken "`；类头注释（HTTP 契约描述）同步更新。
12. `geelato-dyn-message-demo/.../blueprint/message-demo.xml`：`communityToken` 属性注释说明改为填 SystemToken 固定密钥。

## 三、geelato-message 侧改动（D:\geelato\geelato-enterprise\geelato-message）

13. `InAppClient.java:51-53`：`"Bearer "` → `"SystemToken "`；`application.properties:40-41` 注释同步更新。

## 验证

- `mvn compile`（community 各模块）+ 为 `trySystemTokenAuthenticate` 补单测（密钥命中/未命中、方法无注解时 401 且不污染缓存、前缀不匹配不影响其他认证分支）；dyn、message 模块编译通过。
- 运行时 curl 验证：`SystemToken` 令牌调 `/api/notification/send` 成功；无头 401；`SystemToken` 令牌调未标记接口 401；前端 `JWTBearer` 调用不受影响。

## 风险与边界（不在本次范围，仅提示）

- 密钥内置默认值随源码分发，生产环境应通过环境变量覆盖（文档中明确提示）。
- 密钥更换需改配置重启；30 分钟上下文缓存过期后旧密钥失效。
- `/api/notification/send` 对所有已认证主体开放、无角色控制（现状如此，本次不改）。
- JWT 签名密钥、匿名密码硬编码是既有安全债，与本方案独立。