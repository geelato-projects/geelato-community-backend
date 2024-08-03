package cn.geelato.orm.query;

import java.util.List;

public interface QueryOP {
    <T> List<T> pageQueryList();
}
