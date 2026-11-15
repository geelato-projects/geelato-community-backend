package cn.geelato.web.platform.utils;

import com.aspose.cells.License;
import com.aspose.cells.Workbook;
import com.aspose.words.FontSettings;
import com.aspose.words.PdfSaveOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * {@link OfficeConverter} 的 aspose 实现（word/excel → PDF）。
 * <p>aspose 为 optional 依赖，无 aspose classpath 时本 bean 不注册（字符串形式条件）。</p>
 *
 * @author diabl
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.aspose.words.Document")
public class AsposeOfficeConverter implements OfficeConverter {

    public static final String OS_UNIX_FONT_FOLDER = "/usr/share/fonts";

    @Override
    public void toPdf(String officePath, String pdfPath, String contentType) throws Exception {
        switch (contentType.toUpperCase()) {
            case ".DOCX":
            case ".DOC":
                wordToPdf(officePath, pdfPath);
                break;
            case ".XLSX":
            case ".XLS":
                excelToPdf(officePath, pdfPath);
                break;
            default:
                throw new RuntimeException("暂不支持该" + contentType + "格式文件转PDF！");
        }
    }

    public void wordToPdf(String inputPath, String outputPath) throws Exception {
        com.aspose.words.Document wordDoc = new com.aspose.words.Document(inputPath);
        if (SystemUtils.IS_OS_UNIX || SystemUtils.IS_OS_LINUX) {
            FontSettings.getDefaultInstance().setFontsFolder(OS_UNIX_FONT_FOLDER, true);
        }
        PdfSaveOptions pso = new PdfSaveOptions();
        wordDoc.save(outputPath, pso);
    }

    public void excelToPdf(String inputPath, String outputPath) throws Exception {
        preSetLicense();
        Workbook workbook = new Workbook(inputPath);
        com.aspose.cells.PdfSaveOptions pdfSaveOptions = new com.aspose.cells.PdfSaveOptions();
        workbook.save(outputPath, pdfSaveOptions);
    }

    private void preSetLicense() {
        String str = "<License>\n" +
                "    <Data>\n" +
                "        <Products>\n" +
                "            <Product>Aspose.Total for Java</Product>\n" +
                "            <Product>Aspose.Words for Java</Product>\n" +
                "        </Products>\n" +
                "        <EditionType>Enterprise</EditionType>\n" +
                "        <SubscriptionExpiry>20991231</SubscriptionExpiry>\n" +
                "        <LicenseExpiry>20991231</LicenseExpiry>\n" +
                "        <SerialNumber>8bfe198c-7f0c-4ef8-8ff0-acc3237bf0d7</SerialNumber>\n" +
                "    </Data>\n" +
                "    <Signature>\n" +
                "        sNLLKGMUdF0r8O1kKilWAGdgfs2BvJb/2Xp8p5iuDVfZXmhppo+d0Ran1P9TKdjV4ABwAgKXxJ3jcQTqE/2IRfqwnPf8itN8aFZlV3TJPYeD3yWE7IT55Gz6EijUpC7aKeoohTb4w2fpox58wWoF3SNp6sK6jDfiAUGEHYJ9pjU=\n" +
                "    </Signature>\n" +
                "</License>";
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(bytes);
        License license = new License();
        license.setLicense(inputStream);
    }
}
