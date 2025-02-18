package cn.geelato.web.platform.m.ocr.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.plugin.ocr.PDFAnnotationDiscernRule;
import cn.geelato.plugin.ocr.PDFAnnotationMeta;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
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
    @Title(title = "内容处理规则")
    private String rule;
    @Title(title = "位置", description = "pageIndex,x,y,width,height")
    private String position;
    @Title(title = "示例")
    private String example;
    @Title(title = "描述")
    private String description;
    @Title(title = "批注原始序号")
    @Col(name = "annotation_index")
    private Integer annotationIndex;
    @Title(title = "是否可浮动")
    @Col(name = "float_area_y")
    private Boolean floatAreaY;
    @Title(title = "行高")
    @Col(name = "line_height")
    private Integer lineHeight;
    @Title(title = "浮动识别规则")
    @Col(name = "discern_rule")
    private String discernRule;
    @Title(title = "是否未知区域")
    @Col(name = "unknown_area")
    private String unknownArea;

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
     * 将OCR PDF元数据列表转换为PDF注释元数据列表
     *
     * @param list OCR PDF元数据列表
     * @return PDF注释元数据列表
     */
    public static List<PDFAnnotationMeta> toPDFAnnotationMetaList(List<OcrPdfMeta> list) {
        List<PDFAnnotationMeta> pamList = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            for (OcrPdfMeta ocrPdfMeta : list) {
                pamList.add(OcrPdfMeta.toPDFAnnotationMeta(ocrPdfMeta));
            }
        }
        return pamList;
    }

    /**
     * 将OCR PDF元数据转换为PDF注释元数据
     *
     * @param ocrPdfMeta OCR PDF元数据对象
     * @return 转换后的PDF注释元数据对象
     */
    public static PDFAnnotationMeta toPDFAnnotationMeta(OcrPdfMeta ocrPdfMeta) {
        PDFAnnotationMeta pam = new PDFAnnotationMeta();
        pam.setAnnotationIndex(ocrPdfMeta.getAnnotationIndex());
        pam.setTemplateAreaContent(ocrPdfMeta.getExample());
        pam.setContent(ocrPdfMeta.getName());
        pam.setFloatArea(ocrPdfMeta.getFloatAreaY());
        pam.setLineHeight(ocrPdfMeta.getLineHeight() > 0 ? ocrPdfMeta.getLineHeight() : null);
        //  pageIndex,x,y,width,height
        if (Strings.isNotBlank(ocrPdfMeta.getPosition())) {
            String[] positionArr = ocrPdfMeta.getPosition().split(",");
            if (positionArr.length == 5) {
                pam.setPageIndex(positionArr[0] == null ? 0 : Integer.parseInt(positionArr[0]));
                pam.setX(positionArr[1] == null ? 0f : Float.parseFloat(positionArr[1]));
                pam.setY(positionArr[2] == null ? 0f : Float.parseFloat(positionArr[2]));
                pam.setWidth(positionArr[3] == null ? 0f : Float.parseFloat(positionArr[3]));
                pam.setHeight(positionArr[4] == null ? 0f : Float.parseFloat(positionArr[4]));
            }
        }
        // bem,lem,rem,discernWidth,unionType
        PDFAnnotationDiscernRule padRule = null;
        if (Strings.isNotBlank(ocrPdfMeta.getDiscernRule())) {
            ParserConfig.getGlobalInstance().putDeserializer(PDFAnnotationDiscernRule.class, new PDFAnnotationDiscernRuleDeserializer());
            padRule = JSON.parseObject(ocrPdfMeta.getDiscernRule(), PDFAnnotationDiscernRule.class);
        }
        pam.setPdfAnnotationDiscernRule(padRule == null ? new PDFAnnotationDiscernRule() : padRule);
        // 注释属性
        Map<String, Object> annotationAttrs = new HashMap<>();
        if (pam.getFloatArea()) {
            annotationAttrs.put("floatArea", "true");
        }
        if (padRule != null) {
            if (Strings.isNotEmpty(padRule.getBem()) && !padRule.getBem().equals("blank")) {
                annotationAttrs.put("bem", padRule.getBem());
            }
            if (Strings.isNotEmpty(padRule.getLem()) && !padRule.getLem().equals("blank")) {
                annotationAttrs.put("lem", padRule.getLem());
            }
            if (Strings.isNotEmpty(padRule.getRem()) && !padRule.getRem().equals("blank")) {
                annotationAttrs.put("rem", padRule.getRem());
            }
            if (annotationAttrs.size() > 0) {
                annotationAttrs.put("floatArea", "true");
            }
        }
        pam.setAnnotationAttrs(annotationAttrs);

        return pam;
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
