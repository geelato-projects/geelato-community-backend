package cn.geelato.lang.api;

import java.util.Map;

/**
 * 多个分页查询结果
 *
 * @author geemeta
 */
public class ApiMultiPagedResult<E> extends ApiResult<Map<String,ApiMultiPagedResult.PageData>> {

    /**
     * 分页查询的返回结果
     *
     * @author geemeta
     */
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

        public Object getMeta() {
            return meta;
        }

        public void setMeta(Object meta) {
            this.meta = meta;
        }

        public E getData() {
            return data;
        }

        public void setData(E data) {
            this.data = data;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getPage() {
            return page;
        }

        public void setPage(long page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getDataSize() {
            return dataSize;
        }

        public void setDataSize(int dataSize) {
            this.dataSize = dataSize;
        }
    }

}
