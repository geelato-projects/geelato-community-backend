package cn.geelato.web.platform.m.base.service;

import cn.geelato.utils.StringUtils;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.Ctx;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.base.entity.Attach;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author diabl
 * @date 2023/7/4 10:48
 */
@Component
public class UploadService {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    public static final String ROOT_DIRECTORY = "upload";
    public static final String ROOT_CONFIG_DIRECTORY = "/upload/config";
    public static final String ROOT_CONVERT_DIRECTORY = "/upload/convert";
    public static final String ROOT_CONFIG_SUFFIX = ".config";

    /**
     * 返回文件上传绝对路径
     *
     * @param subPath
     * @param fileName
     * @param isRename
     * @return
     */
    public static String getSavePath(String subPath, String fileName, boolean isRename) {
        // 处理子路径
        if (Strings.isNotBlank(subPath)) {
            subPath += "/";
        } else {
            subPath = "";
        }

        // 处理日期路径
        Date date = new Date();
        SimpleDateFormat yyyyFt = new SimpleDateFormat("yyyy");
        SimpleDateFormat MMFt = new SimpleDateFormat("MM");
        SimpleDateFormat ddFt = new SimpleDateFormat("dd");
        SimpleDateFormat HHFt = new SimpleDateFormat("HH");
        SimpleDateFormat mmFt = new SimpleDateFormat("mm");
        String datePath = String.format("%s/%s/%s/%s/%s/", yyyyFt.format(date), MMFt.format(date), ddFt.format(date), HHFt.format(date), mmFt.format(date));

        // 处理文件名称
        if (isRename) {
            String ext = UploadService.getFileExtension(fileName);
            fileName = UIDGenerator.generate() + ext;
        }
        // 路径检验
        UploadService.fileMkdirs("/" + subPath + datePath);

        return "/" + subPath + datePath + fileName;
    }

    /**
     * 根目录添加 租户编码、应用id
     *
     * @param subPath
     * @param tenantCode
     * @param appId
     * @param fileName
     * @param isRename
     * @return
     */
    public static String getSavePath(String subPath, String tenantCode, String appId, String fileName, boolean isRename) {
        String rootPath = subPath;
        tenantCode = Strings.isNotBlank(tenantCode) ? tenantCode : Ctx.getCurrentTenantCode();
        if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
            rootPath = String.format("%s/%s/%s", subPath, tenantCode, appId);
        }

        return getSavePath(rootPath, fileName, isRename);
    }

    /**
     * 根目录添加，存放表 默认为attach
     *
     * @param subPath
     * @param tableType
     * @param tenantCode
     * @param appId
     * @param fileName
     * @param isRename
     * @return
     */
    public static String getSavePath(String subPath, String tableType, String tenantCode, String appId, String fileName, boolean isRename) {
        String rootPath = subPath;
        AttachmentSourceEnum sourceEnum = AttachmentSourceEnum.getEnum(tableType);
        if (sourceEnum == null) {
            sourceEnum = AttachmentSourceEnum.PLATFORM_ATTACH;
        }
        rootPath = String.format("%s/%s", subPath, sourceEnum.getValue());

        return getSavePath(rootPath, tenantCode, appId, fileName, isRename);
    }

    public static String getSaveRootPath(String subPath, String fileName, boolean isRename) {
        // 处理子路径
        subPath = subPath.startsWith("/") ? subPath : "/" + subPath;
        subPath = subPath.endsWith("/") ? subPath : "/" + subPath;

        // 处理文件名称
        if (isRename) {
            String ext = UploadService.getFileExtension(fileName);
            fileName = UIDGenerator.generate() + ext;
        }
        // 路径检验
        UploadService.fileMkdirs(subPath);

        return subPath + fileName;
    }

    /**
     * 创建全部路径
     *
     * @param path
     */
    public static void fileMkdirs(String path) {
        if (Strings.isNotBlank(path)) {
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                pathFile.mkdirs();
            }
        }
    }

    /**
     * 文件后缀。例：.xlsx
     *
     * @param fileName 文件名称
     * @return
     */
    public static String getFileExtension(String fileName) {
        if (Strings.isNotBlank(fileName)) {
            int lastIndexOfDot = fileName.lastIndexOf('.');
            if (lastIndexOfDot != -1) {
                return fileName.substring(lastIndexOfDot);
            }
        }

        return "";
    }

    /**
     * 文件后缀
     * @param fileName 文件名称
     * @return 例：xlsx，不包含.
     */
    public static String getFileExtensionWithNoDot(String fileName) {
        if (StringUtils.isNotBlank(fileName)) {
            int lastIndexOfDot = fileName.lastIndexOf('.');
            if (lastIndexOfDot != -1) {
                return fileName.substring(lastIndexOfDot+1);
            }
        }

        return "";
    }

    public static String getFileName(String fileName) {
        if (Strings.isNotBlank(fileName)) {
            int lastIndexOfDot = fileName.lastIndexOf('.');
            if (lastIndexOfDot != -1) {
                return fileName.substring(0, lastIndexOfDot);
            }
        }

        return "";
    }

    /**
     * 文件复制
     *
     * @param file
     * @param fileName 重命名文件名称
     * @return
     */
    public static boolean fileResetName(File file, String fileName) {
        if (file != null && file.exists()) {
            if (Strings.isBlank(fileName)) {
                fileName = String.format("%s_bak_%s%s", UploadService.getFileName(file.getName()), sdf.format(new Date()), UploadService.getFileExtension(file.getName()));
            }
            File newFile = new File(String.format("%s/%s", file.getParent(), fileName));
            if (!newFile.exists()) {
                return file.renameTo(newFile);
            }
        }

        return false;
    }

    /**
     * 文件复制，重命名：name_uuid_bak.extension
     *
     * @param file
     * @return
     */
    public static boolean fileResetName(File file) {
        return UploadService.fileResetName(file, null);
    }

    /**
     * 复制对象
     *
     * @param source
     * @param entity
     * @param <T>
     * @return
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public static <T> T copyProperties(Attach source, Class<T> entity) {
        T object = null;
        try {
            // 尝试获取无参数的构造函数
            Constructor<T> constructor = entity.getDeclaredConstructor();
            // 确保构造函数是可访问的（即，不是私有的）
            constructor.setAccessible(true);
            // 创建一个新的对象实例
            object = constructor.newInstance();
            // 复制值
            BeanUtils.copyProperties(source, object);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            object = null;
        }

        return object;
    }
}
