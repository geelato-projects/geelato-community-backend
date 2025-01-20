package cn.geelato.web.platform.utils;

import java.util.Random;

public class AuthCodeUtil {
    private static final String NUMBERS = "0123456789";
    private static final int LENGTH = 6;

    /**
     * 生成六位纯数字验证码
     * <p>
     * 使用随机数生成算法，生成一个长度为六位的纯数字验证码。
     *
     * @return 返回生成的六位纯数字验证码字符串
     */
    public static String sixDigitNumber() {
        StringBuilder sb = new StringBuilder(LENGTH);
        Random random = new Random();
        for (int i = 0; i < LENGTH; i++) {
            int index = random.nextInt(NUMBERS.length());
            char randomChar = NUMBERS.charAt(index);
            sb.append(randomChar);
        }
        return sb.toString();
    }
}
