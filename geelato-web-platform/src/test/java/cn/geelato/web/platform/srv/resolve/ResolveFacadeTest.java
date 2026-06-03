package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandler;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandlerRegistry;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoFieldKeys;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoNormalizer;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoPromptProvider;
import cn.geelato.web.platform.resolve.biz.impl.CarrierSoParseBizHandler;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.model.ResolveResponse;
import cn.geelato.web.platform.resolve.model.ResolveStandardResult;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaEnforcer;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaRegistry;
import cn.geelato.web.platform.resolve.task.ResolveTaskStore;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResolveFacadeTest {
    @Test
    void shouldReturnStandardResultWhenBiztagHasSchema() throws Exception {
        ResolveContextBuilder contextBuilder = mock(ResolveContextBuilder.class);
        ResolvePipelineRunner pipelineRunner = mock(ResolvePipelineRunner.class);
        ResolveTaskStore taskStore = mock(ResolveTaskStore.class);
        Executor resolveExecutor = mock(Executor.class);

        ResolveBizHandler handler = new CarrierSoParseBizHandler(new CarrierSoNormalizer());
        ResolveBizHandlerRegistry handlerRegistry = new ResolveBizHandlerRegistry(java.util.List.of(handler));
        ResolveSchemaRegistry schemaRegistry = new ResolveSchemaRegistry();
        ResolveSchemaEnforcer schemaEnforcer = new ResolveSchemaEnforcer(schemaRegistry);

        ResolveContext ctx = new ResolveContext();
        ctx.setBiztag("carrier.so.parse");
        ctx.setPayload(new JSONObject());
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

        when(contextBuilder.build(any(), any(), any(), any(), any(), any())).thenReturn(ctx);
        when(pipelineRunner.run(any())).thenAnswer(inv -> inv.getArgument(0));

        ResolveFacade facade = new ResolveFacade(
                contextBuilder,
                pipelineRunner,
                handlerRegistry,
                schemaRegistry,
                schemaEnforcer,
                taskStore,
                resolveExecutor,
                new CarrierSoPromptProvider()
        );
        ApiResult<?> api = facade.resolve(null, null, "carrier.so.parse", "{}", "app", "tenant");

        assertEquals(20000, api.getCode());
        assertNotNull(api.getData());
        ResolveResponse resp = (ResolveResponse) api.getData();
        assertNotNull(resp.getResult());
        assertTrue(resp.getResult() instanceof ResolveStandardResult);
        ResolveStandardResult standard = (ResolveStandardResult) resp.getResult();
        assertEquals("carrier.so.parse", standard.getBiztag());
        assertNotNull(standard.getSchemaId());
        assertNotNull(standard.getData());
        assertEquals("OOCL", standard.getData().get(CarrierSoFieldKeys.CARRIER_CODE));
        assertEquals("BK001", standard.getData().get(CarrierSoFieldKeys.BOOKING_NO));
        assertEquals("SO001", standard.getData().get(CarrierSoFieldKeys.SO_NO));
        assertTrue(resp.getSteps().stream().anyMatch(s -> "schema.enforce".equals(s.getArtifactId())));
        assertEquals("YANTIAN", standard.getData().get(CarrierSoFieldKeys.POL_NAME));
        assertEquals("LOS ANGELES", standard.getData().get(CarrierSoFieldKeys.POD_NAME));

        verify(contextBuilder, times(1)).cleanup(ctx);
    }
}
