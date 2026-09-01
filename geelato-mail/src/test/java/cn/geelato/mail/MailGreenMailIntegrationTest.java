package cn.geelato.mail;

import cn.geelato.mail.service.MailCryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 邮件模块 GreenMail 集成测试（AC-21）：真实 IMAP/SMTP 协议全链路。
 *
 * <p>链路：GreenMail 内嵌邮件服务器（端口 0 随机空闲端口，并行实例不冲突）
 * → POST /api/mail/accounts 创建指向 GreenMail 的邮箱账户（创建前真实 IMAP LOGIN +
 * SMTP AUTH 连通性验证）→ POST /api/mail/send 经真实 SMTP 发信 → GreenMail 投递到
 * 本地用户 INBOX → POST /api/mail/sync 经真实 IMAP 拉取 → 断言 mail_message 落库、
 * 系统文件夹聚合、未读数、发件箱副本。为 AC-2（同步收信）/AC-3（SMTP 发信）提供
 * 真实协议链路证据；T4 追加收信过滤器钩子断言（sync 落库实时触发 move 动作），
 * 为 AC-12（过滤器）提供真实协议链路证据。
 *
 * <p><b>运行前提（一次性准备，故意不设默认自动跑）</b>：
 * <ol>
 *   <li>本机 MySQL 存在空 scratch schema {@code geelato_wt0714_gm}（模块化后无需
 *       手工导入表结构：MailSchemaInitializer 启动时在主库（scratch 库）自动幂等建表）。
 *       历史搭建脚本见 fms .geelato/logs/sessions/auto-20260812-071447/st-15-R1-output.md。</li>
 *   <li>显式开启：{@code mvn test -Dtest=MailGreenMailIntegrationTest -Dgeelato.mail.greenmail.it=1}。
 *       未加 -D 时整个类跳过（JUnit 条件求值在 Spring 上下文创建之前，裸 mvn test 零影响）。</li>
 *   <li>可用 -DMAIL_GREENMAIL_JDBC_URL='jdbc:mysql://...' 覆盖目标库（仅限 geelato_wt*
 *       开头的 scratch 库，@BeforeAll 护栏检查 URL，指向共享 geelato 库时整类 assumption
 *       abort，防止污染共享开发库）。</li>
 * </ol>
 *
 * <p>数据隔离与可重复跑：测试邮件主题带 epoch 唯一后缀；@BeforeAll/@AfterAll 按
 * 测试邮箱地址清理 mail_message/mail_account/mail_contact_recent 残留；
 * Flyway 双禁（geelato.flyway.enabled + spring.flyway.enabled）。
 */
