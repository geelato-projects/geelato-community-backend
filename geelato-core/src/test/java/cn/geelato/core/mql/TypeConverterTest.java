package cn.geelato.core.mql;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeConverterTest {

    @Test
    void shouldMapJavaTimeTypesToSqlTypes() {
        assertEquals("date", TypeConverter.toSqlTypeString(LocalDate.class));
        assertEquals("datetime", TypeConverter.toSqlTypeString(LocalDateTime.class));
        assertEquals("time", TypeConverter.toSqlTypeString(LocalTime.class));
    }

    @Test
    void shouldThrowWhenJavaTypeIsUnsupported() {
        assertThrows(RuntimeException.class, () -> TypeConverter.toSqlTypeString(Object.class));
    }
}
