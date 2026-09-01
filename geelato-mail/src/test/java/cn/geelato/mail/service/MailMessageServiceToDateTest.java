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
 * MailMessageService.toDate 语义等价性单元测试（ST-14 LocalDateTime 统一修复）。
 *
 * 修复前 10 处 `row.get(x) instanceof Date d ? d : null` 对 MetaQuery.list() 返回的
 * LocalDateTime 静默丢值（且读改写链路会把 null 写回数据库）。修复后统一走 toDate：
 * - null → null（与旧模式等价）
 * - Date → 原样返回（与旧模式等价）
 * - LocalDateTime → 按系统默认时区转 Date（修复点：旧模式静默丢 null）
 * - 未知类型 → fail-fast IllegalStateException（旧模式静默置 null，此处有意收紧，
 *   禁止静默置 null 掩盖类型漂移；毫秒精度：LocalDateTime 纳秒超出毫秒部分按 Instant→Date 语义截断）
 */
class MailMessageServiceToDateTest {

    @Test
    @DisplayName("toDate：null → null（与旧 instanceof 模式等价）")
    void nullStaysNull() {
        assertNull(MailMessageService.toDate(null));
    }

    @Test
    @DisplayName("toDate：Date 原样返回同实例（与旧 instanceof 模式等价）")
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
    @DisplayName("toDate：未知类型 fail-fast（旧模式静默置 null，有意收紧防类型漂移）")
    void unknownTypeFailsFast() {
        assertThrows(IllegalStateException.class, () -> MailMessageService.toDate("2026-08-12"));
        assertThrows(IllegalStateException.class, () -> MailMessageService.toDate(1786539220000L));
    }
}
