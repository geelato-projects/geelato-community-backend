package cn.geelato.mail.service;

import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeUtility;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件 MIME 结构共享辅助（P1 第二批）。
 *
 * <p>同步落库（{@link MailProtocolService} 的 ContentExtractor）与附件内容下载
 * （按 attachments_json 下标回源 IMAP）必须对同一封邮件产生一致的附件顺序，
 * 否则按下标取附件会错位。本类收敛两处共用逻辑：
 * <ul>
 *   <li>{@link #isAttachment(Part)} —— 附件判定谓词（与同步落库完全同口径）</li>
 *   <li>{@link #collectAttachments(Part)} —— 深度优先遍历 MIME 树，按遭遇顺序收集附件 Part</li>
 * </ul>
 *
 * <p>另含源码查看/下载响应所需的纯函数：headers 提取、Content-Disposition 文件名编码、
 * 上传文件名净化、附件类型枚举映射（前端 mailAttachmentSchema type 枚举）。
 */
public final class MailMimeSupport {

    private MailMimeSupport() {
    }

    /**
     * 附件判定谓词（与同步落库 ContentExtractor 同口径，禁止分叉修改）：
     * disposition=ATTACHMENT，或（带文件名且 disposition 非 INLINE 且 disposition 显式存在）。
     */
    public static boolean isAttachment(Part part) throws MessagingException {
        String disposition = part.getDisposition();
        return Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || part.getFileName() != null && !Part.INLINE.equalsIgnoreCase(disposition) && disposition != null;
    }

    /**
     * 深度优先遍历 MIME 树，按遭遇顺序收集全部附件 Part。
     * 顺序与同步落库写 attachments_json 的顺序一致（下标即附件引用 {mailId}:{index} 的 index）。
     */
    public static List<Part> collectAttachments(Part root) throws Exception {
        List<Part> result = new ArrayList<>();
        walk(root, result);
        return result;
    }

    private static void walk(Part part, List<Part> result) throws Exception {
        // 与同步落库 ContentExtractor.walk 同口径：multipart 一律下钻（即使带 ATTACHMENT
        // disposition，如转发的 message/rfc822 嵌套邮件），叶子节点按附件谓词判定
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                walk(mp.getBodyPart(i), result);
            }
            return;
        }
        if (isAttachment(part)) {
            result.add(part);
        }
    }

    /**
     * 提取邮件头为有序 Map（同名头取首次出现值；契约 Record&lt;String,String&gt; 不支持多值，
     * Received 等重复头仅保留首个，文档化语义）。
     */
    public static Map<String, String> headerMap(Message message) throws MessagingException {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<Header> all = message.getAllHeaders();
        while (all.hasMoreElements()) {
            Header header = all.nextElement();
            headers.putIfAbsent(header.getName(), header.getValue() == null ? "" : header.getValue());
        }
        return headers;
    }

    /**
     * 原始报文字节 → 字符串（邮件源码查看）。
     *
     * <p>优先严格 UTF-8 解码（现代邮件 8bit UTF-8 正文正确显示）；非合法 UTF-8 时回退
     * ISO-8859-1 单字节无损映射（GBK 等双字节编码正文呈 Latin-1 形码但字节信息零丢失，
     * RFC822 头部与 base64/QP 编码段恒为 ASCII，两种解码结果一致）。
     * 禁止宽松 UTF-8 解码（默认 REPLACE 静默产生 U+FFFD 替换符——源码查看场景即数据说谎）。
     */
    public static String decodeRawSource(byte[] raw) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(raw, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * 构建 Content-Disposition 响应头值（附件下载）。
     *
     * <p>双文件名策略（RFC 6266/5987）：filename 为 ASCII 兜底（非 ASCII 替换为 _），
     * filename* 为 UTF-8 percent-encoding（中文文件名正确还原）。
     */
    public static String contentDisposition(String filename) {
        String safe = filename == null || filename.isBlank() ? "attachment" : filename;
        StringBuilder ascii = new StringBuilder();
        for (char c : safe.toCharArray()) {
            ascii.append(c >= 0x20 && c < 0x7F && c != '"' && c != '\\' ? c : '_');
        }
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%7E", "~");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 净化上传文件名（仅用于元数据展示，不参与落盘路径）：
     * 剥离路径分隔符/控制字符/引号，压缩空白，上限 128 字符，空白兜底 "attachment"。
     */
    public static String sanitizeFileName(String name) {
        if (name == null) {
            return "attachment";
        }
        // 只保留最后一个路径段，防 ../../etc/passwd 类伪造文件名
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (char c : base.toCharArray()) {
            if (c < 0x20 || c == 0x7F || c == '"' || c == '\'') {
                continue;
            }
            sb.append(c);
        }
        String cleaned = sb.toString().replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "attachment";
        }
        return cleaned.length() > 128 ? cleaned.substring(0, 128) : cleaned;
    }

    /** 解码 MIME encoded-word 文件名（RFC 2047），解码失败回退原文（展示层容错） */
    static String decodeFileName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(name);
        } catch (Exception e) {
            return name;
        }
    }

    /**
     * 附件类型枚举映射（前端 mailAttachmentSchema type: image/pdf/doc/xls/zip/other）。
     * 按 Content-Type 优先、文件扩展名兜底判定；上传响应与附件元数据透传共用，
     * 保证同一附件在上传回显与邮件详情中的类型一致。
     */
    public static String attachmentType(String contentType, String name) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        String ext = name == null ? "" : name.toLowerCase();
        if (ct.startsWith("image/")) {
            return "image";
        }
        if (ct.contains("pdf") || ext.endsWith(".pdf")) {
            return "pdf";
        }
        if (ct.contains("word") || ct.contains("officedocument.word")
                || ext.endsWith(".doc") || ext.endsWith(".docx")) {
            return "doc";
        }
        if (ct.contains("excel") || ct.contains("spreadsheet")
                || ext.endsWith(".xls") || ext.endsWith(".xlsx") || ext.endsWith(".csv")) {
            return "xls";
        }
        if (ct.contains("zip") || ct.contains("compressed")
                || ext.endsWith(".zip") || ext.endsWith(".rar") || ext.endsWith(".7z")) {
            return "zip";
        }
        return "other";
    }
}
