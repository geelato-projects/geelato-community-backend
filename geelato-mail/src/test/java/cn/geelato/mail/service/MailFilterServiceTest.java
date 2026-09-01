package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.mail.entity.MailFilter;
import cn.geelato.mail.entity.MailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MailFilterService 单元测试（P3-V79 过滤器）。
 *
 * 覆盖场景：
 * - matches：条件匹配矩阵（from/to/subject/body 文本、size 数值、attachment 布尔、AND 语义、空条件安全默认）
 * - create：条件/动作结构校验 fail-fast（白名单/互斥/size 数字/名称长度）
 * - reorder：按 ids 顺序重排 1..n；越权 id fail-fast
 * - applyToExisting：匹配邮件应用动作（markRead/move/markStar）+ 不匹配不动 + 写应用历史
 */
class MailFilterServiceTest {

    private static final String USER_ID = "user-001";
    private static final String USER_NAME = "张三";
    private static final String TENANT_CODE = "geelato";

    private MailFilterService service;
    private Dao dynamicDao;
    private MailLabelService labelService;

    @BeforeEach
    void setUp() {
        dynamicDao = mock(Dao.class);
        labelService = mock(MailLabelService.class);
        service = spy(new MailFilterService());
        ReflectionTestUtils.setField(service, "dynamicDao", dynamicDao);
        ReflectionTestUtils.setField(service, "labelService", labelService);
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

    // ==================== 匹配引擎 ====================

    private MailMessage newMail() {
        MailMessage msg = new MailMessage();
        msg.setFromEmail("boss@example.com");
        msg.setFromName("Boss");
        msg.setToJson("[{\"email\":\"me@example.com\",\"name\":\"Me\"}]");
        msg.setCcJson("[]");
        msg.setSubject("Quarterly Report");
        msg.setContentText("please review the draft");
        msg.setPreview("please review");
        msg.setMailSize(2048);
        msg.setHasAttachment(1);
        msg.setFolder("inbox");
        msg.setReadStatus("unread");
        msg.setFlagsJson("[]");
        msg.setLabelIds("[]");
        return msg;
    }

    private Map<String, Object> cond(String field, String operator, Object value) {
        return Map.of("field", field, "operator", operator, "value", value);
    }

    @Test
    @DisplayName("matches：文本条件大小写不敏感（contains/equals/startsWith/endsWith/notContains）")
    void matchTextOperators() {
        MailMessage msg = newMail();
        assertTrue(MailFilterService.matches(msg, List.of(cond("subject", "contains", "quarterly"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("subject", "equals", "quarterly report"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("subject", "startsWith", "quart"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("subject", "endsWith", "REPORT"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("subject", "notContains", "urgent"))));
        assertFalse(MailFilterService.matches(msg, List.of(cond("subject", "contains", "urgent"))));
        // gt/lt 对文本字段不命中
        assertFalse(MailFilterService.matches(msg, List.of(cond("subject", "gt", "abc"))));
    }

    @Test
    @DisplayName("matches：from 同时匹配地址与显示名；to 匹配 to/cc 地址与显示名")
    void matchFromTo() {
        MailMessage msg = newMail();
        assertTrue(MailFilterService.matches(msg, List.of(cond("from", "contains", "boss@example.com"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("from", "equals", "boss@example.com boss"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("to", "contains", "me@example.com"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("body", "contains", "review the draft"))));
    }

    @Test
    @DisplayName("matches：size 数值比较（gt/lt/equals；非数字不命中）")
    void matchSize() {
        MailMessage msg = newMail();
        assertTrue(MailFilterService.matches(msg, List.of(cond("size", "gt", "1024"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("size", "lt", "4096"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("size", "equals", "2048"))));
        assertFalse(MailFilterService.matches(msg, List.of(cond("size", "gt", "2048"))));
        assertFalse(MailFilterService.matches(msg, List.of(cond("size", "contains", "2048"))));
        assertFalse(MailFilterService.matches(msg, List.of(cond("size", "gt", "abc"))));
    }

    @Test
    @DisplayName("matches：attachment 布尔比较（equals true/1/yes）")
    void matchAttachment() {
        MailMessage msg = newMail();
        assertTrue(MailFilterService.matches(msg, List.of(cond("attachment", "equals", "true"))));
        assertTrue(MailFilterService.matches(msg, List.of(cond("attachment", "equals", "1"))));
        assertFalse(MailFilterService.matches(msg, List.of(cond("attachment", "equals", "false"))));
        assertFalse(MailFilterService.matches(msg, List.of(cond("attachment", "contains", "true"))));
    }

    @Test
    @DisplayName("matches：多条件 AND 语义；空条件数组不匹配任何邮件（安全默认）")
    void matchAndSemantics() {
        MailMessage msg = newMail();
        assertTrue(MailFilterService.matches(msg, List.of(
                cond("from", "contains", "boss"), cond("subject", "contains", "report"))));
        assertFalse(MailFilterService.matches(msg, List.of(
                cond("from", "contains", "boss"), cond("subject", "contains", "urgent"))));
        assertFalse(MailFilterService.matches(msg, List.of()));
        assertFalse(MailFilterService.matches(msg, null));
    }

    // ==================== 创建校验 ====================

    @Test
    @DisplayName("create：合法过滤器落库（审计/租户字段 + sortOrder 缺省递增）")
    void createValid() {
        doReturn(new ArrayList<MailFilter>()).when(service).listEntities();
        MailFilter filter = service.create("  老板邮件  ", null,
                List.of(cond("from", "contains", "boss")),
                Map.of("markRead", true, "move", "archive"), null, null);
        assertEquals("老板邮件", filter.getName());
        assertEquals(1, filter.getEnabled());
        assertEquals(1, filter.getSortOrder());

        ArgumentCaptor<MailFilter> captor = ArgumentCaptor.forClass(MailFilter.class);
        verify(dynamicDao).save(captor.capture());
        MailFilter saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(TENANT_CODE, saved.getTenantCode());
        assertEquals(USER_NAME, saved.getCreatorName());
        assertTrue(saved.getConditionsJson().contains("\"contains\""));
        assertTrue(saved.getActionJson().contains("\"markRead\":true"));
    }

    @Test
    @DisplayName("create：非法字段/运算符/动作键/名称超长 fail-fast")
    void createValidationFails() {
        assertThrows(IllegalArgumentException.class, () -> service.create("x", null,
                List.of(cond("weird", "contains", "v")), Map.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.create("x", null,
                List.of(cond("from", "regex", "v")), Map.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.create("x", null,
                List.of(cond("from", "contains", "v")), Map.of("explode", true), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.create("x", null,
                List.of(), Map.of("move", "archive", "delete", true), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.create("x", null,
                List.of(), Map.of("move", "not_a_folder"), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.create("x", null,
                List.of(cond("size", "gt", "abc")), Map.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.create("   ", null,
                List.of(), Map.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.create("n".repeat(129), null,
                List.of(), Map.of(), null, null));
    }

    // ==================== 排序 ====================

    @Test
    @DisplayName("reorder：按 ids 顺序重排 sortOrder 为 1..n")
    void reorderSuccess() {
        MailFilter a = new MailFilter();
        a.setId("a");
        a.setSortOrder(5);
        MailFilter b = new MailFilter();
        b.setId("b");
        b.setSortOrder(9);
        doReturn(a).when(service).getOwned("a");
        doReturn(b).when(service).getOwned("b");

        service.reorder(List.of("b", "a"));

        assertEquals(1, b.getSortOrder());
        assertEquals(2, a.getSortOrder());
        verify(dynamicDao, times(2)).save(any(MailFilter.class));
    }

    @Test
    @DisplayName("reorder：空列表/越权 id fail-fast")
    void reorderFails() {
        assertThrows(IllegalArgumentException.class, () -> service.reorder(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(null));
        doReturn(null).when(service).getOwned(anyString());
        assertThrows(IllegalArgumentException.class, () -> service.reorder(List.of("ghost")));
    }

    // ==================== 应用到既有邮件 ====================

    @Test
    @DisplayName("applyToExisting：匹配邮件应用 markRead+move，不匹配不动，写应用历史")
    void applyToExistingApplies() {
        MailMessage hit = newMail();
        MailMessage miss = newMail();
        miss.setFromEmail("other@example.com");
        miss.setFromName("Other");
        doReturn(List.of(hit, miss)).when(service).listInboxMessages();

        MailFilter filter = new MailFilter();
        filter.setId("f-1");
        filter.setName("老板邮件归档");
        filter.setConditionsJson("[{\"field\":\"from\",\"operator\":\"contains\",\"value\":\"boss\"}]");
        filter.setActionJson("{\"markRead\":true,\"move\":\"archive\"}");

        int applied = service.applyToExisting(filter);

        assertEquals(1, applied);
        assertEquals("read", hit.getReadStatus());
        assertEquals("archive", hit.getFolder());
        assertEquals("unread", miss.getReadStatus(), "不匹配邮件不动");
        assertEquals("inbox", miss.getFolder());
        // 命中邮件 save + 应用历史 save（miss 不写）
        verify(dynamicDao).save(any(MailMessage.class));
        verify(dynamicDao).save(any(cn.geelato.mail.entity.MailFilterApplyLog.class));
    }

    @Test
    @DisplayName("applyToExisting：引用不存在/越权标签 fail-fast 且零写入")
    void applyToExistingLabelGuard() {
        doReturn(Map.of()).when(labelService).mapByIds(any());

        MailFilter filter = new MailFilter();
        filter.setId("f-2");
        filter.setName("悬空标签");
        filter.setConditionsJson("[{\"field\":\"subject\",\"operator\":\"contains\",\"value\":\"report\"}]");
        filter.setActionJson("{\"label\":\"ghost-label\"}");

        assertThrows(IllegalArgumentException.class, () -> service.applyToExisting(filter));
        verify(dynamicDao, times(0)).save(any(MailMessage.class));
    }
}
