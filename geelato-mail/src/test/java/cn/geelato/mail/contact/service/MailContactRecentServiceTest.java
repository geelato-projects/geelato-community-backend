package cn.geelato.mail.contact.service;

import cn.geelato.mail.contact.entity.MailContactRecent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MailContactRecentService 单元测试（P2-V78 最近收件人）。
 *
 * 覆盖场景：
 * - selectEvictions：按 lastUsedAt 倒序保留 cap 条，淘汰最旧
 * - 容量边界：size == cap 不淘汰；lastUsedAt 并列按 id 字典序兜底保证稳定
 */
class MailContactRecentServiceTest {

    @Test
    @DisplayName("selectEvictions：超出 cap 的部分按 lastUsedAt 最旧淘汰")
    void test_selectEvictions_evictsOldest() {
        List<MailContactRecent> all = new ArrayList<>();
        // 205 条，lastUsedAt 递增（id-r001 最旧，id-r205 最新）
        for (int i = 1; i <= 205; i++) {
            all.add(recent(String.format("r%03d", i), new Date(i * 1000L)));
        }

        List<MailContactRecent> evictions =
                MailContactRecentService.selectEvictions(all, MailContactRecentService.RECENT_CAP);

        assertEquals(5, evictions.size(), "205 - 200 = 淘汰最旧 5 条");
        // 返回列表保持 lastUsedAt 倒序：淘汰集 = 最旧 5 条（r005..r001）
        assertEquals("r005", evictions.get(0).getId());
        assertEquals("r001", evictions.get(4).getId());
    }

    @Test
    @DisplayName("selectEvictions：size <= cap 不淘汰")
    void test_selectEvictions_withinCapNoEviction() {
        List<MailContactRecent> all = List.of(
                recent("a", new Date(1000)), recent("b", new Date(2000)));
        assertTrue(MailContactRecentService.selectEvictions(all, 2).isEmpty());
        assertTrue(MailContactRecentService.selectEvictions(all, 200).isEmpty());
    }

    @Test
    @DisplayName("selectEvictions：lastUsedAt 并列按 id 字典序倒序兜底，结果稳定")
    void test_selectEvictions_tieBreakById() {
        Date same = new Date(1000);
        List<MailContactRecent> all = List.of(
                recent("a", same), recent("c", same), recent("b", same));
        // cap=2：保留 id 字典序较大两条中的较新者 —— 并列时 id 倒序优先保留（c、b），淘汰 a
        List<MailContactRecent> evictions = MailContactRecentService.selectEvictions(all, 2);
        assertEquals(1, evictions.size());
        assertEquals("a", evictions.get(0).getId(), "并列时间按 id 字典序兜底，结果可重现");
    }

    private static MailContactRecent recent(String id, Date lastUsedAt) {
        MailContactRecent recent = new MailContactRecent();
        recent.setId(id);
        recent.setLastUsedAt(lastUsedAt);
        return recent;
    }
}
