package cn.geelato.plugin;

import cn.geelato.lang.exception.CoreException;

public class UnFoundPluginException extends CoreException {
    private static final int DEFAULT_CODE = 10001;
    private static final String DEFAULT_MSG = "UnFoundPluginException";

    public UnFoundPluginException(){
        super(DEFAULT_CODE, DEFAULT_MSG);
    }
    public UnFoundPluginException(String msg) {
        super(DEFAULT_CODE, msg);
    }

    public UnFoundPluginException(String msg, Throwable cause) {
        super(DEFAULT_CODE, msg, cause);
    }
}
