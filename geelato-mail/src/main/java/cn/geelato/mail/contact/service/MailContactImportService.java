package cn.geelato.mail.contact.service;

import cn.geelato.mail.contact.entity.MailContact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 联系人导入/导出服务（CSV / vCard）。
 *
 * 导入契约：{success, failed} 计数 + failures 行号明细（前端 zod schema 仅取
 * success/failed，failures 为诊断附加字段）。解析分两级：
 * - 结构性错误（文件为空/格式不支持/超 10MB/超 2000 行/编码非法/行无法解析）
 *   → fail-fast IllegalArgumentException（消息含行号），整批不写入；
 * - 数据级错误（缺邮箱/邮箱格式非法/文件内重复/库内已存在）
 *   → 跳过该行计入 failed 并记录行号明细，其余行正常导入。
 *
 * CSV 要求首行为表头且含 email 列（兼容本系统导出格式 name,email,phone,notes 及
 * Google/Outlook 常见表头：name/email/e-mail/phone/tel/org/company/notes 等别名）。
 * vCard 支持 3.0/4.0 常见属性（FN/N/EMAIL/TEL/ORG/NOTE）与折行（continuation line）。
 *
 * 导出格式与 mock 契约对齐：CSV 表头 name,email,phone,notes；vCard VERSION:3.0，
 * CRLF 分隔。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailContactImportService {

    /** 导入文件大小上限（与前端 MAX_IMPORT_SIZE 对齐） */
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    /** 导入条数上限 */
    static final int MAX_ROWS = 2000;
    /** 失败行号明细上限（防超长响应） */
    static final int MAX_FAILURE_DETAIL = 50;

    @Autowired
    private MailContactService contactService;

    // ==================== 导入 ====================

    /** 导入结果 */
    public static class ImportOutcome {
        public int success;
        public int failed;
        public final List<Map<String, Object>> failures = new ArrayList<>();
    }

    /** 解析出的待导入联系人行（lineNo 为源文件 1 基行号，失败报告用） */
    static class ParsedContact {
        int lineNo;
        String name;
        String email;
        String phone;
        String org;
        String notes;
    }

    /**
     * 导入联系人文件（multipart 字节）。
     *
     * @param filename 原始文件名（按扩展名识别 csv/vcf）
     * @param bytes    文件字节（需 UTF-8 编码，容许 BOM）
     */
    public ImportOutcome importContacts(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("导入文件为空");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "导入文件超过 10MB 上限（实际 " + bytes.length / 1024 / 1024 + "MB）");
        }
        String format = detectFormat(filename);
        String content = decodeUtf8(bytes);
        List<ParsedContact> rows = "vcf".equals(format) ? parseVcf(content) : parseCsv(content);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("文件无有效联系人数据行");
        }
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("导入条数超过上限 " + MAX_ROWS + "（实际 " + rows.size() + "）");
        }

        ImportOutcome outcome = new ImportOutcome();
        // 库内邮箱预载一次（避免逐行全表扫描）；导入成功的邮箱同步入集合，兼作文件内去重
        java.util.Set<String> knownEmails = contactService.listEntities(null).stream()
                .map(c -> MailContactService.normalizeEmail(c.getEmail()))
                .collect(java.util.stream.Collectors.toSet());
        for (ParsedContact row : rows) {
            String email = MailContactService.normalizeEmail(row.email);
            String rejectReason = rejectReason(row, email, knownEmails);
            if (rejectReason != null) {
                recordFailure(outcome, row.lineNo, email, rejectReason);
                continue;
            }
            String name = MailContactService.isBlank(row.name)
                    ? email.substring(0, email.indexOf('@')) : row.name.trim();
            contactService.create(name, email, row.phone, row.org, null, row.notes, null, null);
            knownEmails.add(email);
            outcome.success++;
        }
        return outcome;
    }

    /**
     * 行级数据校验（null=通过；否则为失败原因）：
     * 缺邮箱 → 格式 → 长度（上限对齐 V78 列宽，防落库 500）→ 去重（文件内 + 库内同集合）。
     */
    private String rejectReason(ParsedContact row, String email, java.util.Set<String> knownEmails) {
        if (email == null || email.isEmpty()) {
            return "缺少邮箱";
        }
        if (!MailContactService.EMAIL_PATTERN.matcher(email).matches()) {
            return "邮箱格式非法";
        }
        if (email.length() > MailContactService.MAX_EMAIL_LEN) {
            return "邮箱超长（上限 " + MailContactService.MAX_EMAIL_LEN + " 字符）";
        }
        if (len(row.name) > MailContactService.MAX_NAME_LEN) {
            return "姓名超长（上限 " + MailContactService.MAX_NAME_LEN + " 字符）";
        }
        if (len(row.phone) > MailContactService.MAX_PHONE_LEN) {
            return "电话超长（上限 " + MailContactService.MAX_PHONE_LEN + " 字符）";
        }
        if (len(row.org) > MailContactService.MAX_ORG_LEN) {
            return "公司/组织超长（上限 " + MailContactService.MAX_ORG_LEN + " 字符）";
        }
        if (len(row.notes) > MailContactService.MAX_NOTES_LEN) {
            return "备注超长（上限 " + MailContactService.MAX_NOTES_LEN + " 字符）";
        }
        if (knownEmails.contains(email)) {
            return "邮箱已存在（库内或文件前序行）";
        }
        return null;
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    private void recordFailure(ImportOutcome outcome, int lineNo, String email, String reason) {
        outcome.failed++;
        if (outcome.failures.size() < MAX_FAILURE_DETAIL) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("line", lineNo);
            if (email != null && !email.isEmpty()) {
                failure.put("email", email);
            }
            failure.put("reason", reason);
            outcome.failures.add(failure);
        }
    }

    // ==================== 格式识别与解码 ====================

    private static String detectFormat(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return "csv";
        }
        if (lower.endsWith(".vcf") || lower.endsWith(".vcard")) {
            return "vcf";
        }
        throw new IllegalArgumentException("不支持的导入格式（仅支持 .csv/.vcf/.vcard）: " + filename);
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            //  strip BOM
            return content.startsWith("\uFEFF") ? content.substring(1) : content;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("文件编码非法，需 UTF-8 编码");
        }
    }

    // ==================== CSV 解析 ====================

    /**
     * 解析 CSV（首行表头；支持引号包裹/逗号/双引号转义）。
     * 结构性错误 fail-fast IllegalArgumentException（消息含行号）。
     */
    static List<ParsedContact> parseCsv(String content) {
        String[] lines = content.split("\\r\\n|\\r|\\n", -1);
        int headerLineNo = -1;
        List<String> header = null;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                headerLineNo = i + 1;
                header = parseCsvLine(lines[i], headerLineNo);
                break;
            }
        }
        if (header == null) {
            throw new IllegalArgumentException("CSV 文件为空");
        }
        Map<String, Integer> columns = mapCsvColumns(header, headerLineNo);

        List<ParsedContact> rows = new ArrayList<>();
        for (int i = headerLineNo; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = parseCsvLine(line, i + 1);
            ParsedContact row = new ParsedContact();
            row.lineNo = i + 1;
            row.name = cell(cells, columns.get("name"));
            row.email = cell(cells, columns.get("email"));
            row.phone = cell(cells, columns.get("phone"));
            row.org = cell(cells, columns.get("org"));
            row.notes = cell(cells, columns.get("notes"));
            rows.add(row);
        }
        return rows;
    }

    /** 表头列映射（别名归一）；缺 email 列 fail-fast */
    private static Map<String, Integer> mapCsvColumns(List<String> header, int lineNo) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i) == null ? "" : header.get(i).trim().toLowerCase(Locale.ROOT);
            switch (h) {
                case "name", "full name", "姓名" -> columns.putIfAbsent("name", i);
                case "email", "e-mail", "email address", "e-mail address", "邮箱", "邮箱地址" ->
                        columns.putIfAbsent("email", i);
                case "phone", "tel", "telephone", "mobile", "电话", "手机" -> columns.putIfAbsent("phone", i);
                case "org", "organization", "company", "公司", "组织" -> columns.putIfAbsent("org", i);
                case "notes", "note", "remark", "备注" -> columns.putIfAbsent("notes", i);
                default -> {
                    // 未识别列忽略（兼容 Google/Outlook 导出的扩展列）
                }
            }
        }
        if (!columns.containsKey("email")) {
            throw new IllegalArgumentException(
                    "第 " + lineNo + " 行（表头）缺少 email 列（需含 name,email 等表头）");
        }
        return columns;
    }

    /**
     * 解析单行 CSV（RFC4180 子集：引号包裹、逗号分隔、双引号转义；不支持跨行字段）。
     * 引号未闭合 fail-fast。
     */
    static List<String> parseCsvLine(String line, int lineNo) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("第 " + lineNo + " 行解析失败： 引号未闭合");
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private static String cell(List<String> cells, Integer index) {
        if (index == null || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index);
        return value == null || value.isBlank() ? null : value;
    }

    // ==================== vCard 解析 ====================

    /**
     * 解析 vCard（3.0/4.0 子集：FN/N/EMAIL/TEL/ORG/NOTE + 折行展开）。
     * 结构性错误（非法行/VCARD 未闭合）fail-fast IllegalArgumentException（消息含行号）；
     * 缺 EMAIL 的卡片保留为数据行（email=null），由导入阶段计 failed。
     */
    static List<ParsedContact> parseVcf(String content) {
        List<String> logicalLines = unfoldLines(content);
        List<ParsedContact> rows = new ArrayList<>();
        ParsedContact current = null;
        int cardStartLine = 0;
        String nFamily = null;
        String nGiven = null;
        for (int i = 0; i < logicalLines.size(); i++) {
            String line = logicalLines.get(i);
            int lineNo = i + 1;
            if (line.isBlank()) {
                continue;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.equals("BEGIN:VCARD")) {
                current = new ParsedContact();
                current.lineNo = lineNo;
                cardStartLine = lineNo;
                nFamily = null;
                nGiven = null;
                continue;
            }
            if (upper.equals("END:VCARD")) {
                if (current == null) {
                    throw new IllegalArgumentException("第 " + lineNo + " 行解析失败： END:VCARD 无配对 BEGIN");
                }
                if (MailContactService.isBlank(current.name)) {
                    current.name = buildNameFromN(nGiven, nFamily);
                }
                rows.add(current);
                current = null;
                continue;
            }
            if (current == null) {
                // 卡片外属性行忽略（vCard 全局属性）
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("第 " + lineNo + " 行解析失败： 非法 vCard 行（缺属性分隔符 ':'）");
            }
            String propName = line.substring(0, colon);
            int semicolon = propName.indexOf(';');
            if (semicolon >= 0) {
                propName = propName.substring(0, semicolon);
            }
            String value = unescapeVcard(line.substring(colon + 1));
            switch (propName.trim().toUpperCase(Locale.ROOT)) {
                case "FN" -> current.name = value;
                case "N" -> {
                    String[] parts = value.split(";", -1);
                    nFamily = parts.length > 0 ? parts[0].trim() : null;
                    nGiven = parts.length > 1 ? parts[1].trim() : null;
                }
                case "EMAIL" -> {
                    if (current.email == null) {
                        current.email = value;
                    }
                }
                case "TEL" -> {
                    if (current.phone == null) {
                        current.phone = value;
                    }
                }
                case "ORG" -> {
                    if (current.org == null) {
                        int semi = value.indexOf(';');
                        current.org = semi >= 0 ? value.substring(0, semi).trim() : value;
                    }
                }
                case "NOTE" -> current.notes = value;
                default -> {
                    // 其他属性（PHOTO/ADR/URL 等）忽略
                }
            }
        }
        if (current != null) {
            throw new IllegalArgumentException(
                    "第 " + cardStartLine + " 行解析失败： VCARD 未闭合（缺 END:VCARD）");
        }
        return rows;
    }

    /** 折行展开：以空格/制表符开头的行为上一行的延续（vCard 2.1/3.0 folding） */
    private static List<String> unfoldLines(String content) {
        String[] physical = content.split("\\r\\n|\\r|\\n", -1);
        List<String> logical = new ArrayList<>();
        for (String line : physical) {
            if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')
                    && !logical.isEmpty()) {
                int last = logical.size() - 1;
                logical.set(last, logical.get(last) + line.substring(1));
            } else {
                logical.add(line);
            }
        }
        return logical;
    }

    private static String buildNameFromN(String given, String family) {
        String g = given == null ? "" : given;
        String f = family == null ? "" : family;
        String joined = (g + " " + f).trim();
        return joined.isEmpty() ? null : joined;
    }

    /** vCard 值反转义（\, \; \n \\；\n 归一为空格，与 mock 导出口径一致） */
    private static String unescapeVcard(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (char ch : value.toCharArray()) {
            if (escaped) {
                out.append(ch == 'n' || ch == 'N' ? ' ' : ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                out.append(ch);
            }
        }
        if (escaped) {
            out.append('\\');
        }
        return out.toString().trim();
    }

    // ==================== 导出 ====================

    /** 导出 CSV（表头 name,email,phone,notes，与 mock 契约对齐） */
    public String exportCsv(List<MailContact> contacts) {
        List<String> rows = new ArrayList<>();
        rows.add("name,email,phone,notes");
        for (MailContact c : contacts) {
            rows.add(String.join(",",
                    escapeCsv(nullToEmpty(c.getName())),
                    escapeCsv(nullToEmpty(c.getEmail())),
                    escapeCsv(nullToEmpty(c.getPhone())),
                    escapeCsv(nullToEmpty(c.getNotes()))));
        }
        return String.join("\n", rows);
    }

    /** 导出 vCard（VERSION:3.0，CRLF 分隔，与 mock 契约对齐；值按 vCard 规范转义） */
    public String exportVcf(List<MailContact> contacts) {
        List<String> cards = new ArrayList<>();
        for (MailContact c : contacts) {
            List<String> lines = new ArrayList<>();
            lines.add("BEGIN:VCARD");
            lines.add("VERSION:3.0");
            lines.add("N:" + escapeVcard(nullToEmpty(c.getName())));
            lines.add("FN:" + escapeVcard(nullToEmpty(c.getName())));
            lines.add("EMAIL:" + escapeVcard(nullToEmpty(c.getEmail())));
            if (!MailContactService.isBlank(c.getPhone())) {
                lines.add("TEL:" + escapeVcard(c.getPhone()));
            }
            if (!MailContactService.isBlank(c.getOrg())) {
                lines.add("ORG:" + escapeVcard(c.getOrg()));
            }
            if (!MailContactService.isBlank(c.getNotes())) {
                lines.add("NOTE:" + escapeVcard(c.getNotes().replace("\n", " ")));
            }
            lines.add("END:VCARD");
            cards.add(String.join("\r\n", lines));
        }
        return String.join("\r\n", cards);
    }

    private static String escapeCsv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** vCard 值转义（\ , ; 及换行） */
    private static String escapeVcard(String value) {
        return value.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\r\n", " ")
                .replace("\n", " ");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
