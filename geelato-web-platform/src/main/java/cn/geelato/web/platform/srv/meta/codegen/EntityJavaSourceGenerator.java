package cn.geelato.web.platform.srv.meta.codegen;

import cn.geelato.core.enums.MysqlToJavaEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 实体 Java 源码反向生成器（开发期辅助工具）。
 * <p>
 * 把 {@code platform_dev_table}/{@code platform_dev_column} 里在线定义的实体，
 * 或基于 Java 类解析得到的 EntityMeta，反向构造为带 {@code @Entity}/{@code @Col}/
 * {@code @Title}/{@code @Id} 注解的 Java 源码字符串。
 * </p>
 * <p>
 * 仅供开发者预览/下载后落盘，不写入文件系统、不覆盖已有源文件。
 * 模板范式参考 {@code cn.geelato.meta.User}（@Title 在 @Col 之上，继承 BaseSortableEntity）。
 * </p>
 *
 * @author geemeta
 */
@Component
public class EntityJavaSourceGenerator {

    private final MetaManager metaManager = MetaManager.singleInstance();

    /**
     * 基类（BaseSortableEntity / BaseEntity / IdEntity）已声明的列名集合，
     * 反向生成时跳过这些列，避免与基类字段重复。
     */
    private static final Set<String> BASE_COLUMN_NAMES = new HashSet<>(Arrays.asList(
            "id", "seq_no",
            "create_at", "creator", "creator_name", "bu_id", "dept_id", "tenant_code",
            "update_at", "updater", "updater_name",
            "delete_at", "del_status"
    ));

    /**
     * 默认包名（与现有业务实体一致，见 cn.geelato.meta）。
     */
    public static final String DEFAULT_PACKAGE_NAME = "cn.geelato.meta";

