package cn.geelato.lang.api;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 多个分页查询结果
 *
 * @author geemeta
 */
public class ApiMultiPagedResult<E> extends ApiResult<Map<String,ApiMultiPagedResult.PageData<E>>> {

    /**
     * 分页查询的返回结果
     *
     * @author geemeta
     */
    @Setter
    @Getter
    public static class PageData<E> {
        private long total;
        private long page;
        private int size;
        private int dataSize;
        private E data;
        /**
         * 元数据信息，一般用于实体查询，对查询结果字段的定义信息
         */
        private Object meta;

    }

}
