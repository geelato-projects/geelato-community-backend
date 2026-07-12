package cn.geelato.web.platform.srv.resolve;

import cn.geelato.meta.Attachment;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component
@Slf4j
public class ResolveContextBuilder {
    private final FileHandler fileHandler;

    public ResolveContextBuilder(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    /**
     * 将接口入参统一转换为解析上下文，包含文件来源、payload 与路由所需信息。
     */
    public ResolveContext build(String fileId, MultipartFile multipartFile, String biztag, String config, String appId, String tenantCode) throws IOException {
        if (Strings.isBlank(fileId) && (multipartFile == null || multipartFile.isEmpty())) {
            throw new IllegalArgumentException("fileId or file is required");
        }

        if (log.isDebugEnabled()) {
            log.debug("Building resolve context: biztag={}, appId={}, tenantCode={}, hasFileId={}, hasUploadFile={}, configLength={}",
                    biztag,
                    appId,
                    tenantCode,
                    Strings.isNotBlank(fileId),
                    multipartFile != null && !multipartFile.isEmpty(),
                    config == null ? 0 : config.length());
        }

        ResolveContext ctx = new ResolveContext();
        ctx.setAppId(appId);
        ctx.setTenantCode(tenantCode);
        ctx.setBiztag(biztag);

        File sourceFile;
        String sourceFileName;

        if (Strings.isNotBlank(fileId)) {
            Attachment attachment = fileHandler.getAttachment(fileId);
            if (attachment == null) {
                throw new IllegalArgumentException("file not found");
            }
            ctx.setFileId(attachment.getId());
            sourceFileName = attachment.getName();
            sourceFile = fileHandler.toFile(attachment);
            if (sourceFile == null || !sourceFile.exists()) {
                throw new IllegalArgumentException("file not found");
            }
            if (Strings.isNotBlank(attachment.getObjectId())) {
                ctx.getTempFiles().add(sourceFile);
            }
        } else {
            sourceFileName = multipartFile.getOriginalFilename();
            if (Strings.isBlank(sourceFileName)) {
                sourceFileName = "upload_" + System.currentTimeMillis();
            }
            String suffix = "";
            int dot = sourceFileName.lastIndexOf('.');
            if (dot >= 0) {
                suffix = sourceFileName.substring(dot);
            }
            sourceFile = Files.createTempFile("resolve_upload_", suffix).toFile();
            multipartFile.transferTo(sourceFile);
            ctx.getTempFiles().add(sourceFile);
        }

        ctx.setSourceFile(sourceFile);
        ctx.setSourceFileName(sourceFileName);
        ctx.setSourceExt(extractExt(sourceFileName));

        JSONObject payloadObj = new JSONObject();
        if (Strings.isNotBlank(config)) {
            payloadObj = JSON.parseObject(config);
        }
        ctx.setPayload(payloadObj);
        String feature = Strings.isNotBlank(biztag) ? biztag : payloadObj.getString("feature");
        ctx.setFeature(feature);
        ctx.setParams(payloadObj.getJSONObject("params"));
        ctx.setWorkflow(payloadObj.getJSONObject("workflow"));
        ctx.setTrace(payloadObj.getJSONObject("trace"));

        if (log.isDebugEnabled()) {
            log.debug("Resolve context built: fileId={}, sourceFileName={}, sourceExt={}, feature={}, hasParams={}, hasWorkflow={}, hasTrace={}",
                    ctx.getFileId(),
                    ctx.getSourceFileName(),
                    ctx.getSourceExt(),
                    ctx.getFeature(),
                    ctx.getParams() != null,
                    ctx.getWorkflow() != null,
                    ctx.getTrace() != null);
        }
        return ctx;
    }

    /**
     * 清理本次解析过程中生成的临时文件。
     */
    public void cleanup(ResolveContext ctx) {
        if (ctx == null || CollectionUtils.isEmpty(ctx.getTempFiles())) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Cleaning resolve temp files: count={}, sourceFileName={}",
                    ctx.getTempFiles().size(),
                    ctx.getSourceFileName());
        }
        for (File file : ctx.getTempFiles()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception ignored) {
            }
        }
    }

    private String extractExt(String fileName) {
        if (Strings.isBlank(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return "." + fileName.substring(dot + 1).toUpperCase();
    }
}
