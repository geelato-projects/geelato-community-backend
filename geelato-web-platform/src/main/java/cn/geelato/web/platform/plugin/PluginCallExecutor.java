package cn.geelato.web.platform.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 插件扩展方法调用执行器（P0-C 超时 + P2-D 指标）。
 * <p>包装插件扩展方法的实际调用，提供：</p>
 * <ul>
 *   <li><b>超时保护</b>：超 {@code geelato.plugin.invocation-timeout-millis} 阈值则中断并抛
 *       {@link PluginInvocationTimeoutException}，防止单个插件卡死（如 native 推理死循环）拖垮调用线程。</li>
 *   <li><b>指标记录</b>：记录每次调用的成功/失败与耗时到 {@link PluginMetrics}。</li>
 * </ul>
 *
 * <p>独立单线程执行器池（按需扩容），与调用线程隔离。建议在调用扩展方法时使用本执行器，
 * 例如 {@code executor.invoke(pluginId, () -> ocrService.resolvePDFFile(...))}。</p>
 *
 * @author geelato
 */
@Slf4j
@Component
public class PluginCallExecutor {

    private final PluginConfigurationProperties properties;
    private final PluginMetrics metrics;
    private final ExecutorService executor;

    @Autowired
    public PluginCallExecutor(PluginConfigurationProperties properties, PluginMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "plugin-invoker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 执行插件调用（带超时与指标）。
     *
     * @param pluginId 插件 id（用于指标与日志）
     * @param callable 实际调用
     * @param <T>      返回类型
     * @return 调用结果
     * @throws PluginInvocationTimeoutException 超时
     * @throws RuntimeException                 包装调用本身的异常
     */
    public <T> T invoke(String pluginId, Callable<T> callable) {
        long timeoutMs = properties.getInvocationTimeoutMillis();
        long start = System.currentTimeMillis();
        Future<T> future = executor.submit(callable);
        try {
            T result;
            if (timeoutMs > 0) {
                result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                result = future.get();
            }
            metrics.record(pluginId, true, System.currentTimeMillis() - start);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            metrics.record(pluginId, false, System.currentTimeMillis() - start);
            throw new PluginInvocationTimeoutException("插件 " + pluginId + " 调用超时（>" + timeoutMs + "ms）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.record(pluginId, false, System.currentTimeMillis() - start);
            throw new PluginInvocationTimeoutException("插件 " + pluginId + " 调用被中断", e);
        } catch (ExecutionException e) {
            metrics.record(pluginId, false, System.currentTimeMillis() - start);
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("插件 " + pluginId + " 调用异常", cause != null ? cause : e);
        }
    }
}
