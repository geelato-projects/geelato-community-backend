package cn.geelato.web.platform.m.excel.enums;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

/**
 * @author diabl
 */
@Getter
public enum ExcelAlignmentEnum {
    LEFT("居左", "left", HorizontalAlignment.LEFT),
    CENTER("居中", "center", HorizontalAlignment.CENTER),
    RIGHT("居右", "right", HorizontalAlignment.RIGHT);

    private final String label;// 选项内容
    private final String value;// 选项值
    private final HorizontalAlignment horizontalAlignment;

    ExcelAlignmentEnum(String label, String value, HorizontalAlignment horizontalAlignment) {
        this.label = label;
        this.value = value;
        this.horizontalAlignment = horizontalAlignment;
    }

    public static HorizontalAlignment getHorizontalAlignment(String value) {
        if (Strings.isNotBlank(value)) {
            for (ExcelAlignmentEnum enums : ExcelAlignmentEnum.values()) {
                if (enums.getValue().equalsIgnoreCase(value)) {
                    return enums.getHorizontalAlignment();
                }
            }
        }
        return ExcelAlignmentEnum.CENTER.getHorizontalAlignment();
    }
}
