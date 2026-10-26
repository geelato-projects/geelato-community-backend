package cn.geelato.script.lang;

import org.junit.jupiter.api.Test;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class GuestValuesTest {

    @Test
    void timestampBecomesDatetimeStringNotHostObject() {
        Timestamp ts = Timestamp.valueOf("2026-10-22 09:30:15");
        assertEquals("2026-10-22 09:30:15", GuestValues.toGuest(ts));
    }

    @Test
    void sqlDateAndUtilDateBecomeDateString() {
        assertEquals("2026-10-22", GuestValues.toGuest(java.sql.Date.valueOf("2026-10-22")));
        java.util.Date util = new java.util.Date(Timestamp.valueOf("2026-10-22 09:30:15").getTime());
        assertEquals("2026-10-22 09:30:15", GuestValues.toGuest(util));
    }

    @Test
    void javaTimeTypesBecomeStrings() {
        assertEquals("2026-10-22 09:30:15", GuestValues.toGuest(LocalDateTime.of(2026, 10, 22, 9, 30, 15)));
        assertEquals("2026-10-22", GuestValues.toGuest(LocalDate.of(2026, 10, 22)));
        assertEquals("09:30:15", GuestValues.toGuest(LocalTime.of(9, 30, 15)));
        assertEquals("09:30:15", GuestValues.toGuest(Time.valueOf("09:30:15")));
    }

    @Test
    void nestedTimestampInsideRowMapIsConverted() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("biz_id", 42L);
        row.put("anchor_time", Timestamp.valueOf("2026-10-22 09:30:15"));
        Object guest = GuestValues.toGuest(List.of(row));
        org.graalvm.polyglot.proxy.ProxyArray arr = assertInstanceOf(org.graalvm.polyglot.proxy.ProxyArray.class, guest);
        ProxyObject obj = (ProxyObject) arr.get(0);
        assertEquals(42L, obj.getMember("biz_id"));
        assertEquals("2026-10-22 09:30:15", obj.getMember("anchor_time"));
    }

    @Test
    void scalarsPassThroughUnchanged() {
        assertSame("s", GuestValues.toGuest("s"));
        assertSame(42L, GuestValues.toGuest(42L));
        assertSame(Boolean.TRUE, GuestValues.toGuest(Boolean.TRUE));
        assertSame(null, GuestValues.toGuest(null));
    }
}
