package cn.geelato.utils;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

public class ThumbnailUtils {
    public static final String THUMBNAIL_SUFFIX = "_thumb";
    public static final String THUMBNAIL_GENRE = "Thumbnail";
    private static final int MIN_DIMENSION = 83;
    private static final double MAX_THUMBNAIL_SCALE = 1.0;
    private static final double MIN_THUMBNAIL_SCALE = 0.0;

    /**
     * 生成指定文件的缩略图并保存到指定输出文件中。
     *
     * @param input  原始文件
     * @param output 输出文件
     */
    public static void thumbnail(File input, File output) {
        ThumbnailUtils.thumbnail(input, output, null, null);
    }


    /**
     * 生成指定文件的缩略图并保存到指定输出文件中。
     *
     * @param input     原始文件
     * @param output    输出文件
     * @param dimension 缩略图的最小边长
     * @throws RuntimeException 如果在处理过程中发生异常，则抛出运行时异常
     */
    public static void thumbnail(File input, File output, Integer dimension, Double thumbScale) {
        try {
            // 读取原始图片
            BufferedImage originalImage = ImageIO.read(input);
            // 计算缩略图的宽度和高度（假设缩略图大小为原始图片的一半）
            double minScale = 1 / setThumbScale(thumbScale);
            if (minScale <= MAX_THUMBNAIL_SCALE) {
                minScale = minScale(originalImage.getWidth(), originalImage.getHeight(), setDimension(dimension));
            }
            int scaledWidth = (int) (originalImage.getWidth() / minScale);
            int scaledHeight = (int) (originalImage.getHeight() / minScale);
            // 创建缩略图图片
            Thumbnails.of(input).size(scaledWidth, scaledHeight).outputQuality(minScale > 1 ? 0.8f : 1.0f).toFile(output);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 判断给定文件是否为缩略图。
     *
     * @param input 需要判断的文件
     * @return 如果文件是缩略图，则返回 true；否则返回 false
     */
    public static boolean isThumbnail(File input) {
        return isThumbnail(input, null);
    }

    /**
     * 判断给定文件是否为缩略图。
     *
     * @param input     待判断的文件
     * @param dimension 缩略图的最小尺寸
     * @return 如果文件是缩略图，则返回 true；否则返回 false
     */
    public static boolean isThumbnail(File input, Integer dimension) {
        try {
            if (!input.exists()) {
                return false;
            }
            String mimeType = Files.probeContentType(input.toPath());
            if (StringUtils.isBlank(mimeType) || !mimeType.startsWith("image/")) {
                return false;
            }
            BufferedImage originalImage = ImageIO.read(input);
            double minScale = minScale(originalImage.getWidth(), originalImage.getHeight(), setDimension(dimension));
            return minScale > 1;
        } catch (Exception e) {
            return false;
        }
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
    private static int setDimension(Integer dimension) {
        return Math.max(dimension == null ? 0 : dimension.intValue(), MIN_DIMENSION);
    }

    /**
     * 设置缩略图的缩放比例
     *
     * @param thumbScale 缩略图的缩放比例
     * @return 调整后的缩放比例，最小为0.0，最大为MAX_THUMBNAIL_SCALE
     */
    private static double setThumbScale(Double thumbScale) {
        return Math.min(thumbScale == null ? MAX_THUMBNAIL_SCALE : Math.max(thumbScale.doubleValue(), MIN_THUMBNAIL_SCALE), MAX_THUMBNAIL_SCALE);
    }
}
