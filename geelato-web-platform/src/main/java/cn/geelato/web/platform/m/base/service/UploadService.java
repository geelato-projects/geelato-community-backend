package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.m.file.entity.Attach;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author diabl
 */
@Component
public class UploadService {
    public static final String ROOT_DIRECTORY = "upload";
    public static final String ROOT_CONVERT_DIRECTORY = "upload/convert";
    public static final String ROOT_CONFIG_DIRECTORY = "/upload/config";
    public static final String ROOT_AVATAR_DIRECTORY = "upload/avatar";
    public static final String ROOT_CONFIG_SUFFIX = ".config";
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATEVARIETY);

    /**
     * 返回文件上传的绝对路径
     * <p>
     * 根据提供的子路径、文件名和是否重命名的标志，生成文件上传的绝对路径。
     *
     * @param subPath  子路径，用于指定文件上传的具体目录
     * @param fileName 文件名，表示要上传的文件的名称
     * @param isRename 是否重命名文件，如果为true，则会对文件名进行重命名处理
     * @return 返回文件上传的绝对路径字符串
     */
    public static String getSavePath(String subPath, String fileName, boolean isRename) {
        // 处理子路径
        if (Strings.isNotBlank(subPath)) {
            subPath += "/";
        } else {
            subPath = "";
        }

        // 处理日期路径
        String datePath = DateUtils.getAttachDatePath();

        // 处理文件名称
        if (isRename) {
            String ext = FileUtils.getFileExtension(fileName);
            fileName = UIDGenerator.generate() + ext;
        }
        // 路径检验
        UploadService.fileMkdirs("/" + subPath + datePath);

        return "/" + subPath + datePath + fileName;
    }

    /**
     * 根据子路径、租户编码、应用ID、文件名和是否重命名生成保存路径
     * <p>
     * 该方法根据提供的子路径、租户编码、应用ID、文件名和是否重命名的标志，生成文件的保存路径。
     * 如果租户编码或应用ID为空，则使用当前会话的租户编码或默认路径。
     *
     * @param subPath    子路径，用于指定保存文件的目录
     * @param tenantCode 租户编码，用于区分不同租户的文件
     * @param appId      应用ID，用于进一步区分文件
     * @param fileName   文件名，要保存的文件的名称
     * @param isRename   是否重命名文件，如果为true，则对文件名进行处理以避免重名
     * @return 返回生成的保存路径字符串
     */
    public static String getSavePath(String subPath, String tenantCode, String appId, String fileName, boolean isRename) {
        String rootPath = subPath;
        tenantCode = Strings.isNotBlank(tenantCode) ? tenantCode : SessionCtx.getCurrentTenantCode();
        if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
            rootPath = String.format("%s/%s/%s", subPath, tenantCode, appId);
        }

        return getSavePath(rootPath, fileName, isRename);
    }

    /**
     * 获取文件保存路径
     * <p>
     * 根据给定的子路径、表类型、租户代码、应用ID、文件名和是否重命名标志，生成文件的保存路径。
     *
     * @param subPath    子路径，用于指定文件保存的相对路径
     * @param tableType  表类型，用于指定文件的来源或类型
     * @param tenantCode 租户代码，用于区分不同租户的文件
     * @param appId      应用ID，用于进一步区分同一租户下的不同应用文件
     * @param fileName   文件名，要保存的文件名称
     * @param isRename   是否重命名，如果为true，则文件名可能会根据规则进行重命名
     * @return 返回生成的文件保存路径字符串
     */
    public static String getSavePath(String subPath, String tableType, String tenantCode, String appId, String fileName, boolean isRename) {
        String rootPath = subPath;
        AttachmentSourceEnum sourceEnum = AttachmentSourceEnum.getEnum(tableType);
        if (sourceEnum == null) {
            sourceEnum = AttachmentSourceEnum.ATTACH;
        }
        rootPath = String.format("%s/%s", subPath, sourceEnum.getValue());

        return getSavePath(rootPath, tenantCode, appId, fileName, isRename);
    }

    /**
     * 获取文件保存路径
     *
     * @param root       根目录路径
     * @param tableType  表类型
     * @param name       文件名
     * @param isRename   是否需要重命名
     * @param appId      应用ID
     * @param tenantCode 租户代码
     * @return 文件保存路径
     */
    public static String getSavePath(String root, String tableType, String name, boolean isRename, String appId, String tenantCode) {
        if (Strings.isNotBlank(root)) {
            return UploadService.getSaveRootPath(root, name, isRename);
        } else {
            return UploadService.getSavePath(UploadService.ROOT_DIRECTORY, tableType, tenantCode, appId, name, isRename);
        }
    }

    /**
     * 获取保存文件的根路径
     * <p>
     * 根据提供的子路径、文件名和是否重命名标志，构造并返回文件的完整保存路径。
     *
     * @param subPath  子路径，用于指定文件保存的目录
     * @param fileName 文件名，不包含路径
     * @param isRename 是否重命名文件，如果为true，则文件名将被替换为生成的唯一标识符
     * @return 返回文件的完整保存路径
     */
    public static String getSaveRootPath(String subPath, String fileName, boolean isRename) {
        // 处理子路径
        subPath = subPath.startsWith("/") ? subPath : "/" + subPath;
        subPath = subPath.endsWith("/") ? subPath : "/" + subPath;

        // 处理文件名称
        if (isRename) {
            String ext = FileUtils.getFileExtension(fileName);
            fileName = UIDGenerator.generate() + ext;
        }
        // 路径检验
        UploadService.fileMkdirs(subPath);

        return subPath + fileName;
    }

    /**
     * 创建全部路径
     * <p>
     * 根据给定的路径字符串，检查路径是否存在，如果不存在则创建该路径及其所有父目录。
     *
     * @param path 要创建的路径字符串
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
     * 文件重命名并复制
     * <p>
     * 将指定的文件重命名为新的名称，并复制到原文件所在的目录下。
     *
     * @param file     要重命名并复制的文件对象
     * @param fileName 新的文件名称，如果为空则自动生成一个包含当前日期时间的备份文件名
     * @return 如果文件重命名并复制成功返回true，否则返回false
     */
    public static boolean fileResetName(File file, String fileName) {
        if (file != null && file.exists()) {
            if (Strings.isBlank(fileName)) {
                fileName = String.format("%s_bak_%s%s", FileUtils.getFileName(file.getName()), sdf.format(new Date()), FileUtils.getFileExtension(file.getName()));
            }
            File newFile = new File(String.format("%s/%s", file.getParent(), fileName));
            if (!newFile.exists()) {
                return file.renameTo(newFile);
            }
        }

        return false;
    }

    /**
     * 文件复制并重命名
     * <p>
     * 将给定的文件复制并重命名为 "name_uuid_bak.extension" 格式的新文件名。
     *
     * @param file 要复制并重命名的文件对象
     * @return 如果文件复制并重命名成功，则返回true；否则返回false
     */
    public static boolean fileResetName(File file) {
        return UploadService.fileResetName(file, null);
    }

    /**
     * 复制对象属性
     * <p>
     * 将源对象的属性复制到目标对象的实例中。
     *
     * @param source 源对象，即要复制属性的对象
     * @param entity 目标对象的Class类型，用于创建目标对象实例
     * @param <T>    目标对象的类型
     * @return 返回目标对象的实例，如果复制失败则返回null
     * @throws NoSuchMethodException     如果目标对象的类没有无参数的构造函数，则抛出此异常
     * @throws InvocationTargetException 如果目标对象的构造函数或初始化代码块在执行时抛出异常，则抛出此异常
     * @throws InstantiationException    如果目标对象的类表示一个抽象类、接口、数组类、基本类型或void，或者如果无法实例化目标对象，则抛出此异常
     * @throws IllegalAccessException    如果目标对象的构造函数或初始化代码块不可访问，则抛出此异常
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
