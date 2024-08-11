package cn.geelato.lang.api;

import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 *
 */
@Setter
@Getter
public class ApiMetaResult<E> extends ApiResult<E>{

    /**
     * 元数据信息，一般用于实体查询，对查询结果字段的定义信息
     */
    private Object meta;

}
