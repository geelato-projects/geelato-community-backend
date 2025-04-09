package cn.geelato.web.platform.m.site.utils;

import cn.geelato.web.platform.m.site.entity.FileInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.regex.Matcher;

@Slf4j
public class FileSystemTreeBuilder {
    // 主构建方法
    public static FileInfo buildFileSystemTree(String rootPath, int maxDepth) throws IOException {
        Path root = Paths.get(rootPath).normalize().toAbsolutePath();
        FileInfo rootTree = new FileInfo();
        // 设置根文件夹属性
        FolderUtils.setFolderAttributes(root, rootTree);
        // 使用NIO的walkFileTree构建树结构
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root)) { // 跳过根目录，因为已经处理
                    FileInfo parent = findParentFolder(rootTree, dir.getParent());
                    if (parent != null) {
                        FileInfo current = new FileInfo();
                        FolderUtils.setFolderAttributes(dir, current);
                        parent.getFileInfos().add(current);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                FileInfo parent = findParentFolder(rootTree, file.getParent());
                if (parent != null) {
                    FileInfo fileTree = new FileInfo();
                    FolderUtils.setFileAttributes(file, fileTree, attrs);
                    parent.getFileInfos().add(fileTree);
                }
                return FileVisitResult.CONTINUE;
            }

            // 记录访问失败的文件
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.error("无法访问文件: " + file + ", 原因: " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        return rootTree;
    }

    // 查找父文件夹
    private static FileInfo findParentFolder(FileInfo root, Path parentPath) {
        if (parentPath == null || parentPath.toString().equals(root.getPath())) {
            return root;
        }
        String[] parts = parentPath.toString().substring(root.getPath().length()).split(Matcher.quoteReplacement(FileSystems.getDefault().getSeparator()));
        FileInfo current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            boolean found = false;
            for (FileInfo folder : current.getFileInfos()) {
                if (folder.getName().equals(part)) {
                    current = folder;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return null; // 父文件夹不在树中
            }
        }

        return current;
    }
}
