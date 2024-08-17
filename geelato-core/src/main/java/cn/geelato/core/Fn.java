package cn.geelato.core;

import cn.geelato.utils.DateUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author diabl
 */
public class Fn {
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DateUtils.DATE);
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DateUtils.DATETIME);

    public static String nowDateTime() {
        // 格式化当前日期
        return LocalDateTime.now().format(dateTimeFormatter);
    }

    public static String nowDate() {
        // 格式化当前日期
        return LocalDate.now().format(dateFormatter);
    }
}
