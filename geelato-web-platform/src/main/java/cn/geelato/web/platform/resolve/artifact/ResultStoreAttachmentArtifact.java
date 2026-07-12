package cn.geelato.web.platform.resolve.artifact;

import cn.geelato.meta.Attachment;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.resolve.core.ResolveArtifact;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.srv.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.srv.file.param.FileParam;
import cn.geelato.web.platform.utils.FileParamUtils;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Component
public class ResultStoreAttachmentArtifact implements ResolveArtifact {
    private final FileHandler fileHandler;

    public ResultStoreAttachmentArtifact(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    @Override
    public String getId() {
        return "result.store";
    }

    @Override
    public boolean supports(ResolveContext ctx) {
        if (ctx == null || ctx.getParams() == null) {
            return false;
        }
        return ctx.getParams().getBooleanValue("storeResult");
    }

    @Override
    public Object execute(ResolveContext ctx) throws Exception {
        String json = JSON.toJSONString(ctx.getResult());
        File tempFile = Files.createTempFile("resolve_result_", ".json").toFile();
        Files.write(tempFile.toPath(), json.getBytes(StandardCharsets.UTF_8));

        try {
            String fileName = Strings.isBlank(ctx.getTaskId()) ? "resolve_result.json" : ctx.getTaskId() + ".json";
            String sourceType = AttachmentSourceEnum.ATTACH.getValue();
            FileParam fileParam = FileParamUtils.byLocal(sourceType, "resolveResult", ctx.getAppId(), ctx.getTenantCode());
            Attachment attachment = fileHandler.upload(tempFile, fileName, fileParam);
            if (attachment == null || Strings.isBlank(attachment.getId())) {
                throw new IllegalStateException("store result failed");
            }
            ctx.putArtifactData("result.attachmentId", attachment.getId());
            return attachment.getId();
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }
}
