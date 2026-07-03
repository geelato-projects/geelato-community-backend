package cn.geelato.web.platform.srv.base.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.meta.Attach;
import cn.geelato.web.platform.srv.file.enums.AttachmentSourceEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author diabl
 */
@Component
@Slf4j
public class UploadService {
    public static final String ROOT_CONFIG_SUFFIX = ".config";
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATEVARIETY);

    @Getter
    private static String rootDirectory;
    @Getter
    private static String rootConvertDirectory;
    @Getter
    private static String rootConfigDirectory;

    @Value("${geelato.upload.root-directory}")
    private String tempRootDirectory;

    @Value("${geelato.upload.convert-directory}")
    private String tempRootConvertDirectory;

    @Value("${geelato.upload.config-directory}")
    private String tempRootConfigDirectory;

    @PostConstruct
    public void init() {
        rootDirectory = this.tempRootDirectory;
        rootConvertDirectory = this.tempRootConvertDirectory;
        rootConfigDirectory = this.tempRootConfigDirectory;
    }

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
    public static String getSavePath(String subPath, String fileName, boolean isRename) throws IOException {
        // 参数校验
        if (Strings.isBlank(fileName)) {
            throw new IllegalArgumentException("fileName cannot be blank");
        }
        // 构建基础路径
        Path basePath = Paths.get("/");
        // 添加子路径（如果存在）
        if (Strings.isNotBlank(subPath)) {
            basePath = basePath.resolve(subPath);
        }
        // 添加日期路径
        Path fullDirPath = basePath.resolve(DateUtils.getAttachDatePath());
        // 处理文件名
        String finalFileName = isRename ? UIDGenerator.generate() + FileUtils.getFileExtension(fileName) : fileName;
        // 创建目录
        createDirectories(fullDirPath.toString());
        // 构建最终路径并确保使用Unix风格斜杠
        return fullDirPath.resolve(finalFileName).toString().replace('\\', '/');
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
    public static String getSavePath(String subPath, String tenantCode, String appId, String fileName, boolean isRename) throws IOException {
        // 参数校验
        if (Strings.isBlank(subPath) || Strings.isBlank(fileName)) {
            throw new IllegalArgumentException("subPath and fileName cannot be blank");
        }
        // 构建基础路径
        Path basePath = Paths.get(subPath);
        // 处理租户和应用ID
        tenantCode = Strings.isNotBlank(tenantCode) ? tenantCode : SessionCtx.getCurrentTenantCode();
        if (Strings.isNotBlank(tenantCode) && Strings.isNotBlank(appId)) {
            basePath = basePath.resolve(tenantCode).resolve(appId);
        }
        // 调用单参数版本
        return getSavePath(basePath.toString(), fileName, isRename);
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
    public static String getSavePath(String subPath, String tableType, String tenantCode, String appId, String fileName, boolean isRename) throws IOException {
        // 参数校验
        if (Strings.isBlank(subPath) || Strings.isBlank(fileName)) {
            throw new IllegalArgumentException("subPath and fileName cannot be blank");
        }
        // 获取或默认AttachmentSourceEnum
        AttachmentSourceEnum sourceEnum = AttachmentSourceEnum.getEnum(tableType);
        if (sourceEnum == null) {
            sourceEnum = AttachmentSourceEnum.ATTACH;
        }
        // 构建路径
        Path path = Paths.get(subPath).resolve(sourceEnum.getValue());
        // 调用静默版本
        return getSavePath(path.toString(), tenantCode, appId, fileName, isRename);
    }

    /**
     * 获取根保存路径
     * 该方法用于构造文件的保存路径，基于给定的参数和是否重命名文件的原则
     * 主要用于在特定的目录结构中找到或创建文件的唯一位置
     *
     * @param tableType  表类型，用于区分不同类型的上传
     * @param tenantCode 租户代码，代表文件属于哪个租户，用于隔离不同租户的数据
     * @param appId      应用ID，标识文件是哪个应用上传的，以便按应用分类存储
     * @param fileName   文件名，原始文件名，用于在路径中保留文件的原始名称
     * @param isRename   是否重命名，决定是否对文件进行重命名以避免冲突
     * @return 返回构造的文件保存路径
     * @throws IOException 如果由于I/O错误无法获取保存路径
     */
    public static String getRootSavePath(String tableType, String tenantCode, String appId, String fileName, boolean isRename) throws IOException {
        return getSavePath(UploadService.getRootDirectory(), tableType, tenantCode, appId, fileName, isRename);
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
    public static String getSaveRootPath(String subPath, String fileName, boolean isRename) throws IOException {
        // 参数校验
        if (Strings.isBlank(subPath) || Strings.isBlank(fileName)) {
            throw new IllegalArgumentException("subPath and fileName cannot be blank");
        }
        // 使用Path处理路径标准化
        Path basePath = Paths.get(subPath.startsWith("/") ? subPath : "/" + subPath).normalize();
        // 处理文件名
        String finalFileName = isRename ? UIDGenerator.generate() + FileUtils.getFileExtension(fileName) : fileName;
        // 确保路径以斜杠结尾（用于目录）
        String dirPath = basePath + "/";
        // 创建目录
        createDirectories(dirPath);
        // 拼接最终路径
        return basePath.resolve(finalFileName).toString();
    }

    /**
     * 创建全部路径
     * <p>
     * 根据给定的路径字符串，检查路径是否存在，如果不存在则创建该路径及其所有父目录。
     *
     * @param path 要创建的路径字符串
     */
    public static void createDirectories(String path) throws IOException {
        if (Strings.isNotBlank(path)) {
            Path dirPath = Paths.get(path).normalize();
            try {
                if (!Files.exists(dirPath)) {
                    // 原子性创建所有目录
                    Files.createDirectories(dirPath);
                    log.debug("Created directory: {}", dirPath);
                } else if (!Files.isDirectory(dirPath)) {
                    throw new IOException("Path exists but is not a directory: " + path);
                }
            } catch (IOException e) {
                log.error("Failed to create directory: {}", path, e);
                throw e;
            } catch (SecurityException e) {
                log.error("Permission denied when creating directory: {}", path, e);
                throw e;
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
     */
    public static <T> T copyProperties(Attach source, Class<T> entity) {
        T object;
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
