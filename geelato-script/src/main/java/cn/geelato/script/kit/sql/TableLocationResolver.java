package cn.geelato.script.kit.sql;

/**
 * 表名 → 物理位置的解析口：未登记表返回 null（按执行库解析，不参与改写）。
 */
@FunctionalInterface
public interface TableLocationResolver {

    TableLocation resolve(String tableName);
}
