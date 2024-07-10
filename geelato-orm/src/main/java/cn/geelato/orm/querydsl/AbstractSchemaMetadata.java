package cn.geelato.orm.querydsl;

import lombok.Getter;
import lombok.Setter;

public abstract class AbstractSchemaMetadata {
    @Getter
    @Setter
    private DatabaseMetadata<?> database;
}
