package cn.geelato.meta;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 公司信息实体类
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_company", catalog = "platform")
@Title(title = "公司信息")
public class Company extends BaseEntity {

    @Title(title = "名称")
    @Col(name = "name", nullable = false, charMaxlength = 64)
    private String name;

    @Title(title = "备注")
    @Col(name = "remark", charMaxlength = 1024)
    private String remark;

    @Title(title = "公司简称")
    @Col(name = "short_name", nullable = false, charMaxlength = 64)
    private String shortName;

    @Title(title = "公司地址")
    @Col(name = "address", charMaxlength = 512)
    private String address;

    @Title(title = "2位的编号，如：深圳总公司SZ，广州分公司：GZ")
    @Col(name = "code", charMaxlength = 2)
    private String code;

    @Title(title = "国家/地区")
    @Col(name = "country_code", charMaxlength = 64)
    private String countryCode;

    @Title(title = "本位币")
    @Col(name = "standard_currency", nullable = false, charMaxlength = 3)
    private String standardCurrency;

    @Title(title = "上级公司")
    @Col(name = "pid", charMaxlength = 19)
    private String pid;

    @Title(title = "英文名称")
    @Col(name = "en_name", charMaxlength = 128)
    private String enName;

    @Title(title = "英文地址")
    @Col(name = "en_address", charMaxlength = 1024)
    private String enAddress;

    @Title(title = "本地语言")
    @Col(name = "local_language", charMaxlength = 3)
    private String localLanguage = "CHS";

    @Title(title = "绑定客商")
    @Col(name = "cooperating_org_id", charMaxlength = 32)
    private String cooperatingOrgId;

    @Title(title = "组织树ID")
    @Col(name = "org_id", charMaxlength = 19)
    private String orgId;
}