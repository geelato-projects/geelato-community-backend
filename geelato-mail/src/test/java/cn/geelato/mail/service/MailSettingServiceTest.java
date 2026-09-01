package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.mail.entity.MailSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * MailSettingService 单元测试（P3-V79 通用设置）。
 *
 * 覆盖场景：
 * - patchGeneral：键白名单/布尔类型/枚举/数值范围校验 fail-fast
 * - patchGeneral：upsert 语义（无行新建 / 有行合并，键缺失不动、出现覆盖）
 * - getGeneral：未保存返回默认值快照
 */
class MailSettingServiceTest {

    private static final String USER_ID = "user-001";
    private static final String USER_NAME = "张三";
    private static final String TENANT_CODE = "geelato";

    private MailSettingService service;
    private Dao dynamicDao;

    @BeforeEach
    void setUp() {
        dynamicDao = mock(Dao.class);
        service = spy(new MailSettingService());
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

    // ==================== 校验 ====================

    @Test
    @DisplayName("patchGeneral：空 patch fail-fast")
    void patchEmptyFails() {
        assertThrows(IllegalArgumentException.class, () -> service.patchGeneral(null));
        assertThrows(IllegalArgumentException.class, () -> service.patchGeneral(Map.of()));
    }

    @Test
    @DisplayName("patchGeneral：未知设置键 fail-fast")
    void patchUnknownKeyFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.patchGeneral(Map.of("unknownKey", true)));
        assertTrue(e.getMessage().contains("不支持的设置项"));
    }

    @Test
    @DisplayName("patchGeneral：布尔字段传非布尔 fail-fast")
    void patchNonBooleanFails() {
        assertThrows(IllegalArgumentException.class,
                () -> service.patchGeneral(Map.of("showMailSize", "yes")));
    }

    @Test
    @DisplayName("patchGeneral：density/viewMode 枚举非法 fail-fast")
    void patchEnumFails() {
        assertThrows(IllegalArgumentException.class,
                () -> service.patchGeneral(Map.of("density", "large")));
        assertThrows(IllegalArgumentException.class,
                () -> service.patchGeneral(Map.of("viewMode", "grid")));
        assertDoesNotThrow(() -> {
            doReturn(null).when(service).findByKey(any(), any());
            service.patchGeneral(Map.of("density", "compact", "viewMode", "conversation"));
        });
    }

    @Test
    @DisplayName("patchGeneral：itemsPerPage/trashCleanupDays 范围校验（null=永不清理合法）")
    void patchRangeFails() {
        assertThrows(IllegalArgumentException.class,
                () -> service.patchGeneral(Map.of("itemsPerPage", 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.patchGeneral(Map.of("itemsPerPage", 201)));
        assertThrows(IllegalArgumentException.class,
                () -> service.patchGeneral(Map.of("trashCleanupDays", -1)));

        doReturn(null).when(service).findByKey(any(), any());
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("trashCleanupDays", null);
        assertDoesNotThrow(() -> service.patchGeneral(patch));
    }

    // ==================== upsert ====================

    @Test
    @DisplayName("patchGeneral：无行新建（upsert insert），审计/租户字段齐全")
    void patchInsertWhenAbsent() {
        doReturn(null).when(service).findByKey(any(), any());
        service.patchGeneral(Map.of("itemsPerPage", 50));

        ArgumentCaptor<MailSetting> captor = ArgumentCaptor.forClass(MailSetting.class);
        verify(dynamicDao).save(captor.capture());
        MailSetting saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(MailSettingService.KEY_GENERAL, saved.getSettingKey());
        assertEquals(TENANT_CODE, saved.getTenantCode());
        assertEquals(USER_ID, saved.getCreator());
        assertEquals(USER_NAME, saved.getCreatorName());
        assertTrue(saved.getSettingValue().contains("\"itemsPerPage\":50"));
    }

    @Test
    @DisplayName("patchGeneral：有行合并（键缺失不动、出现覆盖）")
    void patchMergeWhenPresent() {
        MailSetting existing = new MailSetting();
        existing.setId("row-1");
        existing.setUserId(USER_ID);
        existing.setSettingKey(MailSettingService.KEY_GENERAL);
        existing.setSettingValue("{\"density\":\"compact\",\"itemsPerPage\":20}");
        doReturn(existing).when(service).findByKey(any(), any());

        service.patchGeneral(Map.of("itemsPerPage", 100));

        ArgumentCaptor<MailSetting> captor = ArgumentCaptor.forClass(MailSetting.class);
        verify(dynamicDao).save(captor.capture());
        String json = captor.getValue().getSettingValue();
        assertTrue(json.contains("\"itemsPerPage\":100"), "出现字段被覆盖: " + json);
        assertTrue(json.contains("\"density\":\"compact\""), "缺失字段保留: " + json);
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("getGeneral：未保存过返回默认值快照（10 字段）")
    void getDefaults() {
        doReturn(null).when(service).findByKey(any(), any());
        Map<String, Object> result = service.getGeneral();
        assertEquals("default", result.get("density"));
        assertEquals("list", result.get("viewMode"));
        assertEquals(20, result.get("itemsPerPage"));
        assertEquals(Boolean.TRUE, result.get("enableNotifications"));
        assertEquals(10, result.size());
    }

    @Test
    @DisplayName("getGeneral：已保存按默认值兜底合并缺失字段")
    void getMergedWithDefaults() {
        MailSetting existing = new MailSetting();
        existing.setSettingValue("{\"density\":\"compact\"}");
        doReturn(existing).when(service).findByKey(any(), any());
        Map<String, Object> result = service.getGeneral();
        assertEquals("compact", result.get("density"));
        assertEquals(20, result.get("itemsPerPage"), "未保存字段取默认值");
        assertEquals(10, result.size());
    }
}
