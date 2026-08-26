package cn.geelato.core.meta.model.column;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link ColumnMeta#ColumnMeta(Map)} 从 platform_dev_column 行 Map 装载时
 * 必填（is_nullable）与唯一（is_unique）的读取：bit(1) 经 JDBC 映射为 Boolean，
 * 缺键时保持字段默认语义（nullable=true、uniqued=false）。
 */
class ColumnMetaMapLoadTest {

    @Test
    void shouldLoadNullableAndUniqueFromMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("column_name", "user_name");
        map.put("is_nullable", Boolean.FALSE);
        map.put("is_unique", Boolean.TRUE);

        ColumnMeta meta = new ColumnMeta(map);

        assertFalse(meta.isNullable());
        assertTrue(meta.isUniqued());
    }

    @Test
    void shouldKeepDefaultsWhenKeysAbsent() {
        ColumnMeta meta = new ColumnMeta(new HashMap<>());

        assertTrue(meta.isNullable());
        assertFalse(meta.isUniqued());
    }

    @Test
    void shouldLoadExplicitNullableAndNotUnique() {
        Map<String, Object> map = new HashMap<>();
        map.put("is_nullable", Boolean.TRUE);
        map.put("is_unique", Boolean.FALSE);
        map.put("column_key", Boolean.TRUE);

        ColumnMeta meta = new ColumnMeta(map);

        assertTrue(meta.isNullable());
        assertFalse(meta.isUniqued());
        assertTrue(meta.isKey());
    }
}
