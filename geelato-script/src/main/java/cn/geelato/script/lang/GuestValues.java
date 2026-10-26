package cn.geelato.script.lang;

import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * host 结构 -> guest 侧对象（ProxyObject/ProxyArray 递归包装）。
 * 受限 HostAccess 下 host Map/List 成员语义不可访问，kit 返回结构化数据须经此包装；
 * 标量（String/Number/Boolean/null）polyglot 原生直通。
 * 时间类（Date 家族/java.time）在此边界转为字符串：host 对象透进 guest 后成员不可见，
 * 经 ValueUnwrapper 往返会退化成空 Map（如 anchorTime="{}"）。
 */
public final class GuestValues {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private GuestValues() {
    }

    public static Object toGuest(Object v) {
        if (v == null || v instanceof String || v instanceof Boolean
                || v instanceof Number || v instanceof org.graalvm.polyglot.Value) {
            return v;
        }
        if (v instanceof Timestamp t) {
            return DATETIME.format(t.toLocalDateTime());
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof Time t) {
            return TIME.format(t.toLocalTime());
        }
        if (v instanceof LocalDateTime dt) {
            return DATETIME.format(dt);
        }
        if (v instanceof LocalDate || v instanceof LocalTime) {
            return v.toString();
        }
        if (v instanceof java.util.Date d) {
            return DATETIME.format(d.toInstant().atZone(ZoneId.systemDefault()));
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), toGuest(e.getValue()));
            }
            return ProxyObject.fromMap(out);
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object item : l) {
                out.add(toGuest(item));
            }
            return ProxyArray.fromArray(out.toArray());
        }
        if (v instanceof Object[] arr) {
            Object[] out = new Object[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = toGuest(arr[i]);
            }
            return ProxyArray.fromArray(out);
        }
        return v;
    }
}
