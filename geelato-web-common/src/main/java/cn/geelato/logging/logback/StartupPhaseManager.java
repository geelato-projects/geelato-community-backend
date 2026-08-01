package cn.geelato.logging.logback;

/**
 * 标识应用当前所处日志阶段：启动期或运行期。
 *
 * <p>初始为启动期（true）。当 Spring 容器发布 {@code ApplicationReadyEvent}
 * （由 {@code RuntimePhaseListener} 监听）后，切换为运行期（false）。</p>
 *
 * <p>该类为纯静态工具类，不依赖任何 Spring 组件，因此可在 logback 早期初始化阶段
 * （TurboFilter）被安全调用。</p>
 */
public final class StartupPhaseManager {

    private static volatile boolean startupPhase = true;

    private StartupPhaseManager() {
    }

    /**
     * 是否处于启动期。
     *
     * @return 应用尚未进入运行期时返回 true
     */
    public static boolean isStartupPhase() {
        return startupPhase;
    }

    /**
     * 标记应用已进入运行期（ApplicationReadyEvent 触发后调用）。
     * 该操作不可逆。
     */
    public static void markRuntimeStarted() {
        startupPhase = false;
    }
}
