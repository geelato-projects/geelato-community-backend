package cn.geelato.core.sql;

import cn.geelato.lang.exception.CoreException;

public class InvalidFilterFieldException extends CoreException {
    private static final int DEFAULT_CODE = 10011;

    public InvalidFilterFieldException(String entityName, String fieldName, Object operator, String scene) {
        super(
                DEFAULT_CODE,
                String.format("实体[%s]的过滤字段[%s]不存在，operator[%s]，scene[%s]。", entityName, fieldName, operator, scene)
        );
    }
}
