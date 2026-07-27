package cn.geelato.ide.service;

import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.oss.OSSFile;
import cn.geelato.web.oss.OSSResult;
import cn.geelato.web.platform.common.OSSFileHelper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Wasm 字节码存储服务。
 * <p>
 * 策略（双重）：
 * <ol>
 *   <li>OSS 优先：若 {@link OSSFileHelper} Bean 存在（即配置了 geelato.oss.*），字节码存 OSS。</li>
 *   <li>本地磁盘回退：OSS 未配置时，字节码存到 {@code geelato.ide.wasm.local-dir} 指定的本地目录。
 *       适合开发环境；多实例部署需运维定期同步该目录到共享存储。</li>
 * </ol>
 * <p>
 * DB 只存 objectName（OSS 路径或本地相对路径），绝不存二进制。
 *
 * @author geelato
 */
@Service
@Slf4j
public class IdeWasmStorage {

    /** 本地回退模式 objectName 前缀（与 OSS 路径区分，便于排查） */
    private static final String LOCAL_PREFIX = "local://";

    @Autowired(required = false)
    private OSSFileHelper ossFileHelper;

    @Value("${geelato.ide.wasm.local-dir:/data/geelato-wasm}")
    private String localDir;

    /** OSS 是否可用（启动时探测一次） */
    private boolean ossAvailable;

    @PostConstruct
    public void init() {
        ossAvailable = ossFileHelper != null;
        if (ossAvailable) {
            log.info("IdeWasmStorage: 使用 OSS 存储 wasm 字节码");
        } else {
            log.warn("IdeWasmStorage: OSS 未配置（geelato.oss.* 缺失），回退到本地磁盘: {}。多实例部署需运维同步该目录。", localDir);
        }
    }

    /**
     * 保存 wasm 字节码，返回 objectName（存 DB）。
     *
     * @param bytes wasm 字节码
     * @param code  脚本 code（用于生成可读的文件名）
     * @return objectName（OSS 路径或 local://xxx）
     */
    public String save(byte[] bytes, String code) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("wasm 字节码为空");
        }
        if (ossAvailable) {
            return saveToOss(bytes, code);
        }
        return saveToLocal(bytes, code);
    }

    /**
     * 按 objectName 加载 wasm 字节码。
     */
    public byte[] load(String objectName) {
        if (Strings.isBlank(objectName)) {
            return null;
        }
        if (objectName.startsWith(LOCAL_PREFIX)) {
            return loadFromLocal(objectName.substring(LOCAL_PREFIX.length()));
        }
        if (ossAvailable) {
            return loadFromOss(objectName);
        }
        log.warn("objectName 指向 OSS 但 OSS 未配置: {}", objectName);
        return null;
    }

    /**
     * 按 objectName 删除 wasm 字节码（删除失败不阻断主流程，只 warn）。
     */
    public void deleteQuietly(String objectName) {
        if (Strings.isBlank(objectName)) {
            return;
        }
        try {
            if (objectName.startsWith(LOCAL_PREFIX)) {
                deleteFromLocal(objectName.substring(LOCAL_PREFIX.length()));
            } else if (ossAvailable) {
                ossFileHelper.removeFile(objectName);
            }
        } catch (Exception e) {
            log.warn("删除 wasm 字节码失败（不影响主流程）: objectName={}", objectName, e);
        }
    }

    /**
     * 加载并返回 base64（HTTP 传输用）。
     */
    public String loadAsBase64(String objectName) {
        byte[] bytes = load(objectName);
        return bytes != null ? Base64.getEncoder().encodeToString(bytes) : null;
    }

    // ======================================================================
    //                           OSS 路径
    // ======================================================================

    private String saveToOss(byte[] bytes, String code) {
        String fileName = code + ".wasm";
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            OSSResult result = ossFileHelper.putFile(fileName, is);
            if (result == null || result.getOssFile() == null) {
                throw new IOException("OSS putFile 返回空结果: " + (result != null ? result.getMessage() : "null"));
            }
            return result.getOssFile().getObjectName();
        } catch (IOException e) {
            throw new RuntimeException("保存 wasm 到 OSS 失败: " + e.getMessage(), e);
        }
    }

    private byte[] loadFromOss(String objectName) {
        try {
            OSSResult result = ossFileHelper.getFile(objectName);
            if (result == null || result.getOssFile() == null
                    || result.getOssFile().getFileMeta() == null
                    || result.getOssFile().getFileMeta().getFileInputStream() == null) {
                log.warn("OSS 加载 wasm 失败（返回空）: objectName={}, msg={}", objectName, result != null ? result.getMessage() : "null");
                return null;
            }
            try (InputStream is = result.getOssFile().getFileMeta().getFileInputStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            log.error("OSS 加载 wasm 异常: objectName={}", objectName, e);
            return null;
        }
    }

    // ======================================================================
    //                           本地磁盘路径
    // ======================================================================

    private String saveToLocal(byte[] bytes, String code) {
        try {
            Path dir = Paths.get(localDir);
            Files.createDirectories(dir);
            String fileName = sanitizeCode(code) + "-" + UIDGenerator.generate() + ".wasm";
            Path file = dir.resolve(fileName);
            Files.write(file, bytes);
            return LOCAL_PREFIX + fileName;
        } catch (IOException e) {
            throw new RuntimeException("保存 wasm 到本地磁盘失败: " + e.getMessage(), e);
        }
    }

    private byte[] loadFromLocal(String fileName) {
        try {
            Path file = Paths.get(localDir).resolve(fileName);
            if (!Files.exists(file)) {
                log.warn("本地 wasm 文件不存在: {}", file);
                return null;
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.error("本地加载 wasm 异常: {}", fileName, e);
            return null;
        }
    }

    private void deleteFromLocal(String fileName) throws IOException {
        Path file = Paths.get(localDir).resolve(fileName);
        Files.deleteIfExists(file);
    }

    /**
     * 把 code 转成文件名安全的片段（只留字母数字下划线）。
     */
    private String sanitizeCode(String code) {
        if (code == null) {
            return "wasm";
        }
        return code.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
