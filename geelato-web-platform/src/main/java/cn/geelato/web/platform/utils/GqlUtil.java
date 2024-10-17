package cn.geelato.web.platform.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;

@Slf4j
public class GqlUtil {
    /**
     * 从 HttpServletRequest 对象中获取查询的 GQL 字符串。
     *
     * @param request HttpServletRequest 对象，包含了 HTTP 请求的所有信息。
     * @return 从 HttpServletRequest 对象中读取的 GQL 字符串，如果读取失败则返回空字符串。
     */
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
        } catch (IOException e) {
            log.error("未能从httpServletRequest中获取gql的内容", e);
            throw new GqlResolveException();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return stringBuilder.toString();
    }
}
