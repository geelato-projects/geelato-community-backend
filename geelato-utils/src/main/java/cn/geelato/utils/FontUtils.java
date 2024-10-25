package cn.geelato.utils;

import java.awt.*;
import java.text.Collator;
import java.util.List;
import java.util.*;

public class FontUtils {
    public static final String redisTemplateKey = "redis_template_font_families";
    private static final char MIN_ZH_CHAR = '\u4E00';
    private static final char MAX_ZH_CHAR = '\u9FFF';
    // 使用Collator进行本地化排序
    private static final Collator collator = Collator.getInstance(Locale.CHINESE);
    // 繁体汉字常见的Unicode范围
    private static final int TRADITIONAL_CHINESE_START = 0x4E00; // CJK统一汉字块起始
    private static final int TRADITIONAL_CHINESE_END = 0x9FA5;   // CJK统一汉字块结束

    public static List<String> getAvailableFontFamilies() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Font[] allFonts = env.getAllFonts();

        Set<String> familyFonts = new HashSet<>();
        for (Font font : allFonts) {
            familyFonts.add(font.getFamily());
        }

        return familyFonts.stream().sorted(comparator()).toList();
    }

    private static Comparator<String> comparator() {
        Comparator<String> customComparator = new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                char firstCharS1 = getFirstNonWhitespaceChar(s1);
                char firstCharS2 = getFirstNonWhitespaceChar(s2);

                boolean isZhS1 = isChineseCharacter(firstCharS1);
                boolean isZhS2 = isChineseCharacter(firstCharS2);

                // 如果s1的首字符是汉字且s2的首字符不是汉字，则s1应该排在前面
                if (isZhS1 && !isZhS2) {
                    return -1;
                }
                // 如果s1的首字符不是汉字且s2的首字符是汉字，则s2应该排在前面
                if (!isZhS1 && isZhS2) {
                    return 1;
                }
                // 如果两者都是汉字或都不是汉字，则进行正常排序
                return collator.compare(s1, s2);
            }

            private char getFirstNonWhitespaceChar(String str) {
                for (char c : str.toCharArray()) {
                    if (!Character.isWhitespace(c)) {
                        return c;
                    }
                }
                return '\0'; // 如果没有非空白字符，返回空字符（这种情况理论上不会发生）
            }

            private boolean isChineseCharacter(char c) {
                return c >= MIN_ZH_CHAR && c <= MAX_ZH_CHAR;
            }
        };

        return customComparator;
    }
}