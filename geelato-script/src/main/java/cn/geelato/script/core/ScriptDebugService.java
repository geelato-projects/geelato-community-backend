package cn.geelato.script.core;

/**
 * 脚本调试会话门面：以 start/stop 异步会话模型驱动 GraalJS CDP inspector。
 * <p>
 * 与同步 {@link ScriptEngine#execute} 分离——调试执行须长会话存活（Context 不能建后即关），
 * 脚本在守护线程上 eval，阻塞于 inspector 断点/挂起，由浏览器 DevTools（经同源代理）驱动
 * resume/单步。kit 注入、dry-run 事务回滚与正常执行完全等价（复用同一注入链）。
 * <p>
 * 实现由 {@link DefaultScriptEngine} 承担并作为 Spring bean 暴露；宿主（worker/内嵌 REST）
 * 经薄控制器转 {@code /debug/start|stop|status}，再由同源网关代理 CDP ws 帧到 inspector 端口。
 */
public interface ScriptDebugService {

    /**
     * 启动调试会话：分配 inspector 端口→建 debug Context→复用正常执行的 kit/bindings/env/dry-run
     * 注入→守护线程 eval（阻塞于断点）→登记会话。
     *
     * @param execution 调试执行请求（{@code debugMode} 应为 true；{@code debugSessionId}/{@code debugPort}
     *                  可空由服务分配；仅支持 javascript）
     * @return 会话坐标（sessionId/host/port/path/owner），host+port 仅内网可信代理可见
     */
    DebugSession start(ScriptExecution execution);

    /**
     * 终止调试会话：取消守护线程、关闭 Context、释放 inspector 端口、移除登记。幂等（不存在静默返回）。
     */
    void stop(String sessionId);

    /**
     * 查询会话状态。会话不存在返回 {@code alive=false, state=STOPPED}。
     */
    DebugStatus status(String sessionId);
}
