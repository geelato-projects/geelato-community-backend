package cn.geelato.web.platform.srv.resolve;

import cn.geelato.web.platform.handler.FileHandler;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ResolveContextBuilderTest {
    private static final Logger log = LoggerFactory.getLogger(ResolveContextBuilderTest.class);

    @Test
    void shouldBuildContextFromMultipartPdfAndCleanup() throws Exception {
        Path pdfPath = ResolveFixture.ooclSoPdf();
        Assumptions.assumeTrue(Files.exists(pdfPath));

        byte[] bytes = Files.readAllBytes(pdfPath);
        MockMultipartFile file = new MockMultipartFile("file", "2305042743.pdf", "application/pdf", bytes);

        ResolveContextBuilder builder = new ResolveContextBuilder(mock(FileHandler.class));
        ResolveContext ctx = builder.build(null, file, "carrier.so.parse", "{\"params\":{}}", "app", "tenant");

        log.info("ResolveContext built: fileName={}, ext={}, size={}, biztag={}, payload={}",
                ctx.getSourceFileName(),
                ctx.getSourceExt(),
                ctx.getSourceFile() == null ? null : ctx.getSourceFile().length(),
                ctx.getBiztag(),
                ctx.getPayload());

        assertNotNull(ctx.getSourceFile());
        assertTrue(ctx.getSourceFile().exists());
        assertEquals(".PDF", ctx.getSourceExt());
        assertTrue(ctx.getSourceFile().length() > 0);

        builder.cleanup(ctx);
        assertFalse(ctx.getSourceFile().exists());
    }
}
