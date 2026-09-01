package cn.geelato.mail.contact.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.mail.contact.entity.MailContact;
import cn.geelato.mail.contact.entity.MailContactRecent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MailContactService 单元测试（P2-V78 联系人）。
 *
 * 覆盖场景：
 * - create：邮箱规范化（trim+小写）+ 审计/租户字段 + 雪花 id 回填；去重/格式 fail-fast
 * - update：邮箱变更去重排除自身
 * - applyMerge：合并字段回填纯函数（空白回填、首见优先、name/email 不覆盖）
 * - merge：次要联系人缺失/越权 → 返回 failedIds 且零写入
 * - mergeSuggest / suggest：联系人优先 + recent 补齐 + lower(email) 去重 + limit 截断
 * - batchDelete：越权 id 跳过，返回实际删除数
 */
class MailContactServiceTest {

    private static final String USER_ID = "user-001";
    private static final String USER_NAME = "张三";
    private static final String TENANT_CODE = "geelato";

    private MailContactService service;
    private Dao dynamicDao;
    private MailContactRecentService recentService;

    @BeforeEach
    void setUp() {
        dynamicDao = mock(Dao.class);
        recentService = mock(MailContactRecentService.class);
        service = spy(new MailContactService());
        ReflectionTestUtils.setField(service, "dynamicDao", dynamicDao);
        ReflectionTestUtils.setField(service, "recentService", recentService);
        User user = new User();
        user.setUserId(USER_ID);
        user.setUserName(USER_NAME);
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant(TENANT_CODE));
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    // ==================== create ====================

    @Test
    @DisplayName("create：邮箱 trim+小写规范化落库，审计/租户字段齐全，雪花 id 回填")
    void test_create_normalizesEmailAndBackfillsId() {
        doReturn(null).when(service).findByEmail(anyString());
        when(dynamicDao.save(any(MailContact.class))).thenReturn(Map.of("id", "snow-001"));

        MailContact created = service.create(" 张三 ", "  ZS@Example.COM ", "138", "Geelato",
                null, null, null, null);

        assertEquals("snow-001", created.getId());
        assertEquals("zs@example.com", created.getEmail(), "邮箱必须 trim+小写规范化");
        assertEquals("张三", created.getName(), "姓名必须 trim");
        assertEquals(USER_ID, created.getUserId());
        assertEquals(USER_ID, created.getCreator());
        assertEquals(TENANT_CODE, created.getTenantCode());
        assertEquals(0, created.getDelStatus());
    }

    @Test
    @DisplayName("create：邮箱已存在 fail-fast（去重），不产生任何写入")
    void test_create_duplicateEmailFailFast() {
        doReturn(new MailContact()).when(service).findByEmail("dup@x.com");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.create("张三", "DUP@x.com", null, null, null, null, null, null));

