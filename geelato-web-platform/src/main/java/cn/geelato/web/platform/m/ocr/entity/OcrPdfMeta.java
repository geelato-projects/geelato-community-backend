package cn.geelato.web.platform.m.ocr.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Title(title = "PDF批准信息")
@Entity(name = "platform_ocr_pdf_meta")
public class OcrPdfMeta extends BaseSortableEntity {
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "所属PDF")
    @Col(name = "pdf_id")
    private String pdfId;
    @Title(title = "名称")
    private String name;
    @Title(title = "类型")
    private String type;
    @Title(title = "内容")
    private String rule;
    @Title(title = "位置")
    private String position;
    @Title(title = "示例")
    private String example;
    @Title(title = "描述")
    private String description;

    /**
     * 将OcrPdfMeta对象列表转换为Map集合
     *
     * @param list 待转换的OcrPdfMeta对象列表
     * @return 以OcrPdfMeta对象的名称为键，OcrPdfMeta对象为值的Map集合
     */
    public static Map<String, OcrPdfMeta> toMap(List<OcrPdfMeta> list) {
        Map<String, OcrPdfMeta> map = new HashMap<>();
        if (list != null && !list.isEmpty()) {
            for (OcrPdfMeta meta : list) {
                if (Strings.isNotBlank(meta.getName()) || !map.containsKey(meta.getName())) {
                    map.put(meta.getName(), meta);
                }
            }
        }
        return map;
    }

    /**
     * 将当前对象的规则属性转换为OcrPdfMetaRule对象列表
     *
     * @return 如果规则属性不为空且格式正确，则返回解析后的OcrPdfMetaRule对象列表；否则返回null
     */
    public List<OcrPdfMetaRule> toRules() {
        List<OcrPdfMetaRule> rules = null;
        if (Strings.isNotBlank(this.getRule())) {
            try {
                rules = JSON.parseArray(this.getRule(), OcrPdfMetaRule.class);
                if (rules != null) {
                    rules.sort((o1, o2) -> (int) (o1.getOrder() - o2.getOrder()));
                }
            } catch (Exception e) {
                rules = null;
            }
        }
        return rules;
    }
}
