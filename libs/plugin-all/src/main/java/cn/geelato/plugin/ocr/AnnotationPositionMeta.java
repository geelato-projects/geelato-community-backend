package cn.geelato.plugin.ocr;

/**
 * PDF 注释位置元数据。
 *
 * @author geelato
 */
public class AnnotationPositionMeta extends AnnotationSizeMeta {

    private Integer annotationIndex;
    private float x;
    private float y;
    private Integer pageIndex;

    public Integer getAnnotationIndex() {
        return annotationIndex;
    }

    public void setAnnotationIndex(Integer annotationIndex) {
        this.annotationIndex = annotationIndex;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public Integer getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(Integer pageIndex) {
        this.pageIndex = pageIndex;
    }
}
