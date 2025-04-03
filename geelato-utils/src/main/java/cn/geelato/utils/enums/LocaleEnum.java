package cn.geelato.utils.enums;

import lombok.Getter;

import java.util.Locale;

@Getter
public enum LocaleEnum {
    CHINA("中文", "china", Locale.CHINA),
    ENGLISH("英文", "english", Locale.ENGLISH);

    private final String label;
    private final String value;
    private final Locale locale;

    LocaleEnum(String label, String value, Locale locale) {
        this.label = label;
        this.value = value;
        this.locale = locale;
    }

    public static Locale getDefaultLocale(String value) {
        Locale locale = getLocale(value);
        return locale == null ? LocaleEnum.ENGLISH.getLocale() : locale;
    }

    public static Locale getLocale(String value) {
        for (LocaleEnum locale : LocaleEnum.values()) {
            if (locale.getValue().equalsIgnoreCase(value)) {
                return locale.getLocale();
            }
        }
        return null;
    }
}
