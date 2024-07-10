package cn.geelato.core.api;

/**
 * 分页查询的返回结果
 *
 * @author geemeta
 */
public class ApiPagedResult<E> extends ApiResult<E> implements ApiMeta {
    private long total;
    private long page;
    private int size;
    private int dataSize;

    /**
     * 元数据信息，一般用于实体查询，对查询结果字段的定义信息
     */
    private Object meta;

    @Override
    public Object getMeta() {
        return meta;
    }

    public void setMeta(Object meta) {
        this.meta = meta;
    }

    /**
     * @return 符合查询条件且不分页时的总记录数
     */
    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    /**
     * @return 第几页，从1开始
     */
    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    /**
     * @return 每页最多可以展示的记录数
     */
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    /**
     * @return 本次分页查询的实得记录数，若数据较少时，有可能该值小于getSize()
     */
    public int getDataSize() {
        return dataSize;
    }

    public void setDataSize(int dataSize) {
        this.dataSize = dataSize;
    }
}
