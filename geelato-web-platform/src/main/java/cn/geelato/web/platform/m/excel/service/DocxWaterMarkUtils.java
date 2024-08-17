package cn.geelato.web.platform.m.excel.service;

import cn.geelato.web.platform.m.excel.entity.WordWaterMarkMeta;
import com.microsoft.schemas.vml.*;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.STTrueFalse;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import javax.xml.namespace.QName;
import java.util.stream.Stream;

/**
 * @author diabl
 * @description: docx文档，水印工具类
 */
public class DocxWaterMarkUtils {

    /**
     * 基础水印
     *
     * @param document
     * @param markText
     */
    public static void setXWPFDocumentWaterMark(XWPFDocument document, String markText) {
        if (document == null || Strings.isBlank(markText)) {
            throw new RuntimeException("XWPFDocument or WaterMarkText is null");
        }
        XWPFParagraph paragraph = document.createParagraph();
        XWPFHeaderFooterPolicy headerFooterPolicy = document.getHeaderFooterPolicy();
        if (headerFooterPolicy == null) {
            headerFooterPolicy = document.createHeaderFooterPolicy();
        }
        headerFooterPolicy.createWatermark(markText);
        XWPFHeader header = headerFooterPolicy.getHeader(XWPFHeaderFooterPolicy.DEFAULT);
        paragraph = header.getParagraphArray(0);
        paragraph.getCTP().newCursor();
        XmlObject[] xmlObjects = paragraph.getCTP().getRArray(0).getPictArray(0).selectChildren(
                new QName("urn:schemas-microsoft-com:vml", "shape"));
        if (xmlObjects.length > 0) {
            CTShape ctshape = (CTShape) xmlObjects[0];
            ctshape.setStyle(getShapeStyle());
        }
    }

    /**
     * 多行，可调样式水印
     *
     * @param document
     * @param markMeta
     */
    public static void setXWPFDocumentWaterMark(XWPFDocument document, WordWaterMarkMeta markMeta) {
        if (markMeta == null) {
            return;
        }
        markMeta.afterSet();
        String markText = markMeta.formatWaterMark();
        if (document == null || Strings.isBlank(markText)) {
            throw new RuntimeException("XWPFDocument or WaterMarkText is null");
        }
        markText = markText + DocxWaterMarkUtils.repeatString(" ", markMeta.getCellSpace()); // 水印文字之间使用8个空格分隔
        markText = DocxWaterMarkUtils.repeatString(markText, 20); // 一行水印重复水印文字次数
        String styleTop = "0pt";  // 与顶部的间距
        // 遍历文档，添加水印
        for (int lineIndex = -10; lineIndex < 20; lineIndex++) {
            styleTop = markMeta.getRowSpace() * lineIndex + "pt";
            DocxWaterMarkUtils.xwpfDocumentWaterMark(document, markText, styleTop, markMeta);
        }
    }

    /**
     * 将指定的字符串重复repeats次.
     *
     * @param pattern 字符串
     * @param repeats 重复次数
     * @return 生成的字符串
     */
    public static String repeatString(String pattern, int repeats) {
        StringBuilder buffer = new StringBuilder(pattern.length() * repeats);
        Stream.generate(() -> pattern).limit(repeats).forEach(buffer::append);
        return new String(buffer);
    }

