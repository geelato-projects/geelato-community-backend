package cn.geelato.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Base64Utils {
    private static final String DATA_URI_REGEX = "^data:[\\w+/]+;base64,(.+)$";
    private static final Pattern pattern = Pattern.compile(DATA_URI_REGEX);


    /**
     * 将Base64编码的字符串解码为原始字符串。
     *
     * @param str Base64编码的字符串，格式为"data:image/png;base64,<base64 encoded string>"
     * @return 解码后的原始字符串，如果输入字符串为空或格式不正确，则返回null。
     */
    public static String decode(String str) {
        if (StringUtils.isNotBlank(str)) {
            // 创建一个Base64解码器
            Base64.Decoder decoder = Base64.getDecoder();
            // 将Base64编码的字符串解码为字节数组
            String[] parts = str.split(",");
            if (parts.length == 2 && StringUtils.isNotBlank(parts[1])) {
                try {
                    byte[] decodedBytes = decoder.decode(parts[1]);
                    // 将字节数组转换为字符串（使用UTF-8编码，因为原始字符串是UTF-8编码的）
                    return new String(decodedBytes, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return null;
                }
            }
        }
        return null;
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

    /**
     * 将输入的字符串转换为Base64编码格式的字符串。
     *
     * @param base64String 待转换的字符串
     * @return 转换后的Base64编码字符串
     * @throws IOException 如果在字符串处理过程中发生I/O错误
     */
    public static String toBase64String(String base64String) throws IOException {
        Matcher matcher = pattern.matcher(base64String);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return base64String;
    }

    /**
     * 将Base64编码的字符串转换为字节数组。
     *
     * @param base64String Base64编码的字符串
     * @return 转换后的字节数组，如果输入字符串为空或只包含空白字符，则返回一个空的字节数组
     * @throws IOException 如果在解码过程中发生I/O错误
     */
    public static byte[] toBytes(String base64String) throws IOException {
        base64String = toBase64String(base64String);
        if (StringUtils.isNotBlank(base64String)) {
            return Base64.getDecoder().decode(base64String);
        }
        return new byte[0];
    }
}
