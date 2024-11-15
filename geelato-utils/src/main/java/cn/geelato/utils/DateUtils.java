package cn.geelato.utils;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

/**
 * @author geemeta
 */
public class DateUtils {
    public static final String YEAR = "yyyy";
    public static final String MOTH = "yyyy-MM";
    public static final String DATE = "yyyy-MM-dd";
    public static final String TIME = "HH:mm:ss";
    public static final String DATESTART = "yyyy-MM-dd 00:00:00";
    public static final String DATEFINISH = "yyyy-MM-dd 23:59:59";
    public static final String DATEVARIETY = "yyyyMMddHHmmss";
    public static final String DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String TIMESTAMP = "yyyy-MM-dd HH:mm:ss.SSS";

    public static final String TIMEZONE = "GMT+8";

    public static Date asDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date asDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDate asLocalDate(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static LocalDateTime asLocalDateTime(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 获取文件上传的日期路径，格式为 yyyy/MM/dd/HH/mm/
     *
     * @return 返回按日期时间格式化的路径字符串
     */
    public static String getAttachDatePath() {
        Date date = new Date();
        SimpleDateFormat yyyyFt = new SimpleDateFormat("yyyy");
        SimpleDateFormat MMFt = new SimpleDateFormat("MM");
        SimpleDateFormat ddFt = new SimpleDateFormat("dd");
        SimpleDateFormat HHFt = new SimpleDateFormat("HH");
        SimpleDateFormat mmFt = new SimpleDateFormat("mm");

        return String.format("%s/%s/%s/%s/%s/", yyyyFt.format(date), MMFt.format(date), ddFt.format(date), HHFt.format(date), mmFt.format(date));
    }

    /**
     * 计算时间间隔
     * 根据传入的日期类型（年、月、日），计算从当前时间到下一个时间节点的时间间隔（秒）。
     *
     * @param dateType 日期类型，支持以下格式：
     *                 - "yyyy" 或 "yy"：表示以年为间隔，计算到明年今天的时间间隔
     *                 - "yyyyMM" 或 "yyMM"：表示以月为间隔，计算到下个月第一天的时间间隔
     *                 - "yyyyMMdd" 或 "yyMMdd"：表示以日为间隔，计算到明天的时间间隔
     * @return 返回时间间隔（秒）。如果传入的日期类型不支持，则返回-1。
     */
    public static long timeInterval(String dateType) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0); // 将秒数设置为0
        calendar.set(Calendar.MILLISECOND, 0); // 将毫秒数设置为0
        if (Arrays.asList(new String[]{"yyyy", "yy"}).contains(dateType)) {
            calendar.add(Calendar.YEAR, 1); // 将当前时间加上一年
            calendar.set(Calendar.DAY_OF_MONTH, 1); // 将天数设置为1,表示下个月的第一天
            calendar.set(Calendar.HOUR_OF_DAY, 0); // 将小时数设置为0,表示当天的零点
            calendar.set(Calendar.MINUTE, 0); // 将分钟数设置为0
        } else if (Arrays.asList(new String[]{"yyyyMM", "yyMM"}).contains(dateType)) {
            calendar.add(Calendar.MONTH, 1); // 将当前时间加上一个月
            calendar.set(Calendar.DAY_OF_MONTH, 1); // 将天数设置为1,表示下个月的第一天
            calendar.set(Calendar.HOUR_OF_DAY, 0); // 将小时数设置为0,表示当天的零点
            calendar.set(Calendar.MINUTE, 0); // 将分钟数设置为0
        } else if (Arrays.asList(new String[]{"yyyyMMdd", "yyMMdd"}).contains(dateType)) {
            calendar.add(Calendar.HOUR_OF_DAY, 24); // 将当前时间加上一天
        } else {
            return -1;
        }

        Date tonight = calendar.getTime(); // 获取今天晚上的时间
        long diff = tonight.getTime() - System.currentTimeMillis(); // 计算时间差(毫秒)
        return diff / 1000;
    }
}
