package cn.geelato.utils.entity;

import lombok.Getter;
import lombok.Setter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Getter
@Setter
public class Resolution {
    private int width;
    private int height;

    public Resolution() {
    }

    public Resolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * 从给定的 ThumbResolution 列表中移除重复的项。该方法通过检查宽度、高度和宽高乘积（即宽高比）来识别重复项。
     *
     * @param resolutions 要去重的 ThumbResolution 列表
     * @return 去重后的 ThumbResolution 列表
     */
    public static List<Resolution> distinct(List<Resolution> resolutions) {
        List<Resolution> result = new ArrayList<>();
        if (resolutions != null && !resolutions.isEmpty()) {
            List<Integer> widths = new ArrayList<>();
            List<Integer> heights = new ArrayList<>();
            List<Integer> aspects = new ArrayList<>();
            for (Resolution resolution : resolutions) {
                if (!widths.contains(resolution.getWidth()) && !heights.contains(resolution.getHeight()) && !aspects.contains(resolution.getWidth() * resolution.getHeight())) {
                    widths.add(resolution.getWidth());
                    heights.add(resolution.getHeight());
                    aspects.add(resolution.getWidth() * resolution.getHeight());
                    result.add(resolution);
                }
            }
        }
        return result;
    }

    /**
     * 验证 ThumbResolution 对象是否有效。
     *
     * @param resolution 需要验证的 ThumbResolution 对象
     * @return 如果 ThumbResolution 对象的宽度和高度都大于0，则返回 true；否则返回 false
     */
    public static boolean validate(Resolution resolution) {
        if (resolution != null) {
            return resolution.getWidth() > 0 && resolution.getHeight() > 0;
        }
        return false;
    }

    /**
     * 根据输入的图片文件获取其分辨率。
     *
     * @param input 输入的图片文件
     * @return 包含图片宽度和高度的 ThumbResolution 对象
     * @throws IOException 如果读取文件时发生I/O异常
     */
    public static Resolution get(File input) throws IOException {
        BufferedImage originalImage = ImageIO.read(input);
        return new Resolution(originalImage.getWidth(), originalImage.getHeight());
    }

    public static Resolution min(Set<String> resolutions) {
        List<Resolution> list = new ArrayList<>();
        // 将分辨率字符串转换为 Resolution 对象，并添加到列表中
        resolutions.forEach(resolution -> {
            String[] split = resolution.split("x");
            if (split != null && split.length == 2) {
                list.add(new Resolution(Integer.parseInt(split[0]), Integer.parseInt(split[1])));
            }
        });
        // 比较两个 Resolution 对象的面积，返回较小的一个
        Optional<Resolution> optional = list.stream().min((o1, o2) -> {
            return o1.getAmass() - o2.getAmass();
        });
        return optional.orElse(null);
    }

    /**
     * 获取面积，即宽度和高度相乘的结果。
     */
    public Integer getAmass() {
        return this.getWidth() * this.getHeight();
    }

    /**
     * 获取分辨率 字符串，格式为"宽度x高度"。
     */
    public String getProduct() {
        return String.format("%dx%d", this.getWidth(), this.getHeight());
    }
}
