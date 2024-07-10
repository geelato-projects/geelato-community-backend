package cn.geelato.web.platform.m.excel.entity;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.Ctx;
import cn.geelato.core.env.entity.User;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author diabl
 * @description: 水印
 * @date 2024/1/11 13:46
 */
public class WordWaterMarkMeta {
    public static final int CELL_SPACE = 8;
    public static final int ROW_SPACE = 100;
    public static final double ROTATION_ANGLE = -45;
    public static final String FONT_FAMILY = "宋体";
    public static final String FONT_COLOR = "#d8d8d8";
    public static final double FONT_SIZE = 20;
    private static final Pattern templatePatten = Pattern.compile("\\$\\{[\\\u4e00-\\\u9fa5,\\w,\\.]+\\}");
    private static final SimpleDateFormat SDF_DATE = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat SDF_TIME = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat SDF_DATETIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private String defaultText;
    //水印列距
    private Integer cellSpace;
    //水印行距
    private Integer rowSpace;
    //水印旋转角度
    private Double rotationAngle;
    //字体名称
    private String fontFamily;
    //字体颜色
    private String fontColor;
    //字体大小
    private Double fontSize;
    private String template;

    public static WordWaterMarkMeta defaultWaterMarkMeta() {
        WordWaterMarkMeta meta = new WordWaterMarkMeta();
        meta.setRowSpace(WordWaterMarkMeta.ROW_SPACE);
        meta.setCellSpace(WordWaterMarkMeta.CELL_SPACE);
        meta.setRotationAngle(WordWaterMarkMeta.ROTATION_ANGLE);
        meta.setFontFamily(WordWaterMarkMeta.FONT_FAMILY);
        meta.setFontColor(WordWaterMarkMeta.FONT_COLOR);
        meta.setFontSize(WordWaterMarkMeta.FONT_SIZE);
        return meta;
    }

    public String getDefaultText() {
        return defaultText;
    }

    public void setDefaultText(String defaultText) {
        this.defaultText = defaultText;
    }

    public Integer getCellSpace() {
        return cellSpace;
    }

    public void setCellSpace(Integer cellSpace) {
        this.cellSpace = cellSpace;
    }

    public Integer getRowSpace() {
        return rowSpace;
    }

    public void setRowSpace(Integer rowSpace) {
        this.rowSpace = rowSpace;
    }

    public Double getRotationAngle() {
        return rotationAngle;
    }

    public void setRotationAngle(Double rotationAngle) {
        this.rotationAngle = rotationAngle;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public String getFontColor() {
        return fontColor;
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }

    public Double getFontSize() {
        return fontSize;
    }

    public void setFontSize(Double fontSize) {
        this.fontSize = fontSize;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public void afterSet() {
        if (this.rowSpace == null) {
            setRowSpace(WordWaterMarkMeta.ROW_SPACE);
        }
        if (this.cellSpace == null) {
            setCellSpace(WordWaterMarkMeta.CELL_SPACE);
        }
        if (this.rotationAngle == null) {
            setRotationAngle(WordWaterMarkMeta.ROTATION_ANGLE);
        }
        if (Strings.isBlank(this.fontFamily)) {
            setFontFamily(WordWaterMarkMeta.FONT_FAMILY);
        }
        if (Strings.isBlank(this.fontColor)) {
            setFontColor(WordWaterMarkMeta.FONT_COLOR);
        }
        if (this.fontSize == null || this.fontSize.doubleValue() <= 0) {
            setFontSize(WordWaterMarkMeta.FONT_SIZE);
        }
    }

    public String formatWaterMark() {
        StringBuilder sb = new StringBuilder();
        if (Strings.isNotBlank(this.template)) {
            Matcher temMatcher = templatePatten.matcher(this.template);
            while (temMatcher.find()) {
                String replaceStr = getMarkTypeString(this.defaultText, temMatcher.group());
                setTemplate(this.template.replace(temMatcher.group(), Strings.isNotBlank(replaceStr) ? replaceStr : ""));
            }
            sb.append(this.template);
        } else {
            sb.append(this.defaultText);
        }

        return sb.toString();
    }

    private String getMarkTypeString(String markText, String type) {
        if (Strings.isNotBlank(type)) {
            if ("${TEXT}".equalsIgnoreCase(type)) {
                return markText;
            } else if ("${TENANTCODE}".equalsIgnoreCase(type)) {
                return Ctx.getCurrentTenantCode();
            } else if ("${USERID}".equalsIgnoreCase(type)) {
                User user = Ctx.getCurrentUser();
                return user != null ? user.getUserId() : "";
            } else if ("${USERNAME}".equalsIgnoreCase(type)) {
                User user = Ctx.getCurrentUser();
                return user != null ? user.getLoginName() : "";
            } else if ("${LOGINNAME}".equalsIgnoreCase(type)) {
                User user = Ctx.getCurrentUser();
                return user != null ? user.getLoginName() : "";
            } else if ("${ORGNAME}".equalsIgnoreCase(type)) {
                User user = Ctx.getCurrentUser();
                return user != null ? user.getDefaultOrgName() : "";
            } else if ("${DATE}".equalsIgnoreCase(type)) {
                return SDF_DATE.format(new Date());
            } else if ("${TIME}".equalsIgnoreCase(type)) {
                return SDF_TIME.format(new Date());
            } else if ("${DATETIME}".equalsIgnoreCase(type)) {
                return SDF_DATETIME.format(new Date());
            }
        }

        return "";
    }
}
