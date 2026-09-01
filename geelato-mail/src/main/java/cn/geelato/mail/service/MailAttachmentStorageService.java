package cn.geelato.mail.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 邮件附件本地存储服务（P1 第二批）：写信附件真实上传落盘 + 按 token 回读。
 *
 * <p>存储布局：{@code {geelato.upload.root-directory}/mail-attachments/{userId}/{yyyyMM}/{uuid}}
 * ——按用户隔离子目录；落盘文件名不采用用户输入（UUID 随机名），原始文件名仅作为元数据
 * 存 mail_message.attachments_json，从根上规避路径穿越与特殊字符文件系统问题。
 *
 * <p>token 契约：token = 相对路径 {@code {userId}/{yyyyMM}/{uuid}}。回读时强制
 * 校验 token 首段 == 当前用户 ID（归属隔离）+ 归一化路径必须位于存储根目录内（防穿越）。
 *
 * <p>不复用平台 FileHandler 的原因：平台附件模型绑定 accessory 表 + OSS 抽象，
 * 邮件附件需要无表化 token 语义（随 attachments_json 元数据走），引入平台模型会
 * 带来无关表依赖与生命周期耦合。
 */
@Slf4j
@Service
public class MailAttachmentStorageService {

    /** 单附件大小上限（与前端 mail-compose.vue ATTACHMENT_MAX_SIZE 一致：20MB） */
    static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /** token 合法字符（{userId}/{yyyyMM}/{uuid} 只允许这些字符；userId 为平台雪花 ID） */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+/[0-9]{6}/[A-Za-z0-9-]+$");

    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path baseDir;

    public MailAttachmentStorageService(
            @Value("${geelato.upload.root-directory:/upload}") String rootDirectory) {
        this.baseDir = Path.of(rootDirectory, "mail-attachments").normalize();
    }

    /**
     * 落盘上传文件。
     *
     * @param file   multipart 文件（非空、≤20MB，否则 fail-fast IllegalArgumentException）
     * @param userId 当前用户 ID（隔离子目录）
     * @return 存储结果（token/净化后原始名/大小/Content-Type）
     */
    public StoredAttachment store(MultipartFile file, String userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("附件文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "附件大小超过 20MB 上限: " + MailMimeSupport.sanitizeFileName(file.getOriginalFilename()));
        }
        String monthDir = MONTH_DIR.format(LocalDate.now());
        Path userDir = baseDir.resolve(userId).resolve(monthDir).normalize();
        if (!userDir.startsWith(baseDir)) {
            // userId 来自服务端会话上下文，正常不可达；防御性检查防穿越
            throw new IllegalArgumentException("非法的存储目录");
        }
        Files.createDirectories(userDir);
        String storedName = UUID.randomUUID().toString().replace("-", "");
        Path target = userDir.resolve(storedName).normalize();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        }
        String token = userId + "/" + monthDir + "/" + storedName;
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        return new StoredAttachment(token, MailMimeSupport.sanitizeFileName(file.getOriginalFilename()),
                Files.size(target), contentType);
    }

    /**
     * 按 token 解析落盘文件（归属 + 防穿越校验）。
     *
     * @param token   上传时签发的附件引用
     * @param userId  当前用户 ID（token 首段必须等于该值）
     * @return 解析结果；token 非法/越权/文件不存在返回 null（由调用方转 40400）
     */
    public ResolvedAttachment resolve(String token, String userId) {
        if (token == null || token.isBlank() || userId == null || userId.isBlank()) {
            return null;
        }
        if (!TOKEN_PATTERN.matcher(token).matches()) {
            return null;
        }
        String ownerSegment = token.substring(0, token.indexOf('/'));
        if (!ownerSegment.equals(userId)) {
            return null;
        }
        return resolvePath(token);
    }

    /**
     * 系统级按 token 解析落盘文件（不校验 token 归属用户，仍做格式与防穿越校验）。
     *
     * <p>仅用于服务端跨用户代理场景（如 SO PDF 代理：授权锚点为 SO 记录本身，
     * 业务授权由调用方完成），禁止在面向当前用户的下载接口中替代
     * {@link #resolve(String, String)} 使用。
     */
    public ResolvedAttachment resolveSystem(String token) {
        if (token == null || token.isBlank() || !TOKEN_PATTERN.matcher(token).matches()) {
            return null;
        }
        return resolvePath(token);
    }

    /** token → 落盘文件（归一化防穿越 + 存在性 + 元数据读取） */
    private ResolvedAttachment resolvePath(String token) {
        Path path = baseDir.resolve(token).normalize();
        if (!path.startsWith(baseDir)) {
            return null;
        }
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return new ResolvedAttachment(path, Files.size(path));
        } catch (IOException e) {
            // 文件存在但元数据读取失败（权限/IO 异常）— 记日志后按不存在处理（调用方转 40400）
            log.warn("附件元数据读取失败（token={}）: {}", token, e.getMessage());
            return null;
        }
    }

    /** 上传落盘结果（token 随 attachments_json 元数据持久化，发送/下载时回读） */
    public record StoredAttachment(String token, String originalName, long size, String contentType) {
    }

    /** token 解析结果（落盘文件路径 + 实际大小） */
    public record ResolvedAttachment(Path path, long size) {
    }
}
