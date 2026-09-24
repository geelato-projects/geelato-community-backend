package cn.geelato.mail.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MailMessageService.toDate 语义单测。
 *
 * toDate 统一转换（MetaQuery.list() 可能返回 LocalDateTime，直接 instanceof Date 会静默丢值，
 * 读改写链路会把 null 写回数据库）：
 * - null → null
 * - Date → 原样返回
 * - LocalDateTime → 按系统默认时区转 Date（纳秒超出毫秒部分按 Instant→Date 语义截断）
 * - 未知类型 → fail-fast IllegalStateException（禁止静默置 null 掩盖类型漂移）
 */
class MailMessageServiceToDateTest {

    @Test
    @DisplayName("toDate：null → null")
    void nullStaysNull() {
        assertNull(MailMessageService.toDate(null));
    }

    @Test
    @DisplayName("toDate：Date 原样返回同实例")
    void datePassthrough() {
        Date d = new Date(1786539220000L);
        assertSame(d, MailMessageService.toDate(d));
    }

    @Test
    @DisplayName("toDate：LocalDateTime 按系统默认时区转换，毫秒精度保留")
    void localDateTimeConverted() {
        LocalDateTime ldt = LocalDateTime.of(2026, 8, 12, 15, 30, 45, 123_000_000);
        Date converted = MailMessageService.toDate(ldt);
        assertEquals(Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()), converted);
        assertEquals(123L, converted.toInstant().toEpochMilli() % 1000, "毫秒精度必须保留");
    }

    @Test
    @DisplayName("toDate：LocalDateTime 亚毫秒纳秒按 Instant→Date 语义截断（不四舍五入）")
    void localDateTimeSubMilliTruncated() {
        LocalDateTime ldt = LocalDateTime.of(2026, 8, 12, 15, 30, 45, 123_456_789);
        Date converted = MailMessageService.toDate(ldt);
        assertEquals(123L, converted.toInstant().toEpochMilli() % 1000,
                "纳秒超出毫秒部分截断（java.util.Date 仅毫秒精度）");
    }

    @Test
    @DisplayName("toDate：未知类型 fail-fast（禁止静默置 null 掩盖类型漂移）")
    void unknownTypeFailsFast() {
        assertThrows(IllegalStateException.class, () -> MailMessageService.toDate("2026-08-12"));
        assertThrows(IllegalStateException.class, () -> MailMessageService.toDate(1786539220000L));
    }
}
