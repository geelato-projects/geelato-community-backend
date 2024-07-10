package cn.geelato.core.util;

import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.util.Base64;
import java.util.Map;

/**
 * @author diabl
 */
public class Sm2Util {
    private static final Logger logger = LoggerFactory.getLogger(Sm2Util.class);

    /*    这行代码是在Java中用于向安全系统添加Bouncy Castle安全提供器的。
        Bouncy Castle是一个流行的开源加密库，它提供了许多密码学算法和安全协议的实现。
        通过调用Security.addProvider并传入BouncyCastleProvider对象，你可以注册Bouncy Castle提供的安全服务和算法到Java的安全框架中。
        这样一来，你就可以在你的应用程序中使用Bouncy Castle所提供的加密算法、密钥生成和管理等功能。*/
    static {
        Security.addProvider(new BouncyCastleProvider());
    }


    public static String encrypt(String value, Map<String, String> keys) throws Exception {
        // 公钥、密钥
        PublicKey publicKey = KeyUtils.createPublicKey(keys.get(KeyUtils.PUBLIC_KEY));
        PrivateKey privateKey = KeyUtils.createPrivateKey(keys.get(KeyUtils.PRIVATE_KEY));
        // 使用SM2加密
        byte[] encrypt = Sm2Util.encrypt(value.getBytes(), publicKey);
        // 加密转base64
        String encryptBase64Str = Base64.getEncoder().encodeToString(encrypt);
        // 私钥签名,方便对方收到数据后用公钥验签
        // byte[] sign = Sm2Util.signByPrivateKey(encryptBase64Str.getBytes(), privateKey);

        return encryptBase64Str;
    }

    public static String decrypt(String encodeValue, Map<String, String> keys) throws Exception {
        // 公钥、密钥
        PublicKey publicKey = KeyUtils.createPublicKey(keys.get(KeyUtils.PUBLIC_KEY));
        PrivateKey privateKey = KeyUtils.createPrivateKey(keys.get(KeyUtils.PRIVATE_KEY));
        // 私钥签名,方便对方收到数据后用公钥验签
        // byte[] sign = Sm2Util.signByPrivateKey(encodeValue.getBytes(), privateKey);
        // 公钥验签，验证通过后再进行数据解密
       /* boolean b = Sm2Util.verifyByPublicKey(encodeValue.getBytes(), publicKey, sign);
        if (!b) {
            throw new RuntimeException("加密数据验证签名失败！");
        } */
        // 私钥解密
        byte[] decode = Base64.getDecoder().decode(encodeValue);
        byte[] decrypt = Sm2Util.decrypt(decode, privateKey);
        assert decrypt != null;
        return new String(decrypt);
    }

    /**
     * 根据publicKey对原始数据data，使用SM2加密
     */
    public static byte[] encrypt(byte[] data, PublicKey publicKey) {
        ECPublicKeyParameters localECPublicKeyParameters = getEcPublicKeyParameters(publicKey);
        SM2Engine localSM2Engine = new SM2Engine();
        localSM2Engine.init(true, new ParametersWithRandom(localECPublicKeyParameters, new SecureRandom()));
        byte[] arrayOfByte2;
        try {
            arrayOfByte2 = localSM2Engine.processBlock(data, 0, data.length);
            return arrayOfByte2;
        } catch (InvalidCipherTextException e) {
            logger.error("SM2加密失败:{}", e.getMessage(), e);
            return null;
        }
    }

    private static ECPublicKeyParameters getEcPublicKeyParameters(PublicKey publicKey) {
        ECPublicKeyParameters localECPublicKeyParameters = null;
        if (publicKey instanceof BCECPublicKey localECPublicKey) {
            ECParameterSpec localECParameterSpec = localECPublicKey.getParameters();
            ECDomainParameters localECDomainParameters = new ECDomainParameters(localECParameterSpec.getCurve(),
                    localECParameterSpec.getG(), localECParameterSpec.getN());
            localECPublicKeyParameters = new ECPublicKeyParameters(localECPublicKey.getQ(), localECDomainParameters);
        }
        return localECPublicKeyParameters;
    }

    /**
     * 根据privateKey对加密数据encode data，使用SM2解密
     */
    public static byte[] decrypt(byte[] encodeData, PrivateKey privateKey) {
        SM2Engine localSM2Engine = new SM2Engine();
        BCECPrivateKey sm2PriK = (BCECPrivateKey) privateKey;
        ECParameterSpec localECParameterSpec = sm2PriK.getParameters();
        ECDomainParameters localECDomainParameters = new ECDomainParameters(localECParameterSpec.getCurve(),
                localECParameterSpec.getG(), localECParameterSpec.getN());
        ECPrivateKeyParameters localECPrivateKeyParameters = new ECPrivateKeyParameters(sm2PriK.getD(),
                localECDomainParameters);
        localSM2Engine.init(false, localECPrivateKeyParameters);
        try {
            return localSM2Engine.processBlock(encodeData, 0, encodeData.length);
        } catch (InvalidCipherTextException e) {
            logger.error("SM2解密失败:{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 私钥签名
     */
    public static byte[] signByPrivateKey(byte[] data, PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance(GMObjectIdentifiers.sm2sign_with_sm3.toString(), BouncyCastleProvider.PROVIDER_NAME);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    /**
     * 公钥验签
     */
    public static boolean verifyByPublicKey(byte[] data, PublicKey publicKey, byte[] signature) throws Exception {
        Signature sig = Signature.getInstance(GMObjectIdentifiers.sm2sign_with_sm3.toString(), BouncyCastleProvider.PROVIDER_NAME);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

}
