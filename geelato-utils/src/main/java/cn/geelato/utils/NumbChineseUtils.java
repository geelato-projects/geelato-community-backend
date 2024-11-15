package cn.geelato.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NumbChineseUtils {

    private static final String[] OLD_NUM = new String[]{"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "点"};
    private static final String[] OLD_UNIT = new String[]{"", "拾", "佰", "千"};
    private static final String[] OLD_UNIT_LEVE = new String[]{"", "萬", "亿"};

    private static final String[] NUM = new String[]{"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "点"};
    private static final String[] UNIT = new String[]{"", "十", "百", "千"};
    private static final String[] UNIT_LEVE = new String[]{"", "万", "亿"};

    private static final String[] AMOUNT_UNIT = new String[]{"角", "分", "厘", "毫"};

    public static void main(String[] args) {
        System.out.println(byChinese("11112200.09"));
        System.out.println(byChineseAmount("11112200.09"));
        System.out.println(byOldChinese("11112200.09"));
        System.out.println(byOldChineseAmount("11112200.09"));
    }

    public static String byChinese(String num) {
        return byChinese(splitNum(num), false, false);
    }

    public static String byChineseAmount(String num) {
        return byChinese(splitNum(num), false, true);
    }

    public static String byOldChinese(String num) {
        return byChinese(splitNum(num), true, false);
    }

    public static String byOldChineseAmount(String num) {
        return byChinese(splitNum(num), true, true);
    }

    public static String[] splitNum(String num) {
        // 去除小数点后面的数字
        String newNum;
        String decimal = null;
        if (num.indexOf(".") >= 0) {
            String[] split1 = num.split("\\.");
            newNum = split1[0];
            decimal = split1[1];
        } else {
            newNum = num;
        }
        int sub = newNum.length() % 4;
        StringBuilder sb = new StringBuilder(newNum);
        for (int i = 0; i < newNum.length(); i += 4) {
            if (sub != 0) {
                sb.insert(sub + i, ",");
                sub++;
            } else {
                sb.insert(4, ",");
                sub = 5;
            }
        }
        sb.deleteCharAt(sb.length() - 1);
        String[] split = sb.toString().split(",");
        List<String> strings = new ArrayList<>(Arrays.asList(split));
        if (decimal != null) {
            strings.add("." + decimal);
        }
        return strings.toArray(new String[]{});
    }

    public static String byChinese(String[] num, boolean oldNum, boolean amount) {
        StringBuilder sb = new StringBuilder();
        int sub = 0;
        if (num[num.length - 1].indexOf(".") >= 0) {
            sub = 1;
        }
        for (int i = 0; i < num.length; i++) {
            if (num[i].indexOf(".") >= 0) {
                decimal(num[i], sb, oldNum, amount);
            } else {
                chinese(num[i], sb, oldNum, amount);
                if (oldNum) {
                    addLeve(sb, i, num.length - sub, OLD_UNIT_LEVE);
                } else {
                    addLeve(sb, i, num.length - sub, UNIT_LEVE);
                }
                removeSubZero(sb);
            }
        }
        return sb.toString();
    }

    public static void decimal(String num, StringBuilder sb, boolean oldNum, boolean amount) {
        char[] chars = num.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            String s = String.valueOf(c);
            if (oldNum) {
                if (".".equals(s)) {
                    if (!amount) {
                        sb.append(OLD_NUM[OLD_NUM.length - 1]);
                    } else {
                        sb.append("元");
                    }
                } else {
                    String s1 = OLD_NUM[Integer.valueOf(s)];
                    sb.append(s1);
                    if (amount && !s1.equals(OLD_NUM[0])) {
                        addAmountUnit(sb, i, chars.length);
                    }
                }
            } else {
                if (".".equals(s)) {
                    if (!amount) {
                        sb.append(NUM[NUM.length - 1]);
                    } else {
                        sb.append("元");
                    }
                } else {
                    String s1 = NUM[Integer.valueOf(s)];
                    sb.append(s1);
                    if (amount && !s1.equals(NUM[0])) {
                        addAmountUnit(sb, i, chars.length);
                    }
                }
            }
        }

        if (amount) {
            sb.append("整");
        }
    }

    public static void chinese(String num, StringBuilder sb, boolean oldNum, boolean amount) {
        char[] chars = num.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            String s = String.valueOf(c);
            if (oldNum) {
                String s1 = OLD_NUM[Integer.valueOf(s)];
                if (removeRepeatZero(s1, i, sb)) {
                    continue;
                }
                sb.append(s1);

                if (!s1.equals(OLD_NUM[0])) {
                    addUnit(sb, i, chars.length, OLD_UNIT);
                }
            } else {
                String s1 = NUM[Integer.valueOf(s)];
                if (removeRepeatZero(s1, i, sb)) {
                    continue;
                }
                sb.append(s1);
                if (!s1.equals(OLD_NUM[0])) {
                    addUnit(sb, i, chars.length, UNIT);
                }
            }
        }
    }

    public static boolean removeRepeatZero(String s1, int i, StringBuilder sb) {
        if (s1.equals(OLD_NUM[0])
                && i != 0
                && sb.substring(sb.length() - 1, sb.length()).equals(OLD_NUM[0])) {
            return true;
        }
        return false;
    }

    public static void removeSubZero(StringBuilder sb) {
        if (sb.substring(sb.length() - 1, sb.length()).equals(NUM[0])
                ||
                sb.substring(sb.length() - 1, sb.length()).equals(OLD_NUM[0])) {
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    public static void addUnit(StringBuilder sb, int index, int length, String[] unit) {
        sb.append(unit[length - 1 - index]);
    }

    public static void addLeve(StringBuilder sb, int index, int length, String[] leve) {
        sb.append(leve[length - 1 - index]);
    }

    public static void addAmountUnit(StringBuilder sb, int index, int length) {
        sb.append(AMOUNT_UNIT[index - 1]);
    }
}
