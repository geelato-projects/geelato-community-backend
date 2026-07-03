package cn.geelato.web.platform.srv.platform.enums;

import lombok.Getter;

@Getter
public enum AppMultiLangTypeEnum {
    ZH_CN("中文", "zh-CN"),
    EN_US("英语", "en-US"),
    ES_ES("西班牙语", "es-ES"),
    HI_IN("印地语", "hi-IN"),
    AR_SA("阿拉伯语", "ar-SA"),
    PT_BR("葡萄牙语", "pt-BR"),
    RU_RU("俄语", "ru-RU"),
    JA_JP("日语", "ja-JP"),
    DE_DE("德语", "de-DE"),
    FR_FR("法语", "fr-FR"),
    KO_KR("韩语", "ko-KR"),
    IT_IT("意大利语", "it-IT"),
    TR_TR("土耳其语", "tr-TR"),
    VI_VN("越南语", "vi-VN"),
    TH_TH("泰语", "th-TH");

    private final String label;// 选项内容
    private final String value;// 选项值

    AppMultiLangTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
