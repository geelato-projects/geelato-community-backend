package cn.geelato.web.platform.m.excel.entity;

import com.aspose.words.FontSettings;
import com.aspose.words.PdfSaveOptions;
import com.itextpdf.text.Document;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.PicturesManager;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
import org.apache.poi.hwpf.usermodel.PictureType;
import org.apache.poi.xwpf.usermodel.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author diabl
 */
@Slf4j
public class OfficeUtils {

    private static final String CHINA_FONT_RESOURCE = "geelato/fonts/simfang.ttf";
    private static final String WORD_DOCX_CONTENT_TYPE = ".DOCX";
    private static final String WORD_DOC_CONTENT_TYPE = ".DOC";
    public static final String OS_UNIX_FONT_FOLDER = "/usr/share/fonts";

    public static void toPdf(String officePath, String pdfPath, String contentType) throws Exception {
        if (WORD_DOCX_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            asposeToPdf(officePath, pdfPath);
        } else if (WORD_DOC_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            asposeToPdf(officePath, pdfPath);
        } else {
            throw new RuntimeException("暂不支持该格式文件转PDF，敬请期待！");
        }
    }

    public static void wordToPdf(String inputPath, String outputPath, String contentType, String printType, int width, int height) throws Exception {
        if (Strings.isNotBlank(contentType)) {
            if (WORD_DOCX_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
                asposeToPdf(inputPath, outputPath);
            } else if (WORD_DOC_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
                Rectangle pageSize = null;
                if ("RM".equalsIgnoreCase(printType) && width > 0 && height > 0) {
                    pageSize = new Rectangle(width * 72f / 25.4f, height * 72f / 25.4f);
                } else if (Strings.isNotBlank(printType)) {
                    Map<String, Rectangle> rectangleMap = getRectangle();
                    if (!rectangleMap.isEmpty()) {
                        for (Map.Entry<String, Rectangle> entry : rectangleMap.entrySet()) {
                            if (entry.getKey().equalsIgnoreCase(printType)) {
                                pageSize = entry.getValue();
                                break;
                            }
                        }
                    }
                }
                docToPdf(inputPath, outputPath, pageSize);
            }
        } else {
            throw new RuntimeException("文件格式错误！");
        }
    }

