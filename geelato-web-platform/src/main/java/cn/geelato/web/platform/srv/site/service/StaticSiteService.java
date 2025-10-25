package cn.geelato.web.platform.srv.site.service;

import cn.geelato.web.platform.srv.base.service.BaseService;
import cn.geelato.web.platform.srv.site.entity.StaticSite;
import cn.geelato.web.platform.srv.site.utils.FolderUtils;
import net.sf.sevenzipjbinding.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class StaticSiteService extends BaseService {
    private static final String[] allowedExtensions = new String[]{"zip", "rar", "tar", "7z", "gz", "bz2", "xz", "tar.gz", "tar.bz2", "tar.xz"};

    // 添加静态初始化块加载7z库
    static {
        try {
            SevenZip.initSevenZipFromPlatformJAR();
        } catch (SevenZipNativeInitializationException e) {
            throw new RuntimeException("初始化7-Zip库失败", e);
        }
    }

    public StaticSite createModel(StaticSite model, String baseFolderPath) {
        model = super.createModel(model);
        // 创建文件夹
        FolderUtils.create(baseFolderPath, model.getId());

        return model;
    }

    public StaticSite updateModel(StaticSite model, String baseFolderPath) {
        // 更新静态站点
        model = super.updateModel(model);
        // 文件夹不存在，创建
        File folder = new File(baseFolderPath, model.getId());
        if (!folder.exists()) {
            FolderUtils.create(baseFolderPath, model.getId());
        }

        return model;
    }

    public void isDeleteModel(StaticSite model, String baseFolderPath) {
        // 删除文件夹
        FolderUtils.delete(baseFolderPath, model.getId());
        // 删除静态站点
        super.isDeleteModel(model);
    }

    public void uploadFile(MultipartFile file, Path rootPath, boolean isCompress, boolean isByStep, int isExist) throws IOException {
        // 2. 校验文件名合法性
        String originalFilename = file.getOriginalFilename();
        if (FolderUtils.containsIllegalChars(originalFilename)) {
            throw new IllegalArgumentException("文件名包含非法字符: " + originalFilename);
        }
        // 获取文件扩展名（小写）
        String fileExt = Objects.requireNonNull(FilenameUtils.getExtension(originalFilename)).toLowerCase();
        // 1，处理不用解压的文件
        if (!isCompress || !Arrays.asList(allowedExtensions).contains(fileExt)) {
            Path targetPath = handleRegularFile(file, rootPath, isExist);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            return;
        }
        // 2，处理需要解压的文件
        File tempCompress = Files.createTempFile("site_upload_", "." + fileExt).toFile();
        try {
            file.transferTo(tempCompress);
            processCompressedFile(tempCompress, rootPath, isByStep, isExist, fileExt);
        } finally {
            Files.deleteIfExists(tempCompress.toPath());
        }
    }

    private void processCompressedFile(File compressedFile, Path rootPath, boolean isByStep, int isExist, String fileExt) throws IOException {
        switch (fileExt) {
            case "zip":
                processZipFile(compressedFile, rootPath, isByStep, isExist);
                break;
            case "tar":
            case "tar.gz":
            case "tar.bz2":
            case "tar.xz":
                processTarFile(compressedFile, rootPath, isByStep, isExist);
                break;
            case "gz":
            case "bz2":
            case "xz":
                processSingleCompressedFile(compressedFile, rootPath, isExist);
                break;
            case "rar":
                processRarFile(compressedFile, rootPath, isByStep, isExist);
                break;
            case "7z":
                process7zFile(compressedFile, rootPath, isByStep, isExist);
                break;
            default:
                throw new IllegalArgumentException("未知的压缩格式: " + fileExt);
        }
    }

    // 处理ZIP格式的压缩文件
    private void processZipFile(File zipFile, Path rootPath, boolean isByStep, int isExist) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = buildSafePath(rootPath, entry.getName(), isByStep);
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    entryPath = handleFileConflict(entryPath, isExist);
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = zip.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    // 处理TAR格式的压缩文件 .tar/.tar.gz/.tar.bz2/.tar.xz
    private void processTarFile(File tarFile, Path rootPath, boolean isByStep, int isExist) throws IOException {
        try (InputStream is = new FileInputStream(tarFile);
             InputStream decompressedStream = getDecompressedStream(is, tarFile.getName().toLowerCase());
             TarArchiveInputStream tis = new TarArchiveInputStream(decompressedStream)) {

            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                Path entryPath = buildSafePath(rootPath, entry.getName(), isByStep);
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    entryPath = handleFileConflict(entryPath, isExist);
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(tis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private InputStream getDecompressedStream(InputStream is, String filename) throws IOException {
        String ext = FilenameUtils.getExtension(filename).toLowerCase();
        // 处理复合扩展名如 .tar.gz
        if (filename.toLowerCase().endsWith(".tar.gz")) {
            ext = "gz";
        } else if (filename.toLowerCase().endsWith(".tar.bz2")) {
            ext = "bz2";
        } else if (filename.toLowerCase().endsWith(".tar.xz")) {
            ext = "xz";
        }
        // 先解压第一层压缩
        InputStream decompressedStream = switch (ext) {
            case "gz" -> new GZIPInputStream(is);
            case "bz2" -> new BZip2CompressorInputStream(is);
            case "xz" -> new XZCompressorInputStream(is);
            default -> is; // 如果不是压缩格式，直接返回原流
        };
        // 如果有第二层扩展名（如 .tar），返回解压后的流
        // 实际上对于 .tar.gz 等格式，我们只需要解压第一层，然后交给 TarArchiveInputStream 处理
        return decompressedStream;
    }

    // 处理单个压缩文件，例如 .gz/.bz2/.xz
    private void processSingleCompressedFile(File compressedFile, Path rootPath, int isExist) throws IOException {
        String baseName = FilenameUtils.getBaseName(compressedFile.getName()); // 去除 .gz/.bz2/.xz
        Path targetPath = rootPath.resolve(baseName);
        targetPath = handleFileConflict(targetPath, isExist);

        try (InputStream is = new FileInputStream(compressedFile);
             InputStream decompressedStream = getDecompressedStream(is, FilenameUtils.getExtension(compressedFile.getName()))) {
            Files.copy(decompressedStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // 处理RAR格式的压缩文件
    private void processRarFile(File rarFile, Path rootPath, boolean isByStep, int isExist) throws IOException {
        try (IInArchive archive = SevenZip.openInArchive(null, (IInStream) new RandomAccessFile(rarFile, "r"))) {
            int[] in = new int[archive.getNumberOfItems()];
            for (int i = 0; i < in.length; i++) {
                in[i] = i;
            }

            archive.extract(in, false, new IArchiveExtractCallback() {
                private Path currentPath;

                @Override
                public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
                    String itemPath = archive.getStringProperty(index, PropID.PATH);
                    boolean isFolder = (boolean) archive.getProperty(index, PropID.IS_FOLDER);

                    currentPath = buildSafePath(rootPath, itemPath, isByStep);

                    if (isFolder) {
                        try {
                            Files.createDirectories(currentPath);
                        } catch (IOException e) {
                            throw new SevenZipException("创建目录失败: " + currentPath, e);
                        }
                        return null;
                    } else {
                        try {
                            currentPath = handleFileConflict(currentPath, isExist);
                            Files.createDirectories(currentPath.getParent());
                            return new ISequentialOutStream() {
                                @Override
                                public int write(byte[] data) throws SevenZipException {
                                    try (OutputStream os = new FileOutputStream(currentPath.toFile(), true)) {
                                        os.write(data);
                                    } catch (IOException e) {
                                        throw new SevenZipException("写入文件失败: " + currentPath, e);
                                    }
                                    return data.length;
                                }
                            };
                        } catch (IOException e) {
                            throw new SevenZipException("处理文件冲突失败", e);
                        }
                    }
                }

                @Override
                public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
                }

                @Override
                public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
                    if (extractOperationResult != ExtractOperationResult.OK) {
                        throw new SevenZipException("解压失败: " + extractOperationResult);
                    }
                }

                @Override
                public void setCompleted(long complete) throws SevenZipException {
                }

                @Override
                public void setTotal(long total) throws SevenZipException {
                }
            });
        }
    }

    // 处理7Z格式的压缩文件
    private void process7zFile(File sevenZFile, Path rootPath, boolean isByStep, int isExist) throws IOException {
        try (IInArchive archive = SevenZip.openInArchive(null, (IInStream) new RandomAccessFile(sevenZFile, "r"))) {
            int[] in = new int[archive.getNumberOfItems()];
            for (int i = 0; i < in.length; i++) {
                in[i] = i;
            }

            archive.extract(in, false, new IArchiveExtractCallback() {
                private Path currentPath;

                @Override
                public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
                    String itemPath = archive.getStringProperty(index, PropID.PATH);
                    boolean isFolder = (boolean) archive.getProperty(index, PropID.IS_FOLDER);

                    currentPath = buildSafePath(rootPath, itemPath, isByStep);

                    if (isFolder) {
                        try {
                            Files.createDirectories(currentPath);
                        } catch (IOException e) {
                            throw new SevenZipException("创建目录失败: " + currentPath, e);
                        }
                        return null;
                    } else {
                        try {
                            currentPath = handleFileConflict(currentPath, isExist);
                            Files.createDirectories(currentPath.getParent());
                            return new ISequentialOutStream() {
                                @Override
                                public int write(byte[] data) throws SevenZipException {
                                    try (OutputStream os = new FileOutputStream(currentPath.toFile(), true)) {
                                        os.write(data);
                                    } catch (IOException e) {
                                        throw new SevenZipException("写入文件失败: " + currentPath, e);
                                    }
                                    return data.length;
                                }
                            };
                        } catch (IOException e) {
                            throw new SevenZipException("处理文件冲突失败", e);
                        }
                    }
                }

                @Override
                public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
                }

                @Override
                public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
                    if (extractOperationResult != ExtractOperationResult.OK) {
                        throw new SevenZipException("解压失败: " + extractOperationResult);
                    }
                }

                @Override
                public void setCompleted(long complete) throws SevenZipException {
                }

                @Override
                public void setTotal(long total) throws SevenZipException {
                }
            });
        }
    }

    // 构建安全路径（包含ZipSlip防护）
    private Path buildSafePath(Path rootPath, String entryName, boolean isByStep) {
        entryName = entryName.replace('\\', '/');
        Path entryPath = isByStep ? rootPath.resolve(entryName).normalize() : rootPath.resolve(Paths.get(entryName).getFileName());
        if (!entryPath.startsWith(rootPath)) {
            throw new SecurityException("非法路径: " + entryName);
        }
        return entryPath;
    }

    private Path handleFileConflict(Path path, int isExist) throws IOException {
        if (Files.exists(path)) {
            if (isExist == 1) {
                Files.deleteIfExists(path);
            } else if (isExist == 2) {
                path = generateUniquePath(path);
            } else {
                throw new FileAlreadyExistsException("文件已存在: " + path.getFileName().toString());
            }
        }
        return path;
    }

    private Path handleRegularFile(MultipartFile file, Path rootPath, int isExist) throws IOException {
        Path targetPath = rootPath.resolve(Objects.requireNonNull(file.getOriginalFilename()));
        return handleFileConflict(targetPath, isExist);
    }

    private Path generateUniquePath(Path originalPath) {
        String baseName = FilenameUtils.getBaseName(originalPath.toString());
        String extension = FilenameUtils.getExtension(originalPath.toString());
        String timestamp = String.valueOf(System.currentTimeMillis());
        return originalPath.getParent().resolve(baseName + "_" + timestamp + (extension.isEmpty() ? "" : "." + extension));
    }
}
