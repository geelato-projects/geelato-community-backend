package cn.geelato.web.oss.helper;

import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;

public class ContentTypeHelper {
    public static String getContentType(InputStream inputStream) {
        Tika tika = new Tika();
        try {
            return tika.detect(inputStream);
        } catch (IOException e) {
            return "UnknownContentType";
        }
    }
}
