package cn.geelato.web.platform.srv.ormhook.spi;

import java.util.function.Supplier;

/**
 * 钩子执行深度守卫（防递归）。
 * <p>
 * 动作执行期间置位 ThreadLocal；监听器 supports() 检测到深度≥1 即跳过——
 * 防止"钩子动作（脚本）在同线程内写回已配钩子的实体"造成的直接递归
 * （GraalJS 脚本与脚本内 dao 写均在执行线程上同步完成，ThreadLocal 可见）。
 * <p>
 * 局限：脚本内跨线程/异步写（如脚本里再发事件）会逃逸本守卫，属脚本作者的自觉边界。
 *
 * @author geelato
 */
public final class OrmHookDepthHolder {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private OrmHookDepthHolder() {
    }

    /** 当前线程是否处于钩子动作执行中。 */
    public static boolean isInHookExecution() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    /** 在深度+1 的上下文中执行动作，退出时还原（嵌套安全）。 */
    public static <T> T call(Supplier<T> action) {
        int depth = DEPTH.get() == null ? 0 : DEPTH.get();
        DEPTH.set(depth + 1);
        try {
            return action.get();
        } finally {
            if (depth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth);
            }
        }
    }
}
