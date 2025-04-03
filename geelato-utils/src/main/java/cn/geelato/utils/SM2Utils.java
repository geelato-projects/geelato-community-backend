package cn.geelato.utils;

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

import java.security.*;
import java.util.Base64;
import java.util.Map;

/**
 * @author diabl
 */
public class SM2Utils {
    /*    这行代码是在Java中用于向安全系统添加Bouncy Castle安全提供器的。
        Bouncy Castle是一个流行的开源加密库，它提供了许多密码学算法和安全协议的实现。
        通过调用Security.addProvider并传入BouncyCastleProvider对象，你可以注册Bouncy Castle提供的安全服务和算法到Java的安全框架中。
        这样一来，你就可以在你的应用程序中使用Bouncy Castle所提供的加密算法、密钥生成和管理等功能。*/
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * 使用SM2算法加密字符串
     * 该方法接收一个待加密的字符串和一个包含公钥和私钥的Map，使用SM2算法对字符串进行加密，并将加密后的字节数组转换为Base64编码的字符串返回。
     *
     * @param value 待加密的字符串
     * @param keys  包含公钥和私钥的Map，其中公钥的键为KeyUtils.PUBLIC_KEY，私钥的键为KeyUtils.PRIVATE_KEY
     * @return 加密后的字符串，采用Base64编码
     * @throws Exception 如果在加密过程中发生异常，则抛出该异常
     */
    public static String encrypt(String value, Map<String, String> keys) throws Exception {
        // 公钥、密钥
        PublicKey publicKey = KeyUtils.createPublicKey(keys.get(KeyUtils.PUBLIC_KEY));
        PrivateKey privateKey = KeyUtils.createPrivateKey(keys.get(KeyUtils.PRIVATE_KEY));
        // 使用SM2加密
        byte[] encrypt = SM2Utils.encrypt(value.getBytes(), publicKey);
        // 加密转base64
        String encryptBase64Str = Base64.getEncoder().encodeToString(encrypt);
        // 私钥签名,方便对方收到数据后用公钥验签
        // byte[] sign = Sm2Utils.signByPrivateKey(encryptBase64Str.getBytes(), privateKey);

        return encryptBase64Str;
    }

    /**
     * 使用SM2算法解密字符串
     * 该方法接收一个经过Base64编码的加密字符串和一个包含公钥和私钥的Map，使用SM2算法对字符串进行解密，并返回解密后的字符串。
     * 在解密之前，可以选择性地使用私钥对加密数据进行签名，并使用公钥进行验证，以确保数据的完整性和真实性。
     * 如果启用了签名验证功能，则在解密之前会先验证签名，如果验证失败，则抛出异常。
     *
     * @param encodeValue 经过Base64编码的加密字符串
     * @param keys        包含公钥和私钥的Map，其中公钥的键为KeyUtils.PUBLIC_KEY，私钥的键为KeyUtils.PRIVATE_KEY
     * @return 解密后的字符串
     * @throws Exception 如果在解密过程中发生异常，或者启用了签名验证但验证失败，则抛出该异常
     */
    public static String decrypt(String encodeValue, Map<String, String> keys) throws Exception {
        // 公钥、密钥
        PublicKey publicKey = KeyUtils.createPublicKey(keys.get(KeyUtils.PUBLIC_KEY));
        PrivateKey privateKey = KeyUtils.createPrivateKey(keys.get(KeyUtils.PRIVATE_KEY));
        // 私钥签名,方便对方收到数据后用公钥验签
        // byte[] sign = Sm2Utils.signByPrivateKey(encodeValue.getBytes(), privateKey);
        // 公钥验签，验证通过后再进行数据解密
       /* boolean b = Sm2Utils.verifyByPublicKey(encodeValue.getBytes(), publicKey, sign);
        if (!b) {
            throw new RuntimeException("加密数据验证签名失败！");
        } */
        // 私钥解密
        byte[] decode = Base64.getDecoder().decode(encodeValue);
        byte[] decrypt = SM2Utils.decrypt(decode, privateKey);
        assert decrypt != null;
        return new String(decrypt);
    }

    /**
     * 使用SM2算法对原始数据进行加密
     * <p>
     * 该方法接收原始数据的字节数组和公钥，使用SM2算法对数据进行加密，并返回加密后的字节数组。
     *
     * @param data      待加密的原始数据的字节数组
     * @param publicKey 用于加密的公钥
     * @return 加密后的字节数组，如果加密失败则返回null
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
            return null;
        }
    }

    /**
     * 获取EC公钥参数
     * 根据传入的公钥对象，获取对应的EC公钥参数。
     *
     * @param publicKey 公钥对象
     * @return ECPublicKeyParameters对象，如果公钥不是BCECPublicKey类型，则返回null
     */
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
     * 使用SM2算法对加密数据进行解密
     * <p>
     * 根据传入的私钥对加密数据进行解密，并返回解密后的字节数组。
     *
     * @param encodeData 加密后的字节数组
     * @param privateKey 用于解密的私钥
     * @return 解密后的字节数组，如果解密失败则返回null
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
            return null;
        }
    }

    /**
     * 使用私钥对数据进行签名
     * <p>
     * 该方法接收一个字节数组数据和私钥，使用SM2算法和SM3哈希函数对数据进行签名，并返回签名后的字节数组。
     *
     * @param data       待签名的数据字节数组
     * @param privateKey 用于签名的私钥
     * @return 签名后的字节数组
     * @throws Exception 如果在签名过程中发生异常，则抛出该异常
     */
    public static byte[] signByPrivateKey(byte[] data, PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance(GMObjectIdentifiers.sm2sign_with_sm3.toString(), BouncyCastleProvider.PROVIDER_NAME);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    /**
     * 使用公钥验证签名
     * 该方法接收待验证的数据、公钥和签名，使用SM2算法和SM3哈希函数对签名进行验证，
     * 如果签名有效则返回true，否则返回false。
     *
     * @param data      待验证的数据字节数组
     * @param publicKey 用于验证签名的公钥
     * @param signature 待验证的签名字节数组
     * @return 如果签名有效则返回true，否则返回false
     * @throws Exception 如果在验证过程中发生异常，则抛出该异常
     */
    public static boolean verifyByPublicKey(byte[] data, PublicKey publicKey, byte[] signature) throws Exception {
        Signature sig = Signature.getInstance(GMObjectIdentifiers.sm2sign_with_sm3.toString(), BouncyCastleProvider.PROVIDER_NAME);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

}
