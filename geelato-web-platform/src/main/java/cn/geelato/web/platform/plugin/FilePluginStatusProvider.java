package cn.geelato.web.platform.plugin;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginStatusProvider;

import java.util.HashSet;
import java.util.Set;

/**
 * 基于 JSON 文件的平台级插件状态提供者（pf4j {@link PluginStatusProvider}）。
 * <p>数据源为 {@code {config-directory}/plugins-enabled.json}。替代 pf4j 默认的
 * {@code enabled.txt}/{@code disabled.txt}，使平台级总开关可持久化到共享卷，
 * 多节点共享一致。</p>
 * <p>读写委托 {@link PluginStatusJsonStore}（含跨进程文件锁与本地缓存）。</p>
 *
 * <p>pf4j 约定：</p>
 * <ul>
 *   <li>{@link #isDisabled(String)} 返回 true 则 pf4j 不加载该插件（全平台禁用）。</li>
 *   <li>{@link #enablePlugin(String)}/{@link #disablePlugin(String)} 由
 *       {@code PluginManager} 在运行时开关时调用，回写 JSON。</li>
 * </ul>
 *
 * @author geelato
 */
@Slf4j
public class FilePluginStatusProvider implements PluginStatusProvider {

    private final PluginStatusPaths paths;
    private final PluginStatusJsonStore store;

    public FilePluginStatusProvider(PluginStatusPaths paths, PluginStatusJsonStore store) {
        this.paths = paths;
        this.store = store;
    }

    /**
     * pf4j 启动时调用：返回禁用列表。平台级 JSON 中 enabled 之外即视为禁用。
     * <p>策略：若平台级文件不存在，返回空集（由初始化逻辑负责补建文件，
     * 这里不在每次启动都触发写入，避免与 pf4j 启动时序耦合）。</p>
     */
    @Override
    public boolean isPluginDisabled(String pluginId) {
        Set<String> enabled = store.readEnabled(paths.platformFile());
        // 文件不存在时默认不禁用（让 pf4j 正常加载存量插件，由初始化策略补建文件）
        if (!java.nio.file.Files.exists(paths.platformFile())) {
            return false;
        }
        return !enabled.contains(pluginId);
    }

    @Override
    public void enablePlugin(String pluginId) {
        Set<String> enabled = store.readEnabled(paths.platformFile());
        enabled.add(pluginId);
        store.write(paths.platformFile(),
                current -> build(current, enabled),
                currentUser());
    }

    @Override
    public void disablePlugin(String pluginId) {
        Set<String> enabled = store.readEnabled(paths.platformFile());
        enabled.remove(pluginId);
        store.write(paths.platformFile(),
                current -> build(current, enabled),
                currentUser());
    }

    /** 平台级已启用插件 id 集合。 */
    public Set<String> enabledPlugins() {
        return store.readEnabled(paths.platformFile());
    }

    /** 平台级是否已启用指定插件。 */
    public boolean isPlatformEnabled(String pluginId) {
        return enabledPlugins().contains(pluginId);
    }

    private PluginStatusDoc build(PluginStatusDoc current, Set<String> enabled) {
        PluginStatusDoc doc = current != null ? current : new PluginStatusDoc();
        doc.setEnabled(store.toList(enabled));
        return doc;
    }

    private String currentUser() {
        try {
            return cn.geelato.security.SecurityContext.getCurrentUser().getUserName();
        } catch (Exception e) {
            return "system";
        }
    }
}
