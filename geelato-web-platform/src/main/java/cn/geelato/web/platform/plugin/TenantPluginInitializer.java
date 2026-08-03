package cn.geelato.web.platform.plugin;

import cn.geelato.core.GlobalContext;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 插件开关 JSON 初始化器。
 * <p>租户治理默认开启后，未登记的插件调用会被门控拦下。为保证改造零故障，
 * 应用就绪后执行初始化：</p>
 * <ul>
 *   <li>平台级 {@code plugins-enabled.json} 缺失 → 扫描全部已部署插件写入 enabled。</li>
 *   <li>平台租户 {@code tenant_{defaultTenant}.json} 缺失 → 同样写入全量。</li>
 *   <li>其他租户文件不自动生成（默认无插件，显式开启）。</li>
 * </ul>
 * <p>已存在的文件不覆盖，仅补建缺失文件。</p>
 *
 * @author geelato
 */
@Slf4j
@Component
public class TenantPluginInitializer {

    private final org.pf4j.PluginManager pluginManager;
    private final PluginStatusPaths paths;
    private final PluginStatusJsonStore store;

    @Autowired
    public TenantPluginInitializer(org.pf4j.PluginManager pluginManager,
                                   PluginStatusPaths paths,
                                   PluginStatusJsonStore store) {
        this.pluginManager = pluginManager;
        this.paths = paths;
        this.store = store;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            Set<String> deployed = scanDeployedPluginIds();
            if (deployed.isEmpty()) {
                log.info("[插件开关初始化] 未发现已部署插件，跳过 JSON 初始化。");
                return;
            }
            // 平台级文件缺失 → 写全量
            if (!Files.exists(paths.platformFile())) {
                PluginStatusDoc doc = new PluginStatusDoc();
                doc.setEnabled(new java.util.ArrayList<>(deployed));
                store.overwrite(paths.platformFile(), doc, "system-init");
                log.info("[插件开关初始化] 已生成平台级 plugins-enabled.json，启用：{}", deployed);
            }
            // 平台租户文件缺失 → 写全量
            String defaultTenant = GlobalContext.getDefaultTenantCode();
            Path tenantFile = paths.tenantFile(defaultTenant);
            if (!Files.exists(tenantFile)) {
                PluginStatusDoc doc = new PluginStatusDoc();
                doc.setTenantCode(defaultTenant);
                doc.setEnabled(new java.util.ArrayList<>(deployed));
                store.overwrite(tenantFile, doc, "system-init");
                log.info("[插件开关初始化] 已生成平台租户 tenant_{}.json，启用：{}", defaultTenant, deployed);
            }
        } catch (Exception e) {
            // 初始化失败不应阻断应用
            log.error("[插件开关初始化] 失败（不阻断应用）", e);
        }
    }

    /** 扫描 plugins 目录下所有已加载插件的 id。 */
    private Set<String> scanDeployedPluginIds() {
        List<PluginWrapper> plugins = pluginManager.getPlugins();
        if (plugins == null || plugins.isEmpty()) {
            return new TreeSet<>();
        }
        return plugins.stream()
                .map(w -> w.getDescriptor().getPluginId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
