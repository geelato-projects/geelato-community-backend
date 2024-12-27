package cn.geelato.utils;

import java.util.ArrayList;
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

    /**
     * 将字符串数组中的元素通过指定的分隔符连接成一个字符串。
     *
     * @param array     需要连接的字符串数组
     * @param separator 用于连接数组元素的分隔符
     * @return 连接后的字符串
     */
    public static String join(String[] array, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, len = array.length; i < len; i++) {
            if (i == (len - 1)) {
                sb.append(array[i]);
            } else {
                sb.append(array[i]).append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * 将字符串列表中的元素通过指定的分隔符连接成一个字符串。
     *
     * @param array     需要连接的字符串列表
     * @param separator 用于连接列表元素的分隔符
     * @return 连接后的字符串
     */
    public static String join(List<String> array, String separator) {
        StringBuilder sb = new StringBuilder();
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
     * 根据指定的重复次数和连接符，将指定的内容拼接成字符串。
     *
     * @param repeatTimes 重复的次数，表示要拼接的内容片段的数量
     * @param joinValue   需要重复拼接的内容或值
     * @param separator   用于连接各个内容片段的连接符
     * @return 拼接后的字符串
     */
    public static String join(int repeatTimes, String joinValue, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < repeatTimes; i++) {
            if (i == (repeatTimes - 1)) {
                sb.append(joinValue);
            } else {
                sb.append(joinValue).append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * 判断给定的字符串是否为null或空字符串。
     * 如果字符串为null或长度为0（即没有内容），则返回true；否则返回false。
     *
     * @param text 要判断的字符串
     * @return 如果字符串为null或空字符串，则返回true；否则返回false
     */
    public static boolean isEmpty(final String text) {
        return text == null || text.isEmpty();
    }

    /**
     * 判断字符串是否不为null且不为空。
     * 如果字符串不为null且长度大于0（即包含内容），则返回true；否则返回false。
     *
     * @param text 要判断的字符串
     * @return 如果字符串不为null且不为空，则返回true；否则返回false
     */
    public static boolean isNotEmpty(final String text) {
        return !isEmpty(text);
    }

    /**
     * 判断字符串是否为空、null或仅由空白字符组成。
     * 如果传入的字符串为null、空字符串或仅由空白字符（如空格、制表符等）组成，则返回true；否则返回false。
     *
     * @param text 需要判断的字符串
     * @return 如果字符串为空、null或仅由空白字符组成，则返回true；否则返回false
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
     * 判断字符串是否非空、非null且不由空白字符组成。
     * 如果字符串不为null，长度不为0，且不全部由空白字符（如空格、制表符等）组成，则返回true；
     * 否则返回false。
     *
     * @param text 要判断的字符串
     * @return 如果字符串非空、非null且不由空白字符组成，则返回true；否则返回false
     */
    public static boolean isNotBlank(final String text) {
        return !isBlank(text);
    }

    /**
     * 字符串占位替换 "-{0}-{1}-{2}-"
     * <p>
     * 使用提供的替换值来填充字符串模板中的占位符。占位符格式为 "-{数字}-"，其中数字表示替换值的索引。
     *
     * @param template 字符串模板，其中包含占位符
     * @param args     替换值数组，按顺序替换模板中的占位符
     * @return 替换后的字符串
     */
    public static String format(String template, Object... args) {
        if (isEmpty(template)) {
            return "";
        } else if (null != args && args.length != 0) {
            char[] templateChars = template.toCharArray();
            int templateLength = templateChars.length;
            int length = 0;
            int tokenCount = args.length;

            for (Object sourceString : args) {
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
     * 将下划线分隔的字符串转换为驼峰结构
     * <p>
     * 将输入的下划线分隔的字符串转换为驼峰结构的字符串。
     *
     * @param s 输入的下划线分隔的字符串
     * @return 返回转换后的驼峰结构字符串
     */
    public static String toCamelCase(String s) {
        Matcher matcher = UNDERLINE_PATTERN.matcher(s);
        StringBuilder sb = new StringBuilder(s);
        if (matcher.find()) {
            sb = new StringBuilder();
            matcher.appendReplacement(sb, matcher.group(1).toUpperCase(Locale.ENGLISH));
            matcher.appendTail(sb);
        } else {
            return sb.toString().replaceAll("_", "");
        }
        return toCamelCase(sb.toString());
    }

    /**
     * 将字符串转换为字符串列表（不去重）
     *
     * @param arrayString 待转换的字符串,以逗号分隔
     * @return 转换后的字符串列表
     */
    public static List<String> toList(String arrayString) {
        return toList(arrayString, null, false);
    }

    /**
     * 将字符串转换为字符串列表（去重）
     *
     * @param arrayString 待转换的字符串,以逗号分隔
     * @return 转换后的字符串列表
     */
    public static List<String> toListDr(String arrayString) {
        return toList(arrayString, null, true);
    }

    /**
     * 将以指定分隔符分隔的字符串转换为字符串列表
     *
     * @param arrayString    待转换的字符串
     * @param split        分隔符
     * @param deRepetition 是否去重
     * @return 转换后的字符串列表，如果输入字符串为空或分割后没有有效项，则返回空列表
     */
    public static List<String> toList(String arrayString, String split, boolean deRepetition) {
        List<String> list = new ArrayList<>();
        split = split == null ? "," : split;
        if (arrayString != null) {
            String[] arr = arrayString.split(split);
            for (String item : arr) {
                if (deRepetition) {
                    if (StringUtils.isNotBlank(item) && !list.contains(item)) {
                        list.add(item);
                    }
                } else {
                    list.add(item);
                }
            }
        }
        return list;
    }
}
