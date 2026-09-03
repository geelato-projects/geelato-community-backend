package cn.geelato.core.orm;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.QueryJoin;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 查询涉及实体的加密列解析，与加密侧 {@code JsonTextSaveParser.EncryptInner} 读同一真源
 * （{@code FieldMeta.getColumnMeta().isEncrypted()}，即设计器 platform_dev_column.encrypted），
 * 使解密与加密的门控对称：加密只写标记列，解密只试标记列。
 * <p>
 * 解密必须有元数据的明确背书：解析不出（无 command、非查询命令、实体未注册）时返回空集，
 * 即不做任何解密——元数据不可用时加密侧从未加密过该数据，不存在需要解密的密文；
 * 也可避免对普通字符串误尝试解密（形如 "aes:xxx" 的明文不再可能被误判）。
 */
public final class EncryptedColumns {

    private static final MetaManager metaManager = MetaManager.singleInstance();

    private EncryptedColumns() {
    }

    /**
     * 从 BoundSql 解析；非查询命令（如模板 SQL 无 command、保存/删除命令）返回空集。
     */
    public static Set<String> from(BoundSql boundSql) {
        if (boundSql == null || !(boundSql.getCommand() instanceof QueryCommand queryCommand)) {
            return Set.of();
        }
        return resolve(queryCommand);
    }

    /**
     * 收集主实体与 join 实体的全部加密列名；任一实体名缺失或未注册时返回空集（不解密）。
     */
    public static Set<String> resolve(QueryCommand command) {
        if (command == null) {
            return Set.of();
        }
        Set<String> encryptedColumns = new HashSet<>();
        if (!collect(command.getEntityName(), encryptedColumns)) {
            return Set.of();
        }
        List<QueryJoin> joins = command.getJoins();
        if (joins != null) {
            for (QueryJoin join : joins) {
                if (join == null) {
                    continue;
                }
                if (!collect(join.getEntityName(), encryptedColumns)) {
                    return Set.of();
                }
            }
        }
        return encryptedColumns;
    }

    private static boolean collect(String entityName, Set<String> encryptedColumns) {
        if (entityName == null || entityName.isEmpty()) {
            return false;
        }
        EntityMeta entityMeta = metaManager.getByEntityName(entityName);
        if (entityMeta == null || entityMeta.getFieldMetas() == null) {
            return false;
        }
        for (FieldMeta fieldMeta : entityMeta.getFieldMetas()) {
            if (fieldMeta.getColumnMeta() != null
                    && fieldMeta.getColumnMeta().isEncrypted()
                    && fieldMeta.getColumnName() != null) {
                encryptedColumns.add(fieldMeta.getColumnName());
            }
        }
        return true;
    }
}
