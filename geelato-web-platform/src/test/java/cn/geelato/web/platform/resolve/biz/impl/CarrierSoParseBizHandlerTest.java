package cn.geelato.web.platform.resolve.biz.impl;

import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoFieldKeys;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoNormalizer;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import cn.geelato.web.platform.resolve.model.MappingCandidate;
import cn.geelato.web.platform.resolve.model.ResolveDraft;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CarrierSoParseBizHandlerTest {
    @Test
    void shouldExtractStandardSoFields() {
        CarrierSoParseBizHandler handler = new CarrierSoParseBizHandler(new CarrierSoNormalizer());
        ResolveContext ctx = new ResolveContext();
        JSONObject result = new JSONObject();
        result.put("carrier", "OOCL");
        result.put("bookingNo", "BK001");
        result.put("soNo", "SO001");
        result.put("pol", "YANTIAN");
        result.put("pod", "LOS ANGELES");
        result.put("containers", "40HQ*1");
        ctx.setResult(result);

        ExtractedStructuredData extracted = handler.extract(ctx, new JSONObject());

        assertNotNull(extracted);
        assertTrue(extracted.getFields().stream().anyMatch(f -> CarrierSoFieldKeys.CARRIER_CODE.equals(f.getKey()) && "OOCL".equals(f.getValue())));
        assertTrue(extracted.getFields().stream().anyMatch(f -> CarrierSoFieldKeys.BOOKING_NO.equals(f.getKey()) && "BK001".equals(f.getValue())));
    }

    @Test
    void shouldOnlyExposeConfiguredMappingKeys() {
        CarrierSoParseBizHandler handler = new CarrierSoParseBizHandler(new CarrierSoNormalizer());
        List<String> keys = handler.mappingKeys(new ResolveContext(), new JSONObject());

        assertEquals(CarrierSoFieldKeys.MAPPING_KEYS, keys);
    }

    @Test
    void shouldReturnPreviewFriendlyCandidates() {
        CarrierSoParseBizHandler handler = new CarrierSoParseBizHandler(new CarrierSoNormalizer());

        List<MappingCandidate> carrierCandidates = handler.queryCandidates(CarrierSoFieldKeys.CARRIER_CODE, "OOCL", new ResolveContext(), new JSONObject());
        List<MappingCandidate> portCandidates = handler.queryCandidates(CarrierSoFieldKeys.POL_NAME, "YANTIAN", new ResolveContext(), new JSONObject());

        assertFalse(carrierCandidates.isEmpty());
        assertEquals("OOCL", carrierCandidates.get(0).getCode());
        assertEquals(1, portCandidates.size());
        assertEquals("YANTIAN", portCandidates.get(0).getName());
    }

    @Test
    void shouldRejectPersistOutsidePreviewScope() {
        CarrierSoParseBizHandler handler = new CarrierSoParseBizHandler(new CarrierSoNormalizer());

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> handler.persist(new ResolveDraft()));
        assertEquals("carrier.so.parse preview only", ex.getMessage());
    }
}
