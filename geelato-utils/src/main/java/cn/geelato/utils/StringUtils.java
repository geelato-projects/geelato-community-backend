package cn.geelato.utils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 继承org.springframework.util.StringUtils,
 * 增加join
 *
 * @author geemeta
 */
public class StringUtils extends org.springframework.util.StringUtils {
    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("_([a-z])");

    /***
     * @param separator 连接字符串
     * @param array     需要连接的集合
     * @return
     */
    public static String join(String[] array, String separator) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0, len = array.length; i < len; i++) {
            if (i == (len - 1)) {
                sb.append(array[i]);
            } else {
                sb.append(array[i]).append(separator);
            }
        }
        return sb.toString();
    }

    public static String join(List<String> array, String separator) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0, len = array.size(); i < len; i++) {
            if (i == (len - 1)) {
                sb.append(array.get(i));
            } else {
                sb.append(array.get(i)).append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * @param repeatTimes 重复的次数
     * @param joinValue   重复拼接的内容、值
     * @param separator   拼接的连接符
     * @return
     */
    public static String join(int repeatTimes, String joinValue, String separator) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0, len = repeatTimes; i < len; i++) {
            if (i == (len - 1)) {
                sb.append(joinValue);
            } else {
                sb.append(joinValue).append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * 若字符串为null 或 为空，返回 true
     *
     * @param text
     * @return
     */
    public static boolean isEmpty(final String text) {
        return text == null || text.length() == 0;
    }

    /**
     * 若字符串为null 或 为空，返回 false
     *
     * @param text
     * @return
     */
    public static boolean isNotEmpty(final String text) {
        return !isEmpty(text);
    }

    /**
     * 若字符串为null 或 为空 或 空字符组成的，返回 true
     *
     * @param text
     * @return
     */
    public static boolean isBlank(final String text) {
        if (text != null && !text.isEmpty()) {
            for (int i = 0; i < text.length(); ++i) {
                char c = text.charAt(i);
                if (!Character.isWhitespace(c)) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    /**
     * 若字符串为null 或 为空 或 空字符组成的，返回 false
     *
     * @param text
     * @return
     */
    public static boolean isNotBlank(final String text) {
        return !isBlank(text);
    }

    /**
     * 字符串占位替换 "-{0}-{1}-{2}-"
     *
     * @param template 字符串模板
     * @param args     替换值
     * @return
     */
    public static String format(String template, Object... args) {
        if (isEmpty(template)) {
            return "";
        } else if (null != args && args.length != 0) {
            char[] templateChars = template.toCharArray();
            int templateLength = templateChars.length;
            int length = 0;
            int tokenCount = args.length;

            for (int i = 0; i < tokenCount; ++i) {
                Object sourceString = args[i];
                if (sourceString != null) {
                    length += sourceString.toString().length();
                }
            }

            StringBuilder buffer = new StringBuilder(length + templateLength);
            int lastStart = 0;

            for (int i = 0; i < templateLength; ++i) {
                char ch = templateChars[i];
                if (ch == '{' && i + 2 < templateLength && templateChars[i + 2] == '}') {
                    int tokenIndex = templateChars[i + 1] - 48;
                    if (tokenIndex >= 0 && tokenIndex < tokenCount) {
                        buffer.append(templateChars, lastStart, i - lastStart);
                        Object sourceString = args[tokenIndex];
                        if (sourceString != null) {
                            buffer.append(sourceString);
                        }

                        i += 2;
                        lastStart = i + 1;
                    }
                }
            }

            buffer.append(templateChars, lastStart, templateLength - lastStart);
            return new String(buffer);
        } else {
            return template;
        }
    }

    /**
     * _a 转成驼峰结构
     *
     * @param s 字符串
     * @return
     */
    public static String toCamelCase(String s) {
        Matcher matcher = UNDERLINE_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer(s);
        if (matcher.find()) {
            sb = new StringBuffer();
            matcher.appendReplacement(sb, matcher.group(1).toUpperCase(Locale.ENGLISH));
            matcher.appendTail(sb);
        } else {
            return sb.toString().replaceAll("_", "");
        }
        return toCamelCase(sb.toString());
    }
}
