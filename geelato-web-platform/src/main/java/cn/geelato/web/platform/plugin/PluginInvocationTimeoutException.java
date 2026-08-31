package cn.geelato.web.platform.plugin;

import cn.geelato.lang.exception.CoreException;

/**
 * 插件调用超时时抛出。
 * <p>由 {@link PluginBeanProvider} 在扩展方法调用超过配置阈值时抛出，
 * 防止单个插件卡死（如 native 推理死循环）拖垮调用线程。</p>
 *
 * @author geelato
 */
public class PluginInvocationTimeoutException extends CoreException {

    public static final int ERROR_CODE = 40004;

    public PluginInvocationTimeoutException(String msg, Throwable cause) {
        super(ERROR_CODE, msg, cause);
    }
}