    /**
     * 根据 entityName 反向生成 Java 源码。
     *
     * @param entityName 实体名称（如 platform_user、def_shipping_order）
     * @param packageName 生成类所属包名，为空则用 {@link #DEFAULT_PACKAGE_NAME}
     * @return 完整 .java 源码字符串；实体不存在则抛 IllegalArgumentException
     */
    public String generate(String entityName, String packageName) {
        EntityMeta em = metaManager.getByEntityName(entityName);
        if (em == null) {
            throw new IllegalArgumentException("not found meta defined: " + entityName);
        }
        String pkg = StringUtils.isBlank(packageName) ? DEFAULT_PACKAGE_NAME : packageName.trim();
        String className = toClassName(em);

        // 收集 import（保持顺序、去重）
        Set<String> imports = new LinkedHashSet<>();
        imports.add("cn.geelato.core.meta.model.entity.BaseSortableEntity");
        imports.add("cn.geelato.lang.meta.Col");
        imports.add("cn.geelato.lang.meta.Entity");
        imports.add("cn.geelato.lang.meta.Id");
        imports.add("cn.geelato.lang.meta.Title");
        imports.add("lombok.Getter");
        imports.add("lombok.Setter");

        StringBuilder fields = new StringBuilder();
        Collection<FieldMeta> fieldMetas = em.getFieldMetas();
        if (fieldMetas != null) {
            boolean first = true;
            for (FieldMeta fm : fieldMetas) {
                if (fm == null || fm.getColumnMeta() == null) {
                    continue;
                }
                ColumnMeta cm = fm.getColumnMeta();
                String columnName = StringUtils.isNotBlank(cm.getName()) ? cm.getName() : fm.getColumnName();
                if (StringUtils.isBlank(columnName)) {
                    continue;
                }
                if (BASE_COLUMN_NAMES.contains(columnName.toLowerCase())) {
                    continue;
                }
                if (!first) {
                    fields.append("\n");
                }
                first = false;
                appendField(fields, fm, cm, imports);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");
        // 类注释与类头注解
        sb.append("/**\n * ").append(StringUtils.isNotBlank(em.getEntityTitle()) ? em.getEntityTitle() : className).append("\n */\n");
        sb.append("@Getter\n");
        sb.append("@Setter\n");
        sb.append(buildEntityAnnotation(em)).append("\n");
        sb.append(buildTitleAnnotation(em)).append("\n");
        sb.append("public class ").append(className).append(" extends BaseSortableEntity {\n");
        if (fields.length() > 0) {
            sb.append(fields);
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 由 entityName 推导 PascalCase 类名。
     * 规则：去掉 platform_ / def_ 前缀，转驼峰后首字母大写。
     * 例：platform_user → User；def_shipping_order → ShippingOrder；foo_bar → FooBar。
     */
    public static String toClassName(EntityMeta em) {
        String name = em.getEntityName();
        if (StringUtils.isBlank(name)) {
            return "UntitledEntity";
        }
        String lower = name.toLowerCase();
        if (lower.startsWith("platform_")) {
            name = name.substring("platform_".length());
        } else if (lower.startsWith("def_")) {
            name = name.substring("def_".length());
        }
        String camel = StringUtils.toCamelCase(name.toLowerCase());
        if (camel == null || camel.isEmpty()) {
            return "UntitledEntity";
        }
        return Character.toTitleCase(camel.charAt(0)) + camel.substring(1);
    }

    /**
     * 推断字段的 Java 类型简写（用于 import 收集与字段声明）。
     * mysql dataType 未匹配时降级为 String。
     */
    private String resolveJavaType(ColumnMeta cm, Set<String> imports) {
        Class<?> clazz = MysqlToJavaEnum.getJava(cm.getDataType());
        if (clazz == null) {
            return "String";
        }
        String fullName = clazz.getName();
        // java.lang.* 无需 import，直接用简写
        if (fullName.startsWith("java.lang.")) {
            return clazz.getSimpleName();
        }
        imports.add(fullName);
        return clazz.getSimpleName();
    }

    /**
     * 构造类头 @Entity 注解：name=tableName（或 entityName），catalog（仅非空时）。
     */
    private String buildEntityAnnotation(EntityMeta em) {
        TableMeta tm = em.getTableMeta();
        String name = tm != null && StringUtils.isNotBlank(tm.getTableName()) ? tm.getTableName() : em.getEntityName();
        StringBuilder sb = new StringBuilder("@Entity(name = \"").append(name).append("\"");
        String catalog = em.getCatalog();
        if (StringUtils.isNotBlank(catalog) && !"none".equalsIgnoreCase(catalog)) {
            sb.append(", catalog = \"").append(catalog).append("\"");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 构造类头 @Title 注解。
     */
    private String buildTitleAnnotation(EntityMeta em) {
        StringBuilder sb = new StringBuilder("@Title(title = \"");
        sb.append(StringUtils.isNotBlank(em.getEntityTitle()) ? escape(em.getEntityTitle()) : "").append("\"");
        TableMeta tm = em.getTableMeta();
        String desc = tm != null && StringUtils.isNotBlank(tm.getDescription()) ? tm.getDescription() : "";
        if (StringUtils.isNotBlank(desc)) {
            sb.append(", description = \"").append(escape(desc)).append("\"");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 追加一个字段的注解与声明。
     */
    private void appendField(StringBuilder sb, FieldMeta fm, ColumnMeta cm, Set<String> imports) {
        String columnName = StringUtils.isNotBlank(cm.getName()) ? cm.getName() : fm.getColumnName();
        String fieldName = StringUtils.isNotBlank(fm.getFieldName())
                ? fm.getFieldName()
                : StringUtils.toCamelCase(columnName.toLowerCase());
        String title = StringUtils.isNotBlank(cm.getTitle()) ? cm.getTitle()
                : (StringUtils.isNotBlank(fm.getTitle()) ? fm.getTitle() : fieldName);
        String javaType = resolveJavaType(cm, imports);

        // @Title
        sb.append("    @Title(title = \"").append(escape(title)).append("\"");
        if (StringUtils.isNotBlank(cm.getDescription())) {
            sb.append(", description = \"").append(escape(cm.getDescription())).append("\"");
        }
        sb.append(")\n");

        // @Id（主键）
        if (cm.isKey()) {
            sb.append("    @Id\n");
        }

        // @Col（按需裁剪参数）
        String colAnnotation = buildColAnnotation(cm, columnName, fieldName);
        if (colAnnotation != null) {
            sb.append("    ").append(colAnnotation).append("\n");
        }

        // 外键引用占位注释（不生成 @ForeignKey，因目标 Java 类可能不存在）
        if (cm.getIsRefColumn() && StringUtils.isNotBlank(cm.getRefTables())) {
            String refClassHint = toPascalCaseFromTable(cm.getRefTables());
            sb.append("    // @ForeignKey(fTable = ").append(refClassHint).append(".class)\n");
        }

        // 字段声明
        sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n");
    }

    /**
     * 构造字段级 @Col 注解。仅生成与默认值不同的参数；列名与字段名一致时可省 name。
     * 返回 null 表示无需 @Col（即列名==字段名且其余参数均为默认）。
     */
    private String buildColAnnotation(ColumnMeta cm, String columnName, String fieldName) {
        StringBuilder params = new StringBuilder();
        // name：列名与驼峰字段名不同时才写
        if (!columnName.equals(fieldName)) {
            params.append("name = \"").append(columnName).append("\"");
        }
        // dataType（小写）
        if (StringUtils.isNotBlank(cm.getDataType())) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append("dataType = \"").append(cm.getDataType().toLowerCase()).append("\"");
        }
        // nullable：默认 true，仅 false 才写
        if (!cm.isNullable()) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append("nullable = false");
        }
        // unique：默认 false，仅 true 才写
        if (cm.isUniqued()) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append("unique = true");
        }
        // charMaxlength：> 0 才写
        if (cm.getCharMaxLength() > 0) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append("charMaxlength = ").append(cm.getCharMaxLength());
        }
        // numericPrecision：非默认(20) 才写
        if (cm.getNumericPrecision() != 19 && cm.getNumericPrecision() > 0) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append("numericPrecision = ").append(cm.getNumericPrecision());
        }
        // numericScale：> 0 才写
        if (cm.getNumericScale() > 0) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append("numericScale = ").append(cm.getNumericScale());
        }
        // 引用列：isRefColumn=true 时补充 refLocalCol / refColName
        if (cm.getIsRefColumn()) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append("isRefColumn = true");
            if (StringUtils.isNotBlank(cm.getRefLocalCol())) {
                params.append(", refLocalCol = \"").append(cm.getRefLocalCol()).append("\"");
            }
            if (StringUtils.isNotBlank(cm.getRefColName())) {
                params.append(", refColName = \"").append(cm.getRefColName()).append("\"");
            }
            if (StringUtils.isNotBlank(cm.getRefTables())) {
                params.append(", refTables = \"").append(cm.getRefTables()).append("\"");
            }
        }
        if (params.length() == 0) {
            return null;
        }
        return "@Col(" + params + ")";
    }

    /**
     * 从表名推导 PascalCase 提示（用于 @ForeignKey 占位注释，仅作参考）。
     */
    private String toPascalCaseFromTable(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return "Object";
        }
        String lower = tableName.toLowerCase();
        if (lower.startsWith("platform_")) {
            tableName = tableName.substring("platform_".length());
        } else if (lower.startsWith("def_")) {
            tableName = tableName.substring("def_".length());
        }
        String camel = StringUtils.toCamelCase(tableName.toLowerCase());
        if (StringUtils.isBlank(camel)) {
            return "Object";
        }
        return Character.toTitleCase(camel.charAt(0)) + camel.substring(1);
    }

    /**
     * 转义字符串字面量中的双引号和反斜杠。
     */
    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
