package cn.geelato.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * @author diabl
 */
public class KeyUtils {
    public static final String PUBLIC_KEY = "publicKey";
    public static final String PRIVATE_KEY = "privateKey";

    /**
     * 生成国密SM2公私钥对
     * 使用Bouncy Castle加密库生成SM2算法的公私钥对，并将公钥和私钥进行Base64编码后存储在Map中返回。
     *
     * @return 包含公钥和私钥的Map，其中公钥的键为PUBLIC_KEY，私钥的键为PRIVATE_KEY
     * @throws Exception 如果在生成密钥对的过程中发生异常，则抛出该异常
     */
    public static Map<String, String> generateSmKey() throws Exception {
        KeyPairGenerator keyPairGenerator = null;
        SecureRandom secureRandom = new SecureRandom();
        ECGenParameterSpec sm2Spec = new ECGenParameterSpec("sm2p256v1");
        keyPairGenerator = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());
        keyPairGenerator.initialize(sm2Spec);
        keyPairGenerator.initialize(sm2Spec, secureRandom);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        String publicKeyStr = new String(Base64.getEncoder().encode(publicKey.getEncoded()));
        String privateKeyStr = new String(Base64.getEncoder().encode(privateKey.getEncoded()));
        return Map.of(PUBLIC_KEY, publicKeyStr, PRIVATE_KEY, privateKeyStr);
    }

    /**
     * 将Base64转码的公钥串转化为公钥对象
     *
     * @param publicKey Base64转码的公钥串
     * @return 转化后的公钥对象，如果转化失败则返回null
     */
    public static PublicKey createPublicKey(String publicKey) {
        try {
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey));
            KeyFactory keyFactory = KeyFactory.getInstance("EC", new BouncyCastleProvider());
            return keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将Base64转码的私钥串转化为私钥对象
     *
     * @param privateKey Base64转码的私钥串
     * @return 转化后的私钥对象，如果转化失败则返回null
     */
    public static PrivateKey createPrivateKey(String privateKey) {
        try {
            PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey));
            KeyFactory keyFactory = KeyFactory.getInstance("EC", new BouncyCastleProvider());
            return keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        } catch (Exception e) {
            return null;
        }
    }
}
