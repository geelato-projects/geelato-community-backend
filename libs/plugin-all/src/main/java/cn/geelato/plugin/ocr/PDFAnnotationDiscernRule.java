package cn.geelato.plugin.ocr;

/**
 * PDF 注释识别规则。
 *
 * @author geelato
 */
public class PDFAnnotationDiscernRule {

    private String bem;
    private String lem;
    private String rem;
    private int discernWidth;
    private UnionType unionType;

    public String getBem() {
        return bem;
    }

    public void setBem(String bem) {
        this.bem = bem;
    }

    public String getLem() {
        return lem;
    }

    public void setLem(String lem) {
        this.lem = lem;
    }

    public String getRem() {
        return rem;
    }

    public void setRem(String rem) {
        this.rem = rem;
    }

    public int getDiscernWidth() {
        return discernWidth;
    }

    public void setDiscernWidth(int discernWidth) {
        this.discernWidth = discernWidth;
    }

    public UnionType getUnionType() {
        return unionType;
    }

    public void setUnionType(UnionType unionType) {
        this.unionType = unionType;
    }
}
