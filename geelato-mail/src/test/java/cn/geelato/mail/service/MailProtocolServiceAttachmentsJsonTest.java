package cn.geelato.mail.service;

import cn.geelato.mail.entity.MailAccount;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附件元数据 attachments_json 序列化边界测试（ST37-O3 修复回归）。
 *
 * <p>链路：GreenMail 内嵌 IMAP（端口 0 随机，无需 DB/Spring）投递真实 MIME 邮件
 * → {@link MailProtocolService#fetchInbox} 协议拉取解析 → 断言 attachmentsJson
 * 可被 Jackson 严格解析（与读侧 MailMessageService.parseAttachments 同款解析器）
 * 且 name/size/contentType 语义还原。
 *
 * <p>修复前缺陷：ContentExtractor 手拼 JSON 仅对 name 转义双引号，反斜杠/控制字符
 * 未转义、contentType 完全未转义——文件名含反斜杠或 Content-Type 头带引号参数时
 * 产生非法 JSON，读侧解析抛 IllegalStateException（详情接口 500）。
 */
class MailProtocolServiceAttachmentsJsonTest {

    private static final String EMAIL = "st38-o3@greenmail.local";
    private static final String PASSWORD = "st38-o3-secret";
    private static final String EXTERNAL_SENDER = "sender@external.example";

    private static final ObjectMapper JSON = new ObjectMapper();

    private GreenMail greenMail;
    private MailProtocolService service;
    private MailAccount account;

    @BeforeEach
    void setUp() {
        greenMail = new GreenMail(new ServerSetup[]{
                new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP)});
        greenMail.start();
        greenMail.setUser(EMAIL, EMAIL, PASSWORD);
        service = new MailProtocolService();
        account = new MailAccount();
        account.setIncomingProtocol("imap");
        account.setIncomingHost("127.0.0.1");
        account.setIncomingPort(greenMail.getImap().getPort());
        account.setIncomingEncryption("none");
        account.setUsername(EMAIL);
    }

    @AfterEach
    void tearDown() {
        greenMail.stop();
    }

    // ==================== 边界用例 ====================

    @Test
    @DisplayName("文件名含双引号：JSON 合法且 name 还原（旧代码已转义，新代码须同构）")
    void test_fileNameWithDoubleQuote() throws Exception {
        // 混入中文强制 RFC 2047 B 编码：纯 ASCII 引号名不经编码，JavaMail quoting 不转义
        // 内嵌引号，引号在 MIME 头传输层即丢失（生态客观行为，非序列化层问题）
        deliverMail("ST38-QUOTE", attachmentPart("file\"1号.txt", bytes("plain")));
        List<Map<String, Object>> attachments = fetchAttachments("ST38-QUOTE");

        assertEquals(1, attachments.size());
        assertEquals("file\"1号.txt", attachments.get(0).get("name"));
    }

    @Test
    @DisplayName("文件名含反斜杠：JSON 合法且 name 还原（旧代码 \\s 非法转义必坏，本修复核心场景）")
    void test_fileNameWithBackslash() throws Exception {
        deliverMail("ST38-BACKSLASH", attachmentPart("back\\slash-数据.txt", bytes("plain")));
        List<Map<String, Object>> attachments = fetchAttachments("ST38-BACKSLASH");

        assertEquals(1, attachments.size());
        assertEquals("back\\slash-数据.txt", attachments.get(0).get("name"));
    }

    @Test
    @DisplayName("文件名含中文与 emoji：原样输出（非 ASCII 不转义，与旧手拼格式同构）")
    void test_fileNameWithChineseAndEmoji() throws Exception {
        deliverMail("ST38-UNICODE", attachmentPart("第三季度报表📎.pdf", bytes("plain")));
        List<Map<String, Object>> attachments = fetchAttachments("ST38-UNICODE");

        assertEquals(1, attachments.size());
        assertEquals("第三季度报表📎.pdf", attachments.get(0).get("name"));
        String raw = fetchBySubject("ST38-UNICODE").getAttachmentsJson();
        assertTrue(raw.contains("第三季度报表📎.pdf"), "非 ASCII 须原样输出（与旧格式同构）: " + raw);
    }

    @Test
    @DisplayName("size 为 0（空附件）：size 输出数字且不为负（getSize=-1 时 Math.max 兜底为 0）")
    void test_emptyAttachmentSizeNeverNegative() throws Exception {
        deliverMail("ST38-EMPTY", attachmentPart("empty.bin", new byte[0]));
        List<Map<String, Object>> attachments = fetchAttachments("ST38-EMPTY");

        assertEquals(1, attachments.size());
        Object size = attachments.get(0).get("size");
        assertInstanceOf(Number.class, size, "size 须为 JSON 数字: " + attachments.get(0));
        assertTrue(((Number) size).longValue() >= 0, "size 不为负（0 字节附件或 getSize=-1 兜底）: " + size);
    }

    @Test
    @DisplayName("多附件：数组顺序与 MIME part 顺序一致（下标即下载端点 {mailId}:{index} 的 index）")
    void test_multipleAttachmentsOrderPreserved() throws Exception {
        deliverMail("ST38-MULTI",
                attachmentPart("01-第一.pdf", bytes("a")),
                attachmentPart("02\"引号.txt", bytes("b")),
                attachmentPart("03\\slash.zip", bytes("c")));
        List<Map<String, Object>> attachments = fetchAttachments("ST38-MULTI");

        assertEquals(3, attachments.size(), "3 个附件均须记录: " + attachments);
        assertEquals("01-第一.pdf", attachments.get(0).get("name"));
        assertEquals("02\"引号.txt", attachments.get(1).get("name"));
        assertEquals("03\\slash.zip", attachments.get(2).get("name"));
    }

    @Test
    @DisplayName("Content-Type 头含引号参数：contentType 值完整转义，JSON 保持合法（旧代码完全不转义 contentType）")
    void test_contentTypeHeaderWithQuotedParameter() throws Exception {
        MimeBodyPart part = attachmentPart("quote-ct.bin", bytes("data"));
        // 模拟老客户端 Content-Type name 参数内嵌未转义引号（非法但真实存在的头形态）
        part.setHeader("Content-Type", "application/octet-stream; name=\"we\"ird.bin\"");
        deliverMail("ST38-CT", part);
        List<Map<String, Object>> attachments = fetchAttachments("ST38-CT");

        assertEquals(1, attachments.size());
        assertEquals("quote-ct.bin", attachments.get(0).get("name"));
        // GreenMail IMAP BODYSTRUCTURE 将主类型规范化为大写（生态行为），断言大小写不敏感；
        // 引号参数在头解析层被消费——本用例被测对象是「无论 contentType 值形态，JSON 须合法」
        String contentType = String.valueOf(attachments.get(0).get("contentType"));
        assertTrue(contentType.toLowerCase().startsWith("application/octet-stream"),
                "contentType 主类型保留（参数部分透传细节不在本修复断言范围）: " + contentType);
    }

    @Test
    @DisplayName("附件无文件名（disposition=attachment 无 filename）：name 兜底为 attachment（旧语义保留）")
    void test_attachmentWithoutFileNameFallsBack() throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setDisposition(Part.ATTACHMENT);
        part.setContent(bytes("noname"), "application/octet-stream");
        deliverMail("ST38-NONAME", part);
        List<Map<String, Object>> attachments = fetchAttachments("ST38-NONAME");

        assertEquals(1, attachments.size());
        assertEquals("attachment", attachments.get(0).get("name"));
    }

    // ==================== 内部工具 ====================

    /** RFC 2047 B 编码文件名附件（与发送侧 setFileName(encodeText) 同构的真实头形态） */
    private MimeBodyPart attachmentPart(String fileName, byte[] content) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setDisposition(Part.ATTACHMENT);
        part.setFileName(MimeUtility.encodeText(fileName, StandardCharsets.UTF_8.name(), "B"));
        part.setContent(content, "application/octet-stream");
        return part;
    }

    private void deliverMail(String subject, MimeBodyPart... attachments) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(EXTERNAL_SENDER, "外部发件人"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(EMAIL));
        message.setSubject(subject, StandardCharsets.UTF_8.name());
        message.setSentDate(new Date());
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart body = new MimeBodyPart();
        body.setText("ST38 body " + subject, StandardCharsets.UTF_8.name());
        multipart.addBodyPart(body);
        for (MimeBodyPart attachment : attachments) {
            multipart.addBodyPart(attachment);
        }
        message.setContent(multipart);
        greenMail.getUserManager().getUser(EMAIL).deliver(message);
    }

    /** 按主题拉取目标邮件并以读侧同款 Jackson 严格解析 attachmentsJson（非法 JSON 直接抛异常） */
    private List<Map<String, Object>> fetchAttachments(String subject) throws Exception {
        MailProtocolService.ParsedMail mail = fetchBySubject(subject);
        assertEquals(1, mail.getHasAttachment(), "hasAttachment 须置位");
        return JSON.readValue(mail.getAttachmentsJson(), new TypeReference<List<Map<String, Object>>>() {
        });
    }

    private MailProtocolService.ParsedMail fetchBySubject(String subject) throws Exception {
        List<MailProtocolService.ParsedMail> mails = service.fetchInbox(account, PASSWORD);
        return mails.stream()
                .filter(m -> subject.equals(m.getSubject()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("IMAP 未拉取到目标邮件: " + subject));
    }

    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
