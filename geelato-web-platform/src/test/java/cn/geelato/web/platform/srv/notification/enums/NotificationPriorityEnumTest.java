package cn.geelato.web.platform.srv.notification.enums;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站内信重要级别枚举单元测试：值域四级（0普通/1提醒/2重要/3紧急），
 * of 对非法值硬失败且错误信息列明完整值域（发送侧校验依赖该契约）。
 */
class NotificationPriorityEnumTest {

    @Test
    void ofMapsAllValidValues() {
        assertEquals(NotificationPriorityEnum.NORMAL, NotificationPriorityEnum.of(0));
        assertEquals(NotificationPriorityEnum.REMIND, NotificationPriorityEnum.of(1));
        assertEquals(NotificationPriorityEnum.IMPORTANT, NotificationPriorityEnum.of(2));
        assertEquals(NotificationPriorityEnum.URGENT, NotificationPriorityEnum.of(3));
    }

    @Test
    void ofRejectsInvalidValuesWithFullRange() {
        for (int invalid : new int[]{-1, 4, 99}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> NotificationPriorityEnum.of(invalid));
            // 错误信息必须指明非法原值与完整合法值域，供调用方/外部系统直接定位
            assertTrue(ex.getMessage().contains(String.valueOf(invalid)), ex.getMessage());
            assertTrue(ex.getMessage().contains("0(普通)"), ex.getMessage());
            assertTrue(ex.getMessage().contains("1(提醒)"), ex.getMessage());
            assertTrue(ex.getMessage().contains("2(重要)"), ex.getMessage());
            assertTrue(ex.getMessage().contains("3(紧急)"), ex.getMessage());
        }
    }

    @Test
    void isValidCoversExactlyTheValueRange() {
        assertTrue(NotificationPriorityEnum.isValid(0));
        assertTrue(NotificationPriorityEnum.isValid(3));
        assertFalse(NotificationPriorityEnum.isValid(-1));
        assertFalse(NotificationPriorityEnum.isValid(4));
    }

    @Test
    void tryOfReturnsEmptyForInvalidInsteadOfThrowing() {
        assertEquals(Optional.of(NotificationPriorityEnum.URGENT), NotificationPriorityEnum.tryOf(3));
        assertEquals(Optional.empty(), NotificationPriorityEnum.tryOf(5));
    }

    @Test
    void labelsAreChinese() {
        assertEquals("普通", NotificationPriorityEnum.NORMAL.label());
        assertEquals("提醒", NotificationPriorityEnum.REMIND.label());
        assertEquals("重要", NotificationPriorityEnum.IMPORTANT.label());
        assertEquals("紧急", NotificationPriorityEnum.URGENT.label());
    }
}
