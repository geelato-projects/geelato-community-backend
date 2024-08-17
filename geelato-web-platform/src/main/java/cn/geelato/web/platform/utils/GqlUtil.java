package cn.geelato.web.platform.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;

public class GqlUtil {
    public static String resolveGql(HttpServletRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader br = null;
        String str;
        try {
            br = request.getReader();
            if (br != null) {
                while ((str = br.readLine()) != null) {
                    stringBuilder.append(str);
                }
            }
        } catch (IOException ex) {
            throw new GqlResolveException();
        }
        return stringBuilder.toString();

    }
}
