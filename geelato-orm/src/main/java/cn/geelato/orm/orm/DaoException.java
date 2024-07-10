package cn.geelato.orm.orm;

import org.geelato.core.exception.CoreException;

public class DaoException extends CoreException {
    private static final int code=1000;

    public DaoException(String msg) {
        super(code,msg);
    }
    public DaoException(String msg,Throwable throwable) {
        super(code,msg,throwable);
    }

}
