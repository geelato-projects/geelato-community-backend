package cn.geelato.core.orm;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.QueryJoin;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 查询涉及实体(主实体+join 实体)的加密列名,与加密侧 EncryptInner 读同一真源
 * (FieldMeta.getColumnMeta().isEncrypted())。解析不出(无 command、实体未注册)返回空集,即不解密;
 * 加密列集合由 EntityMeta 实例级缓存,本类每查询仅 O(1) 取用。
 */
public final class EncryptedColumns {

    private static final MetaManager metaManager = MetaManager.singleInstance();

    private EncryptedColumns() {
    }

    /**
     * 从 BoundSql 解析;非查询命令返回空集。
     */
    public static Set<String> from(BoundSql boundSql) {
        if (boundSql == null || !(boundSql.getCommand() instanceof QueryCommand queryCommand)) {
            return Set.of();
        }
        return resolve(queryCommand);
    }

    /**
     * 主实体与 join 实体的加密列名并集;任一实体未注册返回空集。
     * 返回集合仅供只读。
     */
    public static Set<String> resolve(QueryCommand command) {
        if (command == null) {
            return Set.of();
        }
        EntityMeta main = resolveEntity(command.getEntityName());
        if (main == null) {
            return Set.of();
        }
        Set<String> encrypted = main.getEncryptedColumnNames();
        List<QueryJoin> joins = command.getJoins();
        if (joins == null || joins.isEmpty()) {
            return encrypted;
        }
        Set<String> result = encrypted;
        boolean copied = false;
        for (QueryJoin join : joins) {
            if (join == null) {
                continue;
            }
            EntityMeta joinMeta = resolveEntity(join.getEntityName());
            if (joinMeta == null) {
                return Set.of();
            }
            Set<String> joinEncrypted = joinMeta.getEncryptedColumnNames();
            if (joinEncrypted.isEmpty()) {
                continue;
            }
            if (result.isEmpty()) {
                result = joinEncrypted;
                continue;
            }
            if (!copied) {
                result = new HashSet<>(result);
                copied = true;
            }
            result.addAll(joinEncrypted);
        }
        return result;
    }

    private static EntityMeta resolveEntity(String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return null;
        }
        return metaManager.getByEntityName(entityName);
    }
}
