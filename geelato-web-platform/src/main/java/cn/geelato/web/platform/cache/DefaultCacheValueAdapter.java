package cn.geelato.web.platform.cache;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultCacheValueAdapter implements CacheValueAdapter {
    @Override
    public Object adaptForStore(Object value) {
        if (value == null) return null;
        if (value instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) value;
            Map<Object, Object> nm = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                nm.put(e.getKey(), adaptForStore(e.getValue()));
            }
            return nm;
        }
        if (value instanceof List) {
            List<?> l = (List<?>) value;
            List<Object> nl = new ArrayList<>(l.size());
            for (Object o : l) {
                nl.add(adaptForStore(o));
            }
            return nl;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toString();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).toString();
        }
        if (value instanceof LocalTime) {
            return ((LocalTime) value).toString();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toInstant().toString();
        }
        return value;
    }

    @Override
    public Object adaptForLoad(Object value) {
        return value;
    }
}
