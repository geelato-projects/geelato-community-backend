package cn.geelato.web.platform.resolve.biz.carrierso;

import cn.geelato.web.platform.resolve.model.ExtractedField;
import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class CarrierSoNormalizer {
    private static final Map<String, String> KEY_ALIASES = buildKeyAliases();
    private static final Map<String, String> CARRIER_ALIASES = buildCarrierAliases();

    /**
     * 将 AI 原始结果归一为 SO 标准字段对象。
     */
    public Map<String, Object> normalize(Object rawResult) {
        Map<String, Object> rawMap = toStringKeyMap(rawResult);
        Map<String, Object> normalized = initStandardMap();
        Map<String, Object> candidateContainer = new LinkedHashMap<>();
        List<Map<String, Object>> containers = new ArrayList<>();

        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            String normalizedKey = canonicalKey(entry.getKey());
            if (normalizedKey == null) {
                continue;
            }
            Object rawValue = entry.getValue();
            if (CarrierSoFieldKeys.CONTAINERS.equals(normalizedKey)) {
                containers.addAll(parseContainers(rawValue));
                continue;
            }
            if (isContainerField(normalizedKey)) {
                putContainerValue(candidateContainer, normalizedKey, rawValue);
                continue;
            }
            normalized.put(normalizedKey, normalizeScalarValue(normalizedKey, rawValue));
        }

        if (!candidateContainer.isEmpty()) {
            containers.add(normalizeContainerMap(candidateContainer));
        }
        normalized.put(CarrierSoFieldKeys.CONTAINERS, containers);

        fillCarrierFields(normalized);
        if (log.isDebugEnabled()) {
            log.debug("Carrier SO normalized: rawKeyCount={}, containerCount={}, carrierCode={}, bookingNo={}, soNo={}",
                    rawMap.size(),
                    containers.size(),
                    normalized.get(CarrierSoFieldKeys.CARRIER_CODE),
                    normalized.get(CarrierSoFieldKeys.BOOKING_NO),
                    normalized.get(CarrierSoFieldKeys.SO_NO));
        }
        return normalized;
    }

    /**
     * 将标准字段对象转换为前端草稿预览使用的 extracted 列表结构。
     */
    public ExtractedStructuredData toExtracted(Object rawResult) {
        Map<String, Object> normalized = normalize(rawResult);
        List<ExtractedField> fields = new ArrayList<>();
        for (String key : CarrierSoFieldKeys.STANDARD_KEYS) {
            ExtractedField field = new ExtractedField();
            field.setKey(key);
            field.setLabel(key);
            field.setValue(stringify(normalized.get(key)));
            fields.add(field);
        }
        ExtractedStructuredData data = new ExtractedStructuredData();
        data.setFields(fields);
        return data;
    }

    private void fillCarrierFields(Map<String, Object> normalized) {
        String carrierCode = asString(normalized.get(CarrierSoFieldKeys.CARRIER_CODE));
        String carrierName = asString(normalized.get(CarrierSoFieldKeys.CARRIER_NAME));
        if (Strings.isBlank(carrierCode) && Strings.isNotBlank(carrierName)) {
            carrierCode = detectCarrierCode(carrierName);
            normalized.put(CarrierSoFieldKeys.CARRIER_CODE, carrierCode);
        }
        if (Strings.isBlank(carrierName) && Strings.isNotBlank(carrierCode)) {
            normalized.put(CarrierSoFieldKeys.CARRIER_NAME, carrierCode);
        }
    }

    private String detectCarrierCode(String value) {
        String cleaned = normalizeName(value);
        for (Map.Entry<String, String> entry : CARRIER_ALIASES.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return Strings.isBlank(value) ? null : value.trim();
    }

    private boolean isContainerField(String key) {
        return "containerType".equals(key)
                || "containerQty".equals(key)
                || "socCoc".equals(key)
                || "isDangerous".equals(key)
                || "isReefer".equals(key)
                || "temperature".equals(key)
                || "remarks".equals(key);
    }

    private void putContainerValue(Map<String, Object> container, String key, Object rawValue) {
        if ("remarks".equals(key) && container.containsKey(key)) {
            return;
        }
        Object normalizedValue;
        if ("isDangerous".equals(key) || "isReefer".equals(key)) {
            normalizedValue = parseBoolean(rawValue);
        } else {
            normalizedValue = cleanupText(rawValue);
        }
        if (normalizedValue != null) {
            container.put(key, normalizedValue);
        }
    }

    private List<Map<String, Object>> parseContainers(Object rawValue) {
        List<Map<String, Object>> containers = new ArrayList<>();
        if (rawValue == null) {
            return containers;
        }
        if (rawValue instanceof JSONArray array) {
            for (Object item : array) {
                Map<String, Object> container = normalizeContainerMap(toStringKeyMap(item));
                if (!container.isEmpty()) {
                    containers.add(container);
                }
            }
            return containers;
        }
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> container = normalizeContainerMap(toStringKeyMap(item));
                if (!container.isEmpty()) {
                    containers.add(container);
                }
            }
            return containers;
        }
        if (rawValue instanceof JSONObject || rawValue instanceof Map<?, ?>) {
            Map<String, Object> container = normalizeContainerMap(toStringKeyMap(rawValue));
            if (!container.isEmpty()) {
                containers.add(container);
            }
            return containers;
        }
        String text = cleanupText(rawValue);
        if (Strings.isBlank(text)) {
            return containers;
        }
        String[] parts = text.split("[,;/\\n]+");
        for (String part : parts) {
            String item = cleanupText(part);
            if (Strings.isBlank(item)) {
                continue;
            }
            Map<String, Object> container = new LinkedHashMap<>();
            String upper = item.toUpperCase(Locale.ROOT);
            if (upper.matches(".*\\d{1,2}(GP|HQ|HC|RF|OT|FR).*")) {
                container.put("containerType", upper.replaceAll(".*?(\\d{1,2}(GP|HQ|HC|RF|OT|FR)).*", "$1"));
            } else {
                container.put("containerType", item);
            }
            String qty = upper.replaceAll(".*?(\\d+)\\s*(X|\\*)\\s*\\d{1,2}(GP|HQ|HC|RF|OT|FR).*", "$1");
            if (qty.matches("\\d+")) {
                container.put("containerQty", qty);
            }
            containers.add(container);
        }
        return containers;
    }

    private Map<String, Object> normalizeContainerMap(Map<String, Object> raw) {
        Map<String, Object> container = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return container;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = canonicalKey(entry.getKey());
            if (!isContainerField(key)) {
                continue;
            }
            putContainerValue(container, key, entry.getValue());
        }
        return container;
    }

    private Object normalizeScalarValue(String key, Object rawValue) {
        String text = cleanupText(rawValue);
        if (Strings.isBlank(text)) {
            return null;
        }
        if (CarrierSoFieldKeys.CARRIER_CODE.equals(key)) {
            return detectCarrierCode(text);
        }
        return text;
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return JSON.toJSONString(value);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Boolean parseBoolean(Object value) {
        String text = cleanupText(value);
        if (Strings.isBlank(text)) {
            return null;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "yes".equals(normalized) || "y".equals(normalized)
                || "dg".equals(normalized) || "danger".equals(normalized) || "reefer".equals(normalized)
                || "rf".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized) || "no".equals(normalized) || "n".equals(normalized)
                || "ndg".equals(normalized) || "dry".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String cleanupText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("null".equalsIgnoreCase(text) || "n/a".equalsIgnoreCase(text) || "-".equals(text)) {
            return null;
        }
        return text;
    }

    private String canonicalKey(String rawKey) {
        if (Strings.isBlank(rawKey)) {
            return null;
        }
        return KEY_ALIASES.get(normalizeName(rawKey));
    }

    private String normalizeName(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "");
    }

    private Map<String, Object> toStringKeyMap(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        Object source = raw;
        if (raw instanceof String text && Strings.isNotBlank(text)) {
            try {
                source = JSON.parse(text);
            } catch (Exception ignored) {
                return result;
            }
        }
        if (source instanceof JSONObject jsonObject) {
            for (String key : jsonObject.keySet()) {
                result.put(key, jsonObject.get(key));
            }
            return result;
        }
        if (source instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
        }
        return result;
    }

    private Map<String, Object> initStandardMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : CarrierSoFieldKeys.STANDARD_KEYS) {
            if (CarrierSoFieldKeys.CONTAINERS.equals(key)) {
                map.put(key, new ArrayList<>());
            } else {
                map.put(key, null);
            }
        }
        return map;
    }

    private static Map<String, String> buildKeyAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("carriercode", CarrierSoFieldKeys.CARRIER_CODE);
        aliases.put("carrier", CarrierSoFieldKeys.CARRIER_NAME);
        aliases.put("carriername", CarrierSoFieldKeys.CARRIER_NAME);
        aliases.put("shippingline", CarrierSoFieldKeys.CARRIER_NAME);
        aliases.put("船司", CarrierSoFieldKeys.CARRIER_NAME);
        aliases.put("bookingno", CarrierSoFieldKeys.BOOKING_NO);
        aliases.put("bookingnumber", CarrierSoFieldKeys.BOOKING_NO);
        aliases.put("bookingref", CarrierSoFieldKeys.BOOKING_NO);
        aliases.put("bkgno", CarrierSoFieldKeys.BOOKING_NO);
        aliases.put("sono", CarrierSoFieldKeys.SO_NO);
        aliases.put("sonumber", CarrierSoFieldKeys.SO_NO);
        aliases.put("shipper", CarrierSoFieldKeys.SHIPPER_NAME);
        aliases.put("consignee", CarrierSoFieldKeys.CONSIGNEE_NAME);
        aliases.put("notifyparty", CarrierSoFieldKeys.NOTIFY_PARTY);
        aliases.put("notify", CarrierSoFieldKeys.NOTIFY_PARTY);
        aliases.put("vessel", CarrierSoFieldKeys.VESSEL_NAME);
        aliases.put("vesselname", CarrierSoFieldKeys.VESSEL_NAME);
        aliases.put("voyage", CarrierSoFieldKeys.VOYAGE_NO);
        aliases.put("voyageno", CarrierSoFieldKeys.VOYAGE_NO);
        aliases.put("pol", CarrierSoFieldKeys.POL_NAME);
        aliases.put("portofloading", CarrierSoFieldKeys.POL_NAME);
        aliases.put("loadport", CarrierSoFieldKeys.POL_NAME);
        aliases.put("pod", CarrierSoFieldKeys.POD_NAME);
        aliases.put("portofdischarge", CarrierSoFieldKeys.POD_NAME);
        aliases.put("dischargeport", CarrierSoFieldKeys.POD_NAME);
        aliases.put("finaldestination", CarrierSoFieldKeys.FINAL_DESTINATION);
        aliases.put("placeofreceipt", CarrierSoFieldKeys.PLACE_OF_RECEIPT);
        aliases.put("placeofdelivery", CarrierSoFieldKeys.PLACE_OF_DELIVERY);
        aliases.put("etd", CarrierSoFieldKeys.ETD);
        aliases.put("eta", CarrierSoFieldKeys.ETA);
        aliases.put("cargodescription", CarrierSoFieldKeys.CARGO_DESCRIPTION);
        aliases.put("commodity", CarrierSoFieldKeys.CARGO_DESCRIPTION);
        aliases.put("packageqty", CarrierSoFieldKeys.PACKAGE_QTY);
        aliases.put("packages", CarrierSoFieldKeys.PACKAGE_QTY);
        aliases.put("packageunit", CarrierSoFieldKeys.PACKAGE_UNIT);
        aliases.put("grossweight", CarrierSoFieldKeys.GROSS_WEIGHT);
        aliases.put("weightunit", CarrierSoFieldKeys.WEIGHT_UNIT);
        aliases.put("measurement", CarrierSoFieldKeys.MEASUREMENT);
        aliases.put("measurementunit", CarrierSoFieldKeys.MEASUREMENT_UNIT);
        aliases.put("paymentterm", CarrierSoFieldKeys.PAYMENT_TERM);
        aliases.put("freightterm", CarrierSoFieldKeys.PAYMENT_TERM);
        aliases.put("servicetype", CarrierSoFieldKeys.SERVICE_TYPE);
        aliases.put("contractno", CarrierSoFieldKeys.CONTRACT_NO);
        aliases.put("customerrefno", CarrierSoFieldKeys.CUSTOMER_REF_NO);
        aliases.put("remarks", CarrierSoFieldKeys.REMARKS);
        aliases.put("containers", CarrierSoFieldKeys.CONTAINERS);
        aliases.put("containerlist", CarrierSoFieldKeys.CONTAINERS);
        aliases.put("containertype", "containerType");
        aliases.put("containerno", "containerType");
        aliases.put("containerqty", "containerQty");
        aliases.put("qty", "containerQty");
        aliases.put("soccoc", "socCoc");
        aliases.put("soc", "socCoc");
        aliases.put("dangerous", "isDangerous");
        aliases.put("isdangerous", "isDangerous");
        aliases.put("reefer", "isReefer");
        aliases.put("isreefer", "isReefer");
        aliases.put("temperature", "temperature");
        return aliases;
    }

    private static Map<String, String> buildCarrierAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("cmacgm", "CMA");
        aliases.put("cma", "CMA");
        aliases.put("cosco", "COSCO");
        aliases.put("evergreen", "EMC");
        aliases.put("emc", "EMC");
        aliases.put("hmm", "HMM");
        aliases.put("msc", "MSC");
        aliases.put("one", "ONE");
        aliases.put("oocl", "OOCL");
        aliases.put("sml", "SML");
        aliases.put("wanhai", "WHL");
        aliases.put("whl", "WHL");
        aliases.put("zim", "ZIM");
        return aliases;
    }
}
