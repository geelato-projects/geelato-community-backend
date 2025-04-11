package cn.geelato.web.platform.m.site.service;

import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.site.entity.StaticSite;
import cn.geelato.web.platform.m.site.utils.FolderUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class StaticSiteService extends BaseService {

    public StaticSite createModel(StaticSite model) {
        // 创建静态站点
        model = super.createModel(model);
        // 创建文件夹
        FolderUtils.create(model.getBaseFolderPath(), model.getId());

        return model;
    }

    public StaticSite updateModel(StaticSite model) {
        // 更新静态站点
        model = super.updateModel(model);
        // 文件夹不存在，创建
        File folder = new File(model.getBaseFolderPath(), model.getId());
        if (!folder.exists()) {
            FolderUtils.create(model.getBaseFolderPath(), model.getId());
        }

        return model;
    }

    public void isDeleteModel(StaticSite model) {
        // 删除文件夹
        FolderUtils.delete(model.getBaseFolderPath(), model.getId());
        // 删除静态站点
        super.isDeleteModel(model);
    }

    public void uploadFile(MultipartFile file, Path rootPath, boolean isCompress, boolean isByStep, int isExist) throws IOException {
        // 2. 校验文件名合法性
        String originalFilename = file.getOriginalFilename();
        if (FolderUtils.containsIllegalChars(originalFilename)) {
            throw new IllegalArgumentException("文件名包含非法字符: " + originalFilename);
        }
        // 1，处理不用解压的文件
        if (!isCompress || !FilenameUtils.isExtension(originalFilename, "zip")) {
            Path targetPath = handleRegularFile(file, rootPath, isExist);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            return;
        }
        // 2，处理需要解压的文件
        String compressExt = FileUtils.getFileExtension(file.getOriginalFilename());
        File tempCompress = Files.createTempFile("site_upload_", compressExt).toFile();
        try {
            file.transferTo(tempCompress);
            processZipFile(tempCompress, rootPath, isByStep, isExist);
        } finally {
            Files.deleteIfExists(tempCompress.toPath());
        }
    }


    private void processZipFile(File zipFile, Path rootPath, boolean isByStep, int isExist) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = buildSafePath(rootPath, entry.getName(), isByStep);
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    entryPath = handleFileConflict(entryPath, isExist);
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = zip.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    // 构建安全路径（包含ZipSlip防护）
    private Path buildSafePath(Path rootPath, String entryName, boolean isByStep) {
        entryName = entryName.replace('\\', '/');
        Path entryPath = isByStep ? rootPath.resolve(entryName).normalize() : rootPath.resolve(Paths.get(entryName).getFileName());
        if (!entryPath.startsWith(rootPath)) {
            throw new SecurityException("非法路径: " + entryName);
        }
        return entryPath;
    }

    private Path handleFileConflict(Path path, int isExist) throws IOException {
        if (Files.exists(path)) {
            if (isExist == 1) {
                Files.deleteIfExists(path);
            } else if (isExist == 2) {
                path = generateUniquePath(path);
            } else {
                throw new FileAlreadyExistsException("文件已存在: " + path.getFileName().toString());
            }
        }
        return path;
    }

    private Path handleRegularFile(MultipartFile file, Path rootPath, int isExist) throws IOException {
        Path targetPath = rootPath.resolve(Objects.requireNonNull(file.getOriginalFilename()));
        return handleFileConflict(targetPath, isExist);
    }

    private Path generateUniquePath(Path originalPath) {
        String baseName = FilenameUtils.getBaseName(originalPath.toString());
        String extension = FilenameUtils.getExtension(originalPath.toString());
        String timestamp = String.valueOf(System.currentTimeMillis());
        return originalPath.getParent().resolve(baseName + "_" + timestamp + (extension.isEmpty() ? "" : "." + extension));
    }
}
