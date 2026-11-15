package cn.geelato.web.platform.utils;

/**
 * Office 文件转 PDF 能力接口。
 * <p>默认实现 {@link AsposeOfficeConverter} 依赖 aspose（optional 依赖）：classpath 无 aspose 时
 * 实现不注册，调用方经 ObjectProvider 解析为空须硬失败并提示补依赖
 * （cn.geelato:geelato-web-platform-extras 或 com.aspose:aspose-words/aspose-cells）。</p>
 *
 * @author geelato
 */
public interface OfficeConverter {

    /**
     * 按扩展名分流转换：.doc/.docx 走 word 引擎，.xls/.xlsx 走 excel 引擎。
     *
     * @param officePath  源文件绝对路径
     * @param pdfPath     输出 PDF 绝对路径
     * @param contentType 源文件扩展名（含点，如 ".docx"）
     */
    void toPdf(String officePath, String pdfPath, String contentType) throws Exception;
}
