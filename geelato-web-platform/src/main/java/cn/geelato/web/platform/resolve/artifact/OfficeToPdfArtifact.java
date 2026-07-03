package cn.geelato.web.platform.resolve.artifact;

import cn.geelato.web.platform.resolve.core.ResolveArtifact;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.utils.OfficeUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;

@Component
public class OfficeToPdfArtifact implements ResolveArtifact {
    @Override
    public String getId() {
        return "file.convert.pdf";
    }

    @Override
    public boolean supports(ResolveContext ctx) {
        if (ctx == null || Strings.isBlank(ctx.getSourceExt())) {
            return false;
        }
        String ext = ctx.getSourceExt().toUpperCase();
        return ".DOC".equals(ext) || ".DOCX".equals(ext) || ".XLS".equals(ext) || ".XLSX".equals(ext);
    }

    @Override
    public Object execute(ResolveContext ctx) throws Exception {
        File sourceFile = ctx.getSourceFile();
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("file not found");
        }

        File pdfFile = Files.createTempFile("resolve_", ".pdf").toFile();
        OfficeUtils.toPdf(sourceFile.getAbsolutePath(), pdfFile.getAbsolutePath(), ctx.getSourceExt());

        ctx.getTempFiles().add(pdfFile);
        ctx.setSourceFile(pdfFile);
        ctx.setSourceExt(".PDF");
        if (Strings.isNotBlank(ctx.getSourceFileName())) {
            String name = ctx.getSourceFileName();
            int dot = name.lastIndexOf('.');
            ctx.setSourceFileName(dot > 0 ? name.substring(0, dot) + ".pdf" : name + ".pdf");
        }
        ctx.putArtifactData("pdf.file", pdfFile);
        return pdfFile.getAbsolutePath();
    }
}
