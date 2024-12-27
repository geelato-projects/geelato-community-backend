package cn.geelato.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Locale;

public class Base64Utils {
    private static final Logger logger = LoggerFactory.getLogger(Base64Utils.class);

    /**
     * 将Base64编码的字符串解码为纯文本字符串
     *
     * @param str 待解码的Base64编码字符串
     * @return 解码后的纯文本字符串，如果解码失败则返回null
     */
    public static String decode(String str) {
        String decodedString = null;
        try {
            if (StringUtils.isNotBlank(str)) {
                // 创建一个Base64解码器
                Base64.Decoder decoder = Base64.getDecoder();
                // 将Base64编码的字符串解码为字节数组
                String[] parts = str.split(",");
                if (parts.length == 2 && StringUtils.isNotBlank(parts[1])) {
                    byte[] decodedBytes = decoder.decode(parts[1]);
                    // 将字节数组转换为字符串（使用UTF-8编码，因为原始字符串是UTF-8编码的）
                    decodedString = new String(decodedBytes, "UTF-8");
                } else {
                    logger.error("decode string part length not equal 2 or content is empty");
                }
            } else {
                logger.error("decode string is blank");
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("Error decoding Base64 string: {}", e.getMessage());
        }
        return StringUtils.isNotBlank(decodedString) ? decodedString : null;
    }

    /**
     * 判断一个字符串是否是特定媒体类型的Base64编码。
     *
     * @param str       要判断的字符串
     * @param mediaType 媒体类型，例如"data:text/plain;base64,"
     * @return 如果是特定媒体类型的Base64编码则返回true，否则返回false
     */
    public static boolean isBase64(String str, String mediaType) {
        if (StringUtils.isNotBlank(str) && StringUtils.isNotBlank(mediaType)) {
            if (str.toLowerCase(Locale.ENGLISH).startsWith(mediaType.toLowerCase(Locale.ENGLISH))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将文件转换为Base64编码的字符串
     *
     * @param file        要转换的文件
     * @param contentType 文件的内容类型（如"image/png"）
     * @return Base64编码的字符串，格式为"data:[contentType];base64,[base64String]"
     * @throws IOException 如果在读取文件或编码过程中发生I/O错误
     */
    public static String fromFile(File file, String contentType) throws IOException {
        String base64String = null;
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            base64String = fromFile(fileInputStream.readAllBytes(), contentType);
        }
        return base64String;
    }

    /**
     * 将字节数组转换为Base64编码的字符串，并添加MIME类型前缀
     *
     * @param fileBytes   要转换的字节数组
     * @param contentType MIME类型，例如"image/png"
     * @return 包含MIME类型和Base64编码字符串的完整字符串，格式为"data:[contentType];base64,[base64String]"
     * @throws IOException 如果在编码过程中发生I/O错误（实际上，这个方法通常不会抛出此异常）
     */
    public static String fromFile(byte[] fileBytes, String contentType) {
        String base64String = Base64.getEncoder().encodeToString(fileBytes);
        return String.format("data:%s;base64,%s", contentType, base64String);
    }
}
