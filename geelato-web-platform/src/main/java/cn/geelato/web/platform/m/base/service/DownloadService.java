package cn.geelato.web.platform.m.base.service;

import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * @author diabl
 */
@Component
public class DownloadService {


    public File downloadFile(String name, String path) {
        if (Strings.isBlank(path)) {
            return null;
        }

        File file = new File(path);
        if (!file.exists()) {
            return null;
        }

        return file;
    }

    public File copyToFile(File source, String targetName) throws IOException {
        Path sourcePath = source.toPath();
        Path targetPath = sourcePath.getParent().resolve(targetName);
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toFile();
    }

}
