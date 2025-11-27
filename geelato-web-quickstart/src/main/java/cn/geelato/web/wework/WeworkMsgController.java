package cn.geelato.web.wework;

import org.apache.commons.codec.binary.Base64;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/wx/validate")
public class WeworkMsgController {
    private static final Logger logger = LoggerFactory.getLogger(WeworkMsgController.class);

    @Value("${wework.token:rPcRqcWuF}")
    private String token;

    @Value("${wework.aeskey:xBkfnZcxCI10PY8tGWEs5sjju1568zpx3M33XWyI4w1}")
    private String encodingAesKey;

    @Value("${wework.corpid:wwc87d7b0460346552}")
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

    @GetMapping("/receive")
    public String verifyUrl(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        
        logger.info("接收到URL验证请求: msg_signature={}, timestamp={}, nonce={}, echostr={}", 
                msgSignature, timestamp, nonce, echostr);
        
        try {
            initCryptUtil();
            String result = cryptUtil.verifyUrl(msgSignature, timestamp, nonce, echostr);
            logger.info("URL验证成功，返回: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("URL验证失败", e);
            return "验证失败";
        }
    }


    /**
     * 企业微信消息加解密工具类
     * 按照企业微信官方文档实现：<a href="https://developer.work.weixin.qq.com/document/path/90968">...</a>
     */
    public static class WeworkMsgCryptUtil {
        private static final Logger logger = LoggerFactory.getLogger(WeworkMsgCryptUtil.class);

        private final String token;
        private final String encodingAesKey;
        private final String receiveId;
        private final byte[] aesKey;
        private static final int BLOCK_SIZE = 32;

        /**
         * 构造函数
         * @param token 企业微信后台设置的token
         * @param encodingAesKey 企业微信后台设置的EncodingAESKey
         * @param receiveId 企业ID或第三方套件ID
         */
        public WeworkMsgCryptUtil(String token, String encodingAesKey, String receiveId) {
            this.token = token;
            this.encodingAesKey = encodingAesKey;
            this.receiveId = receiveId;
            this.aesKey = Base64.decodeBase64(encodingAesKey + "=");
        }

        /**
         * 验证URL有效性的方法
         * @param msgSignature 签名串
         * @param timestamp 时间戳
         * @param nonce 随机串
         * @param echoStr 随机串
         * @return 解密后的明文消息内容
         * @throws Exception 异常信息
         */
        public String verifyUrl(String msgSignature, String timestamp, String nonce, String echoStr) throws Exception {
            // 验证签名
            if (!verifySignature(msgSignature, timestamp, nonce, echoStr)) {
                throw new Exception("签名验证失败");
            }

            // 解密echoStr
            return decrypt(echoStr);
        }

        /**
         * 验证签名
         * @param msgSignature 签名串
         * @param timestamp 时间戳
         * @param nonce 随机串
         * @param encrypt 加密后的消息密文
         * @return 验证结果
         */
        public boolean verifySignature(String msgSignature, String timestamp, String nonce, String encrypt) {
            String signature = getSHA1(token, timestamp, nonce, encrypt);
            return signature != null && signature.equals(msgSignature);
        }

        /**
         * 解密消息
         * @param text 密文
         * @return 明文
         * @throws Exception 异常信息
         */
        public String decrypt(String text) throws Exception {
            byte[] original;
            try {
                // 设置解密模式为AES的CBC模式
                Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
                SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
                IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);

                // 使用BASE64对密文进行解码
                byte[] encrypted = Base64.decodeBase64(text);

                // 解密
                original = cipher.doFinal(encrypted);
            } catch (Exception e) {
                logger.error("解密异常", e);
                throw new Exception("解密异常: " + e.getMessage());
            }

            try {
                // 去除补位字符
                byte[] bytes = removePadding(original);

                // 分离16位随机字符串、网络字节序和receiveId
                // 前16字节是随机字符串
                // 接下来4字节是网络字节序的msg长度
                int xmlLength = bytesToInt(bytes, 16);

                if (xmlLength < 0) {
                    throw new Exception("xml长度不合法");
                }

                // 最后是receiveId
                String fromReceiveId = new String(Arrays.copyOfRange(bytes, 20 + xmlLength, bytes.length), StandardCharsets.UTF_8);

                // receiveId不相同的情况
                if (!fromReceiveId.equals(receiveId)) {
                    throw new Exception("receiveId校验失败，期望: " + receiveId + "，实际: " + fromReceiveId);
                }

                // 返回xml明文
                return new String(Arrays.copyOfRange(bytes, 20, 20 + xmlLength), StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.error("解密后处理异常", e);
                throw new Exception("解密后处理异常: " + e.getMessage());
            }
        }

        /**
         * 加密消息
         * @param text 明文
         * @return 密文
         * @throws Exception 异常信息
         */
        public String encrypt(String text) throws Exception {
            // 生成16字节的随机字符串
            byte[] randomBytes = new byte[16];
            new Random().nextBytes(randomBytes);

            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            byte[] networkBytesOrder = intToBytes(textBytes.length);
            byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);

            // 拼接明文字符串: randomBytes + networkBytesOrder + textBytes + receiveIdBytes
            int contentLength = randomBytes.length + networkBytesOrder.length + textBytes.length + receiveIdBytes.length;

            // 计算需要填充的长度
            int padLength = BLOCK_SIZE - (contentLength % BLOCK_SIZE);
            if (padLength == 0) {
                padLength = BLOCK_SIZE;
            }

            // 创建最终的字节数组
            byte[] unencrypted = new byte[contentLength + padLength];

            // 复制数据
            int pos = 0;
            System.arraycopy(randomBytes, 0, unencrypted, pos, randomBytes.length);
            pos += randomBytes.length;

            System.arraycopy(networkBytesOrder, 0, unencrypted, pos, networkBytesOrder.length);
            pos += networkBytesOrder.length;

            System.arraycopy(textBytes, 0, unencrypted, pos, textBytes.length);
            pos += textBytes.length;

            System.arraycopy(receiveIdBytes, 0, unencrypted, pos, receiveIdBytes.length);
            pos += receiveIdBytes.length;

            // 添加PKCS#7填充
            byte padValue = (byte) padLength;
            for (int i = contentLength; i < contentLength + padLength; i++) {
                unencrypted[i] = padValue;
            }

            try {
                // 设置加密模式为AES的CBC模式
                Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
                SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
                IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);

                // 加密
                byte[] encrypted = cipher.doFinal(unencrypted);

                // 使用BASE64对加密后的字符串进行编码
                return Base64.encodeBase64String(encrypted);
            } catch (Exception e) {
                logger.error("加密异常", e);
                throw new Exception("加密异常: " + e.getMessage());
            }
        }

