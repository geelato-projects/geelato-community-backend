package cn.geelato.web.platform.common;

import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.m.base.entity.Attach;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.io.File;
import java.io.IOException;

/**
 * @author diabl
 */
@Getter
@Setter
public class Base64Helper {
    private String id;
    private String name;
    private String type;
    private Long size;
    private String base64;
    private File file;
    private String remark;

    /**
     * 从附件对象创建一个Base64信息对象
     *
     * @param attach 附件对象
     * @return 包含附件信息的Base64信息对象
     */
    public static Base64Helper fromAttachment(Attach attach) {
        Base64Helper helper = new Base64Helper();
        if (attach != null) {
            helper.setId(attach.getId());
            helper.setName(attach.getName());
            helper.setType(attach.getType());
            helper.setSize(attach.getSize());
            File aFile = new File(attach.getPath());
            helper.setFile(aFile.exists() ? aFile : null);
        }
        return helper;
    }

    public static Base64Helper fromString(String base64) throws IOException {
        Base64Helper helper = null;
        try {
            helper = JSON.parseObject(base64, Base64Helper.class);
        } catch (Exception e) {
            throw new RuntimeException("Base64Helper json parse error");
        }
        if (helper == null || Strings.isBlank(helper.getName()) || Strings.isBlank(helper.getBase64())) {
            throw new RuntimeException("Base64Helper params is blank");
        }
        return helper;
    }

    /**
     * 将Base64编码的字符串转换为临时文件
     *
     * @param base64 包含Base64编码信息的字符串
     * @return 转换得到的临时文件
     * @throws IOException 如果在转换过程中发生I/O异常
     */
    public static File toTempFile(String base64) throws IOException {
        Base64Helper helper = fromString(base64);
        return helper.toTempFile();
    }

    /**
     * 将Base64编码的字符串转换为临时文件
     *
     * @return 创建的临时文件
     * @throws IOException      如果在创建临时文件时发生I/O错误
     * @throws RuntimeException 如果Base64格式错误或创建临时文件失败
     */
    public File toTempFile() throws IOException {
        File tempFile = FileUtils.createTempFile(this.getBase64(), this.getName());
        if (tempFile == null || !tempFile.exists()) {
            throw new RuntimeException("Base64Helper temp file not exist");
        }
        return tempFile;
    }
}
