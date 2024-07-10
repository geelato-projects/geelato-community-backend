package cn.geelato.orm.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * @author diabl
 */
public class FastJsonUtils {

    public static String readJsonFile(String filePath) throws IOException {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
        Reader reader = new InputStreamReader(inputStream, "UTF-8");
        int ch = 0;
        StringBuffer sb = new StringBuffer();
        while ((ch = reader.read()) != -1) {
            sb.append((char) ch);
        }
        reader.close();
        inputStream.close();

        return sb.toString();
    }
}