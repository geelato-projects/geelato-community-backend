package cn.geelato.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;

/**
 * @author diabl
 */
public class Fn {
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String nowDateTime(){
        // 格式化当前日期
        return LocalDateTime.now().format(dateTimeFormatter);
    }

    public static String nowDate(){
        // 格式化当前日期
        return LocalDate.now().format(dateFormatter);
    }
}
