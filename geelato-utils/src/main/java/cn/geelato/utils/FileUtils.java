package cn.geelato.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileUtils {

    public static String getFileExtension(String fileName) {
        if (StringUtils.isNotBlank(fileName)) {
            int lastIndexOfDot = fileName.lastIndexOf('.');
            if (lastIndexOfDot != -1) {
                return fileName.substring(lastIndexOfDot);
            }
        }

        return "";
    }

    /**
     * 获取文件后缀名，但不包括点号（.）
     * 根据传入的文件名称，返回文件的后缀名部分，但不包括点号（.）。
     *
     * @param fileName 文件名称
     * @return 返回文件的后缀名，例如"xlsx"，如果不包含后缀名则返回空字符串
     */
    public static String getFileExtensionWithNoDot(String fileName) {
        if (StringUtils.isNotBlank(fileName)) {
            int lastIndexOfDot = fileName.lastIndexOf('.');
            if (lastIndexOfDot != -1) {
                return fileName.substring(lastIndexOfDot + 1);
            }
        }

        return "";
    }

    /**
     * 将源文件复制到目标路径，如果目标文件已存在则覆盖。
     *
     * @param source     源文件
     * @param targetName 目标文件名（包含路径）
     * @return 复制后生成的目标文件
     * @throws IOException 如果在复制文件过程中发生错误
     */
    public static File copyToFile(File source, String targetName) throws IOException {
        Path sourcePath = source.toPath();
        Path targetPath = sourcePath.getParent().resolve(targetName);
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toFile();
    }

    public static String getFileName(String fileName) {
        if (StringUtils.isNotBlank(fileName)) {
            int lastIndexOfDot = fileName.lastIndexOf('.');
            if (lastIndexOfDot != -1) {
                return fileName.substring(0, lastIndexOfDot);
            }
        }

        return "";
    }

    /**
     * 根据给定的路径字符串，将路径转换为File对象。
     *
     * @param path 路径字符串
     * @return 如果路径有效且文件存在，则返回对应的File对象；如果路径为空或文件不存在，则返回null。
     */
    public static File pathToFile(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        return file;
    }
}
