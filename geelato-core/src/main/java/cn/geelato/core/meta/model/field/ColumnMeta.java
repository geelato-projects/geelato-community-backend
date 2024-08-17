package cn.geelato.core.meta.model.field;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DataTypeRadiusEnum;
import cn.geelato.core.enums.MysqlDataTypeEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.DictDataSrc;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author geemeta
 * <p>
 * 抽象数库中的元数据字段属性、并加入实体属性建立联系
 */
@Title(title = "字段信息")
@Entity(name = "platform_dev_column")
public class ColumnMeta extends BaseSortableEntity implements EntityEnableAble, Serializable {
    @Setter
    @Col(name = "app_id")
    private String appId;
    //******--以下为元数据管理专用辅助字段
    // 实体属性中文
    @Setter
    @Col(name = "title")
    private String title = "";
    /**
     * -- GETTER --
     *
     */
    @Setter
    @Getter
    private String abstractColumnExpressions;
    // 实体属性名称
    @Setter
    @Col(name = "field_name")
    private String fieldName = "";
    //******--以上为元数据管理专用辅助字段
    @Setter
    @Col(name = "table_id")
    private String tableId;
    @Setter
    @Col(name = "table_schema")
    private String tableSchema;
    @Setter
    @Col(name = "table_name")
    private String tableName;
    @Setter
    @Col(name = "table_catalog")
    private String tableCatalog;
    // COLUMN_NAME
    @Setter
    @Col(name = "column_name")
    private String name = "";
    // COLUMN_COMMENT
    @Setter
    @Col(name = "column_comment")
    private String comment = "";
    // ORDINAL_POSITION
    @Setter
    @Col(name = "ordinal_position")
    private int ordinalPosition = 0;
    // COLUMN_DEFAULT
    // 数据字典编码、流水号id、实体id、多组件[{"label":"","code":"","value":""}]
    @Setter
    @Col(name = "default_value")
    private String defaultValue = null;
    // COLUMN_TYPE  --varchar(100)
    @Setter
    @Col(name = "column_type")
    private String type;
    // COLUMN_KEY,-- PRI
    @Setter
    @Col(name = "column_key")
    private boolean key = false;

    // isNullable
    @Setter
    @Col(name = "is_nullable")
    private boolean nullable = true;
    @Setter
    @Col(name = "data_type")
    private String dataType = "";
    @Setter
    @Col(name = "extra")
    private String extra;
    @Setter
    @Col(name = "auto_increment")
    private boolean autoIncrement = false;
    @Setter
    @Col(name = "is_unique")
    private boolean uniqued = false;

    // CHARACTER_MAXIMUM_LENGTH
    @Setter
    @Col(name = "character_maxinum_length")
    private long charMaxLength = 64;// 默认长度
    // NUMERIC_PRECISION
    @Setter
    @Col(name = "numeric_precision")
    private int numericPrecision = 19; // 默认长度
    // NUMERIC_SCALE
    @Setter
    @Col(name = "numeric_scale")
    private int numericScale = 0;

    // MySQL的information_schema.column中没有该字段，该信息体现在type字段中，numericPrecision无符号比有符号长1
    @Setter
    @Col(name = "numeric_signed")
    private boolean numericSigned = false; // 是否有符号，默认有，若无符号，则需在type中增加：unsigned
    // DATETIME_PRECISION
    @Setter
    @Col(name = "datetime_precision")
    private int datetimePrecision = 0; // datetime 长度

    //`DATETIME_PRECISION` bigint(21) unsigned DEFAULT NULL,
    // private int datetime_precision;,
    //`CHARACTER_OCTET_LENGTH` bigint(21) unsigned DEFAULT NULL,
    //----------------
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Setter
    @Col(name = "linked")
    private int linked = 1;
    @Setter
    @Col(name = "description")
    private String description;

    // 1-外表字段，默认0
    @Col(name = "is_foreign_column")
    private boolean isRefColumn;

    // isRefColumn为true时，需要通过本表引用字段
    @Setter
    @Col(name = "ref_local_col")
    private String refLocalCol;
    // 外表字段名称
    @Setter
    @Col(name = "foreign_col_name")
    private String refColName;
    // 外表表名
    @Setter
    @Col(name = "foreign_table")
    private String refTables;
    @Setter
    private boolean abstractColumn;
    // 数据选择类型
    @Setter
    @Col(name = "select_type")
    private String selectType;
    // 数据类型选择 额外字段。
    @Setter
    @Col(name = "type_extra")
    private String typeExtra;
    @Setter
    @Col(name = "extra_value")
    private String extraValue;
    @Setter
    @Col(name = "extra_map")
    private String extraMap;
    @Setter
    @Col(name = "auto_add")
    private boolean autoAdd = false;
    @Setter
    @Col(name = "auto_name")
    private String autoName;
    @Setter
    @Col(name = "synced")
    private boolean synced = false;
    @Setter
    @Col(name = "encrypted")
    private boolean encrypted = false;
    @Setter
    @Col(name = "marker")
    private String marker; // 特殊标记
    @Setter
    @Col(name = "drawed")
    private boolean drawed = false;

    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Col(name = "table_id")
    @Title(title = "表ID")
    public String getTableId() {
        return tableId;
    }

