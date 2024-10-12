package cn.geelato.utils;

import java.awt.*;

public class ColorUtils {
    public static final String WHITE = "#FFFFFF";
    public static final String BLACK = "#000000";
    public static final String RED = "#F53F3F";
    public static final String ORANGERED = "#7816FF";
    public static final String ORANGE = "#00B42A";
    public static final String GOLD = "#165DFF";
    public static final String LIME = "#FF7D00";
    public static final String GREEN = "#EB0AA4";
    public static final String CYAN = "#7BC616";
    public static final String BLUE = "#86909C";
    public static final String ARCOBLUE = "#B71DE8";
    public static final String PURPLE = "#0FC6C2";
    public static final String PINKPURPLE = "#FFB400";
    public static final String MAGENTA = "#168CFF";
    public static final String GRAY = "#FF5722";

    public static Color hexToColor(String color, String defaultColor) {
        return hexToColor(StringUtils.isBlank(color) ? defaultColor : color);
    }

    public static Color hexToColor(String color) {
        // 去掉前导的 '#' 字符（如果存在）
        if (color.startsWith("#")) {
            color = color.substring(1);
        }

        // 确保字符串长度为 6（即 3 个 RGB 分量，每个分量 2 个十六进制字符）
        if (color.length() == 6) {
            try {
                // 将十六进制字符串转换为整数
                int rgb = Integer.parseInt(color, 16);

                // 提取 RGB 分量
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // 创建 Color 对象
                return new Color(r, g, b);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                // 如果解析失败，可以返回一个默认颜色或抛出异常
                return null;
            }
        } else {
            // 如果字符串长度不是 6，可以返回一个默认颜色或抛出异常
            throw new IllegalArgumentException("Invalid hex color string: " + color);
        }
    }
}
