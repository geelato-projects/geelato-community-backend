package cn.geelato.utils;

/**
 * @author diabl
 * @description: 随机字符
 */
public class UUIDUtils {
    private static final String CHARS_NUMBER = "0123456789";
    private static final String CHARS_CAPITAL_LETTER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String CHARS_LOWERCASE_LETTER = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHARS_SPECIAL_CHARACTER = "!@#$%^&*()_+-={}[]\\|:;\"'<>,.?/";

    /**
     * 生成随机数(数值)
     *
     * @param extent
     * @return
     */
    public static String generateRandom(int extent) {
        extent = extent > 0 ? extent : 4;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < extent; i++) {
            int randomIndex = (int) Math.floor(Math.random() * CHARS_NUMBER.length());
            sb.append(CHARS_NUMBER.charAt(randomIndex));
        }
        return sb.toString();
    }

    /**
     * 生成位数
     *
     * @param extent 位数
     * @param num    数字
     * @return
     */
    public static String generateFixation(int extent, int num) {
        extent = extent > 0 ? extent : 4;
        num = num > 0 && num < 10 ? num : 8;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < extent; i++) {
            sb.append(num);
        }
        return sb.toString();
    }

    /**
     * 生成随机字符串（密码）默认8位
     *
     * @param extent
     * @return
     */
    public static String generatePassword(int extent) {
        extent = extent > 0 ? extent : 8;
        String chars = CHARS_NUMBER + CHARS_CAPITAL_LETTER + CHARS_LOWERCASE_LETTER + CHARS_SPECIAL_CHARACTER;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < extent; i++) {
            int randomIndex = (int) Math.floor(Math.random() * chars.length());
            sb.append(chars.charAt(randomIndex));
        }
        return sb.toString();
    }

    /**
     * 生成随机字符串
     *
     * @param extent
     * @param chars
     * @return
     */
    public static String generate(int extent, String chars) {
        extent = extent > 0 ? extent : 8;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < extent; i++) {
            int randomIndex = (int) Math.floor(Math.random() * chars.length());
            sb.append(chars.charAt(randomIndex));
        }
        return sb.toString();
    }

    /**
     * 生成随机字符串（小写字母）
     *
     * @param extent
     * @return
     */
    public static String generateLowerChars(int extent) {
        return generate(extent, CHARS_LOWERCASE_LETTER);
    }

    /**
     * 生成随机字符串（数值+小写字母）
     *
     * @param extent
     * @return
     */
    public static String generateNumberAndLowerChars(int extent) {
        return generate(extent, CHARS_NUMBER + CHARS_LOWERCASE_LETTER);
    }

    /**
     * 生成随机字符串（数值+字母）
     *
     * @param extent
     * @return
     */
    public static String generateNumberAndChars(int extent) {
        return generate(extent, CHARS_NUMBER + CHARS_CAPITAL_LETTER + CHARS_LOWERCASE_LETTER);
    }
}
