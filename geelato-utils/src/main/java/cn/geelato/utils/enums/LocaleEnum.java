package cn.geelato.utils.enums;

import lombok.Getter;

import java.util.Locale;

@Getter
public enum LocaleEnum {
    CHINA(Locale.CHINA),
    ENGLISH(Locale.ENGLISH);

    private final Locale locale;

    LocaleEnum(Locale locale) {
        this.locale = locale;
    }

    public static Locale getDefaultLocale(String code) {
        Locale locale = getLocale(code);
        return locale == null ? LocaleEnum.ENGLISH.getLocale() : locale;
    }

    public static Locale getLocale(String code) {
        for (LocaleEnum locale : LocaleEnum.values()) {
            if (locale.name().equalsIgnoreCase(code)) {
                return locale.getLocale();
            }
        }
        return null;
    }
}
