package cn.geelato.web.oss.helper;

import java.io.IOException;
import java.io.InputStream;

public class FileSizeHelper {
    public static long getFileSize(InputStream inputStream) {
        try {
            return inputStream.available();
        } catch (IOException e) {
            return -1;
        }
    }
}
