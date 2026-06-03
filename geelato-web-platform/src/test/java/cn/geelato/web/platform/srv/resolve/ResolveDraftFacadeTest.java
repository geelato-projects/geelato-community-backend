package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.handler.FileHandler;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandler;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandlerRegistry;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoFieldKeys;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoNormalizer;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoPromptProvider;
import cn.geelato.web.platform.resolve.biz.impl.CarrierSoParseBizHandler;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.draft.ResolveDraftStore;
import cn.geelato.web.platform.resolve.model.*;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaEnforcer;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaRegistry;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResolveDraftFacadeTest {
    private static final Logger log = LoggerFactory.getLogger(ResolveDraftFacadeTest.class);

    @Test
    void shouldCreateDraftUsingCarrierSoPdf() throws Exception {
        Path pdfPath = ResolveFixture.ooclSoPdf();
        Assumptions.assumeTrue(Files.exists(pdfPath));

        byte[] bytes = Files.readAllBytes(pdfPath);
        MockMultipartFile file = new MockMultipartFile("file", "2305042743.pdf", "application/pdf", bytes);

        ResolveContextBuilder contextBuilder = new ResolveContextBuilder(mock(FileHandler.class));
        ResolvePipelineRunner pipelineRunner = mock(ResolvePipelineRunner.class);
        ResolveDraftStore draftStore = mock(ResolveDraftStore.class);

        ResolveBizHandler handler = new CarrierSoParseBizHandler(new CarrierSoNormalizer());
        ResolveBizHandlerRegistry registry = new ResolveBizHandlerRegistry(java.util.List.of(handler));

        when(pipelineRunner.run(any())).thenAnswer(inv -> {
            ResolveContext ctx = inv.getArgument(0);
            assertNotNull(ctx.getParams());
            assertNotNull(ctx.getParams().getString("prompt"));
            JSONObject result = new JSONObject();
            result.put("carrier", "OOCL");
            result.put("bookingNo", "BK001");
            result.put("soNo", "SO001");
            result.put("pol", "YANTIAN");
            result.put("pod", "LOS ANGELES");
            result.put("containers", java.util.List.of(new JSONObject() {{
                put("containerType", "40HQ");
                put("containerQty", 1);
            }}));
            ctx.setResult(result);
            return ctx;
        });

        ResolveSchemaRegistry schemaRegistry = new ResolveSchemaRegistry();
        ResolveSchemaEnforcer schemaEnforcer = new ResolveSchemaEnforcer(schemaRegistry);
        ResolveDraftFacade facade = new ResolveDraftFacade(
                contextBuilder,
                pipelineRunner,
                registry,
                schemaRegistry,
                schemaEnforcer,
                draftStore,
                new CarrierSoPromptProvider()
        );
        ApiResult<?> apiResult = facade.createDraft(null, file, "carrier.so.parse", "{\"anyBizConfig\":{}}", "app", "tenant");

        assertEquals(20000, apiResult.getCode());
        assertEquals("ok", apiResult.getStatus());
        assertNotNull(apiResult.getData());
        ResolveDraft draft = (ResolveDraft) apiResult.getData();
        assertEquals("carrier.so.parse", draft.getBiztag());
        assertEquals(ResolveDraftStatusEnum.DRAFT, draft.getStatus());
        assertNotNull(draft.getDraftId());
        assertNotNull(draft.getExtracted());
        assertNotNull(draft.getMapping());
        assertFalse(draft.getMapping().isEmpty());
        assertTrue(draft.getMapping().stream().anyMatch(m -> CarrierSoFieldKeys.CARRIER_CODE.equals(m.getKey())));
        MappingSuggestion carrierMapping = draft.getMapping().stream()
                .filter(m -> CarrierSoFieldKeys.CARRIER_CODE.equals(m.getKey()))
                .findFirst()
                .orElseThrow();
        assertNotNull(carrierMapping.getCandidates());
        assertFalse(carrierMapping.getCandidates().isEmpty());
        assertTrue(carrierMapping.getCandidates().stream().anyMatch(c -> "carrier:OOCL".equals(c.getId())));
        assertNull(carrierMapping.getSuggested());
        assertTrue(draft.getSteps().stream().anyMatch(s -> "schema.enforce".equals(s.getArtifactId())));
        assertTrue(draft.getExtracted().getFields().stream().anyMatch(f -> CarrierSoFieldKeys.BOOKING_NO.equals(f.getKey()) && "BK001".equals(f.getValue())));

        log.info("ResolveDraft created:\n{}", JSON.toJSONString(draft, JSONWriter.Feature.PrettyFormat));

        verify(draftStore, atLeastOnce()).save(any(ResolveDraft.class));
    }
}
