package cn.geelato.web.platform.plugin;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * 插件未对当前租户启用（或已被平台级禁用）时抛出。
 * <p>由 {@link PluginBeanProvider#getBean(Class, String)} 在调用门控时抛出，
 * 区分两种情况：平台级禁用、租户级未启用。</p>
 *
 * @author geelato
 */
public class PluginNotEnabledForTenantException extends CoreException {

    public PluginNotEnabledForTenantException() {
        super(PlatformErrorCodes.PLUGIN_NOT_ENABLED_FOR_TENANT);
    }

    public PluginNotEnabledForTenantException(String msg) {
        super(PlatformErrorCodes.PLUGIN_NOT_ENABLED_FOR_TENANT, msg);
    }

    public PluginNotEnabledForTenantException(String msg, Throwable cause) {
        super(PlatformErrorCodes.PLUGIN_NOT_ENABLED_FOR_TENANT, msg, cause);
    }

    /**
     * 按平台级/租户级区分的错误码构造。
     *
     * @param platformDisabled true 表示平台级禁用（用 {@link PlatformErrorCodes#PLUGIN_PLATFORM_DISABLED}），
     *                         false 表示租户级未启用（用 {@link PlatformErrorCodes#PLUGIN_NOT_ENABLED_FOR_TENANT}）
     */
    public PluginNotEnabledForTenantException(boolean platformDisabled, String msg) {
        super(platformDisabled
                ? PlatformErrorCodes.PLUGIN_PLATFORM_DISABLED
                : PlatformErrorCodes.PLUGIN_NOT_ENABLED_FOR_TENANT, msg);
    }
}
