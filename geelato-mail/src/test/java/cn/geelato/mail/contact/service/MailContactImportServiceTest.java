package cn.geelato.mail.contact.service;

import cn.geelato.mail.contact.entity.MailContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MailContactImportService 单元测试（P2-V78 导入/导出）。
 *
 * 覆盖场景：
 * - CSV 解析：表头别名归一（Google/Outlook 常见表头）、引号包裹/逗号/双引号转义；
 *   缺 email 列/引号未闭合 fail-fast 含行号
 * - vCard 解析：FN/N/EMAIL/TEL/ORG/NOTE + 折行展开 + 反转义；
 *   END 无配对 BEGIN / VCARD 未闭合 fail-fast 含行号
 * - 导出：CSV 表头 name,email,phone,notes、vCard VERSION:3.0 CRLF，与 mock 契约对齐
 * - importContacts：空文件/超 10MB/坏编码/不支持格式 fail-fast；
 *   行级校验（缺邮箱/格式非法/库内重复/文件内重复）计 failed 附行号明细，合法行正常导入
 */
class MailContactImportServiceTest {

    private MailContactImportService service;
    private MailContactService contactService;

    @BeforeEach
    void setUp() {
        contactService = mock(MailContactService.class);
        service = new MailContactImportService();
        ReflectionTestUtils.setField(service, "contactService", contactService);
    }

    // ==================== CSV 解析 ====================

    @Test
    @DisplayName("parseCsv：表头别名归一 + 引号/逗号/双引号转义")
    void test_parseCsv_headerAliasesAndQuoting() {
        String csv = "Full Name,E-mail Address,Telephone,Company,Remark\n"
                + "\"张,三\",\"ZS@X.COM\",\"138\"\"0001\",\"Geelato, Inc\",\"备注\"\"引号\"\"\"";

        List<MailContactImportService.ParsedContact> rows = MailContactImportService.parseCsv(csv);

        assertEquals(1, rows.size());
        MailContactImportService.ParsedContact row = rows.get(0);
        assertEquals(2, row.lineNo);
        assertEquals("张,三", row.name, "引号包裹内逗号不分割");
        assertEquals("ZS@X.COM", row.email);
        assertEquals("138\"0001", row.phone, "双引号转义为单引号");
        assertEquals("Geelato, Inc", row.org);
        assertEquals("备注\"引号\"", row.notes);
    }

