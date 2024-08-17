package cn.geelato.web.platform.m.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
public class DataItems<T> {
    private long total = 0;
    private T items;

    public DataItems(T items, long total) {
        this.total = total;
        this.items = items;
    }
}
