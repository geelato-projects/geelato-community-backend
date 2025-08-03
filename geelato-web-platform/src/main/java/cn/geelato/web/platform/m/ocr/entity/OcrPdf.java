package cn.geelato.web.platform.m.ocr.entity;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.lang.meta.*;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.List;

@Getter
@Setter
@Title(title = "读取PDF")
@Entity(name = "platform_ocr_pdf")
public class OcrPdf extends BaseEntity implements EntityEnableAble {
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "标题")
    private String title;
    @Title(title = "分组编码")
    @Col(name = "group_code")
    private String groupCode;
    @Title(title = "内容")
    @Ignore(type = {IgnoreType.PAGE_QUERY})
    private String content;
    @Title(title = "模板")
    @Ignore(type = {IgnoreType.PAGE_QUERY})
    private String template;
    @Title(title = "描述")
    private String description;
    @Title(title = "检测规则")
    private String rule;
    @Title(title = "状态")
    @Col(name = "enable_status")
    private int enableStatus;
    @Transient
    private List<OcrPdfMeta> metas;

    /**
     * 将当前对象转换为OcrPdfRule对象
     *
     * @return OcrPdfRule对象，如果转换失败则返回null
     */
    public OcrPdfRule toRules() {
        OcrPdfRule opr = null;
        if (Strings.isNotBlank(this.getRule())) {
            try {
                opr = JSON.parseObject(this.getRule(), OcrPdfRule.class);
            } catch (Exception e) {
                opr = null;
            }
        }
        return opr;
    }
}
