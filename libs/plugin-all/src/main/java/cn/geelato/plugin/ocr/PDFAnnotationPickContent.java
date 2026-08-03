package cn.geelato.plugin.ocr;

/**
 * PDF 注释提取内容。
 *
 * @author geelato
 */
public class PDFAnnotationPickContent extends AnnotationPositionMeta {

    private String annotationAreaContent;
    private String instanceAreaContent;

    /**
     * 批量设置位置与尺寸元数据。
     *
     * @param annotationIndex 注释序号
     * @param x               x 坐标
     * @param y               y 坐标
     * @param width           宽度
     * @param height          高度
     */
    public void setMetaData(int annotationIndex, float x, float y, float width, float height) {
        setAnnotationIndex(annotationIndex);
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
    }

    public String getAnnotationAreaContent() {
        return annotationAreaContent;
    }

    public void setAnnotationAreaContent(String annotationAreaContent) {
        this.annotationAreaContent = annotationAreaContent;
    }

    public String getInstanceAreaContent() {
        return instanceAreaContent;
    }

    public void setInstanceAreaContent(String instanceAreaContent) {
        this.instanceAreaContent = instanceAreaContent;
    }
}
