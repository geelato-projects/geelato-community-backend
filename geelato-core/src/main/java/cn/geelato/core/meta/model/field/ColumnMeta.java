package cn.geelato.core.meta.model.field;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DataTypeRadiusEnum;
import cn.geelato.core.enums.MysqlDataTypeEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.DictDataSrc;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;
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
    @Getter
    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    @Setter
    @Getter
    @Title(title = "中文名")
    @Col(name = "title")
    private String title;
    @Setter
    @Getter
    private String abstractColumnExpressions;
    @Setter
    @Getter
    @Title(title = "列名")
    @Col(name = "field_name")
    private String fieldName;
    @Setter
    @Getter
    @Title(title = "表ID")
    @Col(name = "table_id")
    private String tableId;
    @Setter
    @Getter
    @Title(title = "数据库名", description = "即table_schema")
    @Col(name = "table_schema")
    private String tableSchema;
    @Setter
    @Getter
    @Title(title = "表名")
    @Col(name = "table_name")
    private String tableName;
    @Setter
    @Getter
    @Title(title = "表目录", description = "如：def")
    @Col(name = "table_catalog")
    private String tableCatalog;
    @Setter
    @Getter
    @Title(title = "列名")
    @Col(name = "column_name")
    private String name;
    @Setter
    @Getter
    @Title(title = "备注")
    @Col(name = "column_comment")
    private String comment;
    @Setter
    @Getter
    @Title(title = "次序")
    @Col(name = "ordinal_position")
    private int ordinalPosition = 0;
    @Setter
    @Getter
    @Title(title = "默认值", description = "auto_increment、null、无默认值、current_timestamp、on save current_timestamp、custom")
    @Col(name = "column_default")
    private String defaultValue = null;
    @Setter
    @Getter
    @Title(title = "类型")
    @Col(name = "column_type")
    private String type;
    @Setter
    @Getter
    @Title(title = "列键")
    @Col(name = "column_key")
    private boolean key = false;
    @Setter
    @Getter
    @DictDataSrc(group = "YES_OR_NO")
    @Title(title = "可空")
    @Col(name = "is_nullable")
    private boolean nullable = true;
    @Setter
    @Getter
    @Title(title = "数据类型")
    @Col(name = "data_type")
    private String dataType;
    @Setter
    @Getter
    @Title(title = "特别", description = "value like auto_increment")
    @Col(name = "extra")
    private String extra;
    @Setter
    @Getter
    @Title(title = "自动递增")
    @Col(name = "auto_increment")
    private boolean autoIncrement = false;
    @Setter
    @Getter
    @DictDataSrc(group = "YES_OR_NO")
    @Title(title = "唯一约束")
    @Col(name = "is_unique")
    private boolean uniqued = false;
    @Setter
    @Getter
    @Title(title = "长度")
    @Col(name = "character_maxinum_length")
    private long charMaxLength = 64;
    @Setter
    @Getter
    @Title(title = "整数位")
    @Col(name = "numeric_precision")
    private int numericPrecision = 19;
    @Setter
    @Getter
    @Title(title = "小数位")
    @Col(name = "numeric_scale")
    private int numericScale = 0;
    @Setter
    @Getter
    @Title(title = "是否有符号", description = "是否有符号，默认有，若无符号，则需在type中增加：unsigned")
    @Col(name = "numeric_signed")
    private boolean numericSigned = false;
    @Setter
    @Getter
    @Title(title = "日期长度")
    @Col(name = "datetime_precision")
    private int datetimePrecision = 0;
    @Setter
    @Getter
    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Setter
    @Getter
    @Title(title = "链接")
    @Col(name = "linked")
    private int linked = 1;
    @Setter
    @Getter
    @Title(title = "描述")
    @Col(name = "description")
    private String description;
    @Setter
    @Getter
    @Title(title = "本表引用字段", description = "isRefColumn为true时有效")
    @Col(name = "ref_local_col")
    private String refLocalCol;
    @Setter
    @Getter
    @Title(title = "外表表名", description = "多层关联外表用逗号隔开")
    @Col(name = "foreign_col_name")
    private String refColName;
    @Setter
    @Getter
    @Title(title = "外表字段名称", description = "命名规则：[表名]+[.]+[表字段]")
    @Col(name = "foreign_table")
    private String refTables;
    @Setter
    @Getter
    @Title(title = "选择类型")
    @Col(name = "select_type")
    private String selectType;
    @Setter
    @Getter
    @Title(title = "选择类型额外数据")
    @Col(name = "type_extra")
    private String typeExtra;
    @Setter
    @Getter
    @Title(title = "选择类型额外数据")
    @Col(name = "extra_value")
    private String extraValue;
    @Setter
    @Getter
    @Title(title = "字段与额外字段的映射关系")
    @Col(name = "extra_map")
    private String extraMap;
    @Setter
    @Getter
    @Title(title = "选择类型")
    @Col(name = "auto_add")
    private boolean autoAdd = false;
    @Setter
    @Getter
    @Title(title = "选择类型")
    @Col(name = "auto_name")
    private String autoName;
    @Setter
    @Getter
    @Title(title = "是否已同步")
    @Col(name = "synced")
    private boolean synced = false;
    @Setter
    @Getter
    @Title(title = "是否加密")
    @Col(name = "encrypted")
    private boolean encrypted = false;
    @Setter
    @Getter
    @Title(title = "特殊标记")
    @Col(name = "marker")
    private String marker;
    @Setter
    @Getter
    @Title(title = "drawDB字段显示")
    @Col(name = "drawed")
    private boolean drawed = false;

    private boolean isRefColumn;
    @Setter
    private boolean abstractColumn;

    @Title(title = "外表字段", description = "1-外表字段，默认0")
    @Col(name = "is_foreign_column")
    public boolean getIsRefColumn() {
        return isRefColumn;
    }

    public void setIsRefColumn(boolean refColumn) {
        isRefColumn = refColumn;
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
}
