package cn.geelato.core.meta.spi;

import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;

import java.util.List;
import java.util.Map;

/**
 * 元数据相关静态资源提供者。
 */
public interface MetaResourceProvider {

    List<ColumnMeta> getDefaultColumns();

    List<ColumnSelectType> getColumnSelectTypes();

    Map<String, ColumnMeta> getTableUpgradeColumns();
}
