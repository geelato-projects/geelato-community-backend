package cn.geelato.web.platform.resolve.schema;

import cn.geelato.web.platform.resolve.integration.ResolvePipelineResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResolveSchemaRegistry {
    private static final String SCHEMA_PATH_PREFIX = "resolve/schema/";
    private static final String SCHEMA_FILE_SUFFIX = ".schema.json";

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory factory;
    private final SchemaValidatorsConfig validatorsConfig;
    private final Map<String, ResolveSchema> schemas = new ConcurrentHashMap<>();

    public ResolveSchemaRegistry() {
        this.objectMapper = new ObjectMapper();
        this.validatorsConfig = new SchemaValidatorsConfig();
        this.factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

        loadIfPresent(ResolvePipelineResolver.BIZTAG_CARRIER_SO_PARSE);
        loadIfPresent(ResolvePipelineResolver.BIZTAG_CARRIER_SI_PARSE);
        loadIfPresent(ResolvePipelineResolver.BIZTAG_QUOTE_SHEET_PARSE);
        loadIfPresent(ResolvePipelineResolver.BIZTAG_BOOKING_CONFIRM_PARSE);
        loadIfPresent(ResolvePipelineResolver.BIZTAG_INVOICE_PARSE);
    }

    public ResolveSchema get(String biztag) {
        if (Strings.isBlank(biztag)) {
            return null;
        }
        ResolveSchema cached = schemas.get(biztag);
        if (cached != null) {
            return cached;
        }
        return loadIfPresent(biztag);
    }

    public boolean exists(String biztag) {
        return get(biztag) != null;
    }

    private ResolveSchema loadIfPresent(String biztag) {
        String resource = SCHEMA_PATH_PREFIX + biztag + SCHEMA_FILE_SUFFIX;
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                return null;
            }
            JsonNode schemaNode = objectMapper.readTree(is);
            JsonSchema schema = factory.getSchema(schemaNode, validatorsConfig);
            ResolveSchema rs = new ResolveSchema();
            rs.setBiztag(biztag);
            rs.setSchemaNode(schemaNode);
            rs.setSchema(schema);
            JsonNode id = schemaNode.get("$id");
            rs.setSchemaId(id == null ? null : id.asText());
            schemas.put(biztag, rs);
            return rs;
        } catch (Exception e) {
            throw new IllegalStateException("load resolve schema failed: " + resource, e);
        }
    }
}
