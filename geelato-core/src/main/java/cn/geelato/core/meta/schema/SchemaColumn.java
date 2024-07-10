package cn.geelato.core.meta.schema;

import cn.geelato.core.meta.model.field.ColumnMeta;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.MysqlDataTypeEnum;

import java.io.Serializable;
import java.util.Locale;

/**
 * @author diabl
 * @description: 数据库中字段信息
 * SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE 1 = 1 AND TABLE_SCHEMA = '' AND TABLE_NAME = '';
 * @date 2023/6/16 13:28
 */
public class SchemaColumn implements Serializable {
    private String tableCatalog;
    private String tableSchema;
    private String tableName;
    private String columnName;
    private String ordinalPosition;
    private String columnDefault;
    private String isNullable;
    private String dataType;
    private String characterMaximumLength;
    private String characterOctetLength;
    private String numericPrecision;
    private String numericScale;
    private String datetimePrecision;
    private String characterSetName;
    private String collationName;
    private String columnType;
    private String columnKey;
    private String extra;
    private String privileges;
    private String columnComment;
    private String generationExpression;
    private String srsId;
    private Boolean unique;

    public String getTableCatalog() {
        return tableCatalog;
    }

    public void setTableCatalog(String tableCatalog) {
        this.tableCatalog = tableCatalog;
    }

    public String getTableSchema() {
        return tableSchema;
    }

    public void setTableSchema(String tableSchema) {
        this.tableSchema = tableSchema;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getOrdinalPosition() {
        return ordinalPosition;
    }

    public void setOrdinalPosition(String ordinalPosition) {
        this.ordinalPosition = ordinalPosition;
    }

    public String getColumnDefault() {
        return columnDefault;
    }

    public void setColumnDefault(String columnDefault) {
        this.columnDefault = columnDefault;
    }

    public String getIsNullable() {
        return isNullable;
    }

    public void setIsNullable(String isNullable) {
        this.isNullable = isNullable;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getCharacterMaximumLength() {
        return characterMaximumLength;
    }

    public void setCharacterMaximumLength(String characterMaximumLength) {
        this.characterMaximumLength = characterMaximumLength;
    }

    public String getCharacterOctetLength() {
        return characterOctetLength;
    }

    public void setCharacterOctetLength(String characterOctetLength) {
        this.characterOctetLength = characterOctetLength;
    }

    public String getNumericPrecision() {
        return numericPrecision;
    }

    public void setNumericPrecision(String numericPrecision) {
        this.numericPrecision = numericPrecision;
    }

    public String getNumericScale() {
        return numericScale;
    }

    public void setNumericScale(String numericScale) {
        this.numericScale = numericScale;
    }

    public String getDatetimePrecision() {
        return datetimePrecision;
    }

    public void setDatetimePrecision(String datetimePrecision) {
        this.datetimePrecision = datetimePrecision;
    }

    public String getCharacterSetName() {
        return characterSetName;
    }

    public void setCharacterSetName(String characterSetName) {
        this.characterSetName = characterSetName;
    }

    public String getCollationName() {
        return collationName;
    }

    public void setCollationName(String collationName) {
        this.collationName = collationName;
    }

    public String getColumnType() {
        return columnType;
    }

    public void setColumnType(String columnType) {
        this.columnType = columnType;
    }

    public String getColumnKey() {
        return columnKey;
    }

    public void setColumnKey(String columnKey) {
        this.columnKey = columnKey;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getPrivileges() {
        return privileges;
    }

    public void setPrivileges(String privileges) {
        this.privileges = privileges;
    }

    public String getColumnComment() {
        return columnComment;
    }

    public void setColumnComment(String columnComment) {
        this.columnComment = columnComment;
    }

    public String getGenerationExpression() {
        return generationExpression;
    }

    public void setGenerationExpression(String generationExpression) {
        this.generationExpression = generationExpression;
    }

    public String getSrsId() {
        return srsId;
    }

    public void setSrsId(String srsId) {
        this.srsId = srsId;
    }

    public Boolean getUnique() {
        return unique;
    }

    public void setUnique(Boolean unique) {
        this.unique = unique;
    }

    public ColumnMeta convertIntoMeta(ColumnMeta meta) {
        meta = meta == null ? new ColumnMeta() : meta;
        meta.setTableCatalog(this.tableCatalog);
        meta.setTableSchema(this.tableSchema);
        meta.setTitle(Strings.isBlank(this.getColumnComment()) ? this.columnName : this.columnComment);
        meta.setComment(this.columnComment);
        meta.setOrdinalPosition(Strings.isBlank(this.ordinalPosition) ? 1 : Integer.parseInt(this.ordinalPosition));
        meta.setSeqNo(meta.getOrdinalPosition());
        meta.setDefaultValue(this.columnDefault == null ? null : this.columnDefault);
        if ("b'0'".equals(this.columnDefault)) {
            meta.setDefaultValue(String.valueOf(0));
        } else if ("b'1'".equals(this.columnDefault)) {
            meta.setDefaultValue(String.valueOf(1));
        }
        meta.setType(this.columnType);
        meta.setKey("PRI".equals(this.columnKey));
        meta.setNullable(Strings.isBlank(this.isNullable) || "YES".equals(this.isNullable));
        meta.setDataType(this.dataType);
        // meta.setExtra(this.extra);
        meta.setAutoIncrement(Strings.isNotEmpty(this.extra) && this.extra.toUpperCase(Locale.ENGLISH).indexOf("AUTO_INCREMENT") != -1);
        meta.setUniqued(this.unique);
        meta.setDatetimePrecision(Strings.isBlank(this.datetimePrecision) ? 0 : Integer.parseInt(this.datetimePrecision));
        meta.setCharMaxLength(Strings.isBlank(this.characterMaximumLength) ? 0 : Long.parseLong(this.characterMaximumLength));
        if (MysqlDataTypeEnum.getDecimals().contains(dataType)) {
            meta.setNumericScale(Strings.isBlank(this.numericScale) ? 0 : Integer.parseInt(this.numericScale));
            meta.setNumericPrecision(Strings.isBlank(this.numericPrecision) ? 0 : Integer.parseInt(this.numericPrecision) - meta.getNumericScale());
        } else {
            meta.setNumericPrecision(Strings.isBlank(this.numericPrecision) ? 0 : Integer.parseInt(this.numericPrecision));
            meta.setNumericScale(Strings.isBlank(this.numericScale) ? 0 : Integer.parseInt(this.numericScale));
        }
        meta.setNumericSigned(Strings.isNotEmpty(this.columnType) && this.columnType.toUpperCase(Locale.ENGLISH).indexOf("UNSIGNED") == -1);
        meta.setEnableStatus(ColumnDefault.ENABLE_STATUS_VALUE);
        meta.setSelectType(this.dataType);
        meta.afterSet();

        return meta;
    }
}

























