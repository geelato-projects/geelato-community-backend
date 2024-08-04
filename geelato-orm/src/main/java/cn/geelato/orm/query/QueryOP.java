package cn.geelato.orm.query;

import java.util.List;
import java.util.Map;

public interface QueryOP {
    <T> List<T> pageQueryList();

    List<Map<String, Object>> queryForMapList();
}
