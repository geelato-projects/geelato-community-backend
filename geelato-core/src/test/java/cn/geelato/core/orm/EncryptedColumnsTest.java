package cn.geelato.core.orm;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.QueryJoin;
import cn.geelato.core.mql.execute.BoundSql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 加密列解析单一规则：解密需要元数据明确背书。
 * 非空=仅这些列尝试解密；空集=无加密列（含元数据不可用：实体未注册、无 command、非查询命令），不做任何解密。
 */
class EncryptedColumnsTest {

    private static final String MAIN = "ec_main_test";
    private static final String JOINED = "ec_joined_test";
    private static final String PLAIN = "ec_plain_test";

    @BeforeAll
    static void setUp() {
        MetaManager metaManager = MetaManager.singleInstance();
        metaManager.parseTableEntity(tableMeta(MAIN, "t_ec_main"), List.of(
                column("plain_col", "plainCol", false),
                column("secret_col", "secretCol", true)), null, null, null);
        metaManager.parseTableEntity(tableMeta(JOINED, "t_ec_joined"), List.of(
                column("joined_secret", "joinedSecret", true)), null, null, null);
        metaManager.parseTableEntity(tableMeta(PLAIN, "t_ec_plain"), List.of(
                column("a_col", "aCol", false),
                column("b_col", "bCol", false)), null, null, null);
    }

    @AfterAll
    static void tearDown() {
        MetaManager metaManager = MetaManager.singleInstance();
        metaManager.removeOne(MAIN);
        metaManager.removeOne(JOINED);
        metaManager.removeOne(PLAIN);
    }

    @Test
    void resolvesOnlyEncryptedColumnsOfMainEntity() {
        QueryCommand command = new QueryCommand();
        command.setEntityName(MAIN);

        Set<String> columns = EncryptedColumns.resolve(command);

        assertNotNull(columns);
        assertEquals(Set.of("secret_col"), columns);
    }

    @Test
    void collectsJoinEntitiesToo() {
        QueryCommand command = new QueryCommand();
        command.setEntityName(MAIN);
        QueryJoin join = new QueryJoin();
        join.setEntityName(JOINED);
        command.setJoins(List.of(join));

        Set<String> columns = EncryptedColumns.resolve(command);

        assertNotNull(columns);
        assertEquals(Set.of("secret_col", "joined_secret"), columns);
    }

    @Test
    void emptySetWhenNoEncryptedColumn() {
        QueryCommand command = new QueryCommand();
        command.setEntityName(PLAIN);

        Set<String> columns = EncryptedColumns.resolve(command);

        assertNotNull(columns);
        assertTrue(columns.isEmpty());
    }

    @Test
    void emptySetWhenEntityUnregistered() {
        QueryCommand command = new QueryCommand();
        command.setEntityName("no_such_entity");

        assertTrue(EncryptedColumns.resolve(command).isEmpty());
    }

    @Test
    void emptySetWhenJoinedEntityUnregistered() {
        QueryCommand command = new QueryCommand();
        command.setEntityName(MAIN);
        QueryJoin join = new QueryJoin();
        join.setEntityName("no_such_entity");
        command.setJoins(List.of(join));

        assertTrue(EncryptedColumns.resolve(command).isEmpty());
    }

    @Test
    void emptySetWhenCommandMissingOrNotQuery() {
        assertTrue(EncryptedColumns.resolve(null).isEmpty());
        assertTrue(EncryptedColumns.from(null).isEmpty());

        BoundSql noCommand = new BoundSql();
        assertTrue(EncryptedColumns.from(noCommand).isEmpty());

        BoundSql queryBoundSql = new BoundSql();
        QueryCommand command = new QueryCommand();
        command.setEntityName(MAIN);
        queryBoundSql.setCommand(command);
        assertEquals(Set.of("secret_col"), EncryptedColumns.from(queryBoundSql));
    }

    @Test
    void cacheRecomputedAfterFieldMetasChange() {
        // 实体级懒缓存的失效验证：setFieldMetas 原地变更字段集合后，resolve 必须重算
        MetaManager metaManager = MetaManager.singleInstance();
        String entity = "ec_invalidate_test";
        metaManager.parseTableEntity(tableMeta(entity, "t_ec_invalidate"),
                List.of(column("plain_col", "plainCol", false)), null, null, null);
        try {
            QueryCommand command = new QueryCommand();
            command.setEntityName(entity);
            assertTrue(EncryptedColumns.resolve(command).isEmpty());

            FieldMeta newSecret = new FieldMeta(column("new_secret", "newSecret", true));
            newSecret.setFieldType(String.class);
            metaManager.getByEntityName(entity).setFieldMetas(List.of(newSecret));

            assertEquals(Set.of("new_secret"), EncryptedColumns.resolve(command));
        } finally {
            metaManager.removeOne(entity);
        }
    }

    private static TableMeta tableMeta(String entityName, String tableName) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setEntityName(entityName);
        tableMeta.setTableName(tableName);
        return tableMeta;
    }

    private static ColumnMeta column(String name, String fieldName, boolean encrypted) {
        ColumnMeta columnMeta = new ColumnMeta();
        columnMeta.setName(name);
        columnMeta.setFieldName(fieldName);
        columnMeta.setDataType("varchar");
        columnMeta.setEncrypted(encrypted);
        return columnMeta;
    }
}
