package cn.geelato.orm.querydsl;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RDBDatabaseMetadata extends AbstractDatabaseMetadata<RDBSchemaMetadata>  {

    @Getter
    private Map<String, Feature> features = new ConcurrentHashMap<>();

}
