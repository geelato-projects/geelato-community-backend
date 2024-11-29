package cn.geelato.web.platform.m.ocr.service;

import cn.geelato.web.platform.m.base.entity.Base64Info;
import cn.geelato.web.platform.m.ocr.enums.LocaleEnum;
import cn.geelato.web.platform.m.ocr.enums.TimeUnitEnum;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrUtils {
    public static final String TIME_ZONE_SIGN = "zzz";
    public static final String REGEX_INTEGER = "^-?\\d+$";

    /**
     * 根据Base64编码的字符串生成临时文件
     *
     * @param base64 包含Base64编码信息的字符串
     * @return 生成的临时文件对象
     * @throws IOException      如果在文件操作过程中发生I/O错误，则抛出此异常
     * @throws RuntimeException 如果提供的Base64信息格式错误，则抛出此异常，并附带“模板格式错误”的错误信息
     */
    public static File getTempFile(String base64) throws IOException {
        File tempFile = null;
        Base64Info bi = JSON.parseObject(base64, Base64Info.class);
        if (bi != null && Strings.isNotBlank(bi.getName()) && Strings.isNotBlank(bi.getBase64())) {
            byte[] decodedBytes = Base64.getDecoder().decode(bi.getBase64());
            String fileExt = bi.getName().substring(bi.getName().lastIndexOf("."));
            tempFile = File.createTempFile("temp_base64_" + UUID.randomUUID(), fileExt);
            tempFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(decodedBytes);
            }
        } else {
            throw new RuntimeException("base64 format error");
        }
        if (tempFile == null || !tempFile.exists()) {
            throw new RuntimeException("base64 temp file create error");
        }
        return tempFile;
    }

    /**
     * 从给定内容中提取与正则表达式匹配的子串
     *
     * @param str   待处理的内容字符串
     * @param regex 用于匹配内容的正则表达式
     * @return 包含所有匹配子串的字符串，如果没有匹配项则返回空字符串
     */
    public static String extract(String str, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(str);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            result.append(matcher.group());
        }
        return result.toString();
    }

    /**
     * 将字符串转换为字符串列表（不去重）
     *
     * @param arrString 待转换的字符串,以逗号分隔
     * @return 转换后的字符串列表
     */
    public static List<String> stringToList(String arrString) {
        return stringToList(arrString, null, false);
    }

    /**
     * 将字符串转换为字符串列表（去重）
     *
     * @param arrString 待转换的字符串,以逗号分隔
     * @return 转换后的字符串列表
     */
    public static List<String> stringToListDr(String arrString) {
        return stringToList(arrString, null, true);
    }

    /**
     * 将以指定分隔符分隔的字符串转换为字符串列表
     *
     * @param arrString    待转换的字符串
     * @param split        分隔符
     * @param deRepetition 是否去重
     * @return 转换后的字符串列表，如果输入字符串为空或分割后没有有效项，则返回空列表
     */
    public static List<String> stringToList(String arrString, String split, boolean deRepetition) {
        List<String> list = new ArrayList<>();
        split = split == null ? "," : split;
        if (arrString != null) {
            String[] arr = arrString.split(split);
            if (arr != null) {
                for (String item : arr) {
                    if (deRepetition) {
                        if (Strings.isNotBlank(item) && !list.contains(item)) {
                            list.add(item);
                        }
                    } else {
                        list.add(item);
                    }
                }
            }
        }
        return list;
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
        // 将时间字符串转换为Date对象
        Date date = new SimpleDateFormat(format).parse(time);
        // 获取当前时间的Calendar对象
        Calendar calendar = Calendar.getInstance();
        int unitValue = TimeUnitEnum.getValueByName(unit);
        calendar.setTime(date);
        // 根据时间单位和数量进行时间计算
        if (unitValue > -1 && amount.matches(OcrUtils.REGEX_INTEGER)) {
            calendar.add(unitValue, Integer.parseInt(amount));
        }
        return new SimpleDateFormat(format).format(calendar.getTime());
    }

    /**
     * 移除字符串末尾的换行符（包括"\r\n"、"\n"或"\r"）
     *
     * @param str 待处理的字符串
     * @return 移除换行符后的字符串
     */
    public static String removeLf(String str) {
        if (str != null) {
            if (str.endsWith("\r\n")) {
                str = str.substring(0, str.length() - 2);
            } else if (str.endsWith("\n")) {
                str = str.substring(0, str.length() - 1);
            } else if (str.endsWith("\r")) {
                str = str.substring(0, str.length() - 1);
            }
        }

        return str;
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
        Date date = convertTime(time, parse, timeZone, locale);
        return new SimpleDateFormat(format).format(date);
    }

    /**
     * 将指定格式的字符串转换为Date对象
     *
     * @param time     待转换的时间字符串
     * @param parse    时间字符串的格式
     * @param timeZone 时间时区
     * @param locale   地区设置
     * @return 转换后的Date对象
     * @throws ParseException 如果时间字符串的格式与指定的格式不匹配，则抛出此异常
     */
    public static Date convertTime(String time, String parse, String timeZone, String locale) throws ParseException {
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        Locale le = LocaleEnum.getDefaultLocale(locale);
        SimpleDateFormat sdf = new SimpleDateFormat(parse, le);
        sdf.setTimeZone(tz);
        return sdf.parse(time);
    }

}
