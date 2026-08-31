# 诊断结论：SSE 异步二次分发（ASYNC dispatch）时线程上没有 Shiro SecurityManager

## 根因链条（为什么报这个异常）

1. `geelato-web-platform` 中有 3 个返回 `SseEmitter` 的异步接口：
   - `POST /api/ai/ask`（AiController，DeepSeek 流式代理，超时 30 分钟）
   - `GET /subscribe/{topic}`、`GET /subscribe/topic/all`（SseController，站内通知订阅）
   Spring MVC 处理 `SseEmitter` 时会自动调用 `request.startAsync()`。
2. SSE 会话结束（完成 / 超时 / 断开）后，Tomcat 发起一次 **ASYNC dispatch** 回到 DispatcherServlet 收尾——对应堆栈中的 `AsyncContextImpl.doInternalDispatch` / `CoyoteAdapter.asyncDispatch`。
3. shiroFilter（`ShiroFilterFactoryBean` 产物，ShiroConfiguration.java）由 Spring Boot **自动注册，默认 dispatcherTypes 只有 REQUEST，不含 ASYNC**。堆栈也印证：ASYNC 分发的过滤链里只有 Spring 自带过滤器，没有 Shiro 过滤器。
4. Shiro 的 SecurityManager 只在 REQUEST 分发时由 Shiro 过滤器绑定到当前线程的 ThreadContext（ThreadLocal），请求结束即解绑；ASYNC 分发跑在另一个 Tomcat 工作线程上，ThreadLocal 为空，且项目从未设置过静态 SecurityManager → `SecurityUtils.getSubject()` 抛 `UnavailableSecurityManagerException`。
5. 抛出点是 `FrameworkServlet.publishRequestHandledEvent → getUsernameForRequest → ShiroHttpServletRequest.getUserPrincipal()`：原始请求被 Shiro 包装过，ASYNC 分发收尾发布"请求已处理"事件取用户名时经 Shiro 取 principal，触发异常。

## 为什么不影响正常运行

异常发生在**响应已完全发送给客户端之后**（事件发布在请求处理的收尾阶段），SSE 数据流客户端已正常收完；受影响的只是本该发布的 `ServletRequestHandledEvent` 统计事件，而项目里没有任何监听该事件的监听器。所以纯粹是 ERROR 日志噪音，但修复后可以避免掩盖真正的错误日志。

## 修复方案：把 shiroFilter 显式注册到 REQUEST + ASYNC 分发（Shiro 官方 starter 同款做法）

已验证 Shiro 2.2 的 `OncePerRequestFilter` 没有 ASYNC 跳过逻辑，注册到 ASYNC 类型后会在异步二次分发时重新执行 `doFilterInternal`，把 SecurityManager/Subject 绑定到分发线程，异常即消失。

修改 `geelato-web-platform/src/main/java/cn/geelato/web/platform/boot/ShiroConfiguration.java`，新增一个 `FilterRegistrationBean`：

```java
@Bean
public FilterRegistrationBean<AbstractShiroFilter> shiroFilterRegistration(AbstractShiroFilter shiroFilter) {
    FilterRegistrationBean<AbstractShiroFilter> registration = new FilterRegistrationBean<>(shiroFilter);
    registration.addUrlPatterns("/*");
    registration.setName("shiroFilter");
    registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
    registration.setOrder(0);   // 与原 Boot 自动注册的默认 order 一致，不改变现有 decrypt(0)/securityContext(1)/cache(2) 的顺序
    return registration;
}
```

要点：
- 显式注册后 Spring Boot 不再自动注册该 filter，不会双重注册（注入 `AbstractShiroFilter` 类型，Spring 会自动解析 `ShiroFilterFactoryBean` 的产物）。
- 该配置被 designer / runtime / quickstart 三个入口共用，一处修改全部生效。
- ASYNC 分发时 Shiro 会重新创建匿名 Subject 并绑定/解绑，SSE 路径本就是 `anon` 白名单，行为不变。

## 验证

1. `mvn compile -pl geelato-web-platform -am` 编译通过。
2. 启动 quickstart（或 designer），`curl -N http://localhost:8080/subscribe/topic/all` 建立 SSE 后中断连接（触发 ASYNC dispatch），确认日志不再出现 `UnavailableSecurityManagerException`。
3. 回归普通请求：登录/鉴权接口正常（shiroFilter 仍拦截 REQUEST 分发）。