    @Col(name = "table_schema")
    @Title(title = "数据库名", description = "即table_schema")
    public String getTableSchema() {
        return tableSchema;
    }

    @Col(name = "table_name")
    @Title(title = "表名")
    public String getTableName() {
        return tableName;
    }

    @Col(name = "table_catalog")
    @Title(title = "表目录", description = "如：def")
    public String getTableCatalog() {
        return tableCatalog;
    }

    @Col(name = "title")
    @Title(title = "中文名")
    public String getTitle() {
        return title;
    }

    @Col(name = "field_name")
    @Title(title = "列名")
    public String getFieldName() {
        return fieldName;
    }

    @Col(name = "column_name")
    @Title(title = "列名")
    public String getName() {
        return name;
    }

    @Col(name = "column_comment")
    @Title(title = "备注")
    public String getComment() {
        return comment;
    }

    @Col(name = "ordinal_position")
    @Title(title = "次序")
    public int getOrdinalPosition() {
        return ordinalPosition;
    }

    @Col(name = "column_default")
    @Title(title = "默认值", description = "auto_increment、null、无默认值、current_timestamp、on save current_timestamp、custom")
    public String getDefaultValue() {
        return defaultValue;
    }

    @Col(name = "column_type")
    @Title(title = "类型")
    public String getType() {
        return type;
    }

    @Col(name = "column_key")
    @Title(title = "列键")
    public boolean isKey() {
        return key;
    }


    @DictDataSrc(group = "YES_OR_NO")
    @Col(name = "is_nullable", nullable = false)
    @Title(title = "可空")
    public boolean isNullable() {
        return nullable;
    }

    @DictDataSrc(group = "DATA_TYPE")
    @Col(name = "data_type")
    @Title(title = "数据类型", description = "BIT|VARCHAR|TEXT|LONGTEXT|TINYINT|INT|BIGINT|DECIMAL|YEAR|DATE|TIME|DATETIME|TIMESTAMP")
    public String getDataType() {
        return dataType;
    }

    @Col(name = "extra")
    @Title(title = "特别", description = "value like auto_increment")
    public String getExtra() {
        return extra;
    }

    @Col(name = "auto_increment")
    @Title(title = "自动递增")
    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    @DictDataSrc(group = "YES_OR_NO")
    @Col(name = "is_unique", nullable = false)
    @Title(title = "唯一约束")
    public boolean isUniqued() {
        return uniqued;
    }


    @Col(name = "character_maxinum_length")
    @Title(title = "长度")
    public long getCharMaxLength() {
        return charMaxLength;
    }

    @Col(name = "numeric_precision")
    @Title(title = "整数位")
    public int getNumericPrecision() {
        return numericPrecision;
    }

    @Col(name = "numeric_scale")
    @Title(title = "小数位")
    public int getNumericScale() {
        return numericScale;
    }

    @Col(name = "numeric_signed")
    @Title(title = "是否有符号")
    public boolean isNumericSigned() {
        return numericSigned;
    }