    /**
     * 为文档添加水印
     *
     * @param document
     * @param markText
     * @param styleTop
     * @param markMeta
     */
    private static void xwpfDocumentWaterMark(XWPFDocument document, String markText, String styleTop, WordWaterMarkMeta markMeta) {
        XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT); // 如果之前已经创建过 DEFAULT 的Header，将会复用之
        int size = header.getParagraphs().size();
        if (size == 0) {
            header.createParagraph();
        }
        CTP ctp = header.getParagraphArray(0).getCTP();
        byte[] rb = document.getDocument().getBody().getPArray(0).getRsidR();
        byte[] rDefault = document.getDocument().getBody().getPArray(0).getRsidRDefault();
        ctp.setRsidP(rb);
        ctp.setRsidRDefault(rDefault);
        CTPPr ppr = ctp.addNewPPr();
        ppr.addNewPStyle().setVal("Header");
        // 开始加水印
        CTR ctr = ctp.addNewR();
        CTRPr ctrpr = ctr.addNewRPr();
        ctrpr.addNewNoProof();
        CTGroup group = CTGroup.Factory.newInstance();
        CTShapetype shapeType = group.addNewShapetype();
        CTTextPath shapeTypeTextPath = shapeType.addNewTextpath();
        shapeTypeTextPath.setOn(STTrueFalse.T);
        shapeTypeTextPath.setFitshape(STTrueFalse.T);
        com.microsoft.schemas.office.office.CTLock lock = shapeType.addNewLock();
        lock.setExt(STExt.VIEW);
        CTShape shape = group.addNewShape();
        shape.setId("PowerPlusWaterMarkObject");
        shape.setSpid("_x0000_s102");
        shape.setType("#_x0000_t136");
        shape.setStyle(getShapeStyle(markText, styleTop, markMeta.getFontSize(), markMeta.getRotationAngle())); // 设置形状样式（旋转，位置，相对路径等参数）
        shape.setFillcolor(markMeta.getFontColor());
        shape.setStroked(STTrueFalse.FALSE); // 字体设置为实心
        CTTextPath shapeTextPath = shape.addNewTextpath(); // 绘制文本的路径
        shapeTextPath.setStyle(String.format("font-family:%s;font-size:%spt;", markMeta.getFontFamily(), markMeta.getFontSize())); // 设置文本字体与大小
        shapeTextPath.setString(markText);
        CTPicture pict = ctr.addNewPict();
        pict.set(group);
    }

    /**
     * 构建Shape的样式参数，开放
     *
     * @param customText
     * @param styleTop
     * @param styleRotation
     * @return
     */
    private static String getShapeStyle(String customText, String styleTop, double fontSize, double styleRotation) {
        StringBuilder sb = new StringBuilder();
        sb.append("position: ").append("absolute"); // 文本path绘制的定位方式
        sb.append(";width: ").append(customText.length() * 10).append("pt"); // 计算文本占用的长度（文本总个数*单字长度）
        sb.append(";height: ").append(fontSize + "pt"); // 字体高度
        sb.append(";font-size: ").append(fontSize + "pt");
        sb.append(";z-index: ").append("-251654144");
        sb.append(";mso-wrap-edited: ").append("f");
        sb.append(";margin-top: ").append(styleTop);
        sb.append(";margin-left: ").append("-50pt");
        sb.append(";mso-position-horizontal-relative: ").append("margin");
        sb.append(";mso-position-vertical-relative: ").append("margin");
        sb.append(";mso-position-vertical: ").append("left");
        sb.append(";mso-position-horizontal: ").append("center");
        sb.append(";rotation: ").append(styleRotation);
        return sb.toString();
    }

    /**
     * 构建Shape的样式参数，固定
     *
     * @return
     */
    private static String getShapeStyle() {
        StringBuilder sb = new StringBuilder();
        sb.append("position: ").append("absolute"); // 文本path绘制的定位方式
        sb.append(";left: ").append("opt");
        sb.append(";width: ").append("500pt"); // 计算文本占用的长度（文本总个数*单字长度）
        sb.append(";height: ").append("150pt"); // 字体高度
        sb.append(";z-index: ").append("-251654144");
        sb.append(";mso-wrap-edited: ").append("f");
        sb.append(";margin-left: ").append("-50pt");
        sb.append(";margin-top: ").append("270pt");
        sb.append(";mso-position-horizontal-relative: ").append("margin");
        sb.append(";mso-position-vertical-relative: ").append("margin");
        sb.append(";mso-width-relative: ").append("page");
        sb.append(";mso-height-relative: ").append("page");
        sb.append(";rotation: ").append("-45");
        return sb.toString();
    }

}
