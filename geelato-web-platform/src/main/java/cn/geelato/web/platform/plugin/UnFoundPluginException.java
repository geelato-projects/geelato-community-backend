package cn.geelato.web.platform.plugin;

import cn.geelato.lang.exception.CoreException;

public class UnFoundPluginException extends CoreException {

    public static final int ERROR_CODE = 40001;

    public UnFoundPluginException() {
        super(ERROR_CODE, "插件未找到");
    }
}
