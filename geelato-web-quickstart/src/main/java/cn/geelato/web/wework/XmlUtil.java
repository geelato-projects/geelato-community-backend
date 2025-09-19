package cn.geelato.web.wework;

import org.dom4j.Document;
import org.dom4j.DocumentException;
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
     * 解析XML为Map
     * @param xml XML字符串
     * @return 解析后的Map
     */
    public static Map<String, String> parseXmlToMap(String xml) {
        Map<String, String> map = new HashMap<>();
        try {
            Document document = DocumentHelper.parseText(xml);
            Element root = document.getRootElement();
            List<Element> elements = root.elements();
            for (Element element : elements) {
                map.put(element.getName(), element.getTextTrim());
            }
        } catch (DocumentException e) {
            logger.error("解析XML异常", e);
        }
        return map;
    }
    
    /**
     * 兼容旧方法名
     * @param xml XML字符串
     * @return 解析后的Map
     */
    public static Map<String, String> parseXml(String xml) {
        return parseXmlToMap(xml);
    }
    
    /**
     * 生成回复消息的XML
     * @param encrypt 加密后的消息内容
     * @param signature 签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @return XML字符串
     */
    public static String generateResponseXml(String encrypt, String signature, String timestamp, String nonce) {
        return "<xml>" +
                "<Encrypt><![CDATA[" + encrypt + "]]></Encrypt>" +
                "<MsgSignature><![CDATA[" + signature + "]]></MsgSignature>" +
                "<TimeStamp>" + timestamp + "</TimeStamp>" +
                "<Nonce><![CDATA[" + nonce + "]]></Nonce>" +
                "</xml>";
    }
    
    /**
     * 兼容旧方法名
     * @param encrypt 加密后的消息内容
     * @param signature 签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @return XML字符串
     */
    public static String generateXml(String encrypt, String signature, String timestamp, String nonce) {
        return generateResponseXml(encrypt, signature, timestamp, nonce);
    }
}