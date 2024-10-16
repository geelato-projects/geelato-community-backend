package cn.geelato.web.platform.m.zxing.utils;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.utils.ColorUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.zxing.entity.Barcode;
import cn.geelato.web.platform.m.zxing.enums.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BarcodeUtils {

    public static Map<String, Object> generateBarcodeToAttach(String text, Barcode barcode) {
        try {
            // 图片信息
            String pictureSuffix = BarcodePictureFormatEnum.getEnum(barcode.getPictureFormat());
            String pictureName = String.format("%s.%s", getFileName(barcode), pictureSuffix);
            // 生成条形码
            String picturePath = generateBarcode(text, barcode);
            if (StringUtils.isBlank(picturePath)) {
                throw new RuntimeException("Generate Barcode Image Path is empty！");
            }
            File file = new File(picturePath);
            if (!file.exists()) {
                throw new RuntimeException("Generate Barcode Image is not exists！");
            }
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            Attach attach = new Attach();
            attach.setName(pictureName);
            attach.setType(Files.probeContentType(file.toPath()));
            attach.setSize(attributes.size());
            attach.setPath(picturePath);
            attach.setGenre("Barcode");
            attach.setAppId(barcode.getAppId());
            return dao().save(attach);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String generateBarcodeToBase64(String text, Barcode barcode) {
        try {
            String contentType = BarcodePictureFormatEnum.getContentType(barcode.getPictureFormat());
            // 生成条形码
            String picturePath = generateBarcode(text, barcode);
            if (StringUtils.isBlank(picturePath)) {
                throw new RuntimeException("Generate Barcode Image Path is empty！");
            }
            File file = new File(picturePath);
            if (!file.exists()) {
                throw new RuntimeException("Generate Barcode Image is not exists！");
            }
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] fileBytes = fileInputStream.readAllBytes();
            String base64String = Base64.getEncoder().encodeToString(fileBytes);
            return String.format("data:%s;base64,%s", contentType, base64String);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * 生成条形码
     *
     * @param text    内容
     * @param barcode 条码配置
     * @return
     */
    public static String generateBarcode(String text, Barcode barcode) {
        // 数据处理
        barcode.afterSet();
        // 画布背景颜色
        Color backgroundColor = ColorUtils.hexToColor(barcode.getBackgroundColor(), ColorUtils.WHITE);
        // 画布字体颜色
        Color fontColor = ColorUtils.hexToColor(barcode.getFontColor(), ColorUtils.BLACK);
        // 条码类型
        BarcodeFormat barcodeFormat = BarcodeTypeEnum.getFormatByValue(barcode.getType());
        // 图片格式
        String pictureSuffix = BarcodePictureFormatEnum.getEnum(barcode.getPictureFormat());
        String pictureName = String.format("%s.%s", getFileName(barcode), pictureSuffix);
        String picturePath = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, AttachmentSourceEnum.PLATFORM_ATTACH.getValue(), barcode.getTenantCode(), barcode.getAppId(), pictureName, true);
        // log.info(String.format("%s, %s", pictureName, picturePath));
        // 字体
        Font font = null;
        int textWidth = 0;
        int textHeight = 0;
        if (barcode.getDisplayText()) {
            Graphics2D virtualGraphics = null;
            try {
                font = BarcodeFontStyleEnum.getFont(barcode.getFontFamily(), barcode.getFontStyle(), barcode.getFontSize());
                // 创建虚拟画布，计算字体高度宽度
                BufferedImage virtualImage = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
                virtualGraphics = virtualImage.createGraphics();
                virtualGraphics.setFont(font);
                FontMetrics virtualFontMetrics = virtualGraphics.getFontMetrics();
                virtualGraphics.dispose();
                textWidth = virtualFontMetrics.stringWidth(text);
                textHeight = virtualFontMetrics.getHeight();
                // log.info(String.format("文本宽高：（%d，%d）", textWidth, textHeight));
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            } finally {
                virtualGraphics.dispose();
            }
        }
        // 计算画布高度, 条码高度 + 条码上下边距 + 条码与字体之间的距离 + 文字高度
        int barcodeHeight = barcode.getHeight() + barcode.getBorderTop() + barcode.getBorderBottom() + textHeight + barcode.getFontMargin();
        barcodeHeight = Math.max(barcodeHeight, barcode.getHeight() + barcode.getBorderTop() + barcode.getBorderBottom());
        // 计算画布宽度，最大值(条码宽度,字体宽度) + 条码左右边距*2
        int barcodeWidth = Math.max(barcode.getWidth(), textWidth) + barcode.getBorderLeft() + barcode.getBorderRight();
        // log.info(String.format("画布宽高：（%d，%d）", barcodeWidth, barcodeHeight));
        // 创建画布
        Graphics2D graphics2D = null;
        try {
            int imageType = barcode.getLucency() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            BufferedImage barcodeImage = new BufferedImage(barcodeWidth, barcodeHeight, imageType);
            graphics2D = barcodeImage.createGraphics();
            if (!barcode.getLucency()) {
                graphics2D.setColor(backgroundColor);
                graphics2D.fillRect(0, 0, barcodeWidth, barcodeHeight);
            }
            // 显示字体，头部
            if (barcode.getDisplayText() && BarcodeFontPositionEnum.isTop(barcode.getFontPosition())) {
                graphics2D.setColor(fontColor);
                graphics2D.setFont(font);
                int startX = startXPosition(barcode.getFontAlign(), barcodeWidth, barcode.getBorderLeft(), barcode.getBorderRight(), textWidth);
                int startY = barcode.getBorderTop() + textHeight;
                // log.info(String.format("字体头部位置：（%d，%d）", startX, startY));
                graphics2D.drawString(text, startX, startY);
            }
            // 生成条形码
            Map<EncodeHintType, Object> hintTypeMap = new HashMap<>();
            hintTypeMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, barcodeFormat, barcode.getWidth(), barcode.getHeight(), hintTypeMap);
            if (bitMatrix != null) {
                // log.info(String.format("条形码宽高：（%d，%d）", bitMatrix.getWidth(), bitMatrix.getHeight()));
                int startX = (barcodeWidth - barcode.getWidth()) / 2;
                int startY = barcode.getBorderTop();
                if (BarcodeFontPositionEnum.isTop(barcode.getFontPosition())) {
                    startY = barcode.getBorderTop() + textHeight + barcode.getFontMargin();
                }
                // log.info(String.format("条形码位置：（%d，%d）", startX, startY));
                for (int x = 0; x < bitMatrix.getWidth(); x++) {
                    for (int y = 0; y < bitMatrix.getHeight(); y++) {
                        barcodeImage.setRGB(startX + x, startY + y,
                                bitMatrix.get(x, y) ? fontColor.getRGB() :
                                        (barcode.getLucency() ? new Color(0, 0, 0, 0).getRGB() :
                                                backgroundColor.getRGB()));
                    }
                }
            }
            // 显示字体，底部
            if (barcode.getDisplayText() && BarcodeFontPositionEnum.isBottom(barcode.getFontPosition())) {
                graphics2D.setColor(fontColor);
                graphics2D.setFont(font);
                int startX = startXPosition(barcode.getFontAlign(), barcodeWidth, barcode.getBorderLeft(), barcode.getBorderRight(), textWidth);
                int startY = barcode.getBorderTop() + barcode.getHeight() + barcode.getFontMargin() + textHeight;
                // log.info(String.format("字体底部位置：（%d，%d）", startX, startY));
                graphics2D.drawString(text, startX, startY);
            }
            // 保存图片
            File file = new File(picturePath);
            ImageIO.write(barcodeImage, pictureSuffix, file);
            return picturePath;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            graphics2D.dispose();
        }
    }

    private static String getFileName(Barcode barcode) {
        if (StringUtils.isNotBlank(barcode.getTitle())) {
            return barcode.getTitle();
        } else if (StringUtils.isNotBlank(barcode.getCode())) {
            return barcode.getCode();
        }
        return String.valueOf(new Date().getTime());
    }

    private static int startXPosition(String alignType, int totalWidth, int borderLeftWidth, int borderRightWidth, int textWidth) {
        int startX = 0;
        if (BarcodeFontAlignEnum.isLeft(alignType)) {
            startX = borderLeftWidth;
        } else if (BarcodeFontAlignEnum.isRight(alignType)) {
            startX = totalWidth - borderRightWidth - textWidth;
        } else {
            startX = (totalWidth - textWidth) / 2;
        }
        return startX;
    }

    private static Dao dao() {
        DataSource ds = (DataSource) DataSourceManager.singleInstance().getDynamicDataSourceMap().get("primary");
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        return new Dao(jdbcTemplate);
    }

}