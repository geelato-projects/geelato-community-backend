package cn.geelato.web.platform.m.base.service;

import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.utils.KeyUtils;
import cn.geelato.utils.Sm2Util;
import cn.geelato.web.platform.m.base.entity.SysConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author diabl
 * @date 2023/9/15 10:56
 */
@Component
public class SysConfigService extends BaseService {
    private final Logger logger = LoggerFactory.getLogger(SysConfigService.class);

    /**
     * 加密
     *
     * @param model
     * @throws Exception
     */
    public static void encrypt(SysConfig model) throws Exception {
        Map<String, String> keys = null;
        if (Strings.isNotBlank(model.getSm2Key())) {
            try {
                keys = JSON.parseObject(model.getSm2Key(), Map.class);
            } catch (Exception ex) {
                throw new RuntimeException(ex.getMessage());
            }
        } else {
            keys = KeyUtils.generateSmKey();
        }
        String encodeValue = Sm2Util.encrypt(model.getConfigValue(), keys);
        model.setConfigValue(encodeValue);
        model.setSm2Key(JSON.toJSONString(keys));
    }

    /**
     * 解密
     *
     * @param model
     * @throws Exception
     */
    public static void decrypt(SysConfig model) throws Exception {
        Map<String, String> keys = null;
        if (Strings.isNotBlank(model.getSm2Key())) {
            try {
                keys = JSON.parseObject(model.getSm2Key(), Map.class);
                String encodeValue = Sm2Util.decrypt(model.getConfigValue(), keys);
                model.setConfigValue(encodeValue);
            } catch (Exception ex) {
                throw new RuntimeException("密钥解析失败！");
            }
        } else {
            throw new RuntimeException("密钥缺失！");
        }
    }

    /**
     * 创建一条数据
     *
     * @param model 实体数据
     * @return
     */
    public SysConfig createModel(SysConfig model) throws Exception {
        model.setSm2Key(null);
        if (model.isEncrypted() && Strings.isNotBlank(model.getConfigValue())) {
            SysConfigService.encrypt(model);
        }
        return super.createModel(model);
    }

    /**
     * 更新一条数据
     *
     * @param model 实体数据
     * @return
     */
    public SysConfig updateModel(SysConfig model) throws Exception {
        SysConfig oldModel = this.getModel(SysConfig.class, model.getId());
        if (Strings.isNotBlank(model.getConfigValue())) {
            if (model.isEncrypted() && oldModel.isEncrypted()) {// 重新加密
                if (!model.getConfigValue().equals(oldModel.getConfigValue())) {
                    SysConfigService.encrypt(model);
                }
            }
            if (model.isEncrypted() && !oldModel.isEncrypted()) {// 加密
                SysConfigService.encrypt(model);
            }
            if (!model.isEncrypted() && !oldModel.isEncrypted()) {
                model.setSm2Key(null);
            }
            if (!model.isEncrypted() && oldModel.isEncrypted()) {
                if (model.getConfigValue().equals(oldModel.getConfigValue())) {// 解密
                    // SysConfigService.decrypt(oldModel);
                    // model.setConfigValue(oldModel.getConfigValue());
                }
                model.setSm2Key(null);
            }
        } else {
            model.setSm2Key(null);
            model.setConfigValue(null);
        }

        return super.updateModel(model);
    }
}
