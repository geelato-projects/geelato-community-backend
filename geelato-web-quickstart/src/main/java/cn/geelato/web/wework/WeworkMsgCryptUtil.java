package cn.geelato.web.wework;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

/**
 * 企业微信消息加解密工具类
 */
public class WeworkMsgCryptUtil {
    private static final Logger logger = LoggerFactory.getLogger(WeworkMsgCryptUtil.class);
    
    private final String token;
    private final String encodingAesKey;
    private final String corpId;
    private final byte[] aesKey;
    
    /**
     * 构造函数
     * @param token 企业微信后台设置的token
     * @param encodingAesKey 企业微信后台设置的EncodingAESKey
     * @param corpId 企业ID
     */
    public WeworkMsgCryptUtil(String token, String encodingAesKey, String corpId) {
        this.token = token;
        this.encodingAesKey = encodingAesKey;
        this.corpId = corpId;
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
        String signature = getSHA1(token, timestamp, nonce, "");
        if (!signature.equals(msgSignature)) {
            throw new Exception("签名验证失败");
        }
        
        // 解密echoStr
        return decrypt(echoStr);
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
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            
            // 使用BASE64对密文进行解码
            byte[] encrypted = Base64.decodeBase64(text);
            
            // 解密
            original = cipher.doFinal(encrypted);
        } catch (Exception e) {
            logger.error("解密异常", e);
            throw new Exception("解密异常");
        }
        
        try {
            // 去除补位字符
            byte[] bytes = PKCS7Padding.removePadding(original);
            
            // 分离16位随机字符串,网络字节序和corpId
            int xmlLength = (bytes[16] & 0xFF) << 24 | (bytes[17] & 0xFF) << 16 | (bytes[18] & 0xFF) << 8 | (bytes[19] & 0xFF);
            
            if (xmlLength < 0) {
                throw new Exception("xml长度不合法");
            }
            
            String fromCorpId = new String(Arrays.copyOfRange(bytes, 20 + xmlLength, bytes.length), StandardCharsets.UTF_8);
            
            // corpId不相同的情况
            if (!fromCorpId.equals(corpId)) {
                throw new Exception("corpId校验失败");
            }
            
            return new String(Arrays.copyOfRange(bytes, 20, 20 + xmlLength), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("解密后处理异常", e);
            throw new Exception("解密后处理异常");
        }
    }
    
    /**
     * 加密消息
     * @param text 明文
     * @return 密文
     * @throws Exception 异常信息
     */
    public String encrypt(String text) throws Exception {
        byte[] randomBytes = new byte[16];
        new Random().nextBytes(randomBytes);
        
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] networkBytesOrder = getNetworkBytesOrder(textBytes.length);
        byte[] corpIdBytes = corpId.getBytes(StandardCharsets.UTF_8);
        
        // randomBytes + networkBytesOrder + textBytes + corpIdBytes
        byte[] padBytes = PKCS7Padding.addPadding(randomBytes.length + networkBytesOrder.length + textBytes.length + corpIdBytes.length);
        byte[] unencrypted = new byte[randomBytes.length + networkBytesOrder.length + textBytes.length + corpIdBytes.length + padBytes.length];
        
        System.arraycopy(randomBytes, 0, unencrypted, 0, randomBytes.length);
        System.arraycopy(networkBytesOrder, 0, unencrypted, randomBytes.length, networkBytesOrder.length);
        System.arraycopy(textBytes, 0, unencrypted, randomBytes.length + networkBytesOrder.length, textBytes.length);
        System.arraycopy(corpIdBytes, 0, unencrypted, randomBytes.length + networkBytesOrder.length + textBytes.length, corpIdBytes.length);
        System.arraycopy(padBytes, 0, unencrypted, randomBytes.length + networkBytesOrder.length + textBytes.length + corpIdBytes.length, padBytes.length);
        
        // 设置加密模式为AES的CBC模式
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
        
        // 加密
        byte[] encrypted = cipher.doFinal(unencrypted);
        
        // 使用BASE64对加密后的字符串进行编码
        return Base64.encodeBase64String(encrypted);
    }
    
    /**
     * 生成签名
     * @param token 票据
     * @param timestamp 时间戳
     * @param nonce 随机字符串
     * @param encrypt 加密后的消息密文
     * @return 签名
     * @throws Exception 异常信息
     */
    public String getSHA1(String token, String timestamp, String nonce, String encrypt) throws Exception {
        try {
            String[] array = new String[]{token, timestamp, nonce, encrypt};
            Arrays.sort(array);
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < 4 && array[i].length() > 0; i++) {
                sb.append(array[i]);
            }
            String str = sb.toString();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(str.getBytes());
            byte[] digest = md.digest();
            
            StringBuffer hexstr = new StringBuffer();
            String shaHex = "";
            for (int i = 0; i < digest.length; i++) {
                shaHex = Integer.toHexString(digest[i] & 0xFF);
                if (shaHex.length() < 2) {
                    hexstr.append(0);
                }
                hexstr.append(shaHex);
            }
            return hexstr.toString();
        } catch (Exception e) {
            logger.error("生成签名异常", e);
            throw new Exception("生成签名异常");
        }
    }
    
    /**
     * 获取网络字节序
     * @param sourceNumber 源数字
     * @return 网络字节序
     */
    private byte[] getNetworkBytesOrder(int sourceNumber) {
        byte[] orderBytes = new byte[4];
        orderBytes[0] = (byte) (sourceNumber >> 24 & 0xFF);
        orderBytes[1] = (byte) (sourceNumber >> 16 & 0xFF);
        orderBytes[2] = (byte) (sourceNumber >> 8 & 0xFF);
        orderBytes[3] = (byte) (sourceNumber & 0xFF);
        return orderBytes;
    }
    
    /**
     * PKCS7填充工具类
     */
    private static class PKCS7Padding {
        private static final int BLOCK_SIZE = 32;
        
        /**
         * 添加PKCS7填充
         * @param count 计数
         * @return 填充后的字节数组
         */
        public static byte[] addPadding(int count) {
            int amountToPad = BLOCK_SIZE - (count % BLOCK_SIZE);
            if (amountToPad == 0) {
                amountToPad = BLOCK_SIZE;
            }
            char padChr = (char) amountToPad;
            String tmp = "";
            for (int i = 0; i < amountToPad; i++) {
                tmp += padChr;
            }
            return tmp.getBytes(StandardCharsets.UTF_8);
        }
        
        /**
         * 移除PKCS7填充
         * @param decrypted 解密后的字节数组
         * @return 移除填充后的字节数组
         */
        public static byte[] removePadding(byte[] decrypted) {
            int pad = decrypted[decrypted.length - 1];
            if (pad < 1 || pad > BLOCK_SIZE) {
                pad = 0;
            }
            return Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
        }
    }
}