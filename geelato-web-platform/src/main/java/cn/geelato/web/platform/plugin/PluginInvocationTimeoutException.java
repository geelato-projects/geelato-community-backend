package cn.geelato.web.platform.plugin;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.platform.exception.PlatformErrorCodes;

/**
 * 插件调用超时时抛出。
 * <p>由 {@link PluginBeanProvider} 在扩展方法调用超过配置阈值时抛出，
 * 防止单个插件卡死（如 native 推理死循环）拖垮调用线程。</p>
 *
 * @author geelato
 */
public class PluginInvocationTimeoutException extends CoreException {

    public PluginInvocationTimeoutException() {
        super(PlatformErrorCodes.PLUGIN_INVOCATION_TIMEOUT);
    }

    public PluginInvocationTimeoutException(String msg) {
        super(PlatformErrorCodes.PLUGIN_INVOCATION_TIMEOUT, msg);
    }

    public PluginInvocationTimeoutException(String msg, Throwable cause) {
        super(PlatformErrorCodes.PLUGIN_INVOCATION_TIMEOUT, msg, cause);
    }
}
