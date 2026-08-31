package cn.geelato.core.mql;

import cn.geelato.lang.exception.CoreException;

import java.util.Collection;

public class ViewTemplateParamException extends CoreException {

    public static final int ERROR_CODE = 10007;

    public ViewTemplateParamException(String entityName, Collection<String> paramNames, String reason) {
        super(
                ERROR_CODE,
                String.format("实体[%s]收到@pf参数%s，但%s。@pf参数仅支持虚拟视图（virtual）类型视图实体。", entityName, paramNames, reason)
        );
    }
}
