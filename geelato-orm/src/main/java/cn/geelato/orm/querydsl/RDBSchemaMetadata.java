package cn.geelato.orm.querydsl;

public class RDBSchemaMetadata extends AbstractSchemaMetadata {
    @Override
    @SuppressWarnings("all")
    public RDBDatabaseMetadata getDatabase() {
        return ((RDBDatabaseMetadata) super.getDatabase());
    }
}
