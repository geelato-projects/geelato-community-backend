package cn.geelato.core.sql;

import cn.geelato.lang.exception.CoreException;

public class InvalidFilterFieldException extends CoreException {

    public static final int ERROR_CODE = 10003;

    public InvalidFilterFieldException(String entityName, String fieldName, Object operator, String scene) {
        super(
                ERROR_CODE,
                String.format("实体[%s]的过滤字段[%s]不存在，operator[%s]，scene[%s]。", entityName, fieldName, operator, scene)
        );
    }
}
