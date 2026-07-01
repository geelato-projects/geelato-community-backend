package cn.geelato.core.meta.support;

import cn.geelato.core.constants.ResourcesFiles;
import cn.geelato.core.enums.DataTypeRadiusEnum;
import cn.geelato.core.enums.MysqlToJavaEnum;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;
import cn.geelato.core.meta.spi.MetaResourceProvider;
import cn.geelato.utils.JsonUtils;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 当前平台资源文件的默认实现。
 */
public class DefaultMetaResourceProvider implements MetaResourceProvider {

    @Override
    public List<ColumnMeta> getDefaultColumns() {
        List<ColumnMeta> defaultColumnMetaList = new ArrayList<>();
        try {
            String jsonStr = JsonUtils.readJsonFile(ResourcesFiles.COLUMN_DEFAULT_JSON);
            List<ColumnMeta> columnMetaList = JSON.parseArray(jsonStr, ColumnMeta.class);
            if (columnMetaList != null && !columnMetaList.isEmpty()) {
                for (ColumnMeta meta : columnMetaList) {
                    meta.afterSet();
                    defaultColumnMetaList.add(meta);
                }
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return defaultColumnMetaList;
    }

    @Override
    public List<ColumnSelectType> getColumnSelectTypes() {
        List<ColumnSelectType> columnSelectTypes = new ArrayList<>();
        try {
            String jsonStr = JsonUtils.readJsonFile(ResourcesFiles.COLUMN_SELECT_TYPE_JSON);
            List<ColumnSelectType> selectTypeList = JSON.parseArray(jsonStr, ColumnSelectType.class);
            if (selectTypeList != null && !selectTypeList.isEmpty()) {
                for (ColumnSelectType selectType : selectTypeList) {
                    if (Strings.isBlank(selectType.getLabel()) || Strings.isBlank(selectType.getValue()) || Strings.isBlank(selectType.getMysql())) {
                        continue;
                    }
                    selectType.setValue(selectType.getValue().toUpperCase(Locale.ENGLISH));
                    selectType.setMysql(selectType.getMysql().toUpperCase(Locale.ENGLISH));
                    selectType.setJava(MysqlToJavaEnum.getJava(selectType.getMysql()));
                    selectType.setRadius(DataTypeRadiusEnum.getRadius(selectType.getMysql()));
                    if (selectType.getFixed() && (selectType.getExtent() == null || selectType.getExtent() == 0)) {
                        selectType.setExtent(selectType.getRadius().getMax());
                    }
                    columnSelectTypes.add(selectType);
                }
                columnSelectTypes.sort(Comparator.comparingInt(o -> o.getSeqNo().intValue()));
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return columnSelectTypes;
    }

    @Override
    public Map<String, ColumnMeta> getTableUpgradeColumns() {
        Map<String, ColumnMeta> columnMetaMap = new HashMap<>();
        try {
            String jsonStr = JsonUtils.readJsonFile(ResourcesFiles.TABLE_UPGRADE_JSON);
            List<ColumnMeta> columnMetaList = JSON.parseArray(jsonStr, ColumnMeta.class);
            if (columnMetaList != null && !columnMetaList.isEmpty()) {
                for (ColumnMeta columnMeta : columnMetaList) {
                    columnMeta.afterSet();
                    columnMetaMap.put(columnMeta.getFieldName(), columnMeta);
                }
            }
        } catch (IOException e) {
            return new HashMap<>();
        }
        return columnMetaMap;
    }
}