@SpringBootTest(properties = {
        "spring.datasource.primary.jdbc-url=${MAIL_GREENMAIL_JDBC_URL:"
                + "jdbc:mysql://127.0.0.1:3306/geelato_wt0714_gm"
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&tinyInt1isBit=false}",
        "geelato.flyway.enabled=false",
        "spring.flyway.enabled=false",
        "geelato.mail.kek=st15-greenmail-kek-20260812",
        "geelato.upload.root-directory=${java.io.tmpdir}/st15-gm-upload"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfSystemProperty(named = "geelato.mail.greenmail.it", matches = "1")
class MailGreenMailIntegrationTest {

    private static final String TEST_EMAIL = "st15@greenmail.local";
    private static final String TEST_PASSWORD = "st15-secret-123";
    private static final String EXTERNAL_SENDER = "boss@external.example";
    /** SMTP 投递收件等待上限（毫秒；本地回环正常 <1s，8s 留足慢机余量） */
    private static final long SMTP_WAIT_MS = 8000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    private MailCryptoService cryptoService;

    @Autowired
    private Environment environment;

    private GreenMail greenMail;
    private JdbcTemplate jdbc;
    private String jwtToken;
    private String accountId;
    private int imapPort;
    private int smtpPort;

    // ==================== 生命周期 ====================

    @BeforeAll
    void setUp() throws Exception {
        // 护栏：scratch 库强制（防误指共享 geelato 开发库造成测试数据污染）
        String url = environment.getProperty("spring.datasource.primary.jdbc-url");
        Assumptions.assumeTrue(url != null && url.contains("/geelato_wt"),
                "GreenMail IT 仅允许指向 geelato_wt* scratch 库，当前: " + url);

        // 端口 0 = GreenMail 绑定随机空闲端口（2.0.1 实证支持），并行实例天然隔离
        greenMail = new GreenMail(new ServerSetup[]{
                new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP),
                new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP)});
        greenMail.start();
        smtpPort = greenMail.getSmtp().getPort();
        imapPort = greenMail.getImap().getPort();
        assertTrue(smtpPort > 0 && imapPort > 0, "GreenMail 须绑定非零端口");
        greenMail.setUser(TEST_EMAIL, TEST_EMAIL, TEST_PASSWORD);

        jdbc = new JdbcTemplate(primaryDataSource);
        jwtToken = login("admin", "123456");
        cleanupDb();
        accountId = createAccount();
    }

    @AfterAll
    void tearDown() {
        try {
            if (jdbc != null) {
                cleanupDb();
            }
        } finally {
            // finally 保证 DB 清理异常时邮件服务器仍被停止（随机端口不残留绑定）
            if (greenMail != null) {
                greenMail.stop();
            }
        }
    }

    private void cleanupDb() {
        jdbc.update("DELETE m FROM mail_message m JOIN mail_account a ON m.account_id = a.id "
                + "WHERE a.email = ?", TEST_EMAIL);
        jdbc.update("DELETE FROM mail_account WHERE email = ?", TEST_EMAIL);
        jdbc.update("DELETE FROM mail_contact_recent WHERE email = ?", TEST_EMAIL);
        // T4 过滤器兜底清理：残留启用过滤器（条件 to 含测试邮箱）会在复跑时误移走 T2/T3 同步邮件
        jdbc.update("DELETE FROM mail_filter WHERE name LIKE 'ST15-%'");
    }

    // ==================== 链路用例 ====================

    @Test
    @Order(1)
    @DisplayName("账户：真实 IMAP+SMTP 连通性验证通过；凭据 AES-GCM 加密落库且响应不回显")
    void test01_verifyAndCredentialEncryption() throws Exception {
        // 真实协议握手（不落库）
        JsonNode verify = doPost("/api/mail/accounts/verify", accountRequestJson());
        assertEquals(20000, verify.path("code").asInt(), "verify 业务码");
        assertTrue(verify.path("data").path("success").asBoolean(),
                "真实 IMAP LOGIN + SMTP AUTH 须成功: " + verify);

        // 落库行：密文非明文、可被同 KEK 解密回原文
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT password_cipher, is_default, username, incoming_host, incoming_port, "
                        + "incoming_encryption, outgoing_host, outgoing_port, outgoing_encryption "
                        + "FROM mail_account WHERE id = ?", accountId);
        String cipher = (String) row.get("password_cipher");
        assertNotNull(cipher, "password_cipher 必须落库");
        assertNotEquals(TEST_PASSWORD, cipher, "严禁明文落库");
        assertTrue(Base64.getDecoder().decode(cipher).length > 12, "密文须含 GCM IV(12B)+tag");
        assertEquals(TEST_PASSWORD, cryptoService.decrypt(cipher), "同 KEK 解密须还原明文");
        assertEquals(1, ((Number) row.get("is_default")).intValue(), "首个账户自动默认");
        assertEquals(TEST_EMAIL, row.get("username"), "username 默认同邮箱");
        assertEquals("127.0.0.1", row.get("incoming_host"));
        assertEquals(imapPort, ((Number) row.get("incoming_port")).intValue());
        assertEquals("none", row.get("incoming_encryption"));
        assertEquals("127.0.0.1", row.get("outgoing_host"));
        assertEquals(smtpPort, ((Number) row.get("outgoing_port")).intValue());
        assertEquals("none", row.get("outgoing_encryption"));

        // 响应契约：任何账户读路径不回显凭据字段
        JsonNode accounts = doGet("/api/mail/accounts");
        assertEquals(20000, accounts.path("code").asInt());
        for (JsonNode acc : accounts.path("data")) {
            assertFalse(acc.has("passwordCipher"), "账户响应严禁回显 passwordCipher");
            assertFalse(acc.has("password"), "账户响应严禁回显 password");
        }
    }

    @Test
    @Order(2)
    @DisplayName("IMAP 同步：真实协议拉取落库 inbox、未读聚合正确、UID 去重幂等、标已读未读数归零")
    void test02_imapSyncChain() throws Exception {
        String subject = "ST15-SYNC-" + System.currentTimeMillis();
        String bodyMarker = "SYNC-BODY-" + System.nanoTime();
        deliverToGreenMailInbox(subject, bodyMarker);

        // 首次同步：拉 1 落 1
        JsonNode sync = doPost("/api/mail/sync?accountId=" + accountId, null);
        assertEquals(20000, sync.path("code").asInt(), "sync 业务码: " + sync);
        assertEquals(1, sync.path("data").path("synced").asInt(), "首次同步落库 1 封");
        assertEquals(1, sync.path("data").path("total").asInt(), "服务器侧共 1 封");

        // mail_message 落库断言
        Map<String, Object> msg = jdbc.queryForMap(
                "SELECT id, folder, read_status, imap_uid, subject, from_email, to_json, "
                        + "send_date, content_text, preview, is_draft, del_status "
                        + "FROM mail_message WHERE account_id = ? AND folder = 'inbox'", accountId);
        assertEquals("inbox", msg.get("folder"));
        assertEquals("unread", msg.get("read_status"), "新同步邮件须为未读");
        assertNotNull(msg.get("imap_uid"), "IMAP UID 必须落库（去重依据）");
        assertFalse(msg.get("imap_uid").toString().isBlank());
        assertEquals(subject, msg.get("subject"));
        assertEquals(EXTERNAL_SENDER, msg.get("from_email"));
        assertTrue(msg.get("to_json").toString().contains(TEST_EMAIL), "to_json 含收件人");
        assertNotNull(msg.get("send_date"), "发送时间必须落库");
        assertTrue(msg.get("content_text").toString().contains(bodyMarker), "正文须完整落库");
        assertNotNull(msg.get("preview"), "预览须生成");
        assertEquals(0, ((Number) msg.get("is_draft")).intValue());
        assertEquals(0, ((Number) msg.get("del_status")).intValue());
        String messageId = msg.get("id").toString();

        // 系统文件夹聚合：inbox unread=1 total=1
        assertFolderCount("inbox", 1, 1);

        // 列表可见（读路径走本地表）
        JsonNode list = doGet("/api/mail/list?folder=inbox&accountId=" + accountId);
        assertEquals(20000, list.path("code").asInt());
        assertEquals(1, list.path("data").path("total").asInt(), "inbox 列表总数");
        assertEquals(subject, list.path("data").path("list").get(0).path("subject").asText());
        assertEquals("unread", list.path("data").path("list").get(0).path("readStatus").asText());

        // 幂等：UID 去重，重复同步不落库
        JsonNode sync2 = doPost("/api/mail/sync?accountId=" + accountId, null);
        assertEquals(0, sync2.path("data").path("synced").asInt(), "重复同步须 UID 去重");
        assertEquals(1, sync2.path("data").path("total").asInt());
        Integer inboxRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mail_message WHERE account_id = ? AND folder = 'inbox' "
                        + "AND del_status = 0", Integer.class, accountId);
        assertEquals(1, inboxRows, "重复同步后 inbox 仍只有 1 行");

        // 标已读 → 未读数归零
        JsonNode batch = doPost("/api/mail/batch",
                "{\"ids\":[\"" + messageId + "\"],\"op\":\"read\"}");
        assertEquals(20000, batch.path("code").asInt());
        assertEquals(1, batch.path("data").path("affected").asInt());
        assertFolderCount("inbox", 0, 1);
    }

    @Test
    @Order(3)
    @DisplayName("SMTP 发信：真实协议送达 GreenMail、发件箱副本落库、再经 IMAP 回收到 inbox（端到端回环）")
    void test03_smtpSendRoundTrip() throws Exception {
        String subject = "ST15-SEND-" + System.currentTimeMillis();
        String htmlMarker = "SEND-HTML-" + System.nanoTime();

        JsonNode send = doPost("/api/mail/send",
                "{\"fromAccountId\":\"" + accountId + "\","
                        + "\"to\":[{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"自己\"}],"
                        + "\"subject\":\"" + subject + "\","
                        + "\"content\":\"<p>" + htmlMarker + "</p>\"}");
        assertEquals(20000, send.path("code").asInt(), "send 业务码: " + send);
        String copyId = send.path("data").path("id").asText();
        assertFalse(copyId.isBlank(), "发件箱副本 id 必须返回");

        // GreenMail 侧真实收到 SMTP 邮件
        assertTrue(greenMail.waitForIncomingEmail(SMTP_WAIT_MS, 1), "GreenMail 须在限时内收到邮件");
        MimeMessage received = findReceivedBySubject(subject);
        assertNotNull(received, "GreenMail 收件中须存在本主题邮件");
        assertEquals(TEST_EMAIL, ((InternetAddress) received.getFrom()[0]).getAddress());
        assertTrue(received.getContent().toString().contains(htmlMarker), "SMTP 送达正文完整");

        // 发件箱副本落库
        Map<String, Object> sent = jdbc.queryForMap(
                "SELECT folder, send_status, subject, from_email, to_json, read_status, is_draft "
                        + "FROM mail_message WHERE id = ?", copyId);
        assertEquals("sent", sent.get("folder"), "发件箱副本 folder=sent");
        assertEquals("sent", sent.get("send_status"), "发送成功 send_status=sent");
        assertEquals(subject, sent.get("subject"));
        assertEquals(TEST_EMAIL, sent.get("from_email"));
        assertTrue(sent.get("to_json").toString().contains(TEST_EMAIL));
        assertEquals("read", sent.get("read_status"), "发件箱副本恒已读");
        assertEquals(0, ((Number) sent.get("is_draft")).intValue());

        // 端到端回环：SMTP 发出 → GreenMail 投递 INBOX → IMAP 同步回本地
        // （test02 的邮件 UID 已落库被去重，本次仅新落自发信 1 封）
        JsonNode sync = doPost("/api/mail/sync?accountId=" + accountId, null);
        assertEquals(20000, sync.path("code").asInt());
        assertEquals(1, sync.path("data").path("synced").asInt(), "自发信经 IMAP 回环新落 1 封");
        assertEquals(2, sync.path("data").path("total").asInt(), "服务器侧累计 2 封");
        Map<String, Object> echo = jdbc.queryForMap(
                "SELECT folder, read_status, subject FROM mail_message "
                        + "WHERE account_id = ? AND folder = 'inbox' AND subject = ?",
                accountId, subject);
        assertEquals("unread", echo.get("read_status"), "回环邮件为未读新件");

        // 文件夹终态聚合：inbox 2 封（test02 已读 + 回环未读）/ sent 1 封
        assertFolderCount("inbox", 1, 2);
        assertFolderCount("sent", 0, 1);

        // 发件箱列表可见（前端契约读路径）
        JsonNode sentList = doGet("/api/mail/list?folder=sent&accountId=" + accountId);
        assertEquals(1, sentList.path("data").path("total").asInt(), "发件箱列表总数");
        assertEquals(subject, sentList.path("data").path("list").get(0).path("subject").asText());
    }

    @Test
    @Order(4)
    @DisplayName("收信过滤器钩子：sync 落库实时触发启用过滤器，命中邮件移入自定义文件夹而非 inbox（AC-12）")
    void test04_filterHookOnIncoming() throws Exception {
        // move 目标为 custom_* 文件夹键（与前端 MailFilterAction.move 契约同构；
        // 保存时仅校验 custom_ 前缀形态，不要求 mail_folder_custom 记录，见 MailFilterService.validateAction）
        String targetFolder = "custom_st15it";
        String subject = "ST15-FILTER-" + System.currentTimeMillis();

        // 配置启用过滤器：收件人含本测试邮箱地址 → move 到自定义文件夹
        JsonNode created = doPost("/api/mail/filters",
                "{\"name\":\"ST15-FILTER-HOOK\",\"enabled\":true,"
                        + "\"conditions\":[{\"field\":\"to\",\"operator\":\"contains\",\"value\":\""
                        + TEST_EMAIL + "\"}],"
                        + "\"action\":{\"move\":\"" + targetFolder + "\"}}");
        assertEquals(20000, created.path("code").asInt(), "创建过滤器业务码: " + created);
        String filterId = created.path("data").path("id").asText();
        assertFalse(filterId.isBlank(), "过滤器 id 必须回填: " + created);

        try {
            // 外部来信 → IMAP sync → 收信钩子（MailController.sync → applyToIncoming）实时命中执行 move
            deliverToGreenMailInbox(subject, "FILTER-BODY-" + System.nanoTime());
            JsonNode sync = doPost("/api/mail/sync?accountId=" + accountId, null);
            assertEquals(20000, sync.path("code").asInt(), "sync 业务码: " + sync);
            assertEquals(1, sync.path("data").path("synced").asInt(), "仅新落本用例投递的 1 封");
            assertEquals(3, sync.path("data").path("total").asInt(), "服务器侧累计 3 封（T2/T3 UID 去重跳过）");

            // 落库断言：邮件在目标自定义文件夹而非 inbox；move 不改已读态
            Map<String, Object> moved = jdbc.queryForMap(
                    "SELECT folder, read_status, to_json FROM mail_message "
                            + "WHERE account_id = ? AND subject = ?", accountId, subject);
            assertEquals(targetFolder, moved.get("folder"), "命中过滤器须移入自定义文件夹而非 inbox");
            assertEquals("unread", moved.get("read_status"), "move 动作不改变已读态");
            assertTrue(moved.get("to_json").toString().contains(TEST_EMAIL), "to_json 含收件人");
            Integer inboxHit = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mail_message WHERE account_id = ? AND folder = 'inbox' "
                            + "AND subject = ?", Integer.class, accountId, subject);
            assertEquals(0, inboxHit, "被移动邮件不得留在 inbox");

            // 聚合：被移动邮件不计入 inbox，inbox 保持 T3 终态（unread=1 total=2）
            assertFolderCount("inbox", 1, 2);

            // 读路径：自定义文件夹列表可见，inbox 列表不可见
            JsonNode customList = doGet("/api/mail/list?folder=" + targetFolder + "&accountId=" + accountId);
            assertEquals(20000, customList.path("code").asInt());
            assertEquals(1, customList.path("data").path("total").asInt(), "自定义文件夹列表总数");
            assertEquals(subject, customList.path("data").path("list").get(0).path("subject").asText());
            assertEquals("unread", customList.path("data").path("list").get(0).path("readStatus").asText());
            JsonNode inboxList = doGet("/api/mail/list?folder=inbox&accountId=" + accountId);
            assertEquals(2, inboxList.path("data").path("total").asInt(), "inbox 列表总数保持 T3 终态");
            for (JsonNode item : inboxList.path("data").path("list")) {
                assertNotEquals(subject, item.path("subject").asText(), "inbox 列表不得含被移动邮件");
            }
        } finally {
            // 收尾删除过滤器（cleanupDb 双向兜底同口径）：残留启用过滤器会在复跑时误移走 T2/T3 邮件
            JsonNode deleted = doDelete("/api/mail/filters/" + filterId);
            assertEquals(20000, deleted.path("code").asInt(), "删除过滤器业务码: " + deleted);
        }
    }

    // ==================== 内部工具 ====================

    /** 经 MockMvc 走完整 HTTP 链路（含安全拦截器），返回业务响应 JSON */
    private JsonNode doPost(String uri, String jsonBody) throws Exception {
        MvcResult result = mockMvc.perform(post(uri)
                        .header("Authorization", "JWTBearer " + jwtToken)
                        .header("Tenant-Code", "geelato")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody == null ? "{}" : jsonBody))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode doGet(String uri) throws Exception {
        MvcResult result = mockMvc.perform(get(uri)
                        .header("Authorization", "JWTBearer " + jwtToken)
                        .header("Tenant-Code", "geelato"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode doDelete(String uri) throws Exception {
        MvcResult result = mockMvc.perform(delete(uri)
                        .header("Authorization", "JWTBearer " + jwtToken)
                        .header("Tenant-Code", "geelato"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String token = root.path("data").path("token").asText(null);
        if (token == null) {
            fail("登录失败（scratch 库须含 admin/123456 预置用户）: " + root);
        }
        return token;
    }

    /** 创建账户（创建链路内含真实 IMAP+SMTP 连通性验证，失败会拒绝创建） */
    private String createAccount() throws Exception {
        JsonNode created = doPost("/api/mail/accounts", accountRequestJson());
        assertEquals(20000, created.path("code").asInt(), "创建账户业务码: " + created);
        String id = created.path("data").path("id").asText(null);
        assertNotNull(id, "创建响应须回填雪花 id（L-1 已修复）: " + created);
        assertFalse(id.isBlank());
        return id;
    }

    private String accountRequestJson() {
        return "{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"ST15链路账户\","
                + "\"password\":\"" + TEST_PASSWORD + "\","
                + "\"servers\":{"
                + "\"incoming\":{\"host\":\"127.0.0.1\",\"port\":" + imapPort
                + ",\"protocol\":\"imap\",\"encryption\":\"none\"},"
                + "\"outgoing\":{\"host\":\"127.0.0.1\",\"port\":" + smtpPort
                + ",\"encryption\":\"none\"}}}";
    }

    /** 直接向 GreenMail 用户 INBOX 投递一封邮件（不经过被测 SMTP 链路，保证同步用例独立） */
    private void deliverToGreenMailInbox(String subject, String bodyMarker) throws Exception {
        GreenMailUser user = greenMail.getUserManager().getUser(TEST_EMAIL);
        assertNotNull(user, "GreenMail 用户须已创建");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(EXTERNAL_SENDER, "外部发件人"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(TEST_EMAIL));
        message.setSubject(subject, StandardCharsets.UTF_8.name());
        message.setSentDate(new Date());
        message.setText(bodyMarker, StandardCharsets.UTF_8.name());
        user.deliver(message);
    }

    private MimeMessage findReceivedBySubject(String subject) throws Exception {
        for (MimeMessage m : greenMail.getReceivedMessages()) {
            if (subject.equals(m.getSubject())) {
                return m;
            }
        }
        return null;
    }

    /** 断言 GET /folders 指定系统文件夹的 [unread,total]（兼容 long 被序列化为字符串的契约） */
    private void assertFolderCount(String folderKey, long expectedUnread, long expectedTotal)
            throws Exception {
        JsonNode folders = doGet("/api/mail/folders?accountId=" + accountId);
        assertEquals(20000, folders.path("code").asInt());
        for (JsonNode f : folders.path("data")) {
            if (folderKey.equals(f.path("key").asText())) {
                assertEquals(expectedUnread, f.path("unreadCount").asLong(),
                        folderKey + " 未读数: " + f);
                assertEquals(expectedTotal, f.path("totalCount").asLong(),
                        folderKey + " 总数: " + f);
                return;
            }
        }
        fail("folders 响应缺少系统文件夹: " + folderKey);
    }
}