    @Test
    @DisplayName("parseCsv：缺 email 列 fail-fast，消息含表头行号")
    void test_parseCsv_missingEmailColumnFailFast() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MailContactImportService.parseCsv("name,phone\n张三,138"));
        assertTrue(e.getMessage().contains("第 1 行"), "消息须含行号: " + e.getMessage());
        assertTrue(e.getMessage().contains("email"));
    }

    @Test
    @DisplayName("parseCsvLine：引号未闭合 fail-fast，消息含行号")
    void test_parseCsvLine_unclosedQuoteFailFast() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MailContactImportService.parseCsvLine("\"张三,zhang@x.com", 3));
        assertTrue(e.getMessage().contains("第 3 行"), "消息须含行号: " + e.getMessage());
    }

    // ==================== vCard 解析 ====================

    @Test
    @DisplayName("parseVcf：完整卡片（FN/N/EMAIL/TEL/ORG/NOTE）+ 折行展开 + 反转义")
    void test_parseVcf_fullCard() {
        String vcf = "BEGIN:VCARD\r\n"
                + "VERSION:3.0\r\n"
                + "N:三;张;;;\r\n"
                + "FN:张三\r\n"
                + "EMAIL;TYPE=WORK:zhang@x.com\r\n"
                + "TEL;TYPE=CELL:1380001\r\n"
                + "ORG:Geelato\\, Inc;技术部\r\n"
                + "NOTE:第一行\r\n"
                + " 第二行\r\n"
                + "END:VCARD\r\n";

        List<MailContactImportService.ParsedContact> rows = MailContactImportService.parseVcf(vcf);

        assertEquals(1, rows.size());
        MailContactImportService.ParsedContact row = rows.get(0);
        assertEquals(1, row.lineNo, "行号定位到 BEGIN:VCARD");
        assertEquals("张三", row.name);
        assertEquals("zhang@x.com", row.email, "属性参数（;TYPE=）须剥离");
        assertEquals("1380001", row.phone);
        assertEquals("Geelato, Inc", row.org, "\\, 反转义 + 分号取首段");
        assertEquals("第一行第二行", row.notes, "折行展开拼接");
    }

    @Test
    @DisplayName("parseVcf：缺 FN 时由 N（姓/名）合成姓名")
    void test_parseVcf_nameFromN() {
        String vcf = "BEGIN:VCARD\nN:三;张;;;\nEMAIL:a@x.com\nEND:VCARD\n";
        List<MailContactImportService.ParsedContact> rows = MailContactImportService.parseVcf(vcf);
        assertEquals("张 三", rows.get(0).name, "N:姓;名 → '名 姓' 合成");
    }

    @Test
    @DisplayName("parseVcf：END 无配对 BEGIN / VCARD 未闭合 fail-fast 含行号")
    void test_parseVcf_structureFailFast() {
        IllegalArgumentException orphan = assertThrows(IllegalArgumentException.class,
                () -> MailContactImportService.parseVcf("END:VCARD"));
        assertTrue(orphan.getMessage().contains("第 1 行"));

        IllegalArgumentException unclosed = assertThrows(IllegalArgumentException.class,
                () -> MailContactImportService.parseVcf("BEGIN:VCARD\nEMAIL:a@x.com\n"));
        assertTrue(unclosed.getMessage().contains("第 1 行"), "定位到 BEGIN 行: " + unclosed.getMessage());
        assertTrue(unclosed.getMessage().contains("未闭合"));
    }

    // ==================== 导出（与 mock 契约对齐） ====================

    @Test
    @DisplayName("exportCsv：表头 name,email,phone,notes + 引号包裹，与 mock 契约对齐")
    void test_exportCsv_matchesMockContract() {
        MailContact c = contact("张,三", "zhang@x.com", "138", "备注\"引号\"");
        String csv = service.exportCsv(List.of(c));
        String[] lines = csv.split("\n");
        assertEquals("name,email,phone,notes", lines[0]);
        assertEquals("\"张,三\",\"zhang@x.com\",\"138\",\"备注\"\"引号\"\"\"", lines[1]);
    }

    @Test
    @DisplayName("exportVcf：VERSION:3.0 + CRLF + 可空字段缺席，与 mock 契约对齐")
    void test_exportVcf_matchesMockContract() {
        MailContact c = contact("张三", "zhang@x.com", null, "备注");
        String vcf = service.exportVcf(List.of(c));
        assertTrue(vcf.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"), "CRLF 分隔");
        assertTrue(vcf.contains("FN:张三\r\nEMAIL:zhang@x.com\r\n"));
        assertTrue(vcf.contains("NOTE:备注\r\nEND:VCARD"));
        assertTrue(!vcf.contains("TEL:"), "phone 为 null 时 TEL 缺席");
    }

    // ==================== importContacts 端到端（mock contactService） ====================

    @Test
    @DisplayName("importContacts：结构性错误 fail-fast（空文件/超限/坏编码/不支持格式）")
    void test_importContacts_structuralFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> service.importContacts("a.csv", new byte[0]), "空文件");

        byte[] oversize = new byte[(int) MailContactImportService.MAX_FILE_BYTES + 1];
        IllegalArgumentException big = assertThrows(IllegalArgumentException.class,
                () -> service.importContacts("a.csv", oversize));
        assertTrue(big.getMessage().contains("10MB"));

        IllegalArgumentException badEncoding = assertThrows(IllegalArgumentException.class,
                () -> service.importContacts("a.csv", "中文".getBytes("GBK")));
        assertTrue(badEncoding.getMessage().contains("UTF-8"));

        IllegalArgumentException badExt = assertThrows(IllegalArgumentException.class,
                () -> service.importContacts("a.xlsx", "name,email\n张,a@x.com".getBytes(StandardCharsets.UTF_8)));
        assertTrue(badExt.getMessage().contains("不支持"));
    }

    @Test
    @DisplayName("importContacts：行级校验计 failed 附行号明细，合法行导入；name 缺省回退邮箱前缀")
    void test_importContacts_rowLevelValidation() {
        MailContact existing = new MailContact();
        existing.setEmail("dup@x.com");
        when(contactService.listEntities(isNull())).thenReturn(List.of(existing));
        when(contactService.create(any(), any(), any(), any(), isNull(), any(), isNull(), isNull()))
                .thenReturn(new MailContact());

        String csv = "name,email,phone\n"
                + "张三,zhang@x.com,138\n"   // 行2 成功
                + "李四,\n"                   // 行3 缺邮箱
                + "王五,not-an-email,\n"      // 行4 格式非法
                + "赵六,dup@x.com,\n"         // 行5 库内重复
                + "孙七,zhang@x.com,\n";      // 行6 文件内重复（与行2）
        MailContactImportService.ImportOutcome outcome =
                service.importContacts("c.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, outcome.success);
        assertEquals(4, outcome.failed);
        assertEquals(4, outcome.failures.size());
        assertEquals(3, outcome.failures.get(0).get("line"));
        assertEquals("缺少邮箱", outcome.failures.get(0).get("reason"));
        assertEquals(4, outcome.failures.get(1).get("line"));
        assertEquals("邮箱格式非法", outcome.failures.get(1).get("reason"));
        assertEquals(5, outcome.failures.get(2).get("line"));
        assertEquals(6, outcome.failures.get(3).get("line"));
        verify(contactService, times(1)).create(any(), any(), any(), any(), isNull(), any(), isNull(), isNull());
    }

    @Test
    @DisplayName("importContacts：UTF-8 BOM 容忍 + 超 2000 行 fail-fast")
    void test_importContacts_bomAndRowCap() {
        when(contactService.listEntities(isNull())).thenReturn(List.of());
        when(contactService.create(any(), any(), any(), any(), isNull(), any(), isNull(), isNull()))
                .thenReturn(new MailContact());

        byte[] body = "name,email\n,blank-name@x.com\n".getBytes(StandardCharsets.UTF_8);
        byte[] bomCsv = new byte[3 + body.length];
        bomCsv[0] = (byte) 0xEF; // UTF-8 BOM
        bomCsv[1] = (byte) 0xBB;
        bomCsv[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bomCsv, 3, body.length);
        MailContactImportService.ImportOutcome outcome = service.importContacts("c.csv", bomCsv);
        assertEquals(1, outcome.success, "BOM 剥除后表头识别正常");
        // name 空白 → 回退邮箱 local-part
        verify(contactService).create(org.mockito.ArgumentMatchers.eq("blank-name"),
                org.mockito.ArgumentMatchers.eq("blank-name@x.com"),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull());

        StringBuilder big = new StringBuilder("name,email\n");
        for (int i = 0; i < 2001; i++) {
            big.append("n").append(i).append(",a").append(i).append("@x.com\n");
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.importContacts("big.csv", big.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(e.getMessage().contains("2000"));
    }

    @Test
    @DisplayName("importContacts：字段超列上限的行计 failed 附原因，合法行不受影响")
    void test_importContacts_overLengthRows() {
        when(contactService.listEntities(isNull())).thenReturn(List.of());
        when(contactService.create(any(), any(), any(), any(), isNull(), any(), isNull(), isNull()))
                .thenReturn(new MailContact());

        String overName = "张".repeat(129);
        String csv = "name,email,phone\n"
                + overName + ",over-name@x.com,\n"   // 行2 姓名超长
                + "李四,ok@x.com,138\n";               // 行3 正常
        MailContactImportService.ImportOutcome outcome =
                service.importContacts("c.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, outcome.success);
        assertEquals(1, outcome.failed);
        assertEquals(2, outcome.failures.get(0).get("line"));
        assertTrue(String.valueOf(outcome.failures.get(0).get("reason")).contains("姓名超长"));
        verify(contactService, times(1)).create(any(), any(), any(), any(), isNull(), any(), isNull(), isNull());
    }

    private static MailContact contact(String name, String email, String phone, String notes) {
        MailContact c = new MailContact();
        c.setName(name);
        c.setEmail(email);
        c.setPhone(phone);
        c.setNotes(notes);
        return c;
    }
}
