package cn.geelato.utils;

import cn.geelato.utils.entity.Resolution;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ThumbnailUtils {
    public static final String THUMBNAIL_GENRE = "Thumbnail";
    private static final int MIN_DIMENSION = 83;
    private static final double MAX_THUMBNAIL_SCALE = 1.00;
    private static final double MIN_THUMBNAIL_SCALE = 0.00;

    /**
     * 生成指定文件的缩略图并保存到指定输出文件中。
     *
     * @param input      原始文件
     * @param output     输出文件
     * @param resolution 缩略图的分辨率，如果为null则使用默认的83x83
     * @throws RuntimeException 如果在处理过程中发生异常，则抛出运行时异常
     */
    public static void thumbnail(File input, File output, Resolution resolution) {
        try {
            if (!Resolution.validate(resolution)) {
                throw new RuntimeException("缩略图分辨率不合法");
            }
            // 创建缩略图图片
            Thumbnails.of(input).size(resolution.getWidth(), resolution.getHeight()).toFile(output);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 根据输入的图片文件和指定的尺寸、缩放比例生成一系列的缩略图分辨率。
     *
     * @param input      输入的图片文件
     * @param dimension  尺寸字符串，例如 "100,200"
     * @param thumbScale 缩放比例字符串，例如 "0.5,0.75"
     * @return 包含所有生成的缩略图分辨率的列表
     * @throws IOException 如果读取文件时发生I/O异常
     */
    public static List<Resolution> resolution(File input, String dimension, String thumbScale) throws IOException {
        List<Resolution> resolutions = new ArrayList<>();
        if (!ThumbnailUtils.isImage(input)) {
            return resolutions;
        }
        BufferedImage originalImage = ImageIO.read(input);
        Set<Double> scales = new LinkedHashSet<>();
        // 解析缩略图尺寸
        List<String> dimensions = StringUtils.toListDr(dimension);
        for (String dis : dimensions) {
            double minScale = minScale(originalImage.getWidth(), originalImage.getHeight(), setDimension(dis));
            scales.add(1 / minScale);
        }
        // 解析缩略图缩放比例
        List<String> thumbScales = StringUtils.toListDr(thumbScale);
        for (String ts : thumbScales) {
            scales.add(setThumbScale(ts));
        }
        // 如果没有指定尺寸和缩放比例，则默认使用最小边长为83的缩略图
        if (scales.isEmpty()) {
            double minScale = minScale(originalImage.getWidth(), originalImage.getHeight(), setDimension(null));
            scales.add(1 / minScale);
        }
        // 生成缩略图分辨率
        for (Double scale : scales) {
            if (scale > MIN_THUMBNAIL_SCALE && scale < MAX_THUMBNAIL_SCALE) {
                int scaledWidth = (int) (originalImage.getWidth() * scale);
                int scaledHeight = (int) (originalImage.getHeight() * scale);
                resolutions.add(new Resolution(scaledWidth, scaledHeight));
            }
        }
        return Resolution.distinct(resolutions);
    }

    /**
     * 判断给定文件是否为图片文件
     *
     * @param input 要判断的文件
     * @return 如果文件存在且是图片文件，则返回true；否则返回false
     * @throws IOException 如果文件无法读取或发生其他I/O错误，则抛出IOException
     */
    public static boolean isImage(File input) throws IOException {
        if (!input.exists()) {
            return false;
        }
        String mimeType = Files.probeContentType(input.toPath());
        return !StringUtils.isBlank(mimeType) && mimeType.startsWith("image/");
    }

    /**
     * 计算缩略图的最小比例
     *
     * @param width     图像的宽度
     * @param height    图像的高度
     * @param dimension 缩略图的最小边长
     * @return 返回图像缩放到缩略图尺寸时的最小比例，如果图像尺寸小于缩略图尺寸，则返回1
     */
    private static double minScale(int width, int height, int dimension) {
        if (width > dimension && height > dimension) {
            return Math.min(width / dimension, height / dimension);
        }
        return 1;
    }

    /**
     * 设置缩略图的尺寸
     *
     * @param dimension 缩略图的尺寸
     * @return 缩略图的尺寸，最小为MIN_DIMENSION
     */
    private static int setDimension(String dimension) {
        return Math.max(dimension == null ? 0 : Integer.valueOf(dimension), MIN_DIMENSION);
    }

    /**
     * 设置缩略图缩放比例
     *
     * @param thumbScale 缩放比例字符串，可为null
     * @return 缩放比例值，double类型
     */
    private static double setThumbScale(String thumbScale) {
        double scale = (thumbScale == null) ? MIN_THUMBNAIL_SCALE : Math.max(Double.valueOf(thumbScale), MIN_THUMBNAIL_SCALE);
        return scale > MAX_THUMBNAIL_SCALE ? (1 / scale) : scale;
    }
}
