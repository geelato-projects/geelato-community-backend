package cn.geelato.web.platform.m.site.utils;

import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.m.site.entity.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class FolderUtils {
    private static final String[] ILLEGAL_CHARS = {"\\", "/", ":", "*", "?", "\"", "<", ">", "|"};

    public static void create(String rootPath, String folderName) {
        File uploadDir = new File(rootPath, folderName);
        try {
            // 创建文件夹（如果不存在）
            if (!uploadDir.exists()) {
                FileUtils.forceMkdir(uploadDir);
                // 设置文件夹权限（Linux/Unix系统）
                if (isUnixLike()) {
                    // 设置Unix/Linux特有的权限
                    uploadDir.setReadable(true, false);
                    uploadDir.setWritable(true, false);
                    uploadDir.setExecutable(false, false);
                } else {
                    // Windows系统下的处理
                    uploadDir.setReadable(true);
                    uploadDir.setWritable(true);
                    uploadDir.setExecutable(false);
                }
            } else {
                throw new RuntimeException("文件夹已存在");
            }
        } catch (IOException e) {
            log.error("文件夹创建失败: " + e.getMessage());
            throw new RuntimeException("文件夹创建失败: " + e.getMessage());
        } catch (SecurityException e) {
            log.error("权限不足，无法创建文件夹: " + e.getMessage());
            throw new RuntimeException("权限不足，无法创建文件夹: " + e.getMessage());
        }
    }

    public static void update(String rootPath, String sourceFolderName, String destFolderName) {
        File sourceDir = new File(rootPath, sourceFolderName);
        File destDir = new File(rootPath, destFolderName);

        try {
            // 检查源文件夹是否存在
            if (!sourceDir.exists()) {
                throw new RuntimeException("源文件夹不存在");
            }
            // 检查目标文件夹是否已存在
            if (destDir.exists()) {
                throw new RuntimeException("目标文件夹已存在");
            }
            // 重命名/移动文件夹
            FileUtils.moveDirectory(sourceDir, destDir);
        } catch (IOException e) {
            log.error("移动文件夹失败: " + e.getMessage());
            throw new RuntimeException("移动文件夹失败: " + e.getMessage());
            // 记录详细日志
        } catch (SecurityException e) {
            log.error("权限不足，无法移动文件夹: " + e.getMessage());
            throw new RuntimeException("权限不足，无法移动文件夹: " + e.getMessage());
        }
    }

    public static void delete(String rootPath, String folderName) {
        File dirToDelete = new File(rootPath, folderName);
        delete(dirToDelete);
    }

    public static void delete(File dirToDelete) {
        try {
            // 检查文件夹是否存在
            if (!dirToDelete.exists()) {
                throw new RuntimeException("文件夹/文件不存在");
            }
            // 检查文件夹是否有读写权限
            if (!dirToDelete.canRead() || !dirToDelete.canWrite()) {
                throw new SecurityException("权限不足");
            }
            // 删除文件夹及其内容
            if (Files.isDirectory(dirToDelete.toPath())) {
                FileUtils.deleteDirectory(dirToDelete);
            } else {
                FileUtils.delete(dirToDelete);
            }
        } catch (IOException e) {
            log.error("删除文件夹/文件失败: " + e.getMessage());
            throw new RuntimeException("删除文件夹/文件失败: " + e.getMessage());
        } catch (SecurityException e) {
            log.error("权限不足，无法删除文件夹/文件: " + e.getMessage());
            throw new RuntimeException("权限不足，无法删除文件夹/文件: " + e.getMessage());
        }
    }

    public static Set<FileInfo> getRootFolders(String rootPath) {
        Set<FileInfo> folders = new LinkedHashSet<>();
        Path root = Paths.get(rootPath);
        // 检查根目录是否可访问
        validateRootDirectory(root);
        // 获取文件夹列表
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    folders.add(setFolderAttributes(entry));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("访问根目录时出错: " + e.getMessage());
        } catch (SecurityException e) {
            throw new RuntimeException("没有足够的权限: " + e.getMessage());
        }

        return folders;
    }

    public static Set<FileInfo> getRootFiles(String rootPath, String type) {
        Set<FileInfo> files = new LinkedHashSet<>();
        Path root = Paths.get(rootPath);
        // 检查根目录是否可访问
        validateRootDirectory(root);
        // 获取文件夹列表
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    if (List.of("all", "folder").contains(type)) {
                        files.add(setFolderAttributes(entry));
                    }
                } else {
                    if (List.of("all", "file").contains(type)) {
                        files.add(setFileAttributes(entry, Files.readAttributes(entry, BasicFileAttributes.class)));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("访问根目录时出错: " + e.getMessage());
        } catch (SecurityException e) {
            throw new RuntimeException("没有足够的权限: " + e.getMessage());
        }

        return files;
    }

    public static void renameFileOrFolder(String path, String newName) {
        // 路径安全校验
        if (newName.contains("..") || newName.contains("/") || newName.contains("\\")) {
            throw new RuntimeException("新名称包含非法字符");
        }
        // 获取原文件/文件夹和新路径
        Path sourcePath = Paths.get(path);
        Path targetPath = sourcePath.resolveSibling(newName);
        try {
            // 检查源路径是否存在
            if (!Files.exists(sourcePath)) {
                throw new RuntimeException("指定的路径不存在: " + path);
            }
            // 检查目标路径是否已存在
            if (Files.exists(targetPath)) {
                throw new RuntimeException("目标名称已存在: " + newName);
            }
            // 执行重命名操作
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("重命名失败: " + e.getMessage(), e);
        } catch (SecurityException e) {
            throw new RuntimeException("没有足够的权限执行此操作", e);
        }
    }

    private static void validateRootDirectory(Path root) {
        if (!Files.exists(root)) {
            throw new RuntimeException("根路径不存在");
        }
        if (!Files.isDirectory(root)) {
            throw new RuntimeException("根路径不是文件夹");
        }
        if (!Files.isReadable(root)) {
            throw new RuntimeException("没有读取根目录的权限");
        }
    }

    public static boolean hasNoSubFolders(Path folder) throws IOException {
        return Files.list(folder).noneMatch(Files::isDirectory);
    }

    public static boolean isUnixLike() {
        return SystemUtils.IS_OS_UNIX || SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX;
    }

    public static FileInfo setFolderAttributes(Path folderPath) throws IOException {
        FileInfo fileInfo = new FileInfo();
        setFolderAttributes(folderPath, fileInfo);
        return fileInfo;
    }

    public static void setFolderAttributes(Path folderPath, FileInfo fileInfo) throws IOException {
        fileInfo.setPath(folderPath.toString());
        fileInfo.setName(folderPath.getFileName().toString());
        // 设置权限
        fileInfo.setDirectory(true);
        fileInfo.setLastModified(setLastModified(Files.getLastModifiedTime(folderPath)));
        fileInfo.setHidden(Files.isHidden(folderPath));
        fileInfo.setCanRead(Files.isReadable(folderPath));
        fileInfo.setCanWrite(Files.isWritable(folderPath));
        fileInfo.setCanExecute(Files.isExecutable(folderPath));
        // 初始化集合
        if (fileInfo.getFileInfos() == null) {
            fileInfo.setFileInfos(new LinkedHashSet<>());
        }
    }

    // 设置文件属性
    public static FileInfo setFileAttributes(Path filePath, BasicFileAttributes attrs) throws IOException {
        FileInfo fileInfo = new FileInfo();
        setFileAttributes(filePath, fileInfo, attrs);
        return fileInfo;
    }

    public static void setFileAttributes(Path filePath, FileInfo fileInfo, BasicFileAttributes attrs) throws IOException {
        fileInfo.setPath(filePath.toString());
        fileInfo.setName(filePath.getFileName().toString());
        fileInfo.setFileType(Files.probeContentType(filePath));
        fileInfo.setFileSize(attrs.size());
        // 设置权限
        fileInfo.setDirectory(false);
        fileInfo.setLastModified(setLastModified(attrs.lastModifiedTime()));
        fileInfo.setHidden(Files.isHidden(filePath));
        fileInfo.setCanRead(Files.isReadable(filePath));
        fileInfo.setCanWrite(Files.isWritable(filePath));
        fileInfo.setCanExecute(Files.isExecutable(filePath));
        fileInfo.setFileInfos(new LinkedHashSet<>());
    }

    public static boolean containsIllegalChars(String filename) {
        for (String illegalChar : ILLEGAL_CHARS) {
            if (filename.contains(illegalChar)) {
                return true;
            }
        }
        return false;
    }

    private static String setLastModified(FileTime lastModifiedTime) {
        // 转换为Instant
        Instant instant = lastModifiedTime.toInstant();
        // 转换为本地时区
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
        // 定义格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateUtils.DATETIME);
        // 格式化输出
        return zonedDateTime.format(formatter);
    }
}
