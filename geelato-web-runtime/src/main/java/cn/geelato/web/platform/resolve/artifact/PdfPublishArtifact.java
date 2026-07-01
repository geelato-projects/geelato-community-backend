package cn.geelato.web.platform.resolve.artifact;

import cn.geelato.web.platform.resolve.core.ResolveArtifact;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.util.PdfPublishService;
import cn.geelato.web.platform.resolve.util.PublishedFile;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

@Component
public class PdfPublishArtifact implements ResolveArtifact {
    private final PdfPublishService pdfPublishService;

    public PdfPublishArtifact(PdfPublishService pdfPublishService) {
        this.pdfPublishService = pdfPublishService;
    }

    @Override
    public String getId() {
        return "file.publish";
    }

    @Override
    public boolean supports(ResolveContext ctx) {
        return ctx != null && Strings.isNotBlank(ctx.getSourceExt()) && ".PDF".equals(ctx.getSourceExt().toUpperCase());
    }

    @Override
    public Object execute(ResolveContext ctx) throws Exception {
        PublishedFile published = pdfPublishService.publish(ctx.getSourceFile(), ctx.getSourceFileName());
        ctx.putArtifactData("pdf.published", published);
        ctx.putArtifactData("pdf.url", published.getUrl());
        return published.getUrl();
    }
}
