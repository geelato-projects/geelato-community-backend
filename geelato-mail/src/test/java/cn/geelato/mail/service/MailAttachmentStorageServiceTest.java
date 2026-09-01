package cn.geelato.mail.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MailAttachmentStorageService 单元测试（P1 第二批，附件真实上传/回读）。
 *
 * 覆盖 9 项场景：
 * - store（4 项）：正常落盘（token 格式/内容一致/Content-Type 保留）/ 空文件 fail-fast
 *   / 超 20MB fail-fast / Content-Type 缺失兜底 octet-stream
 * - resolve（5 项）：正常回读 / 越权 token（首段非当前用户）/ 路径穿越 token
 *   / 非法字符 token / 合法 token 但文件已删除
 */
class MailAttachmentStorageServiceTest {

    private static final String USER_ID = "1892345678901234567";

    @TempDir
    Path tempDir;

    private MailAttachmentStorageService service;

    @BeforeEach
    void setUp() {
        service = new MailAttachmentStorageService(tempDir.toString());
    }

    // ==================== store ====================

    @Test
    @DisplayName("store 正常：token 为 {userId}/{yyyyMM}/{uuid32}，文件内容与上传一致")
    void test_store_success() throws Exception {
        byte[] content = "hello attachment".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "报表.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        MailAttachmentStorageService.StoredAttachment stored = service.store(file, USER_ID);

        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        assertTrue(stored.token().startsWith(USER_ID + "/" + monthDir + "/"), "token 应含用户与月份段: " + stored.token());
        String storedName = stored.token().substring(stored.token().lastIndexOf('/') + 1);
        assertTrue(storedName.matches("[a-f0-9]{32}"), "落盘文件名应为 32 位 UUID hex: " + storedName);
        assertEquals("报表.xlsx", stored.originalName(), "原始文件名应净化保留");
        assertEquals(content.length, stored.size());
        assertTrue(stored.contentType().contains("spreadsheetml"), "Content-Type 应保留上传值");
        Path storedPath = tempDir.resolve("mail-attachments").resolve(USER_ID).resolve(monthDir).resolve(storedName);
        assertTrue(Files.isRegularFile(storedPath), "文件应真实落盘: " + storedPath);
        assertArrayEquals(content, Files.readAllBytes(storedPath), "落盘内容应与上传一致");
    }

    @Test
    @DisplayName("store 空文件 → fail-fast IllegalArgumentException")
    void test_store_emptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.store(file, USER_ID));
        assertTrue(e.getMessage().contains("为空"));
    }

    @Test
    @DisplayName("store 超过 20MB → fail-fast IllegalArgumentException（不落盘）")
    void test_store_oversized_rejected() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(20L * 1024 * 1024 + 1);
        when(file.getOriginalFilename()).thenReturn("big.zip");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.store(file, USER_ID));
        assertTrue(e.getMessage().contains("20MB"), "错误信息应含上限说明: " + e.getMessage());
    }

    @Test
    @DisplayName("store Content-Type 缺失 → 兜底 application/octet-stream")
    void test_store_nullContentType_defaulted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.bin", null, "x".getBytes(StandardCharsets.UTF_8));
        MailAttachmentStorageService.StoredAttachment stored = service.store(file, USER_ID);
        assertEquals("application/octet-stream", stored.contentType());
    }

    // ==================== resolve ====================

    @Test
    @DisplayName("resolve 正常：store 签发的 token 可回读文件与大小")
    void test_resolve_success() throws Exception {
        byte[] content = "roundtrip".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", content);
        String token = service.store(file, USER_ID).token();

        MailAttachmentStorageService.ResolvedAttachment resolved = service.resolve(token, USER_ID);

        assertNotNull(resolved);
        assertEquals(content.length, resolved.size());
        assertArrayEquals(content, Files.readAllBytes(resolved.path()));
    }

    @Test
    @DisplayName("resolve 越权 token（首段为他人 userId）→ null")
    void test_resolve_foreignToken_rejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());
        String token = service.store(file, USER_ID).token();
        String foreignToken = "9999999999999999999" + token.substring(token.indexOf('/'));

        assertNull(service.resolve(foreignToken, USER_ID), "他人 token 不得解析成功");
    }

    @Test
    @DisplayName("resolve 路径穿越/非法字符 token → null（TOKEN_PATTERN 拒绝）")
    void test_resolve_traversalToken_rejected() {
        assertNull(service.resolve("../../etc/passwd", USER_ID));
        assertNull(service.resolve(USER_ID + "/202608/..%2F..%2Fsecret", USER_ID));
        assertNull(service.resolve(USER_ID + "/2026-08/abc", USER_ID), "月份段必须 6 位数字");
        assertNull(service.resolve("", USER_ID));
        assertNull(service.resolve(null, USER_ID));
    }

    @Test
    @DisplayName("resolve 合法 token 但文件已删除 → null")
    void test_resolve_missingFile_null() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());
        String token = service.store(file, USER_ID).token();
        Files.delete(tempDir.resolve("mail-attachments").resolve(token));

        assertNull(service.resolve(token, USER_ID), "文件被清理后应解析失败（调用方转 40400）");
    }
}
