package cn.geelato.core.util;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MapUtilsTest {

    @Test
    public void shouldReturnValueWhenKeyExists() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "geelato");

        String value = MapUtils.getOrDefaultString(map, "name", "default");

        assertEquals("geelato", value);
    }

    @Test
    public void shouldReturnDefaultWhenKeyMissingOrNull() {
        Map<String, Object> map = new HashMap<>();
        map.put("nullValue", null);

        String missingString = MapUtils.getOrDefaultString(map, "missing", "default");
        String nullString = MapUtils.getOrDefaultString(map, "nullValue", "default");
        int missingInt = MapUtils.getOrDefaultInt(map, "missingInt", 10);

        assertEquals("default", missingString);
        assertEquals("default", nullString);
        assertEquals(10, missingInt);
    }

    @Test
    public void shouldThrowNumberFormatExceptionWhenValueNotInteger() {
        Map<String, Object> map = new HashMap<>();
        map.put("age", "notNumber");

        try {
            MapUtils.getOrDefaultInt(map, "age", 0);
            fail();
        } catch (NumberFormatException ex) {
            assertEquals(NumberFormatException.class, ex.getClass());
        }
    }
}

