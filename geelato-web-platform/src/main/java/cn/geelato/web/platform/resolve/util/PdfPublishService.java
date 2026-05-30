package cn.geelato.web.platform.resolve.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@Slf4j
public class PdfPublishService {

    @Value("${geelato.ocr.pdf.base-url:http://47.121.135.61}")
    private String pdfBaseUrl;

    @Value("${geelato.ocr.pdf.save-path:/ocrpdf}")
    private String pdfSavePath;

    public PublishedFile publish(File file, String originalFilename) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("file not found");
        }
        if (Strings.isBlank(originalFilename)) {
            originalFilename = "upload_" + System.currentTimeMillis() + ".pdf";
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "_" + originalFilename;
        File targetDir = new File(pdfSavePath);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        Path targetPath = targetDir.toPath().resolve(fileName);
        Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        PublishedFile publishedFile = new PublishedFile();
        publishedFile.setFile(targetPath.toFile());
        publishedFile.setFileName(fileName);
        publishedFile.setUrl(constructUrl(fileName));
        return publishedFile;
    }

    public String constructUrl(String fileName) {
        return pdfBaseUrl + "/" + fileName;
    }
}

