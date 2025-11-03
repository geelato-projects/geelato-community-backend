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

    @Title(title = "上级公司")
    private String pid;

    @Title(title = "名称")
    private String name;

    @Title(title = "编号")
    private String code;

    @Title(title = "备注")
    private String remark;

    @Title(title = "公司简称")
    @Col(name = "short_name")
    private String shortName;

    @Title(title = "英文名称")
    @Col(name = "en_name")
    private String enName;

    @Title(title = "公司地址")
    private String address;

    @Title(title = "国家/地区")
    @Col(name = "country_code")
    private String countryCode;

    @Title(title = "本位币")
    @Col(name = "standard_currency")
    private String standardCurrency;

    @Title(title = "英文地址")
    @Col(name = "en_address")
    private String enAddress;

    @Title(title = "本地语言")
    @Col(name = "local_language")
    private String localLanguage;

    @Title(title = "绑定客商")
    @Col(name = "cooperating_org_id")
    private String cooperatingOrgId;

    @Title(title = "组织ID")
    @Col(name = "org_id")
    private String orgId;

    @Title(title = "企业微信配置")
    @Col(name = "weixin_work_info")
    private String weixinWorkInfo;
}