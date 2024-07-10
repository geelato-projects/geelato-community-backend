package cn.geelato.orm.querydsl;

public class RDBSchemaMetadata extends AbstractSchemaMetadata implements SchemaMetadata {
    @Override
    @SuppressWarnings("all")
    public RDBDatabaseMetadata getDatabase() {
        return ((RDBDatabaseMetadata) super.getDatabase());
    }
}
