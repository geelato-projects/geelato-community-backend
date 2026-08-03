package cn.geelato.web.platform.plugin;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginState;
import org.pf4j.PluginStatusProvider;
import org.pf4j.PluginWrapper;
import org.pf4j.spring.SpringPluginManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/**
 * geelato 定义的 {@link SpringPluginManager} 子类。
 * <p>承担三项职责：</p>
 * <ul>
 *   <li><b>P0-B 加载容错</b>：重写 {@link #startPlugin(String)}，单个插件启动失败时
 *       仅记录日志并返回，不向上抛出，避免一个坏插件（如 native 库缺失）阻断整个应用启动。</li>
 *   <li><b>P1 装配</b>：重写 {@link #createPluginStatusProvider()}，用 {@link FilePluginStatusProvider}
 *       替代 pf4j 默认实现，使平台级开关持久化到共享卷 JSON。</li>
 *   <li><b>P2-C 签名校验</b>：当 {@code signatureVerify=true} 时，重写 {@link #loadPluginFromPath(Path)}
 *       校验插件 jar 签名，签名损坏则拒绝加载。</li>
 * </ul>
 *
 * <h3>关于状态提供者的装配时序</h3>
 * <p>pf4j 的 {@code AbstractPluginManager} 在<strong>构造器</strong>中调用 {@code initialize()}，
 * 后者立即调用 {@link #createPluginStatusProvider()}。这意味着构造器参数赋值给实例字段的语句
 * （位于 {@code super(...)} 之后）<em>尚未执行</em>，此时访问实例字段会得到 null。
 * 因此 {@link #createPluginStatusProvider()} 不能读取构造器赋值的实例字段，
 * 而是通过 {@link #PROVIDER_HOLDER}（构造期 ThreadLocal）获取在 super 之前已构造好的 provider。</p>
 *
 * @author geelato
 */
@Slf4j
public class FileSpringPluginManager extends SpringPluginManager {

    /**
     * 构造期临时持有 {@link FilePluginStatusProvider}。
     * <p>由 {@link PluginConfiguration#pluginManager} 在调用构造器前设置，
     * 构造器内 {@link #createPluginStatusProvider()} 读取后立即清除，避免泄漏。</p>
     */
    static final ThreadLocal<FilePluginStatusProvider> PROVIDER_HOLDER = new ThreadLocal<>();

    private final boolean signatureVerify;
    /** 构造完成后持有的 provider 引用，供运行时使用（非构造期路径）。 */
    private FilePluginStatusProvider filePluginStatusProvider;

    public FileSpringPluginManager(Path pluginDirectory,
                                   FilePluginStatusProvider filePluginStatusProvider,
                                   boolean signatureVerify) {
        super(pluginDirectory);
        // super() 已触发 initialize() → createPluginStatusProvider()（从 PROVIDER_HOLDER 取值）
        this.signatureVerify = signatureVerify;
        this.filePluginStatusProvider = filePluginStatusProvider;
        // 清理 ThreadLocal，防止泄漏
        PROVIDER_HOLDER.remove();
    }

    /**
     * 用基于 JSON 文件的状态提供者替代 pf4j 默认实现。
     * <p>本方法在 super 构造期被调用，此时实例字段尚未赋值，故从 {@link #PROVIDER_HOLDER} 取 provider。</p>
     */
    @Override
    protected PluginStatusProvider createPluginStatusProvider() {
        // 构造期路径：PROVIDER_HOLDER 由 PluginConfiguration 在 new 本对象前设置
        FilePluginStatusProvider provider = PROVIDER_HOLDER.get();
        if (provider != null) {
            return provider;
        }
        // 兜底：理论上不走到（pf4j 仅在 initialize 调一次）；若走到说明装配异常，给出明确报错
        throw new IllegalStateException("FilePluginStatusProvider 未通过 PROVIDER_HOLDER 注入，请检查 PluginConfiguration.pluginManager 装配");
    }

    /**
     * 单插件启动容错：失败时不抛出，标记为禁用并继续启动其它插件。
     * <p>OCR 等 native 重插件首次启动（模型/native 加载）风险较高，
     * 单点失败不应拖垮主程序。</p>
     */
    @Override
    public PluginState startPlugin(String pluginId) {
        try {
            return super.startPlugin(pluginId);
        } catch (Exception e) {
            PluginWrapper wrapper = getPlugin(pluginId);
            String id = wrapper != null ? wrapper.getPluginId() : pluginId;
            log.error("插件启动失败，已跳过（不影响主程序与其他插件）：{}", id, e);
            try {
                if (wrapper != null && wrapper.getPluginState() != PluginState.STOPPED) {
                    super.stopPlugin(pluginId);
                }
            } catch (Exception stopEx) {
                log.warn("失败插件 {} 停止时再次异常", id, stopEx);
            }
            return PluginState.STOPPED;
        }
    }

    /**
     * P2-C 签名校验：开启后校验插件 jar 签名完整性（JDK {@link JarFile} 的 verify 模式）。
     * 签名损坏（{@link SecurityException}）则拒绝加载；无签名或校验通过则放行。
     * <p>默认关闭（{@code signatureVerify=false}），避免阻塞现有无签名插件。</p>
     */
    @Override
    protected PluginWrapper loadPluginFromPath(Path pluginPath) {
        if (signatureVerify && Files.isRegularFile(pluginPath) && pluginPath.toString().endsWith(".jar")) {
            try (JarFile jar = new JarFile(pluginPath.toFile(), true)) {
                jar.stream().forEach(entry -> {
                    try {
                        jar.getInputStream(entry).close();
                    } catch (SecurityException se) {
                        throw se;
                    } catch (Exception ignored) {
                    }
                });
            } catch (SecurityException se) {
                log.error("插件 jar 签名校验失败，拒绝加载：{}", pluginPath, se);
                return null;
            } catch (Exception ignored) {
                // 读取失败走默认流程
            }
        }
        return super.loadPluginFromPath(pluginPath);
    }
}
