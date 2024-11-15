package cn.geelato.web.platform.m.base.service;

import cn.geelato.utils.KeyUtils;
import cn.geelato.utils.Sm2Util;
import cn.geelato.web.platform.m.base.entity.SysConfig;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author diabl
 */
@Component
public class SysConfigService extends BaseService {
    private final Logger logger = LoggerFactory.getLogger(SysConfigService.class);

    /**
     * 加密方法
     * <p>
     * 该方法用于对SysConfig对象中的配置值进行加密处理。
     *
     * @param model SysConfig对象，包含待加密的配置值及可能已存在的SM2密钥
     * @throws Exception 如果在加密过程中发生异常，则抛出该异常
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
     * 解密系统配置中的加密信息
     * <p>
     * 使用SM2算法对系统配置模型中的加密值进行解密操作。
     *
     * @param model 系统配置模型，包含待解密的配置信息
     * @throws Exception 如果解密过程中发生异常，则抛出该异常
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
     * <p>
     * 该方法用于创建一条新的系统配置数据。
     * 如果传入的模型对象设置了加密标志且配置值不为空，则会对配置值进行加密处理。
     *
     * @param model 系统配置数据的实体模型
     * @return 返回创建后的系统配置数据实体模型
     * @throws Exception 如果在数据创建过程中发生异常，则抛出该异常
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
     * <p>
     * 该方法用于更新系统配置信息。根据传入的实体数据更新相应的配置信息，并根据加密状态对配置值进行相应的加密或解密处理。
     *
     * @param model 要更新的系统配置实体数据
     * @return 返回更新后的系统配置实体数据
     * @throws Exception 如果在更新过程中出现异常，则抛出该异常
     */
    public SysConfig updateModel(SysConfig model) throws Exception {
        SysConfig oldModel = this.getModel(SysConfig.class, model.getId());
        oldModel.afterSet();
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
