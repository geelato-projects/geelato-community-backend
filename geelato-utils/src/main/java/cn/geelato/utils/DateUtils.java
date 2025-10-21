package cn.geelato.utils;

import cn.geelato.utils.enums.LocaleEnum;
import cn.geelato.utils.enums.TimeUnitEnum;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

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

    public static final String DEFAULT_DELETE_AT = "1970-01-01 00:00:00";

    public static final String TIMEZONE = "GMT+8";

    public static final String TIME_ZONE_SIGN = "zzz";
    public static final String REGEX_INTEGER = "^-?\\d+$";

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

    /**
     * 将指定格式的时间字符串转换为另一种格式的时间字符串
     *
     * @param time     待转换的时间字符串
     * @param parse    原始时间字符串的格式
     * @param format   目标时间字符串的格式
     * @param timeZone 时间时区
     * @param locale   地区设置
     * @return 转换后的时间字符串
     * @throws ParseException 如果时间字符串的格式与指定的格式不匹配，则抛出此异常
     */
    public static String convertTime(String time, String parse, String format, String timeZone, String locale) throws ParseException {
        if (StringUtils.isNotBlank(time)) {
            SimpleDateFormat parseSDF = buildSimpleDateFormat(parse, timeZone, locale);
            Date date = parseSDF.parse(time);
            // 补全日期信息
            date = completeDate(date, parse, format);
            // 将Date对象转换为目标格式的字符串
            SimpleDateFormat formatSDF = buildSimpleDateFormat(format, timeZone, locale);
            return formatSDF.format(date);
        }
        return null;
    }

    /**
     * 补全日期信息
     *
     * @param date   需要补全的日期对象
     * @param parse  实际解析的日期字符串
     * @param format 日期格式
     * @return 补全后的日期对象
     */
    public static Date completeDate(Date date, String parse, String format) {
        // 检查parse中没有年份但format中有年份的情况
        if (!hasYear(parse) && hasYear(format)) {
            // 获取当前日历实例
            Calendar calendar = Calendar.getInstance();
            int currentYear = calendar.get(Calendar.YEAR);

            // 设置解析出的日期的年份为当前年份
            calendar.setTime(date);
            calendar.set(Calendar.YEAR, currentYear);
            date = calendar.getTime();
        }
        return date;
    }

    /**
     * 检查字符串中是否包含年份标识符(y或Y)
     *
     * @param str 要检查的字符串
     * @return 是否包含年份标识符
     */
    private static boolean hasYear(String str) {
        if (str == null) {
            return false;
        }
        return str.indexOf('y') != -1 || str.indexOf('Y') != -1;
    }

    /**
     * 构建一个SimpleDateFormat对象
     *
     * @param format   日期格式字符串
     * @param timeZone 时区字符串
     * @param locale   地域字符串
     * @return 构建好的SimpleDateFormat对象
     */
    private static SimpleDateFormat buildSimpleDateFormat(String format, String timeZone, String locale) throws ParseException {
        if (format.contains(DateUtils.TIME_ZONE_SIGN) && StringUtils.isBlank(timeZone)) {
            throw new ParseException("Time zone is not specified", 0);
        }
        // 解析时间字符串为Date对象
        Locale le = LocaleEnum.getDefaultLocale(locale);
        SimpleDateFormat sdf = new SimpleDateFormat(format, le);
        if (format.contains(DateUtils.TIME_ZONE_SIGN) && StringUtils.isNotBlank(timeZone)) {
            TimeZone tz = TimeZone.getTimeZone(timeZone);
            sdf.setTimeZone(tz);
        }
        return sdf;
    }

    /**
     * 根据给定的时间、格式、数量和时间单位计算新的时间
     *
     * @param time   原始时间字符串
     * @param format 时间格式
     * @param amount 增减的时间数量
     * @param unit   时间单位
     * @return 计算后的时间字符串
     * @throws ParseException 如果解析时间字符串时发生错误，则抛出此异常
     */
    public static String calculateTime(String time, String format, String amount, String unit) throws ParseException {
        if (StringUtils.isNotBlank(time)) {
            // 将时间字符串转换为Date对象
            Date date = new SimpleDateFormat(format).parse(time);
            // 获取当前时间的Calendar对象
            Calendar calendar = Calendar.getInstance();
            int unitValue = TimeUnitEnum.getValueByName(unit);
            calendar.setTime(date);
            // 根据时间单位和数量进行时间计算
            if (unitValue > -1 && amount.matches(DateUtils.REGEX_INTEGER)) {
                calendar.add(unitValue, Integer.parseInt(amount));
            }
            return new SimpleDateFormat(format).format(calendar.getTime());
        }
        return null;
    }

    /**
     * 根据给定的时间单位和数量计算时间，并返回格式化后的时间字符串。
     * 当前时间
     *
     * @param amount 时间数量，必须为整数。
     * @param unit   时间单位，对应TimeUnitEnum枚举类中的名称。
     * @return 格式化后的时间字符串，格式为"yyyy-MM-dd HH:mm:ss"。
     */
    public static Date calculateTime(String amount, String unit) {
        // 将时间字符串转换为Date对象
        Date date = new Date();
        // 获取当前时间的Calendar对象
        Calendar calendar = Calendar.getInstance();
        int unitValue = TimeUnitEnum.getValueByName(unit);
        calendar.setTime(date);
        // 根据时间单位和数量进行时间计算
        if (unitValue > -1 && amount.matches(DateUtils.REGEX_INTEGER)) {
            calendar.add(unitValue, Integer.parseInt(amount));
        }
        return calendar.getTime();
    }

    public static Date parse(String time, String parse) {
        Date date = null;
        if (StringUtils.isNotBlank(time)) {
            try {
                SimpleDateFormat parseSDF = buildSimpleDateFormat(parse, null, null);
                date = parseSDF.parse(time);
            } catch (ParseException ignored) {
            }
        }
        return date;
    }

    public static Date defaultDeleteAt() {
        return parse(DateUtils.DEFAULT_DELETE_AT, DateUtils.DATETIME);
    }
}
