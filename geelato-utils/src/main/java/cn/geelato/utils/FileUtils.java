package cn.geelato.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

public class FileUtils {
    public static final String TEMPORARY_FILE_PREFIX = "_temp_";

    /**
     * 获取文件的扩展名,包括点号（.）
     *
     * @param fileName 文件名
     * @return 文件的扩展名，如果文件名不包含扩展名，则返回空字符串
     */
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
     * 获取文件名（不包含扩展名）
     *
     * @param fileName 包含扩展名的文件名
     * @return 不包含扩展名的文件名，如果fileName为空或不包含扩展名，则返回空字符串
     */
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

    /**
     * 根据输入流创建一个临时文件
     *
     * @param inputStream 输入流
     * @param fileExt     文件的扩展名
     * @return 创建的临时文件
     * @throws IOException 如果发生I/O错误
     */
    public static File createTempFile(InputStream inputStream, String fileExt) throws IOException {
        File tempFile = FileUtils.createTempFile(fileExt);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
        return tempFile;
    }

    /**
     * 根据Base64编码的字符串创建临时文件
     *
     * @param base64String Base64编码的字符串
     * @param fileName     文件名（用于获取文件扩展名）
     * @return 创建的临时文件
     * @throws IOException 如果在创建临时文件或写入文件时发生I/O错误
     */
    public static File createTempFile(String base64String, String fileName) throws IOException {
        // 解码Base64字符串为字节数组
        byte[] decodedBytes = Base64.getDecoder().decode(base64String);
        // 创建临时文件
        String fileExt = FileUtils.getFileExtension(fileName);
        File tempFile = FileUtils.createTempFile(fileExt);
        // 将字节数组写入临时文件
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(decodedBytes);
        }
        return tempFile;
    }

    /**
     * 创建一个临时文件
     * Java虚拟机（JVM）正常终止时自动删除
     *
     * @param fileExt 文件扩展名
     * @return 创建的临时文件
     * @throws IOException 如果在创建临时文件时发生I/O错误
     */
    public static File createTempFile(String fileExt) throws IOException {
        File tempFile = File.createTempFile(TEMPORARY_FILE_PREFIX, fileExt);
        tempFile.deleteOnExit();
        return tempFile;
    }
}
