package cn.geelato.web.platform.resolve.biz.carrierso;

import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CarrierSoNormalizerTest {
    @Test
    void shouldNormalizeCommonSoFieldsAndContainers() {
        CarrierSoNormalizer normalizer = new CarrierSoNormalizer();
        JSONObject raw = new JSONObject();
        raw.put("Carrier", "OOCL");
        raw.put("Booking No", "BK001");
        raw.put("SO NO", "SO001");
        raw.put("Port of Loading", "YANTIAN");
        raw.put("Port of Discharge", "LOS ANGELES");
        raw.put("containers", List.of(new JSONObject() {{
            put("containerType", "40HQ");
            put("containerQty", 1);
            put("isDangerous", false);
        }}));

        Map<String, Object> normalized = normalizer.normalize(raw);

        assertEquals("OOCL", normalized.get(CarrierSoFieldKeys.CARRIER_CODE));
        assertEquals("OOCL", normalized.get(CarrierSoFieldKeys.CARRIER_NAME));
        assertEquals("BK001", normalized.get(CarrierSoFieldKeys.BOOKING_NO));
        assertEquals("SO001", normalized.get(CarrierSoFieldKeys.SO_NO));
        assertEquals("YANTIAN", normalized.get(CarrierSoFieldKeys.POL_NAME));
        assertEquals("LOS ANGELES", normalized.get(CarrierSoFieldKeys.POD_NAME));
        assertTrue(normalized.get(CarrierSoFieldKeys.CONTAINERS) instanceof List<?>);
        List<?> containers = (List<?>) normalized.get(CarrierSoFieldKeys.CONTAINERS);
        assertEquals(1, containers.size());
    }

    @Test
    void shouldProduceAllStandardFieldsForExtractedPreview() {
        CarrierSoNormalizer normalizer = new CarrierSoNormalizer();
        JSONObject raw = new JSONObject();
        raw.put("bookingNo", "BK001");
        raw.put("soNo", "SO001");
        raw.put("pol", "YANTIAN");
        raw.put("pod", "LOS ANGELES");
        raw.put("containers", "40HQ*1");

        ExtractedStructuredData extracted = normalizer.toExtracted(raw);

        assertNotNull(extracted);
        assertNotNull(extracted.getFields());
        assertEquals(CarrierSoFieldKeys.STANDARD_KEYS.size(), extracted.getFields().size());
        assertTrue(extracted.getFields().stream().anyMatch(f -> CarrierSoFieldKeys.BOOKING_NO.equals(f.getKey()) && "BK001".equals(f.getValue())));
        assertTrue(extracted.getFields().stream().anyMatch(f -> CarrierSoFieldKeys.CONTAINERS.equals(f.getKey()) && f.getValue() != null));
    }
}
