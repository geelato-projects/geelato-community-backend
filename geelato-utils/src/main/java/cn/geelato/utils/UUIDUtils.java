package cn.geelato.utils;

/**
 * @author diabl
 * @description: 随机字符
 */
public class UUIDUtils {
    public static final String CHARS_NUMBER = "0123456789";
    private static final String CHARS_CAPITAL_LETTER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String CHARS_LOWERCASE_LETTER = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHARS_SPECIAL_CHARACTER = "!@#$%^&*()_+-={}[]\\|:;\"'<>,.?/";

    /**
     * 生成随机数（数值型）
     * 该方法生成一个由数字组成的随机字符串，字符串的长度由参数extent决定。
     * 如果extent小于等于0，则默认字符串长度为4。
     *
     * @param extent 随机字符串的长度
     * @return 返回生成的随机字符串
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
     * 生成固定长度的数字字符串
     * 根据给定的位数和数字，生成一个由指定数字重复组成的字符串。
     * 如果给定的位数小于等于0，则默认使用4位。
     * 如果给定的数字小于1或大于等于10，则默认使用数字8。
     *
     * @param extent 生成的字符串的位数
     * @param num    用于重复生成的数字
     * @return 生成的固定长度的数字字符串
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
     * 生成随机字符串（密码），默认长度为8位。
     *
     * @param extent 密码的长度，如果传入的值小于等于0，则默认为8位。
     * @return 生成的随机字符串（密码）。
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
     * 根据指定的长度和字符集生成一个随机字符串。
     *
     * @param extent 随机字符串的长度，如果小于等于0，则默认为8
     * @param chars  字符集，用于生成随机字符串的字符集合
     * @return 生成的随机字符串
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
     * 该方法生成指定长度的随机字符串，字符串由小写字母组成。
     *
     * @param extent 指定生成的随机字符串的长度
     * @return 返回生成的随机字符串
     */
    public static String generateLowerChars(int extent) {
        return generate(extent, CHARS_LOWERCASE_LETTER);
    }

    /**
     * 生成随机字符串（包含数值和小写字母）
     *
     * @param extent 指定生成的随机字符串的长度
     * @return 返回生成的随机字符串
     */
    public static String generateNumberAndLowerChars(int extent) {
        return generate(extent, CHARS_NUMBER + CHARS_LOWERCASE_LETTER);
    }

    /**
     * 生成包含数字和字母的随机字符串。
     *
     * @param extent 指定生成字符串的长度
     * @return 返回生成的随机字符串
     */
    public static String generateNumberAndChars(int extent) {
        return generate(extent, CHARS_NUMBER + CHARS_CAPITAL_LETTER + CHARS_LOWERCASE_LETTER);
    }
}
