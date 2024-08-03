package cn.geelato.core.orm;


import cn.geelato.lang.exception.CoreException;

public class DaoException extends CoreException {
    private static final int DEFAULT_CODE = 1000;
    private static final String DEFAULT_MSG = "DaoException";

    public DaoException() {
        super(DEFAULT_CODE,DEFAULT_MSG);
    }

    public DaoException(String message) {
        super(DEFAULT_CODE, message);
    }

    public DaoException(String message, Throwable throwable) {
        super(DEFAULT_CODE, message, throwable);
    }
}
