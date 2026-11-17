package cn.geelato.web.platform.srv.site.service;

import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * RAR / 7z 解压实现，从 {@link StaticSiteService} 中拆出：
 * <ul>
 *     <li>sevenzipjbinding 在 geelato-web-platform 里是 {@code <optional>true</optional>} 依赖，
 *         不会向下传递到宿主工程；本类只有在真正需要解压 rar/7z 时才会被类加载器加载。</li>
 *     <li>StaticSiteService 的类文件不再引用任何 sevenzipjbinding 类型，Spring 反射装配 staticSiteService
 *         Bean 时不会再触发 NoClassDefFoundError；缺少该 jar 的宿主（例如纯业务后端）也能正常启动。</li>
 *     <li>所有来自 sevenzipjbinding 的 checked 异常都在本类内部包成运行时异常
 *         {@link ArchiveExtractionException}，调用方只需处理 IOException / IllegalStateException。</li>
 * </ul>
 */
final class SevenZipProcessor {

    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized = false;

    private SevenZipProcessor() {
    }

    static void extractRar(File archive, Path rootPath, boolean isByStep, int isExist) throws IOException {
        extract(archive, rootPath, isByStep, isExist);
    }

    static void extract7z(File archive, Path rootPath, boolean isByStep, int isExist) throws IOException {
        extract(archive, rootPath, isByStep, isExist);
    }

    private static void extract(File archiveFile, Path rootPath, boolean isByStep, int isExist) throws IOException {
        ensureInitialized();
        try (IInArchive archive = SevenZip.openInArchive(null, (IInStream) new RandomAccessFile(archiveFile, "r"))) {
            int[] indexes = new int[archive.getNumberOfItems()];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = i;
            }
            archive.extract(indexes, false, new ExtractCallback(archive, rootPath, isByStep, isExist));
        } catch (SevenZipException e) {
            throw new ArchiveExtractionException("7z/rar 解压失败: " + archiveFile, e);
        }
    }

    /** 首次调用时把 native 库解压到进程独占的临时目录，避免多 JVM 抢占同一 DLL；Mac 由系统直接加载 dylib。 */
    private static void ensureInitialized() {
        if (isMacOs() || initialized) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }
            try {
                String appName = System.getProperty("spring.application.name", "geelato-site")
                        .replaceAll("[^a-zA-Z0-9._-]", "_");
                String runtimeName = java.lang.management.ManagementFactory.getRuntimeMXBean()
                        .getName()
                        .replace('@', '-')
                        .replaceAll("[^a-zA-Z0-9._-]", "_");
                Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "sevenzipjbinding", appName + "-" + runtimeName);
                Files.createDirectories(tmpDir);
                SevenZip.initSevenZipFromPlatformJAR(tmpDir.toFile());
                initialized = true;
            } catch (IOException | SevenZipNativeInitializationException e) {
                throw new IllegalStateException("初始化 7-Zip 库失败", e);
            }
        }
    }

    private static boolean isMacOs() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("mac");
    }

    /** 静默关闭输入流，工具方法。 */
    static void closeQuietly(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    /**
     * 解压回调：接口方法声明的 SevenZipException 通过覆盖时不再 throws 的方式屏蔽，
     * 所有异常一律包成 {@link ArchiveExtractionException} 抛出。
     */
    private static final class ExtractCallback implements IArchiveExtractCallback {
        private final IInArchive archive;
        private final Path rootPath;
        private final boolean isByStep;
        private final int isExist;
        private Path currentPath;

        ExtractCallback(IInArchive archive, Path rootPath, boolean isByStep, int isExist) {
            this.archive = archive;
            this.rootPath = rootPath;
            this.isByStep = isByStep;
            this.isExist = isExist;
        }

        @Override
        public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) {
            try {
                String itemPath = archive.getStringProperty(index, PropID.PATH);
                boolean isFolder = (Boolean) archive.getProperty(index, PropID.IS_FOLDER);
                currentPath = StaticSiteService.buildSafePath(rootPath, itemPath, isByStep);
                if (isFolder) {
                    Files.createDirectories(currentPath);
                    return null;
                }
                currentPath = StaticSiteService.handleFileConflict(currentPath, isExist);
                Files.createDirectories(currentPath.getParent());
                final Path outputFile = currentPath;
                return data -> {
                    try (OutputStream os = new FileOutputStream(outputFile.toFile(), true)) {
                        os.write(data);
                    } catch (IOException e) {
                        throw new ArchiveExtractionException("写入文件失败: " + outputFile, e);
                    }
                    return data.length;
                };
            } catch (IOException e) {
                // SevenZipException 继承自 IOException，单一 catch 已能捕获两类异常
                throw new ArchiveExtractionException("处理归档条目失败", e);
            }
        }

        @Override
        public void prepareOperation(ExtractAskMode extractAskMode) {
            // no-op
        }

        @Override
        public void setOperationResult(ExtractOperationResult extractOperationResult) {
            if (extractOperationResult != ExtractOperationResult.OK) {
                throw new ArchiveExtractionException("解压失败: " + extractOperationResult);
            }
        }

        @Override
        public void setCompleted(long complete) {
            // no-op
        }

        @Override
        public void setTotal(long total) {
            // no-op
        }
    }

    /** 统一的运行时异常包装类，屏蔽 sevenzipjbinding 的 checked exception 类型，避免上层感知。 */
    static class ArchiveExtractionException extends RuntimeException {
        ArchiveExtractionException(String message, Throwable cause) {
            super(message, cause);
        }

        ArchiveExtractionException(String message) {
            super(message);
        }
    }
}