        assertTrue(e.getMessage().contains("邮箱已存在"));
        verify(dynamicDao, never()).save(any(MailContact.class));
    }

    @Test
    @DisplayName("create：邮箱格式非法 fail-fast")
    void test_create_invalidEmailFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("张三", "not-an-email", null, null, null, null, null, null));
        verify(dynamicDao, never()).save(any(MailContact.class));
    }

    @Test
    @DisplayName("create/update：字段超列上限 fail-fast（防落库 500），消息含上限与实际长度")
    void test_fieldLengthFailFast() {
        doReturn(null).when(service).findByEmail(anyString());
        String overName = "张".repeat(MailContactService.MAX_NAME_LEN + 1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.create(overName, "a@x.com", null, null, null, null, null, null));
        assertTrue(e.getMessage().contains("姓名超长"), e.getMessage());
        assertTrue(e.getMessage().contains("128"));
        verify(dynamicDao, never()).save(any(MailContact.class));

        // update 路径：备注超 1024 拦截（实体字段被变更但不落库）
        MailContact existing = new MailContact();
        existing.setId("c1");
        existing.setName("张三");
        existing.setEmail("a@x.com");
        String overNotes = "备".repeat(MailContactService.MAX_NOTES_LEN + 1);
        IllegalArgumentException ue = assertThrows(IllegalArgumentException.class,
                () -> service.update(existing, null, null, null, null, null, overNotes, null));
        assertTrue(ue.getMessage().contains("备注超长"), ue.getMessage());
        verify(dynamicDao, never()).save(any(MailContact.class));
    }

    // ==================== update ====================

    @Test
    @DisplayName("update：邮箱变更为他人已占用 fail-fast；变更为自身原邮箱放行")
    void test_update_emailConflictExcludesSelf() {
        MailContact self = new MailContact();
        self.setId("c1");
        self.setEmail("old@x.com");
        MailContact other = new MailContact();
        other.setId("c2");
        org.mockito.Mockito.doAnswer(inv -> {
            String email = inv.getArgument(0);
            if ("taken@x.com".equals(email)) {
                return other;
            }
            if ("old@x.com".equals(email)) {
                return self;
            }
            return null;
        }).when(service).findByEmail(anyString());

        assertThrows(IllegalArgumentException.class,
                () -> service.update(self, null, "taken@x.com", null, null, null, null, null));
        verify(dynamicDao, never()).save(any(MailContact.class));

        // 变更为自身原邮箱（大小写不同）→ 放行
        service.update(self, null, "OLD@x.com", null, null, null, null, null);
        assertEquals("old@x.com", self.getEmail(), "自身邮箱大小写变更应放行并规范化");
        verify(dynamicDao, times(1)).save(self);
    }

    // ==================== applyMerge（纯函数） ====================

    @Test
    @DisplayName("applyMerge：主联系人空白字段按序回填首个非空白值，name/email 永不覆盖")
    void test_applyMerge_backfillsBlanksOnly() {
        MailContact primary = new MailContact();
        primary.setName("主联系人");
        primary.setEmail("primary@x.com");
        primary.setAvatar("https://a/1.png");

        MailContact s1 = new MailContact();
        s1.setName("次要1");
        s1.setEmail("s1@x.com");
        s1.setPhone("111");
        s1.setOrg("  "); // 空白视为缺失
        s1.setNotes("N1");

        MailContact s2 = new MailContact();
        s2.setPhone("222"); // 首见优先：s1 已回填 phone，s2 不覆盖
        s2.setOrg("O2");
        s2.setGroupId("g2");

        boolean changed = MailContactService.applyMerge(primary, List.of(s1, s2));

        assertTrue(changed);
        assertEquals("111", primary.getPhone(), "首见非空白值回填");
        assertEquals("O2", primary.getOrg(), "s1 org 为空白串，s2 非空白回填");
        assertEquals("N1", primary.getNotes());
        assertEquals("g2", primary.getGroupId());
        assertEquals("https://a/1.png", primary.getAvatar(), "主联系人非空白字段不覆盖");
        assertEquals("主联系人", primary.getName(), "name 永不覆盖");
        assertEquals("primary@x.com", primary.getEmail(), "email 永不覆盖");
    }

    @Test
    @DisplayName("applyMerge：次要联系人全部字段空白 → 无变更返回 false")
    void test_applyMerge_noChangeWhenAllBlank() {
        MailContact primary = new MailContact();
        primary.setPhone("111");
        boolean changed = MailContactService.applyMerge(primary, List.of(new MailContact()));
        assertFalse(changed);
    }

    @Test
    @DisplayName("merge：次要联系人不存在/越权 → 返回 failedIds 且零写入（事务性）")
    void test_merge_missingSecondaryNoWrites() {
        MailContact primary = new MailContact();
        primary.setId("p1");
        org.mockito.Mockito.doAnswer(inv -> {
            String id = inv.getArgument(0);
            if ("s1".equals(id)) {
                MailContact c = new MailContact();
                c.setId("s1");
                return c;
            }
            return null; // s-foreign 越权/不存在
        }).when(service).getOwned(anyString());

        List<String> failedIds = service.merge(primary, List.of("s1", "s-foreign"));

        assertEquals(List.of("s-foreign"), failedIds);
        verify(dynamicDao, never()).save(any(MailContact.class));
    }

    @Test
    @DisplayName("merge：全部命中 → 主联系人回填保存 + 次要联系人逻辑删除")
    void test_merge_successWrites() {
        MailContact primary = new MailContact();
        primary.setId("p1");
        MailContact secondary = new MailContact();
        secondary.setId("s1");
        secondary.setPhone("111");
        org.mockito.Mockito.doAnswer(inv -> {
            String id = inv.getArgument(0);
            if ("s1".equals(id)) {
                return secondary;
            }
            return null;
        }).when(service).getOwned(anyString());

        List<String> failedIds = service.merge(primary, List.of("s1"));

        assertTrue(failedIds.isEmpty());
        assertEquals("111", primary.getPhone());
        ArgumentCaptor<MailContact> captor = ArgumentCaptor.forClass(MailContact.class);
        verify(dynamicDao, times(2)).save(captor.capture());
        List<MailContact> saved = captor.getAllValues();
        assertEquals("p1", saved.get(0).getId(), "先保存主联系人");
        assertEquals("s1", saved.get(1).getId());
        assertEquals(1, saved.get(1).getDelStatus(), "次要联系人逻辑删除");
    }

    // ==================== suggest ====================

    @Test
    @DisplayName("mergeSuggest：联系人优先 + recent 补齐 + lower(email) 去重 + limit 截断")
    void test_mergeSuggest_contactsFirstDedupLimit() {
        List<Map<String, Object>> contacts = new ArrayList<>();
        contacts.add(item("c1", "张三", "Zhang@x.com", "contact"));
        contacts.add(item("c2", "李四", "li@x.com", "contact"));
        List<Map<String, Object>> recents = new ArrayList<>();
        recents.add(item(null, "张三", "zhang@x.com", "recent")); // 与 c1 同邮箱（大小写不同）→ 去重
        recents.add(item(null, "王五", "wang@x.com", "recent"));

        List<Map<String, Object>> merged = MailContactService.mergeSuggest(contacts, recents, 10);

        assertEquals(3, merged.size());
        assertEquals("c1", merged.get(0).get("id"), "联系人优先");
        assertEquals("contact", merged.get(0).get("source"));
        assertEquals("wang@x.com", merged.get(2).get("email"));
        assertEquals("recent", merged.get(2).get("source"));

        List<Map<String, Object>> limited = MailContactService.mergeSuggest(contacts, recents, 2);
        assertEquals(2, limited.size(), "limit 截断");
        assertEquals("c1", limited.get(0).get("id"));
        assertEquals("c2", limited.get(1).get("id"));
    }

    @Test
    @DisplayName("suggest：q 为空仅返回最近收件人（撰写页初始下拉）；前缀匹配 name/email")
    void test_suggest_emptyQueryReturnsRecentOnly() {
        MailContact contact = new MailContact();
        contact.setId("c1");
        contact.setName("张三");
        contact.setEmail("zhang@x.com");
        doReturn(List.of(contact)).when(service).listEntities(null);

        MailContactRecent recent = new MailContactRecent();
        recent.setEmail("wang@x.com");
        recent.setName("王五");
        when(recentService.list(anyString(), anyInt())).thenReturn(List.of(recent));

        // q 为空：联系人数据源不启用，仅 recent
        List<Map<String, Object>> emptyQ = service.suggest("", 10);
        assertEquals(1, emptyQ.size());
        assertEquals("recent", emptyQ.get(0).get("source"));
        assertEquals("wang@x.com", emptyQ.get(0).get("email"));

        // q=zh：联系人 name 前缀命中 + recent 前缀过滤（mock 未过滤，由 merge 去重后并列）
        List<Map<String, Object>> zh = service.suggest("zh", 10);
        assertEquals("c1", zh.get(0).get("id"), "联系人优先于 recent");
    }

    @Test
    @DisplayName("suggest：limit 上限钳制到 50，非法值回退默认 10")
    void test_suggest_limitClamp() {
        doReturn(List.of()).when(service).listEntities(null);
        when(recentService.list(anyString(), anyInt())).thenReturn(List.of());
        service.suggest("a", 999);
        verify(recentService).list("a", 50);
        service.suggest("a", -1);
        verify(recentService).list("a", 10);
    }

    // ==================== toResponse ====================

    @Test
    @DisplayName("toResponse：createAt 输出 ISO 串；无统计时 mailCount/lastContactAt 缺席；可空字段缺席")
    void test_toResponse_contractShape() {
        MailContact contact = new MailContact();
        contact.setId("c1");
        contact.setName("张三");
        contact.setEmail("zhang@x.com");
        contact.setCreateAt(Date.from(java.time.Instant.parse("2026-08-12T05:00:00Z")));

        Map<String, Object> noStat = service.toResponse(contact, null);
        assertEquals("2026-08-12T05:00:00Z", noStat.get("createdAt"));
        assertFalse(noStat.containsKey("mailCount"), "无往来统计时 mailCount 缺席（前端 optional 字段）");
        assertFalse(noStat.containsKey("phone"), "phone 为 null 时缺席");

        Map<String, Object> withStat = service.toResponse(contact, new long[]{3L, 1786500000000L});
        assertEquals(3, withStat.get("mailCount"));
        assertTrue(withStat.containsKey("lastContactAt"));
    }

    // ==================== batchDelete ====================

    @Test
    @DisplayName("batchDelete：越权/不存在 id 跳过，返回实际删除数，命中行逻辑删除")
    void test_batchDelete_skipsForeign() {
        MailContact mine = new MailContact();
        mine.setId("mine");
        org.mockito.Mockito.doAnswer(inv -> {
            String id = inv.getArgument(0);
            return "mine".equals(id) ? mine : null;
        }).when(service).getOwned(anyString());

        int affected = service.batchDelete(List.of("mine", "foreign", "ghost"));

        assertEquals(1, affected);
        assertEquals(1, mine.getDelStatus());
        verify(dynamicDao, times(1)).save(mine);
    }

    private static Map<String, Object> item(String id, String name, String email, String source) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("email", email);
        item.put("source", source);
        return item;
    }
}
