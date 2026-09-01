package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.mail.entity.MailVacation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * MailVacationService 单元测试（P3-V79 假期自动回复配置）。
 *
 * 覆盖场景：
 * - put：主题/正文长度、时间格式、时间先后校验 fail-fast
 * - put：upsert 语义（无行新建 / 有行替换），lastSentAt 不被客户端覆盖
 * - get：未配置返回默认快照（enabled=false）
 */
class MailVacationServiceTest {

    private static final String USER_ID = "user-001";
    private static final String USER_NAME = "张三";
    private static final String TENANT_CODE = "geelato";

    private MailVacationService service;
    private Dao dynamicDao;

    @BeforeEach
    void setUp() {
        dynamicDao = mock(Dao.class);
        service = spy(new MailVacationService());
        ReflectionTestUtils.setField(service, "dynamicDao", dynamicDao);
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

    @Test
    @DisplayName("put：主题超长/时间格式非法/开始晚于结束 fail-fast")
    void putValidationFails() {
        assertThrows(IllegalArgumentException.class, () -> service.put(
                true, "s".repeat(257), "c", false, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.put(
                true, "s", "c", false, "2026-08-12 08:00:00", null));
        assertThrows(IllegalArgumentException.class, () -> service.put(
                true, "s", "c", false, "2026-08-13T00:00:00Z", "2026-08-12T00:00:00Z"));
    }

    @Test
    @DisplayName("put：无行新建（upsert insert），审计/租户字段齐全，时间解析为 Date")
    void putInsertWhenAbsent() {
        doReturn(null).when(service).findOwned();
        service.put(true, "休假中", "<p>下周返岗</p>", true,
                "2026-08-20T00:00:00Z", "2026-08-27T00:00:00Z");

        ArgumentCaptor<MailVacation> captor = ArgumentCaptor.forClass(MailVacation.class);
        verify(dynamicDao).save(captor.capture());
        MailVacation saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(TENANT_CODE, saved.getTenantCode());
        assertEquals(USER_NAME, saved.getCreatorName());
        assertEquals(1, saved.getEnabled());
        assertEquals(1, saved.getOnlyContacts());
        assertEquals(Date.from(java.time.Instant.parse("2026-08-20T00:00:00Z")), saved.getStartTime());
        assertEquals(Date.from(java.time.Instant.parse("2026-08-27T00:00:00Z")), saved.getEndTime());
    }

    @Test
    @DisplayName("put：有行替换，lastSentAt 保留不被客户端清除")
    void putUpdateKeepsLastSentAt() {
        MailVacation existing = new MailVacation();
        existing.setId("v-1");
        existing.setUserId(USER_ID);
        existing.setEnabled(1);
        existing.setLastSentAt(new Date(1700000000000L));
        doReturn(existing).when(service).findOwned();

        service.put(false, "新主题", "新正文", false, null, null);

        ArgumentCaptor<MailVacation> captor = ArgumentCaptor.forClass(MailVacation.class);
        verify(dynamicDao).save(captor.capture());
        MailVacation saved = captor.getValue();
        assertEquals(0, saved.getEnabled());
        assertEquals("新主题", saved.getSubject());
        assertNull(saved.getStartTime());
        assertEquals(new Date(1700000000000L), saved.getLastSentAt(), "lastSentAt 仅引擎回写");
    }

    @Test
    @DisplayName("get：未配置返回默认快照 enabled=false")
    void getDefaults() {
        doReturn(null).when(service).findOwned();
        Map<String, Object> result = service.get();
        assertEquals(Boolean.FALSE, result.get("enabled"));
        assertEquals("", result.get("subject"));
        assertEquals("", result.get("content"));
        assertEquals(Boolean.FALSE, result.get("onlyContacts"));
        assertFalse(result.containsKey("startTime"));
    }

    @Test
    @DisplayName("get：已配置完整回显（时间转 ISO 串）")
    void getConfigured() {
        MailVacation existing = new MailVacation();
        existing.setEnabled(1);
        existing.setSubject("休假中");
        existing.setContent("<p>x</p>");
        existing.setOnlyContacts(0);
        existing.setStartTime(Date.from(java.time.Instant.parse("2026-08-20T00:00:00Z")));
        doReturn(existing).when(service).findOwned();
        Map<String, Object> result = service.get();
        assertEquals(Boolean.TRUE, result.get("enabled"));
        assertEquals("2026-08-20T00:00:00Z", result.get("startTime"));
        assertFalse(result.containsKey("endTime"));
    }
}
