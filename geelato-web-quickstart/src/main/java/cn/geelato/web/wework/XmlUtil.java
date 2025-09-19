package cn.geelato.web.wework;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XML工具类，用于解析和生成XML
 */
public class XmlUtil {
    private static final Logger logger = LoggerFactory.getLogger(XmlUtil.class);

    /**
     * 解析XML字符串为Map
     * @param xml XML字符串
     * @return 解析后的Map
     * @throws Exception 解析异常
     */
    public static Map<String, String> parseXml(String xml) throws Exception {
        Map<String, String> map = new HashMap<>();
        try {
            Document document = DocumentHelper.parseText(xml);
            Element root = document.getRootElement();
            List<Element> elements = root.elements();
            for (Element e : elements) {
                map.put(e.getName(), e.getText());
            }
            return map;
        } catch (Exception e) {
            logger.error("解析XML异常", e);
            throw new Exception("解析XML异常");
        }
    }

    /**
     * 生成回复消息的XML
     * @param encrypt 加密后的消息内容
     * @param signature 消息签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @return XML字符串
     */
    public static String generateXml(String encrypt, String signature, String timestamp, String nonce) {
        return "<xml>" +
                "<Encrypt><![CDATA[" + encrypt + "]]></Encrypt>" +
                "<MsgSignature><![CDATA[" + signature + "]]></MsgSignature>" +
                "<TimeStamp>" + timestamp + "</TimeStamp>" +
                "<Nonce><![CDATA[" + nonce + "]]></Nonce>" +
                "</xml>";
    }
}