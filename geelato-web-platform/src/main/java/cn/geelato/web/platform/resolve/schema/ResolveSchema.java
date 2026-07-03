package cn.geelato.web.platform.resolve.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import lombok.Data;

@Data
public class ResolveSchema {
    private String biztag;
    private String schemaId;
    private JsonNode schemaNode;
    private JsonSchema schema;
}
