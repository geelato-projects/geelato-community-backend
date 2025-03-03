package cn.geelato.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author diabl
 */
public class FastJsonUtils {

    /**
     * 从指定路径的文件中读取JSON格式的字符串。
     *
     * @param filePath JSON文件的路径。
     * @return 文件中读取到的JSON格式的字符串。
     * @throws IOException 如果在读取文件过程中发生I/O错误。
     */
    public static String readJsonFile(String filePath) throws IOException {
        // 使用 try-with-resources 自动关闭资源
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
             Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder sb = new StringBuilder();
            String line;
            // 逐行读取文件内容
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
