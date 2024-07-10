package cn.geelato.orm.querydsl;

import cn.geelato.orm.utils.CastUtil;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class RDBDatabaseMetadata extends AbstractDatabaseMetadata<RDBSchemaMetadata>  {

    @Getter
    private Map<String, Feature> features = new ConcurrentHashMap<>();

}
