package cn.geelato.web.platform.utils;


import cn.geelato.utils.Digests;
import cn.geelato.utils.Encodes;
import cn.geelato.utils.StringUtils;
import cn.geelato.meta.User;

public class EncryptUtil {
    public static final int HASH_ITERATIONS = 1024;
    public static final int SALT_SIZE = 8;

    /**
     * 对用户密码进行加密处理
     *
     * @param user 用户对象，包含明文密码等信息
     */
    public static void encryptPassword(User user) {
        byte[] salt = Digests.generateSalt(SALT_SIZE);
        user.setSalt(Encodes.encodeHex(salt));
        byte[] hashPassword = Digests.sha1(user.getPlainPassword().getBytes(), salt, HASH_ITERATIONS);
        user.setPassword(Encodes.encodeHex(hashPassword));
    }

    /**
     * 对密码进行加密处理
     *
     * @param plainPassword 明文密码
     * @param salt          盐值
     * @return 加密后的密码
     */
    public static String encryptPassword(String plainPassword, String salt) {
        byte[] hashPassword = Digests.sha1(plainPassword.getBytes(), Encodes.decodeHex(salt), HASH_ITERATIONS);
        return Encodes.encodeHex(hashPassword);
    }

    /**
     * 对授权码进行加密处理
     *
     * @param authCode 授权码
     * @param salt     盐值
     * @return 加密后的授权码，如果salt或authCode为空则返回null
     */
    public static String encryptAuthCode(String authCode, String salt) {
        if (StringUtils.isNotBlank(salt) && StringUtils.isNotBlank(authCode)) {
            return Encodes.encodeHex(Digests.sha1(authCode.getBytes(), salt.getBytes(), HASH_ITERATIONS));
        }
        return null;
    }
}
