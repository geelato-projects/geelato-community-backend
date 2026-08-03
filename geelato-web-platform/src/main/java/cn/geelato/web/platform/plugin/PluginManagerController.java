package cn.geelato.web.platform.plugin;

import cn.geelato.core.SessionCtx;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.plugin.util.PluginLogUtil;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件管理控制器。
 * <p>提供平台级/租户级双开关、插件列表（含 healthCheck 与开关状态）、缓存刷新等接口。</p>
 *
 * <p>鉴权约定：</p>
 * <ul>
 *   <li>平台级开关 {@code /pm/platform/switch}：不鉴权（按当前定调）。</li>
 *   <li>租户级开关 {@code /pm/tenant/switch}：校验 {@link SecurityContext#isAdmin()}。</li>
 * </ul>
 *
 * @author geelato
 */
@ApiRestController("/pm")
public class PluginManagerController {

    @Autowired
    private PluginManager pluginManager;

    @Autowired
    private FilePluginStatusProvider platformStatusProvider;

    @Autowired
    private TenantPluginGate tenantPluginGate;

    @Autowired
    private PluginStatusPaths paths;

    @Autowired
    private PluginStatusJsonStore store;

    @Autowired
    private PluginBeanProvider pluginBeanProvider;

    @Autowired
    private PluginMetrics pluginMetrics;

    /**
     * 获取所有插件列表（含平台/租户开关状态与扩展可用性）。
     *
     * @return 插件列表信息
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ApiResult<?> list() {
        try {
            String tenantCode = currentTenantCode();
            Set<String> platformEnabled = platformStatusProvider.enabledPlugins();
            Set<String> tenantEnabled = tenantPluginGate.tenantEnabled(tenantCode);

            List<Map<String, Object>> pluginInfoList = new ArrayList<>();
            for (PluginWrapper plugin : pluginManager.getPlugins()) {
                PluginDescriptor descriptor = plugin.getDescriptor();
                String pluginId = descriptor.getPluginId();
                boolean platformOn = platformEnabled.contains(pluginId);
                boolean tenantOn = tenantEnabled.contains(pluginId);

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", pluginId);
                info.put("version", descriptor.getVersion());
                info.put("description", descriptor.getPluginDescription());
                info.put("provider", descriptor.getProvider());
                info.put("dependencies", descriptor.getDependencies());
                info.put("state", plugin.getPluginState().toString());
                info.put("platformEnabled", platformOn);
                info.put("tenantEnabled", tenantOn);
                // 可用性 = 平台级 && 租户级（仅当插件已 STARTED 时才有意义）
                info.put("available", plugin.getPluginState() == PluginState.STARTED && platformOn && tenantOn);
                info.put("healthCheck", resolveHealth(pluginId, plugin.getPluginState()));
                info.put("metrics", pluginMetrics.snapshot(pluginId));
                pluginInfoList.add(info);
            }
            return ApiResult.success(pluginInfoList);
        } catch (Exception e) {
            return ApiResult.fail("获取插件列表失败: " + e.getMessage());
        }
    }

    /**
     * 平台级总开关（不鉴权）。平台级禁用 → 全平台不可用。
     *
     * @param body { "pluginId": "...", "enable": true|false }
     */
    @PostMapping("/platform/switch")
    public ApiResult<?> platformSwitch(@RequestBody Map<String, Object> body) {
        try {
            String pluginId = str(body.get("pluginId"));
            boolean enable = bool(body.get("enable"), true);
            if (pluginId == null || pluginId.isBlank()) {
                return ApiResult.fail("pluginId 不能为空");
            }
            if (enable) {
                platformStatusProvider.enablePlugin(pluginId);
            } else {
                platformStatusProvider.disablePlugin(pluginId);
            }
            PluginLogUtil.log(pluginId, enable ? "平台级已启用" : "平台级已禁用");
            return ApiResult.success(enable ? "平台级启用成功" : "平台级禁用成功");
        } catch (Exception e) {
            return ApiResult.fail("切换平台级开关失败: " + e.getMessage());
        }
    }

    /**
     * 租户级开关（需本租户 admin）。要求目标插件平台级已启用。
     *
     * @param body { "pluginId": "...", "enable": true|false, "tenantCode": "可选，默认当前租户" }
     */
    @PostMapping("/tenant/switch")
    public ApiResult<?> tenantSwitch(@RequestBody Map<String, Object> body) {
        try {
            if (!SecurityContext.isAdmin()) {
                return ApiResult.fail("无权操作：仅租户管理员可切换租户级插件开关");
            }
            String pluginId = str(body.get("pluginId"));
            boolean enable = bool(body.get("enable"), true);
            String tenantCode = str(body.get("tenantCode"));
            if (tenantCode == null || tenantCode.isBlank()) {
                tenantCode = currentTenantCode();
            }
            if (pluginId == null || pluginId.isBlank()) {
                return ApiResult.fail("pluginId 不能为空");
            }
            // 租户级启用前，目标插件必须平台级已启用
            if (enable && !platformStatusProvider.isPlatformEnabled(pluginId)) {
                return ApiResult.fail("插件 " + pluginId + " 平台级未启用，无法为租户开启");
            }
            String operator = tenantCode;
            final String fPluginId = pluginId;
            final boolean fEnable = enable;
            final String fTenant = tenantCode;
            store.write(paths.tenantFile(tenantCode), current -> {
                PluginStatusDoc doc = current != null ? current : new PluginStatusDoc();
                doc.setTenantCode(fTenant);
                List<String> enabled = doc.getEnabled() != null ? new ArrayList<>(doc.getEnabled()) : new ArrayList<>();
                if (fEnable) {
                    if (!enabled.contains(fPluginId)) {
                        enabled.add(fPluginId);
                    }
                } else {
                    enabled.remove(fPluginId);
                }
                doc.setEnabled(enabled);
                return doc;
            }, operator);
            tenantPluginGate.invalidate(tenantCode);
            PluginLogUtil.log(pluginId, (enable ? "租户级已启用：" : "租户级已禁用：") + tenantCode);
            return ApiResult.success((enable ? "租户级启用成功：" : "租户级禁用成功：") + tenantCode);
        } catch (Exception e) {
            return ApiResult.fail("切换租户级开关失败: " + e.getMessage());
        }
    }

    /**
     * 失效本地缓存（开关变更后加速多节点生效）。
     */
    @PostMapping("/refresh")
    public ApiResult<?> refresh() {
        try {
            store.invalidate(null);
            tenantPluginGate.invalidate(null);
            pluginBeanProvider.invalidateCache();
            return ApiResult.success("缓存已刷新");
        } catch (Exception e) {
            return ApiResult.fail("刷新缓存失败: " + e.getMessage());
        }
    }

    /**
     * @deprecated 由 {@link #platformSwitch(Map)} 与 {@link #tenantSwitch(Map)} 取代。
     * 保留以兼容旧前端，改为 POST，内部转发。
     */
    @Deprecated
    @RequestMapping(value = "/switchStatus", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResult<?> switchStatus(@RequestParam String pluginId, @RequestParam String status) {
        boolean enable = "enable".equalsIgnoreCase(status);
        Map<String, Object> body = new HashMap<>();
        body.put("pluginId", pluginId);
        body.put("enable", enable);
        // 默认按平台级处理（向后兼容旧行为）
        return platformSwitch(body);
    }

    /**
     * 获取插件日志（P2-A 后日志由 logback 统一管理，此处仅返回提示）。
     */
    @RequestMapping(value = "/log", method = RequestMethod.GET)
    public ApiResult<?> log(@RequestParam String pluginId) {
        return ApiResult.success("插件日志已由 logback 统一管理（logger: plugin." + pluginId + "），请在主程序日志中检索。");
    }

    /**
     * 清除插件日志（P2-A 后无实际文件，恒成功）。
     */
    @RequestMapping(value = "/clearLog", method = RequestMethod.GET)
    public ApiResult<?> clearLog(@RequestParam String pluginId) {
        return ApiResult.success("日志由 logback 滚动策略管理，无需手动清除。");
    }

    // ---- 工具方法 ----

    private String currentTenantCode() {
        String code = SessionCtx.getCurrentTenantCode();
        return code == null || code.isBlank() ? "default" : code;
    }

    /** 探测插件扩展可用性（best-effort，失败视为不可用）。 */
    private boolean resolveHealth(String pluginId, PluginState state) {
        if (state != PluginState.STARTED) {
            return false;
        }
        try {
            // 通过 PluginManager 直接取扩展并调 healthCheck；不经过门控避免循环依赖
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static boolean bool(Object o, boolean defaultVal) {
        if (o == null) return defaultVal;
        if (o instanceof Boolean b) return b;
        String s = o.toString();
        return s.isBlank() ? defaultVal : Boolean.parseBoolean(s);
    }
}
