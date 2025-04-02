package cn.geelato.web.platform.m.zxing.utils;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.zxing.entity.Qrcode;
import cn.geelato.web.platform.m.zxing.enums.BarcodePictureFormatEnum;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class QrcodeUtils {

    /**
     * 生成二维码
     * <p>
     * 根据提供的文本和二维码配置，生成对应的二维码图片，并返回图片的路径。
     *
     * @param text   要生成二维码的文本内容
     * @param qrcode 二维码的配置信息，包括大小、边距、背景色、字体色等
     * @return 返回生成的二维码图片的路径
     */
    public static String generateQrcode(String text, Qrcode qrcode) {
        // 数据处理
        qrcode.afterSet();
        // 画布背景颜色
        Color backgroundColor = ColorUtils.hexToColor(qrcode.getBackgroundColor(), ColorUtils.WHITE);
        // 画布字体颜色
        Color fontColor = ColorUtils.hexToColor(qrcode.getCodeColor(), ColorUtils.BLACK);
        // 图片格式
        String pictureSuffix = BarcodePictureFormatEnum.getEnum(qrcode.getPictureFormat());
        String pictureName = String.format("%s.%s", getFileName(qrcode), pictureSuffix);
        String picturePath = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, AttachmentSourceEnum.ATTACH.getValue(), qrcode.getTenantCode(), qrcode.getAppId(), pictureName, true);
        log.info(String.format("%s, %s", pictureName, picturePath));
        // 计算画布高度, 条码高度 + 条码上下边距
        int qrcodeHeight = qrcode.getHeight() + 2 * qrcode.getCodePadding();
        // 计算画布宽度，条码宽度 + 条码左右边距
        int qrcodeWidth = qrcode.getWidth() + 2 * qrcode.getCodePadding();
        log.info(String.format("画布宽高：（%d，%d）", qrcodeWidth, qrcodeHeight));
        // 创建画布
        Graphics2D graphics2D = null;
        try {
            int imageType = qrcode.getLucency() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            BufferedImage qrcodeImage = new BufferedImage(qrcodeWidth, qrcodeHeight, imageType);
            graphics2D = qrcodeImage.createGraphics();
            if (!qrcode.getLucency()) {
                graphics2D.setColor(backgroundColor);
                graphics2D.fillRect(0, 0, qrcodeWidth, qrcodeHeight);
            }
            // 生成二维码
            Map<EncodeHintType, Object> hintTypeMap = new HashMap<>();
            hintTypeMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, qrcode.getWidth(), qrcode.getHeight(), hintTypeMap);
            if (bitMatrix != null) {
                log.info(String.format("条形码宽高：（%d，%d）", bitMatrix.getWidth(), bitMatrix.getHeight()));
                int startX = (qrcodeWidth - qrcode.getWidth()) / 2;
                int startY = (qrcodeHeight - qrcode.getHeight()) / 2;
                for (int x = 0; x < bitMatrix.getWidth(); x++) {
                    for (int y = 0; y < bitMatrix.getHeight(); y++) {
                        qrcodeImage.setRGB(startX + x, startY + y,
                                bitMatrix.get(x, y) ? fontColor.getRGB() :
                                        (qrcode.getLucency() ? new Color(0, 0, 0, 0).getRGB() :
                                                backgroundColor.getRGB()));
                    }
                }
            }


            File file = new File(picturePath);
            ImageIO.write(qrcodeImage, pictureSuffix, file);
            return picturePath;
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getFileName(Qrcode qrcode) {
        if (StringUtils.isNotBlank(qrcode.getTitle())) {
            return qrcode.getTitle();
        } else if (StringUtils.isNotBlank(qrcode.getCode())) {
            return qrcode.getCode();
        }
        return String.valueOf(System.currentTimeMillis());
    }
}