        /**
         * 生成签名
         * @param token 票据
         * @param timestamp 时间戳
         * @param nonce 随机字符串
         * @param encrypt 加密后的消息密文
         * @return 签名
         */
        public String getSHA1(String token, String timestamp, String nonce, String encrypt) {
            try {
                String[] array = new String[]{token, timestamp, nonce, encrypt};
                // 字典序排序
                Arrays.sort(array);

                StringBuilder sb = new StringBuilder();
                for (String item : array) {
                    if (item != null && !item.isEmpty()) {
                        sb.append(item);
                    }
                }

                // SHA1签名生成
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(sb.toString().getBytes(StandardCharsets.UTF_8));
                byte[] digest = md.digest();

                // 将签名转换为十六进制字符串
                StringBuilder hexstr = new StringBuilder();
                for (byte b : digest) {
                    String shaHex = Integer.toHexString(b & 0xFF);
                    if (shaHex.length() < 2) {
                        hexstr.append(0);
                    }
                    hexstr.append(shaHex);
                }
                return hexstr.toString();
            } catch (NoSuchAlgorithmException e) {
                logger.error("生成签名异常", e);
                return null;
            }
        }

        /**
         * 将int转换为网络字节序的byte数组
         * @param value 整数值
         * @return 网络字节序的byte数组
         */
        private byte[] intToBytes(int value) {
            byte[] bytes = new byte[4];
            bytes[0] = (byte) ((value >> 24) & 0xFF);
            bytes[1] = (byte) ((value >> 16) & 0xFF);
            bytes[2] = (byte) ((value >> 8) & 0xFF);
            bytes[3] = (byte) (value & 0xFF);
            return bytes;
        }

        /**
         * 将网络字节序的byte数组转换为int
         * @param bytes byte数组
         * @param offset 偏移量
         * @return 整数值
         */
        private int bytesToInt(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xFF) << 24)
                    | ((bytes[offset + 1] & 0xFF) << 16)
                    | ((bytes[offset + 2] & 0xFF) << 8)
                    | (bytes[offset + 3] & 0xFF);
        }

        /**
         * 移除PKCS#7填充
         * @param decrypted 解密后的字节数组
         * @return 移除填充后的字节数组
         */
        private byte[] removePadding(byte[] decrypted) {
            int pad = decrypted[decrypted.length - 1];
            if (pad < 1 || pad > BLOCK_SIZE) {
                pad = 0;
            }
            return Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
        }
    }

    /**
     * XML工具类，用于解析和生成XML
     */
    public static class XmlUtil {
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
}