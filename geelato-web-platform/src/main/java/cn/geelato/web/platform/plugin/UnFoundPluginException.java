package cn.geelato.web.platform.plugin;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.platform.exception.PlatformErrorCodes;

public class UnFoundPluginException extends CoreException {

    public UnFoundPluginException() {
        super(PlatformErrorCodes.PLUGIN_NOT_FOUND);
    }

    public UnFoundPluginException(String msg) {
        super(PlatformErrorCodes.PLUGIN_NOT_FOUND, msg);
    }

    public UnFoundPluginException(String msg, Throwable cause) {
        super(PlatformErrorCodes.PLUGIN_NOT_FOUND, msg, cause);
    }
}
