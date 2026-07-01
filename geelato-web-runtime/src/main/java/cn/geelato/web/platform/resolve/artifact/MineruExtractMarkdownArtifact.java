package cn.geelato.web.platform.resolve.artifact;

import cn.geelato.web.platform.resolve.client.MineruClient;
import cn.geelato.web.platform.resolve.core.ResolveArtifact;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MineruExtractMarkdownArtifact implements ResolveArtifact {
    private final MineruClient mineruClient;

    public MineruExtractMarkdownArtifact(MineruClient mineruClient) {
        this.mineruClient = mineruClient;
    }

    @Override
    public String getId() {
        return "file.extract.md";
    }

    @Override
    public boolean supports(ResolveContext ctx) {
        Object url = ctx == null ? null : ctx.getArtifactData("pdf.url");
        return url != null && Strings.isNotBlank(url.toString());
    }

    /**
     * 调用 Mineru 将 PDF 提取为 markdown，并将任务信息写回上下文工件数据。
     */
    @Override
    public Object execute(ResolveContext ctx) throws Exception {
        String pdfUrl = ctx.getArtifactData("pdf.url").toString();
        if (log.isDebugEnabled()) {
            log.debug("Submitting Mineru markdown extraction: biztag={}, sourceFileName={}, pdfUrlLength={}",
                    ctx == null ? null : ctx.getBiztag(),
                    ctx == null ? null : ctx.getSourceFileName(),
                    pdfUrl == null ? 0 : pdfUrl.length());
        }
        String taskId = mineruClient.submitExtractionTask(pdfUrl);
        String zipUrl = mineruClient.pollTaskResult(taskId);
        String fullMd = mineruClient.downloadAndExtractFile(zipUrl, MineruClient.FULL_MD_FILE);

        ctx.putArtifactData("mineru.taskId", taskId);
        ctx.putArtifactData("mineru.zipUrl", zipUrl);
        ctx.putArtifactData("mineru.fullMd", fullMd);
        if (log.isDebugEnabled()) {
            log.debug("Mineru markdown extraction finished: taskId={}, zipUrlLength={}, markdownLength={}",
                    taskId,
                    zipUrl == null ? 0 : zipUrl.length(),
                    fullMd == null ? 0 : fullMd.length());
        }
        return fullMd == null ? 0 : fullMd.length();
    }
}
