package cn.geelato.web.platform.plugin;

import cn.geelato.lang.exception.CoreException;

/**
 * 插件未对当前租户启用（或已被平台级禁用）时抛出。
 * <p>由 {@link PluginBeanProvider#getBean(Class, String)} 在调用门控时抛出，
 * 区分两种情况：平台级禁用、租户级未启用。</p>
 *
 * @author geelato
 */
public class PluginNotEnabledForTenantException extends CoreException {

    /** 租户级未启用。 */
    public static final int ERROR_CODE = 40002;
    /** 平台级禁用。 */
    public static final int ERROR_CODE_PLATFORM_DISABLED = 40003;

    public PluginNotEnabledForTenantException() {
        super(ERROR_CODE, "插件未对当前租户启用");
    }

    /**
     * 按平台级/租户级区分的错误码构造。
     *
     * @param platformDisabled true 表示平台级禁用，false 表示租户级未启用
     */
    public PluginNotEnabledForTenantException(boolean platformDisabled, String msg) {
        super(platformDisabled ? ERROR_CODE_PLATFORM_DISABLED : ERROR_CODE, msg);
    }
}
