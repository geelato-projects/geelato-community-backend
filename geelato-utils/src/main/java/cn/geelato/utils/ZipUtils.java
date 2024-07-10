package cn.geelato.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

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
                    reader = new InputStreamReader(inputStream, "UTF-8");
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
                    reader = new InputStreamReader(inputStream, "UTF-8");
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
}
