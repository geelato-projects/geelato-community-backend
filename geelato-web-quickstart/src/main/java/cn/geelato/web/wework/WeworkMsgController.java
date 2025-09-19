package cn.geelato.web.wework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * 企业微信消息接收控制器
 */
@RestController
@RequestMapping("/wx/validate")
public class WeworkMsgController {
    private static final Logger logger = LoggerFactory.getLogger(WeworkMsgController.class);

    @Value("${wework.token:rPcRqcWuF}")
    private String token;

    @Value("${wework.aeskey:xBkfnZcxCI10PY8tGWEs5sjju1568zpx3M33XWyI4w1}")
    private String encodingAesKey;

    @Value("${wework.corpid:wwfed14f2fada336fd}")
    private String corpId;

    private WeworkMsgCryptUtil cryptUtil;

    /**
     * 初始化加密工具
     */
    private void initCryptUtil() {
        if (cryptUtil == null) {
            cryptUtil = new WeworkMsgCryptUtil(token, encodingAesKey, corpId);
        }
    }

    /**
     * 验证URL有效性
     * 企业微信验证接口是否可用时会调用此接口
     */
    @GetMapping("/receive")
    public String verifyUrl(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        
        logger.info("收到企业微信URL验证请求: msg_signature={}, timestamp={}, nonce={}", 
                msgSignature, timestamp, nonce);
        
        try {
            initCryptUtil();
            String result = cryptUtil.verifyUrl(msgSignature, timestamp, nonce, echostr);
            logger.info("URL验证成功");
            return result;
        } catch (Exception e) {
            logger.error("URL验证失败", e);
            return "验证失败";
        }
    }

    /**
     * 接收消息
     * 企业微信发送消息时会调用此接口
     */
    @PostMapping("/receive")
    public String receiveMessage(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            HttpServletRequest request) {
        
        logger.info("收到企业微信消息: msg_signature={}, timestamp={}, nonce={}", 
                msgSignature, timestamp, nonce);
        
        try {
            // 读取请求体
            String postData = readRequestBody(request);
            logger.debug("收到的加密消息: {}", postData);
            
            // 初始化加密工具
            initCryptUtil();
            
            // 解析XML
            Map<String, String> xmlMap = XmlUtil.parseXml(postData);
            String encrypt = xmlMap.get("Encrypt");
            
            // 验证签名
            String signature = cryptUtil.getSHA1(token, timestamp, nonce, encrypt);
            if (!signature.equals(msgSignature)) {
                logger.error("消息签名验证失败");
                return "success"; // 即使验证失败也返回success，避免企业微信重试
            }
            
            // 解密消息
            String decryptedMsg = cryptUtil.decrypt(encrypt);
            logger.info("解密后的消息: {}", decryptedMsg);
            
            // 解析消息内容
            Map<String, String> msgMap = XmlUtil.parseXml(decryptedMsg);
            String msgType = msgMap.get("MsgType");
            
            // 根据消息类型处理
            String replyContent = processMessage(msgMap, msgType);
            
            // 如果不需要回复，直接返回success
            if (replyContent == null || replyContent.isEmpty()) {
                return "success";
            }
            
            // 加密回复消息
            String encryptedReply = cryptUtil.encrypt(replyContent);
            String replySignature = cryptUtil.getSHA1(token, timestamp, nonce, encryptedReply);
            
            // 构造回复XML
            String result = XmlUtil.generateXml(encryptedReply, replySignature, timestamp, nonce);
            logger.debug("回复消息: {}", result);
            
            return result;
        } catch (Exception e) {
            logger.error("处理消息异常", e);
            return "success"; // 出现异常也返回success，避免企业微信重试
        }
    }
    
    /**
     * 处理不同类型的消息
     * @param msgMap 消息内容Map
     * @param msgType 消息类型
     * @return 回复的消息内容，如果不需要回复则返回null
     */
    private String processMessage(Map<String, String> msgMap, String msgType) {
        String fromUserName = msgMap.get("FromUserName");
        String toUserName = msgMap.get("ToUserName");
        
        // 交换发送方和接收方
        String replyToUserName = fromUserName;
        String replyFromUserName = toUserName;
        
        // 根据消息类型处理
        switch (msgType) {
            case "text":
                // 处理文本消息
                String content = msgMap.get("Content");
                logger.info("收到文本消息: {}", content);
                
                // 简单回复相同的内容
                return generateTextReply(replyFromUserName, replyToUserName, "收到消息: " + content);
                
            case "image":
                // 处理图片消息
                logger.info("收到图片消息");
                return generateTextReply(replyFromUserName, replyToUserName, "收到图片消息");
                
            case "voice":
                // 处理语音消息
                logger.info("收到语音消息");
                return generateTextReply(replyFromUserName, replyToUserName, "收到语音消息");
                
            case "video":
                // 处理视频消息
                logger.info("收到视频消息");
                return generateTextReply(replyFromUserName, replyToUserName, "收到视频消息");
                
            case "location":
                // 处理位置消息
                logger.info("收到位置消息");
                return generateTextReply(replyFromUserName, replyToUserName, "收到位置消息");
                
            case "link":
                // 处理链接消息
                logger.info("收到链接消息");
                return generateTextReply(replyFromUserName, replyToUserName, "收到链接消息");
                
            case "event":
                // 处理事件消息
                String event = msgMap.get("Event");
                logger.info("收到事件消息: {}", event);
                return processEventMessage(msgMap, event, replyFromUserName, replyToUserName);
                
            default:
                logger.info("收到未知类型消息: {}", msgType);
                return generateTextReply(replyFromUserName, replyToUserName, "收到未知类型消息");
        }
    }
    
    /**
     * 处理事件消息
     * @param msgMap 消息内容Map
     * @param event 事件类型
     * @param fromUserName 发送方
     * @param toUserName 接收方
     * @return 回复的消息内容，如果不需要回复则返回null
     */
    private String processEventMessage(Map<String, String> msgMap, String event, String fromUserName, String toUserName) {
        switch (event) {
            case "subscribe":
                // 处理订阅事件
                return generateTextReply(fromUserName, toUserName, "感谢关注！");
                
            case "unsubscribe":
                // 处理取消订阅事件
                logger.info("用户取消关注");
                return null; // 无需回复
                
            case "CLICK":
                // 处理点击菜单事件
                String eventKey = msgMap.get("EventKey");
                logger.info("用户点击菜单: {}", eventKey);
                return generateTextReply(fromUserName, toUserName, "您点击了菜单: " + eventKey);
                
            default:
                logger.info("收到未知事件: {}", event);
                return null; // 无需回复
        }
    }
    
    /**
     * 生成文本回复消息
     * @param fromUserName 发送方
     * @param toUserName 接收方
     * @param content 回复内容
     * @return XML格式的回复消息
     */
    private String generateTextReply(String fromUserName, String toUserName, String content) {
        long createTime = System.currentTimeMillis() / 1000;
        return "<xml>" +
                "<ToUserName><![CDATA[" + toUserName + "]]></ToUserName>" +
                "<FromUserName><![CDATA[" + fromUserName + "]]></FromUserName>" +
                "<CreateTime>" + createTime + "</CreateTime>" +
                "<MsgType><![CDATA[text]]></MsgType>" +
                "<Content><![CDATA[" + content + "]]></Content>" +
                "</xml>";
    }
    
    /**
     * 读取请求体内容
     * @param request HTTP请求
     * @return 请求体内容
     * @throws IOException IO异常
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}