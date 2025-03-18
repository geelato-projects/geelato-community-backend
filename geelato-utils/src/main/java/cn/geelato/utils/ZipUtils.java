package cn.geelato.utils;

import cn.geelato.utils.entity.FileIS;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    public static void compressFiles(List<FileIS> fileISList, String zipFilePath) throws IOException {
        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        try {
            fos = new FileOutputStream(zipFilePath);
            zos = new ZipOutputStream(fos);
            byte[] buffer = new byte[1024];
            int bytesRead;
            FileIS.repeatFileNameToNewFileName(fileISList);
            for (FileIS fileIs : fileISList) {
                ZipEntry zipEntry = new ZipEntry(fileIs.getFileName());
                zos.putNextEntry(zipEntry);
                while ((bytesRead = fileIs.getInputStream().read(buffer)) != -1) {
                    zos.write(buffer, 0, bytesRead);
                }
                fileIs.getInputStream().close();
                zos.closeEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zos != null) {
                zos.close();
            }
            if (fos != null) {
                fos.close();
            }
        }
    }


    public static void compressDirectory(String sourceFolder, String zipFilePath) {
        try {
            FileOutputStream fos = new FileOutputStream(zipFilePath);
            ZipOutputStream zos = new ZipOutputStream(fos);
            File sourceFile = new File(sourceFolder);
            addDirectoryToZip(zos, sourceFile, "");
            zos.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deCompressPackage(String packagePath, String targetPath) {
        String packageFilePath = packagePath;
        String targetDirectoryPath = targetPath;
        File packageFIle = new File(packageFilePath);
        File targetDirectory = new File(targetDirectoryPath);
        decompressPackage(packageFIle, targetDirectory);
    }

    private static void addDirectoryToZip(ZipOutputStream zos, File sourceFile, String parentDirectoryName) throws IOException {
        File[] files = sourceFile.listFiles();
        byte[] buffer = new byte[1024];
        int bytesRead;
        for (File file : files) {
            if (file.isDirectory()) {
                String directoryName = file.getName();
                addDirectoryToZip(zos, file, parentDirectoryName + "/" + directoryName);
                continue;
            }
            FileInputStream fis = new FileInputStream(file);
            String entryName;
            if (!StringUtils.isEmpty(parentDirectoryName)) {
                entryName = parentDirectoryName + "/" + file.getName();
            } else {
                entryName = file.getName();
            }

            ZipEntry zipEntry = new ZipEntry(entryName);
            zos.putNextEntry(zipEntry);
            while ((bytesRead = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
            fis.close();
            zos.closeEntry();
        }
    }

    private static void decompressPackage(File zipFile, File destDir) {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                File file = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                entry = zis.getNextEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String readPackageData(String packagePath, String fileSuffix) throws IOException {
        String filePath = packagePath;
        String packageData = "";
        InputStream inputStream = null;
        Reader reader = null;
        BufferedReader bufferedReader = null;
        try (ZipFile zipFile = new ZipFile(filePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                String suffix = entryName.substring(entryName.lastIndexOf("."));
                if (fileSuffix.equals(suffix)) {
                    inputStream = zipFile.getInputStream(entry);
                    reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                    bufferedReader = new BufferedReader(reader);
                    String line = "";
                    while ((line = bufferedReader.readLine()) != null) {
                        packageData += line;
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }

        return packageData;
    }

    public static String readPackageData(File file, String fileSuffix) throws IOException {
        String packageData = "";
        InputStream inputStream = null;
        Reader reader = null;
        BufferedReader bufferedReader = null;
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                String suffix = entryName.substring(entryName.lastIndexOf("."));
                if (fileSuffix.equals(suffix)) {
                    inputStream = zipFile.getInputStream(entry);
                    reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                    bufferedReader = new BufferedReader(reader);
                    String line = "";
                    while ((line = bufferedReader.readLine()) != null) {
                        packageData += line;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }

        return packageData;
    }

    /**
     * 从压缩文件中解析指定字段
     *
     * @param file       压缩文件
     * @param fileSuffix 内部文件后缀（如 ".gdp"）
     * @param fields     需要提取的字段名数组
     * @return 包含提取字段的 Map
     * @throws IOException 如果读取或解析失败
     */
    public static Map<String, String> parseGdpFromZip(File file, String fileSuffix, String... fields) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().endsWith(fileSuffix)) {
                    // 将 ZipInputStream 的内容读取到字节数组中
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        byteArrayOutputStream.write(buffer, 0, len);
                    }
                    byte[] jsonData = byteArrayOutputStream.toByteArray();
                    // 使用 JsonParser 解析字节数组
                    JsonFactory jsonFactory = new JsonFactory();
                    try (JsonParser jsonParser = jsonFactory.createParser(jsonData)) {
                        while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                            String fieldName = jsonParser.getCurrentName();
                            if (fieldName != null) {
                                jsonParser.nextToken(); // Move to the value
                                for (String field : fields) {
                                    if (fieldName.equals(field)) {
                                        result.put(field, jsonParser.getValueAsString());
                                        break;
                                    }
                                }
                                jsonParser.skipChildren(); // Skip unwanted fields
                            }
                        }
                    }
                } else {
                    zipInputStream.closeEntry();
                }
            }
        }

        return result;
    }
}
