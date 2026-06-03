package cn.geelato.web.platform.resolve.schema;

import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoFieldKeys;
import cn.geelato.web.platform.resolve.model.ExtractedField;
import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResolveSchemaEnforcerTest {
    @Test
    void shouldCoerceCommonScalarTypes() {
        ResolveSchemaRegistry registry = new ResolveSchemaRegistry();
        ResolveSchemaEnforcer enforcer = new ResolveSchemaEnforcer(registry);

        ExtractedField f1 = new ExtractedField();
        f1.setKey(CarrierSoFieldKeys.CARRIER_CODE);
        f1.setValue("OOCL");
        ExtractedField f2 = new ExtractedField();
        f2.setKey(CarrierSoFieldKeys.BOOKING_NO);
        f2.setValue("BK001");
        ExtractedField f3 = new ExtractedField();
        f3.setKey(CarrierSoFieldKeys.SO_NO);
        f3.setValue("SO001");
        ExtractedField f4 = new ExtractedField();
        f4.setKey(CarrierSoFieldKeys.POL_NAME);
        f4.setValue("YANTIAN");
        ExtractedField f5 = new ExtractedField();
        f5.setKey(CarrierSoFieldKeys.POD_NAME);
        f5.setValue("LOS ANGELES");
        ExtractedField f6 = new ExtractedField();
        f6.setKey(CarrierSoFieldKeys.CONTAINERS);
        f6.setValue("[{\"containerType\":\"40HQ\",\"containerQty\":1,\"isDangerous\":false}]");

        ExtractedStructuredData extracted = new ExtractedStructuredData();
        extracted.setFields(List.of(f1, f2, f3, f4, f5, f6));

        ResolveSchemaEnforceResult result = enforcer.enforce("carrier.so.parse", extracted);
        assertTrue(result.isSuccess());
        assertNotNull(result.getNormalizedData());
        assertEquals("OOCL", result.getNormalizedData().get(CarrierSoFieldKeys.CARRIER_CODE));
        assertEquals("BK001", result.getNormalizedData().get(CarrierSoFieldKeys.BOOKING_NO));
        assertNotNull(result.getNormalizedData().get(CarrierSoFieldKeys.CONTAINERS));
        assertTrue(result.getFixedCount() >= 1);
    }

    @Test
    void shouldFailWhenKeyPatternInvalid() {
        ResolveSchemaRegistry registry = new ResolveSchemaRegistry();
        ResolveSchemaEnforcer enforcer = new ResolveSchemaEnforcer(registry);

        ExtractedField f1 = new ExtractedField();
        f1.setKey("bad key");
        f1.setValue("x");
        ExtractedField f2 = new ExtractedField();
        f2.setKey(CarrierSoFieldKeys.BOOKING_NO);
        f2.setValue("BK001");
        ExtractedField f3 = new ExtractedField();
        f3.setKey(CarrierSoFieldKeys.SO_NO);
        f3.setValue("SO001");
        ExtractedField f4 = new ExtractedField();
        f4.setKey(CarrierSoFieldKeys.POL_NAME);
        f4.setValue("YANTIAN");
        ExtractedField f5 = new ExtractedField();
        f5.setKey(CarrierSoFieldKeys.POD_NAME);
        f5.setValue("LOS ANGELES");
        ExtractedField f6 = new ExtractedField();
        f6.setKey(CarrierSoFieldKeys.CONTAINERS);
        f6.setValue("[]");
        ExtractedStructuredData extracted = new ExtractedStructuredData();
        extracted.setFields(List.of(f1, f2, f3, f4, f5, f6));

        ResolveSchemaEnforceResult result = enforcer.enforce("carrier.so.parse", extracted);
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrors());
        assertFalse(result.getErrors().isEmpty());
    }
}
