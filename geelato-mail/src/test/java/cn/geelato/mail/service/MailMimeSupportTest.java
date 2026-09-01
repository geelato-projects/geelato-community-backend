package cn.geelato.mail.service;

import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MailMimeSupport 单元测试（P1 第二批，MIME 共享辅助）。
 *
 * 覆盖 13 项场景：
 * - isAttachment（4 项）：ATTACHMENT disposition / INLINE+文件名 / 无 disposition 带文件名
 *   / 正文 text part
 * - collectAttachments（2 项）：multipart/mixed 平铺顺序 / 嵌套 multipart 深度优先遭遇顺序
 *   （与同步落库 ContentExtractor 同口径，下标即附件引用 index）
 * - contentDisposition（3 项）：ASCII 文件名 / 中文文件名 RFC 6266 双文件名 / 引号反斜杠净化
 * - sanitizeFileName（3 项）：路径剥离 / 控制字符与空白归一 / 空白与 ".." 兜底
 * - attachmentType（按 Content-Type 优先、扩展名兜底）
 */
class MailMimeSupportTest {

    // ==================== isAttachment ====================

    @Test
    @DisplayName("isAttachment：disposition=ATTACHMENT → true")
    void test_isAttachment_attachmentDisposition() throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setText("data");
        part.setDisposition(Part.ATTACHMENT);
        assertTrue(MailMimeSupport.isAttachment(part));
    }

    @Test
    @DisplayName("isAttachment：INLINE + 文件名 → false（内联图片不算附件）")
    void test_isAttachment_inlineWithFileName_false() throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent("<img>", "text/html");
        part.setFileName("logo.png");
        part.setDisposition(Part.INLINE);
        assertFalse(MailMimeSupport.isAttachment(part));
    }

    @Test
    @DisplayName("isAttachment：setFileName 自动置 ATTACHMENT disposition（jakarta.mail 行为）→ true")
    void test_isAttachment_fileNameImpliesAttachmentDisposition() throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setText("data");
        // jakarta.mail MimeBodyPart.setFileName 在 Content-Disposition 未设置时自动置 ATTACHMENT，
        // 故构造期"带文件名"必然命中谓词第一分支；谓词第二分支（disposition 显式存在且非 INLINE）
        // 针对的是真实报文解析期 Content-Type 带 name 参数但无 Content-Disposition 头的形态
        part.setFileName("a.txt");
        assertEquals(Part.ATTACHMENT, part.getDisposition());
        assertTrue(MailMimeSupport.isAttachment(part));
    }

    @Test
    @DisplayName("isAttachment：纯正文 text part → false")
    void test_isAttachment_plainBody_false() throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setText("hello");
        assertFalse(MailMimeSupport.isAttachment(part));
    }

    // ==================== collectAttachments ====================

    @Test
    @DisplayName("collectAttachments：multipart/mixed 平铺按遭遇顺序收集")
    void test_collectAttachments_flatMixed_order() throws Exception {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart body = new MimeBodyPart();
        body.setContent("<p>正文</p>", "text/html; charset=UTF-8");
        mixed.addBodyPart(body);
        mixed.addBodyPart(attachment("a.pdf"));
        mixed.addBodyPart(attachment("b.zip"));
        msg.setContent(mixed);
        msg.saveChanges();

        List<Part> attachments = MailMimeSupport.collectAttachments(msg);

        assertEquals(2, attachments.size());
        assertEquals("a.pdf", attachments.get(0).getFileName());
        assertEquals("b.zip", attachments.get(1).getFileName());
    }

    @Test
    @DisplayName("collectAttachments：嵌套 multipart（含 related 内联图）深度优先，INLINE 不收集")
    void test_collectAttachments_nested_order() throws Exception {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        MimeMultipart mixed = new MimeMultipart("mixed");
        // 第一个附件先于正文出现（验证严格遭遇顺序）
        mixed.addBodyPart(attachment("first.txt"));
        // 正文 alternative/related 嵌套 + 内联图
        MimeBodyPart relatedWrapper = new MimeBodyPart();
        MimeMultipart related = new MimeMultipart("related");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<p>正文<img cid:logo></p>", "text/html; charset=UTF-8");
        related.addBodyPart(html);
        MimeBodyPart inlineImg = new MimeBodyPart();
        inlineImg.setContent("PNG", "image/png");
        inlineImg.setFileName("logo.png");
        inlineImg.setDisposition(Part.INLINE);
        related.addBodyPart(inlineImg);
        relatedWrapper.setContent(related);
        mixed.addBodyPart(relatedWrapper);
        mixed.addBodyPart(attachment("last.xlsx"));
        msg.setContent(mixed);
        msg.saveChanges();

        List<Part> attachments = MailMimeSupport.collectAttachments(msg);

        assertEquals(2, attachments.size(), "INLINE 内联图不得计为附件");
        assertEquals("first.txt", attachments.get(0).getFileName());
        assertEquals("last.xlsx", attachments.get(1).getFileName());
    }

    private MimeBodyPart attachment(String fileName) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setText("content-of-" + fileName);
        part.setFileName(fileName);
        part.setDisposition(Part.ATTACHMENT);
        return part;
    }

    // ==================== headerMap ====================

    @Test
    @DisplayName("headerMap：同名头取首次出现值（契约 Record<String,String> 不支持多值）")
    void test_headerMap_duplicateFirstWins() throws Exception {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        msg.setSubject("主题", "UTF-8");
        msg.addHeader("X-Tag", "one");
        msg.addHeader("X-Tag", "two");
        msg.saveChanges();

        Map<String, String> headers = MailMimeSupport.headerMap(msg);

        assertEquals("one", headers.get("X-Tag"), "重复头应保留首值");
        assertTrue(headers.containsKey("Subject"));
    }

    // ==================== contentDisposition ====================

    @Test
    @DisplayName("contentDisposition：ASCII 文件名直接输出 filename")
    void test_contentDisposition_ascii() {
        String value = MailMimeSupport.contentDisposition("report-2026.pdf");
        assertEquals("attachment; filename=\"report-2026.pdf\"; filename*=UTF-8''report-2026.pdf", value);
    }

    @Test
    @DisplayName("contentDisposition：中文文件名 ASCII 段下划线兜底 + filename* percent 编码")
    void test_contentDisposition_chinese() {
        String value = MailMimeSupport.contentDisposition("报表 8月.xlsx");
        // ASCII 兜底段：报表→__，空格保留（0x20 属可打印 ASCII），8 保留，月→_，故为 "__ 8_.xlsx"
        assertTrue(value.startsWith("attachment; filename=\"__ 8_.xlsx\"; filename*=UTF-8''"),
                "ASCII 兜底段应将非 ASCII 字符替换为下划线: " + value);
        assertTrue(value.contains("%E6%8A%A5%E8%A1%A8"), "filename* 应含 UTF-8 percent 编码: " + value);
        assertTrue(value.contains("%20"), "空格应编码为 %20 而非 +: " + value);
    }

    @Test
    @DisplayName("contentDisposition：引号/反斜杠净化，空白名兜底 attachment")
    void test_contentDisposition_sanitize() {
        String value = MailMimeSupport.contentDisposition("a\"b\\c.txt");
        assertTrue(value.contains("filename=\"a_b_c.txt\""), "引号与反斜杠应替换为下划线: " + value);
        assertEquals("attachment; filename=\"attachment\"; filename*=UTF-8''attachment",
                MailMimeSupport.contentDisposition("  "));
    }

    // ==================== sanitizeFileName ====================

    @Test
    @DisplayName("sanitizeFileName：剥离路径段（防 ../../etc/passwd 伪造名）")
    void test_sanitizeFileName_stripPath() {
        assertEquals("passwd", MailMimeSupport.sanitizeFileName("../../etc/passwd"));
        assertEquals("evil.bat", MailMimeSupport.sanitizeFileName("..\\..\\evil.bat"));
    }

    @Test
    @DisplayName("sanitizeFileName：控制字符/引号剔除，连续空白压缩")
    void test_sanitizeFileName_controlChars() {
        assertEquals("abc.txt", MailMimeSupport.sanitizeFileName("a\rb\nc.txt"));
        assertEquals("ab.txt", MailMimeSupport.sanitizeFileName("a\"b'.txt"));
        assertEquals("a b.txt", MailMimeSupport.sanitizeFileName("a \t b.txt"));
    }

    @Test
    @DisplayName("sanitizeFileName：空白/纯点/超长兜底与截断")
    void test_sanitizeFileName_fallback() {
        assertEquals("attachment", MailMimeSupport.sanitizeFileName(null));
        assertEquals("attachment", MailMimeSupport.sanitizeFileName("   "));
        assertEquals("attachment", MailMimeSupport.sanitizeFileName(".."));
        String longName = "a".repeat(200) + ".txt";
        assertEquals(128, MailMimeSupport.sanitizeFileName(longName).length(), "超长名应截断 128 字符");
    }

    // ==================== attachmentType ====================

    @Test
    @DisplayName("attachmentType：Content-Type 优先，扩展名兜底，未知 other")
    void test_attachmentType() {
        assertEquals("image", MailMimeSupport.attachmentType("image/png", "a.bin"));
        assertEquals("pdf", MailMimeSupport.attachmentType("application/pdf", "a.bin"));
        assertEquals("pdf", MailMimeSupport.attachmentType("application/octet-stream", "a.pdf"));
        assertEquals("doc", MailMimeSupport.attachmentType(null, "合同.docx"));
        assertEquals("xls", MailMimeSupport.attachmentType("text/csv", "data.csv"));
        assertEquals("zip", MailMimeSupport.attachmentType(null, "backup.7z"));
        assertEquals("other", MailMimeSupport.attachmentType("text/plain", "notes.txt"));
        assertEquals("other", MailMimeSupport.attachmentType(null, null));
    }

    // ==================== decodeRawSource ====================

    @Test
    @DisplayName("decodeRawSource：合法 UTF-8（含中文 8bit 正文）按 UTF-8 解码")
    void test_decodeRawSource_utf8() {
        String src = "Subject: =?UTF-8?B?5rWL6K+V?=\r\n\r\n正文中文 8bit";
        assertEquals(src, MailMimeSupport.decodeRawSource(src.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("decodeRawSource：非法 UTF-8（GBK 字节）回退 ISO-8859-1 无损映射，禁止 U+FFFD 替换符")
    void test_decodeRawSource_gbkFallback() {
        byte[] gbk = "中文".getBytes(java.nio.charset.Charset.forName("GBK"));
        String decoded = MailMimeSupport.decodeRawSource(gbk);
        assertFalse(decoded.contains("�"), "不得出现 U+FFFD 替换符（宽松 UTF-8 解码即数据说谎）");
        assertEquals(gbk.length, decoded.length(), "ISO-8859-1 单字节映射须无损保长");
        assertEquals(new String(gbk, java.nio.charset.StandardCharsets.ISO_8859_1), decoded);
    }
}
