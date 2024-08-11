package cn.geelato.lang.api;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询的返回结果
 *
 * @author geemeta
 */
@Getter
@Setter
public class ApiPagedResult<E> extends ApiResult<E> {

    private long total;

    private long page;
    /**
     * -- GETTER --
     *
     */
    private int size;
    /**
     * -- GETTER --
     *
     */
    private int dataSize;
    /**
     * 元数据信息，一般用于实体查询，对查询结果字段的定义信息
     */
    private Object meta;

}
