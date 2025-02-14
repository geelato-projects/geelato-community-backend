package cn.geelato.web.platform.m.syspackage;

import cn.geelato.lang.exception.CoreException;

public class PackageException extends CoreException {
    private static final int DEFAULT_CODE = 2000;
    private static final String DEFAULT_MSG = "PackageException";

    public PackageException() {
        super(DEFAULT_CODE,DEFAULT_MSG);
    }

    public PackageException(String message) {
        super(DEFAULT_CODE, message);
    }

    public PackageException(String message, Throwable throwable) {
        super(DEFAULT_CODE, message, throwable);
    }
}
