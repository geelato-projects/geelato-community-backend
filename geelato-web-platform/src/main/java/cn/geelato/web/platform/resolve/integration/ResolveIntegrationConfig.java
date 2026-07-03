package cn.geelato.web.platform.resolve.integration;

import cn.geelato.web.platform.resolve.artifact.*;
import cn.geelato.web.platform.resolve.core.ResolveArtifactRunner;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

import java.util.concurrent.Executor;

@Configuration
public class ResolveIntegrationConfig {

    @Bean(name = ResolvePipelineResolver.CHANNEL_PDF_MINERU_NATURAL)
    public MessageChannel resolvePdfMineruNaturalInput() {
        return new DirectChannel();
    }

    @Bean(name = ResolvePipelineResolver.CHANNEL_PDF_MINERU_AI)
    public MessageChannel resolvePdfMineruAiInput() {
        return new DirectChannel();
    }

    @Bean(name = ResolvePipelineResolver.CHANNEL_PDF_TEMPLATE)
    public MessageChannel resolvePdfTemplateInput() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow resolvePdfMineruNaturalFlow(
            @Qualifier(ResolvePipelineResolver.CHANNEL_PDF_MINERU_NATURAL) MessageChannel input,
            OfficeToPdfArtifact officeToPdfArtifact,
            PdfPublishArtifact pdfPublishArtifact,
            MineruExtractNaturalArtifact mineruExtractNaturalArtifact,
            ResultStoreAttachmentArtifact resultStoreAttachmentArtifact,
            @Qualifier("resolveExecutor") Executor resolveExecutor
    ) {
        return IntegrationFlow.from(input)
                .handle((payload, headers) -> {
                    ResolveContext ctx = (ResolveContext) payload;
                    ResolveArtifactRunner.run(officeToPdfArtifact, ctx);
                    return ctx;
                })
                .handle((payload, headers) -> {
                    ResolveContext ctx = (ResolveContext) payload;
                    ResolveArtifactRunner.run(pdfPublishArtifact, ctx);
                    return ctx;
                })
                .handle((payload, headers) -> {
                    ResolveContext ctx = (ResolveContext) payload;
                    ResolveArtifactRunner.run(mineruExtractNaturalArtifact, ctx);
                    return ctx;
                })
                .publishSubscribeChannel(resolveExecutor, pub -> pub
                        .subscribe(flow -> flow.handle((payload, headers) -> {
                            ResolveContext ctx = (ResolveContext) payload;
                            ResolveArtifactRunner.run(resultStoreAttachmentArtifact, ctx);
                            return ctx;
                        }))
                )
                .get();
    }

    @Bean
    public IntegrationFlow resolvePdfMineruAiFlow(
            @Qualifier(ResolvePipelineResolver.CHANNEL_PDF_MINERU_AI) MessageChannel input,
            OfficeToPdfArtifact officeToPdfArtifact,
            PdfPublishArtifact pdfPublishArtifact,
            MineruExtractMarkdownArtifact mineruExtractMarkdownArtifact,
            DeepSeekExtractArtifact deepSeekExtractArtifact,
            ResultStoreAttachmentArtifact resultStoreAttachmentArtifact,
            @Qualifier("resolveExecutor") Executor resolveExecutor
    ) {
        return IntegrationFlow.from(input)
                .handle((payload, headers) -> {
                    ResolveContext ctx = (ResolveContext) payload;
                    ResolveArtifactRunner.run(officeToPdfArtifact, ctx);
                    return ctx;
                })
                .handle((payload, headers) -> {
                    ResolveContext ctx = (ResolveContext) payload;
                    ResolveArtifactRunner.run(pdfPublishArtifact, ctx);
                    return ctx;
                })
                .handle((payload, headers) -> {
                    ResolveContext ctx = (ResolveContext) payload;
                    ResolveArtifactRunner.run(mineruExtractMarkdownArtifact, ctx);
                    return ctx;
                })
                .handle((payload, headers) -> {
                    ResolveContext ctx = (ResolveContext) payload;
                    ResolveArtifactRunner.run(deepSeekExtractArtifact, ctx);
                    return ctx;
                })
                .publishSubscribeChannel(resolveExecutor, pub -> pub
                        .subscribe(flow -> flow.handle((payload, headers) -> {
                            ResolveContext ctx = (ResolveContext) payload;
                            ResolveArtifactRunner.run(resultStoreAttachmentArtifact, ctx);
                            return ctx;
                        }))
                )
                .get();
    }

    @Bean
    public IntegrationFlow resolvePdfTemplateFlow(
            @Qualifier(ResolvePipelineResolver.CHANNEL_PDF_TEMPLATE) MessageChannel input,
            TemplateResolveArtifact templateResolveArtifact,
            ResultStoreAttachmentArtifact resultStoreAttachmentArtifact,
            @Qualifier("resolveExecutor") Executor resolveExecutor
    ) {
        return IntegrationFlow.from(input)
                .handle((payload, headers) -> {
                    ResolveContext ctx = (ResolveContext) payload;
                    ResolveArtifactRunner.run(templateResolveArtifact, ctx);
                    return ctx;
                })
                .publishSubscribeChannel(resolveExecutor, pub -> pub
                        .subscribe(flow -> flow.handle((payload, headers) -> {
                            ResolveContext ctx = (ResolveContext) payload;
                            ResolveArtifactRunner.run(resultStoreAttachmentArtifact, ctx);
                            return ctx;
                        }))
                )
                .get();
    }
}