    private static Map<String, Rectangle> getRectangle() {
        Map<String, Rectangle> pageSizeMap = new HashMap<>();
        Field[] fields = PageSize.class.getDeclaredFields();
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                try {
                    // 获取字段的值，注意这里可能抛出IllegalAccessException
                    // 如果字段不是public的，则需要先设置setAccessible(true)
                    field.setAccessible(true); // 允许访问私有字段
                    Object value = field.get(null); // static字段的实例为null
                    pageSizeMap.put(field.getName(), (Rectangle) value);
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        return pageSizeMap;
    }

    /**
     * 将 docx 文件转换为 pdf 文件
     * <p>
     * 该方法将指定路径的 docx 文件转换为 pdf 文件，并保存到指定路径。
     *
     * @param inputPath  docx 文件的输入路径
     * @param outputPath pdf 文件的输出路径
     * @throws IOException       如果在文件输入输出过程中发生错误，则抛出 IOException
     * @throws DocumentException 如果在文档处理过程中发生错误，则抛出 DocumentException
     */
    public static void docxToPdf(String inputPath, String outputPath) throws IOException, DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // pdf文件的尺寸
        Document pdfDocument = new Document(PageSize.A4);
        PdfWriter pdfWriter = PdfWriter.getInstance(pdfDocument, baos);
        FileInputStream fis = new FileInputStream(inputPath);
        XWPFDocument doc = new XWPFDocument(fis);
        pdfWriter.setInitialLeading(20);
        java.util.List<XWPFParagraph> plist = doc.getParagraphs();
        pdfWriter.open();
        pdfDocument.open();
        for (XWPFParagraph pa : plist) {
            java.util.List<XWPFRun> runs = pa.getRuns();
            for (XWPFRun run : runs) {
                java.util.List<XWPFPicture> piclist = run.getEmbeddedPictures();
                for (XWPFPicture pic : piclist) {
                    XWPFPictureData picdata = pic.getPictureData();
                    byte[] bytepic = picdata.getData();
                    Image imag = Image.getInstance(bytepic);
                    pdfDocument.add(imag);
                }
                // 中文字体的解决
                BaseFont bf = BaseFont.createFont(CHINA_FONT_RESOURCE, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                Font font = new Font(bf, 11.0f, Font.NORMAL, BaseColor.BLACK);
                String text = run.getText(-1);
                byte[] bs;
                if (text != null) {
                    bs = text.getBytes();
                    String str = new String(bs);
                    Chunk chObj1 = new Chunk(str, font);
                    pdfDocument.add(chObj1);
                }
            }
            pdfDocument.add(new Chunk(Chunk.NEWLINE));
        }
        // 需要关闭，不然无法获取到输出流
        pdfDocument.close();
        pdfWriter.close();
        baos.writeTo(new FileOutputStream(outputPath));
        baos.close();
    }

    public static void asposeToPdf(String inputPath, String outputPath) throws Exception {
        com.aspose.words.Document wordDoc = new com.aspose.words.Document(inputPath);
        if (SystemUtils.IS_OS_UNIX || SystemUtils.IS_OS_LINUX) {
            FontSettings.getDefaultInstance().setFontsFolder(OS_UNIX_FONT_FOLDER, true);
        }
        PdfSaveOptions pso = new PdfSaveOptions();
        wordDoc.save(outputPath, pso);
    }

    public static void spireToPdf(String inputPath, String outputPath) {
        //  com.spire.license.LicenseProvider.setLicenseFile("license.elic.xml");
        // 实例化Document类的对象
        //  com.spire.doc.Document doc = new com.spire.doc.Document();
        // 加载Word
        //   doc.loadFromFile(inputPath);
        // 保存为PDF格式
        //   doc.saveToFile(outputPath, FileFormat.PDF);
    }

    /**
     * 将 doc 文件转换为 pdf 文件
     * <p>
     * 该方法将 doc 文件转换为 pdf 文件，并保存到指定的输出路径。
     *
     * @param inputPath  doc 文件的输入路径
     * @param outputPath pdf 文件的输出路径
     * @param pageSize   pdf 页面的尺寸
     * @throws IOException                  如果文件读写过程中发生 I/O 错误
     * @throws DocumentException            如果处理文档时出现异常
     * @throws ParserConfigurationException 如果解析 XML 时发生配置错误
     * @throws TransformerException         如果转换 XML 时发生错误
     */
    public static void docToPdf(String inputPath, String outputPath, Rectangle pageSize) throws IOException, DocumentException, ParserConfigurationException, TransformerException {
        String html = docToHtml(inputPath);
        html = formatHtml(html);
        htmlToPdf(html, outputPath, pageSize);
    }

    /**
     * 将doc文件转换为html格式
     * <p>
     * 该方法接收一个doc文件的路径作为输入，将doc文件内容转换为html格式，并返回转换后的html字符串。
     *
     * @param inputPath doc文件的路径
     * @return 转换后的html字符串
     * @throws IOException                  如果在文件读写过程中发生I/O错误
     * @throws ParserConfigurationException 如果在解析XML或HTML文档时发生配置错误
     * @throws TransformerException         如果在将DOM树转换为输出格式时发生错误
     */
    public static String docToHtml(String inputPath) throws IOException, ParserConfigurationException, TransformerException {
        FileInputStream fis = new FileInputStream(inputPath);
        HWPFDocument wordDocument = new HWPFDocument(fis);
        WordToHtmlConverter wordToHtmlConverter = new WordToHtmlConverter(DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument());
        wordToHtmlConverter.setPicturesManager((content, pictureType, suggestedName, widthInches, heightInches) -> null);
        wordToHtmlConverter.processDocument(wordDocument);
        org.w3c.dom.Document htmlDocument = wordToHtmlConverter.getDocument();
        DOMSource domSource = new DOMSource(htmlDocument);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamResult streamResult = new StreamResult(baos);

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer serializer = tf.newTransformer();
        serializer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty(OutputKeys.METHOD, "html");
        serializer.transform(domSource, streamResult);
        String content = baos.toString(StandardCharsets.UTF_8);
        baos.close();

        return content;
    }

    /**
     * 使用jsoup规范化html
     *
     * @param html html内容
     * @return 规范化后的html
     */
    public static String formatHtml(String html) {
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        // 去除过大的宽度
        String style = doc.attr("style");
        if (Strings.isNotBlank(style) && style.contains("width")) {
            doc.attr("style", "");
        }
        Elements divs = doc.select("div");
        for (org.jsoup.nodes.Element div : divs) {
            String divStyle = div.attr("style");
            if (Strings.isNotEmpty(divStyle) && divStyle.contains("width")) {
                div.attr("style", "");
            }
        }
        // jsoup生成闭合标签
        doc.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        return doc.html();
    }

    /**
     * 将HTML内容转换为PDF文件
     * <p>
     * 将给定的HTML内容转换为PDF文件，并保存到指定的输出路径。
     *
     * @param html       HTML内容字符串
     * @param outputPath 输出PDF文件的路径
     * @param pageSize   PDF页面的大小
     * @throws DocumentException 当文档操作异常时抛出
     * @throws IOException       当I/O操作异常时抛出
     */
    public static void htmlToPdf(String html, String outputPath, Rectangle pageSize) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new com.itextpdf.text.Document(pageSize);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();
        ByteArrayInputStream bais = new ByteArrayInputStream(html.getBytes());
        XMLWorkerHelper.getInstance().parseXHtml(writer, document, bais, StandardCharsets.UTF_8, new FontProvider() {
            @Override
            public boolean isRegistered(String s) {
                return false;
            }

            @Override
            public Font getFont(String s, String s1, boolean embedded, float size, int style, BaseColor baseColor) {
                // 配置字体
                Font font = null;
                try {
                    BaseFont bf = BaseFont.createFont(CHINA_FONT_RESOURCE, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    // BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.EMBEDDED);
                    font = new Font(bf, size, style, baseColor);
                    font.setColor(baseColor);
                } catch (Exception ignored) {
                }
                return font;
            }
        });
        document.close();
        writer.close();
        baos.writeTo(new FileOutputStream(outputPath));
        baos.close();
        bais.close();
    }


}
