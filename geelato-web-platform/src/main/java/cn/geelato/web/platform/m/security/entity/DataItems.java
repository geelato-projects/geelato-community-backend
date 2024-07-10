package cn.geelato.web.platform.m.security.entity;

public class DataItems<T> {
    private long total = 0;
    private T items;

    public DataItems(T items, long total) {
        this.total = total;
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public T getItems() {
        return items;
    }

    public void setItems(T items) {
        this.items = items;
    }
}
