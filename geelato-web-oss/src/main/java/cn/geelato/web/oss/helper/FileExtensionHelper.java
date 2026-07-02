package cn.geelato.web.oss.helper;

public class FileExtensionHelper {
    public static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "UnknownExtension";
    }
}
