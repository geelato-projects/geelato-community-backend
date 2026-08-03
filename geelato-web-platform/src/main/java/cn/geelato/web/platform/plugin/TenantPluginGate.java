package cn.geelato.web.platform.plugin;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.GlobalContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 租户级插件门控。
 * <p>判定「某插件对当前租户是否可用」，规则：<br/>
 * {@code 可用 = 平台级已启用 AND 当前租户已启用}。</p>
 *
 * <p>多租户语义：</p>
 * <ul>
 *   <li>平台级禁用 → 全平台不可用（短路返回 false）。</li>
 *   <li>当前为平台租户（{@link GlobalContext#getDefaultTenantCode()}）且其文件不存在
 *       → 视为可用（由 {@link TenantPluginInitializer} 保证平台租户文件存在；
 *       此兜底防止初始化未完成时平台租户被误拦）。</li>
 *   <li>其他租户文件不存在 → 视为未启用（返回 false）。</li>
 * </ul>
 *
 * <p>读取走 {@link PluginStatusJsonStore} 的本地缓存（mtime + TTL），
 * 变更最长在 TTL 内对全部节点生效。</p>
 *
 * @author geelato
 */
@Slf4j
@Component
public class TenantPluginGate {

    private final FilePluginStatusProvider platformStatusProvider;
    private final PluginStatusPaths paths;
    private final PluginStatusJsonStore store;

    public TenantPluginGate(FilePluginStatusProvider platformStatusProvider,
                            PluginStatusPaths paths,
                            PluginStatusJsonStore store) {
        this.platformStatusProvider = platformStatusProvider;
        this.paths = paths;
        this.store = store;
    }

    /**
     * 判定指定插件对当前租户是否可用。
     *
     * @param pluginId 插件 id
     * @return true 表示平台级与租户级均启用
     */
    public boolean isAvailable(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return false;
        }
        // 平台级总闸：禁用则全平台不可用
        if (!isPlatformEnabled(pluginId)) {
            return false;
        }
        String tenantCode = currentTenantCode();
        Path tenantFile = paths.tenantFile(tenantCode);
        // 文件不存在：平台租户兜底放行，其他租户视为未启用
        if (!Files.exists(tenantFile)) {
            return isDefaultTenant(tenantCode);
        }
        Set<String> enabled = store.readEnabled(tenantFile);
        return enabled.contains(pluginId);
    }

    /** 当前是否平台级禁用（短路用）。 */
    public boolean isPlatformDisabled(String pluginId) {
        return !isPlatformEnabled(pluginId);
    }

    private boolean isPlatformEnabled(String pluginId) {
        // 平台级文件不存在时，FilePluginStatusProvider 的语义是不禁用；
        // 但门控层要求显式「平台启用」才放行，故文件缺失时按「未配置=放行」处理，
        // 与初始化策略（自动补建文件）保持一致。
        if (!Files.exists(paths.platformFile())) {
            return true;
        }
        return platformStatusProvider.isPlatformEnabled(pluginId);
    }

    private String currentTenantCode() {
        String code = SessionCtx.getCurrentTenantCode();
        return code == null || code.isBlank() ? GlobalContext.getDefaultTenantCode() : code;
    }

    private boolean isDefaultTenant(String tenantCode) {
        return GlobalContext.getDefaultTenantCode().equals(tenantCode);
    }

    /** 租户级已启用集合（供 /pm/list 展示）。 */
    public Set<String> tenantEnabled(String tenantCode) {
        Path tenantFile = paths.tenantFile(tenantCode);
        return store.readEnabled(tenantFile);
    }

    /** 失效指定租户缓存（或全部）。 */
    public void invalidate(String tenantCode) {
        store.invalidate(tenantCode == null ? null : paths.tenantFile(tenantCode));
        store.invalidate(paths.platformFile());
    }
}
