package cn.geelato.utils;

import lombok.SneakyThrows;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SM4Utils {
    /**
     * 说明：
     * 当前实现刻意保持与历史版本一致的“SM4 + ECB + Padding”兼容语义，
     * 目的是保证已入库密文仍可正常解密。
     *
     * 这不是新的推荐方案。后续若要提升安全性，应升级为带随机 IV 的模式
     * （如 CBC/GCM），并同步调整密文存储格式，避免直接切换导致历史数据失效。
     */
    @SneakyThrows
    public static String encrypt(String data, String keyStr) {
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedBytes = process(true, dataBytes, keyStr);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    @SneakyThrows
    public static String decrypt(String encryptedData, String keyStr) {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = process(false, encryptedBytes, keyStr);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    @SneakyThrows
    private static byte[] process(boolean encrypt, byte[] input, String keyStr) {
        // 这里使用 BC lightweight API，绕开 JCE Provider 验签链路，
        // 避免在部分 fat-jar / 特定 JDK 运行时触发 "JCE cannot authenticate the provider BC"。
        PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new SM4Engine());
        cipher.init(encrypt, new KeyParameter(keyStr.getBytes(StandardCharsets.UTF_8)));

        byte[] output = new byte[cipher.getOutputSize(input.length)];
        int length = cipher.processBytes(input, 0, input.length, output, 0);
        length += cipher.doFinal(output, length);

        byte[] result = new byte[length];
        System.arraycopy(output, 0, result, 0, length);
        return result;
    }
}