    @Col(name = "datetime_precision")
    @Title(title = "日期长度")
    public int getDatetimePrecision() {
        return datetimePrecision;
    }

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, dataType = "tinyint", numericPrecision = 1)
    @Override
    public int getEnableStatus() {
        return this.enableStatus;
    }

    @Override
    public void setEnableStatus(int enableStatus) {
        this.enableStatus = enableStatus;
    }

    @Col(name = "linked")
    @Title(title = "链接")
    public int getLinked() {
        return linked;
    }

    @Col(name = "description")
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }

    @Col(name = "is_foreign_column")
    @Title(title = "外表字段")
    public boolean getIsRefColumn() {
        return isRefColumn;
    }

    @Col(name = "ref_local_col")
    @Title(title = "本表引用字段", description = "isRefColumn为true时有效")
    public String getRefLocalCol() {
        return refLocalCol;
    }

    @Col(name = "foreign_table")
    @Title(title = "外表表名", description = "多层关联外表用逗号隔开")
    public String getRefTables() {
        return refTables;
    }

    @Col(name = "foreign_col_name")
    @Title(title = "外表字段名称", description = "命名规则：[表名]+[.]+[表字段]")
    public String getRefColName() {
        return refColName;
    }

    /**
     * (select columnName from t2) as abstractColumn
     * sum(columnName) as abstractColumn
     *
     * @return 是否为计算出来的列，即非物理列
     */
    public boolean isAbstractColumn() {
        return StringUtils.hasText(abstractColumnExpressions);
    }

    @Col(name = "select_type")
    @Title(title = "选择类型")
    public String getSelectType() {
        return selectType;
    }

    @Col(name = "type_extra")
    @Title(title = "选择类型额外数据")
    public String getTypeExtra() {
        return typeExtra;
    }

    @Col(name = "extra_value")
    @Title(title = "选择类型额外数据")
    public String getExtraValue() {
        return extraValue;
    }

    @Col(name = "extra_map")
    @Title(title = "字段与额外字段的映射关系")
    public String getExtraMap() {
        return extraMap;
    }

    @Col(name = "auto_add")
    @Title(title = "选择类型")
    public boolean isAutoAdd() {
        return autoAdd;
    }

    @Col(name = "auto_name")
    @Title(title = "选择类型")
    public String getAutoName() {
        return autoName;
    }

    @Col(name = "synced")
    @Title(title = "是否已同步")
    public boolean isSynced() {
        return synced;
    }

    @Col(name = "encrypted")
    @Title(title = "是否加密")
    public boolean isEncrypted() {
        return encrypted;
    }

    @Col(name = "marker")
    @Title(title = "特殊标记")
    public String getMarker() {
        return marker;
    }

    @Col(name = "drawed")
    @Title(title = "drawDB字段显示")
    public boolean isDrawed() {
        return drawed;
    }

    @Override
    public void afterSet() {
        // 更加数据类型dataType 设置 type
        if (Strings.isNotBlank(dataType)) {
            dataType = dataType.toUpperCase(Locale.ENGLISH);
            selectType = Strings.isNotBlank(selectType) ? selectType.toUpperCase(Locale.ENGLISH) : dataType;
            DataTypeRadius radius = DataTypeRadiusEnum.getRadius(dataType);
            String columnType = null;
            if (MysqlDataTypeEnum.getBooleans().contains(dataType)) {
                setCharMaxLength(1L);
                columnType = dataType + "(" + charMaxLength + ")";
            } else if (MysqlDataTypeEnum.getTinyBooleans().contains(dataType) && Arrays.asList(new String[]{"BIT", "SWITCH"}).contains(selectType)) {
                setCharMaxLength(0);
                setNumericPrecision(1);
                columnType = dataType + "(" + numericPrecision + ")" + (isNumericSigned() ? "" : " UNSIGNED");
            } else if (MysqlDataTypeEnum.getChars().contains(dataType)) {
                columnType = dataType + "(" + charMaxLength + ")";
            } else if (MysqlDataTypeEnum.getTexts().contains(dataType)) {
                setCharMaxLength(radius.getMax());
                setDefaultValue(null);
                columnType = dataType;
            } else if (MysqlDataTypeEnum.getIntegers().contains(dataType)) {
                setCharMaxLength(0);
                columnType = dataType + "(" + numericPrecision + ")" + (isNumericSigned() ? "" : " UNSIGNED");
            } else if (MysqlDataTypeEnum.getDecimals().contains(dataType)) {
                setAutoIncrement(false);
                setCharMaxLength(0);
                columnType = dataType + "(" + (numericPrecision + numericScale) + "," + numericScale + ")" + (isNumericSigned() ? "" : " UNSIGNED");
            } else if (MysqlDataTypeEnum.getDates().contains(dataType)) {
                columnType = dataType;
            } else {
                columnType = dataType;
            }
            setDataType(dataType);
            setType(columnType);
            // 设置是否自动新增，数值型主键才能自动新增
            if (!(isKey() && MysqlDataTypeEnum.getIntegers().contains(dataType))) {
                setAutoIncrement(false);
            }
            // 设置额外值
            List<String> extras = new ArrayList<>();
            if (isUniqued()) {
                extras.add("UNIQUE");
            }
            if (MysqlDataTypeEnum.getNumbers().contains(dataType)) {
                if (isAutoIncrement()) {
                    extras.add("AUTO_INCREMENT");
                    setDefaultValue(null);
                }
                if (!isNumericSigned()) {
                    extras.add("UNSIGNED");
                }
            }
            setExtra(String.join(",", extras));
            // 设置默认值
            setDefaultValue(Strings.isNotBlank(defaultValue) ? defaultValue : null);
        }
    }

    public void setIsRefColumn(boolean refColumn) {
        isRefColumn = refColumn;
    }
}