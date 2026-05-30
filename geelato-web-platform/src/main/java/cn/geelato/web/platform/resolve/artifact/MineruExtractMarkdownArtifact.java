package cn.geelato.web.platform.resolve.artifact;

import cn.geelato.web.platform.resolve.client.MineruClient;
import cn.geelato.web.platform.resolve.core.ResolveArtifact;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

@Component
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

    @Override
    public Object execute(ResolveContext ctx) throws Exception {
        String pdfUrl = ctx.getArtifactData("pdf.url").toString();
        String taskId = mineruClient.submitExtractionTask(pdfUrl);
        String zipUrl = mineruClient.pollTaskResult(taskId);
        String fullMd = mineruClient.downloadAndExtractFile(zipUrl, MineruClient.FULL_MD_FILE);

        ctx.putArtifactData("mineru.taskId", taskId);
        ctx.putArtifactData("mineru.zipUrl", zipUrl);
        ctx.putArtifactData("mineru.fullMd", fullMd);
        return fullMd == null ? 0 : fullMd.length();
    }
}
