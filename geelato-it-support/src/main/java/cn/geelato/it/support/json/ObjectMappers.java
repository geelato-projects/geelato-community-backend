package cn.geelato.it.support.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class ObjectMappers {
    private static final ObjectMapper DEFAULT = createDefault();

    private ObjectMappers() {
    }

    public static ObjectMapper defaultMapper() {
        return DEFAULT;
    }

    private static ObjectMapper createDefault() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
