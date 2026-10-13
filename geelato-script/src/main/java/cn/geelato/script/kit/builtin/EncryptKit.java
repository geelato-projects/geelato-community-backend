package cn.geelato.script.kit.builtin;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.core.TraceEntry;
import cn.geelato.script.kit.KitContext;
import cn.geelato.utils.AesUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 加解密 kit（$gl.encrypt）：摘要与 AES——AES 复用平台 AesUtils（AES/GCM，密文互操作），
 * 密钥经 geelato.script.engine.encrypt.aes-key 配置，未配置时两方法抛错。
 */
@GraalService(name = "encrypt", built = "true", descrption = "摘要与 AES 加解密（md5/aes；密钥未配置时抛错）")
public class EncryptKit {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final KitContext ctx;

    public EncryptKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.encrypt.md5('text')", description = "MD5 摘要（32 位小写十六进制）")
    public String md5(String text) {
        long start = System.currentTimeMillis();
        String out = digestHex(text, "MD5");
        trace("md5", text, start);
        return out;
    }

    @GraalFunction(example = "$gl.encrypt.md5Short('text')", description = "MD5 摘要（16 位：32 位截取 [8,24)）")
    public String md5Short(String text) {
        long start = System.currentTimeMillis();
        String full = digestHex(text, "MD5");
        trace("md5Short", text, start);
        return full.substring(8, 24);
    }

    @GraalFunction(example = "$gl.encrypt.aes('text')", description = "AES 加密（GCM，Base64(IV+密文)；密钥未配置抛错）")
    public String aes(String text) {
        long start = System.currentTimeMillis();
        String out = AesUtils.encrypt(requireText(text, "text"), requireKey());
        trace("aes", text, start);
        return out;
    }

    @GraalFunction(example = "$gl.encrypt.aesDecrypt(cipher)", description = "AES 解密；密钥未配置抛错")
    public String aesDecrypt(String text) {
        long start = System.currentTimeMillis();
        String out = AesUtils.decrypt(requireText(text, "cipher"), requireKey());
        trace("aesDecrypt", text, start);
        return out;
    }

    /** trace 摘要只记输入长度——加解密入参常为敏感明文，不打值。 */
    private void trace(String method, String input, long start) {
        ctx.record(TraceEntry.of("encrypt", method, method + "(len=" + (input == null ? 0 : input.length()) + ")",
                0, System.currentTimeMillis() - start));
    }

    private String requireKey() {
        String key = ctx.getAesKey();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("AES 密钥未配置（geelato.script.engine.encrypt.aes-key），无法调用 $gl.encrypt.aes/aesDecrypt");
        }
        return key;
    }

    private static String requireText(String text, String what) {
        if (text == null) {
            throw new IllegalArgumentException("加密内容不能为空: " + what);
        }
        return text;
    }

    private static String digestHex(String text, String algorithm) {
        if (text == null) {
            throw new IllegalArgumentException("摘要内容不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败: " + algorithm, e);
        }
    }
}
