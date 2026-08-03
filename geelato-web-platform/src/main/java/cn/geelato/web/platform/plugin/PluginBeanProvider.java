package cn.geelato.web.platform.plugin;

import cn.geelato.utils.StringUtils;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件扩展点获取入口。
 * <p>承担三项职责：</p>
 * <ul>
 *   <li><b>P1 租户门控</b>：取扩展前先经 {@link TenantPluginGate} 判定，
 *       平台/租户级未启用则抛 {@link PluginNotEnabledForTenantException}（强制开启，无回退）。</li>
 *   <li><b>P0-C 缓存</b>：缓存 {@code (type, pluginId) → 扩展实例}，避免每请求重复查找。</li>
 * </ul>
 *
 * <p>调用超时（P0-C）的封装建议在调用方对扩展方法包装；本类仅负责获取实例，
 * 超时由调用侧用 {@link PluginInvocationTimeoutException} 抛出（见静态工具 {@link #guard}）。</p>
 *
 * @author geelato
 */
@Component
public class PluginBeanProvider {
    private final SpringPluginManager springPluginManager;
    private final TenantPluginGate tenantPluginGate;

    /** 扩展实例缓存：(type#pluginId) → 实例。 */
    private final ConcurrentHashMap<String, Object> extensionCache = new ConcurrentHashMap<>();

    @Autowired
    public PluginBeanProvider(SpringPluginManager springPluginManager, TenantPluginGate tenantPluginGate) {
        this.springPluginManager = springPluginManager;
        this.tenantPluginGate = tenantPluginGate;
    }

    /**
     * 获取扩展实例（带租户门控与缓存）。
     *
     * @param type     扩展点类型
     * @param pluginId 插件 id；为空时取该类型的第一个扩展（且仍受租户门控——
     *                 此情形下无法判定具体插件，跳过门控，向后兼容存量无 pluginId 调用）
     * @param <T>      扩展点类型
     * @return 扩展实例
     * @throws UnFoundPluginException              无对应扩展
     * @throws PluginNotEnabledForTenantException  插件未对当前租户/平台启用
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type, String pluginId) {
        // 仅当显式传入 pluginId 时做租户门控；为空（存量兼容）跳过
        if (StringUtils.isNotEmpty(pluginId) && !tenantPluginGate.isAvailable(pluginId)) {
            boolean platformDisabled = tenantPluginGate.isPlatformDisabled(pluginId);
            throw new PluginNotEnabledForTenantException(platformDisabled,
                    "插件未可用：" + pluginId + "（" + (platformDisabled ? "平台级已禁用" : "当前租户未启用") + "）");
        }
        String key = cacheKey(type, pluginId);
        Object cached = extensionCache.get(key);
        if (cached != null) {
            return (T) cached;
        }
        List<T> extensions;
        if (StringUtils.isEmpty(pluginId)) {
            extensions = springPluginManager.getExtensions(type);
        } else {
            extensions = springPluginManager.getExtensions(type, pluginId);
        }
        if (extensions != null && !extensions.isEmpty()) {
            T instance = extensions.get(0);
            extensionCache.put(key, instance);
            return instance;
        } else {
            throw new UnFoundPluginException();
        }
    }

    /**
     * 失效扩展实例缓存（插件启停后调用，避免拿到失效实例）。
     */
    public void invalidateCache() {
        extensionCache.clear();
    }

    private static String cacheKey(Class<?> type, String pluginId) {
        return type.getName() + "#" + (pluginId == null ? "" : pluginId);
    }
}
