package cn.geelato.web.platform.m.ocr.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrUtils {

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
}
