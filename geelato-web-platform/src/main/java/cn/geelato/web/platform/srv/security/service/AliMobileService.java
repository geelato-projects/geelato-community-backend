package cn.geelato.web.platform.srv.security.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.meta.SysConfig;
import cn.geelato.web.platform.srv.platform.service.SysConfigService;
import cn.geelato.web.platform.srv.security.entity.AliMobile;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author diabl
 */
@Component
@Slf4j
public class AliMobileService {
    // private static final String SIGN_NAME = "深圳海桥物流";// 阿里云短信测试
    // private static final String TEMPLATE_CODE = "SMS_465430460";// SMS_154950909
    private static final int SEND_SMS_RESPONSE_STATUS_CODE = 200;
    private static final String SEND_SMS_RESPONSE_BODY_CODE = "OK";
    private static final String CONFIG_KEY_SIGN_NAME = "mobileSignName";
    private static final String CONFIG_KEY_ACCESS_KEY_ID = "mobileAccessKeyId";
    private static final String CONFIG_KEY_ACCESS_KEY_SECRET = "mobileAccessKeySecret";
    private static final String CONFIG_KEY_SECURITY_TOKEN = "mobileSecurityToken";

    @Autowired
    @Qualifier("primaryDao")
    public Dao dao;

    /**
     * 使用AK&SK初始化账号Client
     * <p>
     * 根据提供的AliMobile对象中的AccessKeyId和AccessKeySecret，创建一个用于访问阿里云短信服务的Client对象。
     *
     * @param aliMobile 包含阿里云账号信息的AliMobile对象
     * @return 返回初始化后的Client对象
     * @throws Exception 如果初始化过程中发生异常，则抛出该异常
     */
    private com.aliyun.dysmsapi20170525.Client createClient(AliMobile aliMobile) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                // 必填，您的 AccessKey ID
                .setAccessKeyId(aliMobile.getAccessKeyId())
                // 必填，您的 AccessKey Secret
                .setAccessKeySecret(aliMobile.getAccessKeySecret());
        // Endpoint 请参考 https://api.aliyun.com/product/Dysmsapi
        config.endpoint = "dysmsapi.aliyuncs.com";
        return new com.aliyun.dysmsapi20170525.Client(config);
    }

    /**
     * 使用STS鉴权方式初始化账号Client，推荐此方式。
     * <p>
     * 通过提供的AliMobile对象中的AccessKey ID、AccessKey Secret和Security Token信息，使用STS鉴权方式初始化阿里云短信服务的Client。
     *
     * @param aliMobile 包含AccessKey ID、AccessKey Secret和Security Token的AliMobile对象
     * @return 初始化后的阿里云短信服务Client对象
     * @throws Exception 如果初始化过程中发生异常，则抛出该异常
     */
    private com.aliyun.dysmsapi20170525.Client createClientWithSTS(AliMobile aliMobile) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                // 必填，您的 AccessKey ID
                .setAccessKeyId(aliMobile.getAccessKeyId())
                // 必填，您的 AccessKey Secret
                .setAccessKeySecret(aliMobile.getAccessKeySecret())
                // 必填，您的 Security Token
                .setSecurityToken(aliMobile.getSecurityToken())
                // 必填，表明使用 STS 方式
                .setType("sts");
        // Endpoint 请参考 https://api.aliyun.com/product/Dysmsapi
        config.endpoint = "dysmsapi.aliyuncs.com";
        return new com.aliyun.dysmsapi20170525.Client(config);
    }

    /**
     * 发送信息
     * <p>
     * 通过阿里云的短信服务发送短信信息。
     *
     * @param templateCode  模板代码，用于指定要发送的短信模板
     * @param phoneNumbers  电话号码，指定接收短信的电话号码
     * @param templateParam 模板参数，包含要填充到短信模板中的参数值
     * @return 如果短信发送成功，则返回true；否则返回false
     * @throws Exception 如果在发送短信过程中发生异常，则抛出该异常
     */
    public boolean sendMobile(String templateCode, String phoneNumbers, Map<String, Object> templateParam) throws Exception {
        // 查询参数值
        AliMobile aliMobile = getAliMobileBySysConfig(CONFIG_KEY_SIGN_NAME, templateCode, CONFIG_KEY_ACCESS_KEY_ID, CONFIG_KEY_ACCESS_KEY_SECRET, CONFIG_KEY_SECURITY_TOKEN);
        // 请确保代码运行环境设置了环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID 和 ALIBABA_CLOUD_ACCESS_KEY_SECRET。
        // 工程代码泄露可能会导致 AccessKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议使用更安全的 STS 方式，更多鉴权访问方式请参见：https://help.aliyun.com/document_detail/378657.html
        // com.aliyun.dysmsapi20170525.Client client = AliMobileUtils.createClient(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"), System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"));
        com.aliyun.dysmsapi20170525.Client client = createClient(aliMobile);
        com.aliyun.dysmsapi20170525.models.SendSmsRequest sendSmsRequest = new com.aliyun.dysmsapi20170525.models.SendSmsRequest()
                .setSignName(aliMobile.getSignName())
                .setTemplateCode(aliMobile.getTemplateCode())
                .setPhoneNumbers(phoneNumbers)
                .setTemplateParam(JSON.toJSONString(templateParam));
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        com.aliyun.dysmsapi20170525.models.SendSmsResponse resp = client.sendSmsWithOptions(sendSmsRequest, runtime);
        com.aliyun.teaconsole.Client.log(com.aliyun.teautil.Common.toJSONString(resp));
        if (resp.getStatusCode() == SEND_SMS_RESPONSE_STATUS_CODE) {
            if (Strings.isNotBlank(resp.getBody().getCode()) && SEND_SMS_RESPONSE_BODY_CODE.equals(resp.getBody().getCode().toUpperCase(Locale.ENGLISH))) {
                log.info("短信发送成功！" + phoneNumbers);
                return true;
            }
        }
        return false;
    }

    /**
     * 获取配置值
     * <p>
     * 根据提供的签名名称、模板代码、访问密钥ID、访问密钥秘密和安全令牌，从系统配置中获取对应的配置值，并填充到AliMobile对象中。
     *
     * @param signName        签名名称
     * @param templateCode    模板代码
     * @param accessKeyId     访问密钥ID
     * @param accessKeySecret 访问密钥秘密
     * @param securityToken   安全令牌
     * @return 填充了配置值的AliMobile对象
     * @throws Exception 如果在获取配置值的过程中发生异常，则抛出该异常
     */
    private AliMobile getAliMobileBySysConfig(String signName, String templateCode, String accessKeyId, String accessKeySecret, String securityToken) throws Exception {
        AliMobile aliMobile = new AliMobile();
        // 配置键
        List<String> configKeys = new ArrayList<>();
        configKeys.add(signName);
        configKeys.add(templateCode);
        configKeys.add(accessKeyId);
        configKeys.add(accessKeySecret);
        configKeys.add(securityToken);
        // 查询配置值
        FilterGroup filterGroup = new FilterGroup();
        filterGroup.addFilter(ColumnDefault.ENABLE_STATUS_FIELD, String.valueOf(ColumnDefault.ENABLE_STATUS_VALUE));
        filterGroup.addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
        filterGroup.addFilter("configKey", FilterGroup.Operator.in, String.join(",", configKeys));
        List<SysConfig> sysConfigs = dao.queryList(SysConfig.class, filterGroup, null);
        // 填充
        if (sysConfigs != null && sysConfigs.size() > 0) {
            for (SysConfig config : sysConfigs) {
                if (config == null || Strings.isBlank(config.getConfigKey())) {
                    continue;
                }
                if (config.isEncrypted()) {
                    SysConfigService.decrypt(config);
                }
                String value = config.getConfigValue();

                if (config.getConfigKey().equals(signName)) {
                    aliMobile.setSignName(value);
                } else if (config.getConfigKey().equals(templateCode)) {
                    aliMobile.setTemplateCode(value);
                } else if (config.getConfigKey().equals(accessKeyId)) {
                    aliMobile.setAccessKeyId(value);
                } else if (config.getConfigKey().equals(accessKeySecret)) {
                    aliMobile.setAccessKeySecret(value);
                } else if (config.getConfigKey().equals(securityToken)) {
                    aliMobile.setSecurityToken(value);
                }
            }
        }
        // 校验
        if (Strings.isBlank(aliMobile.getSignName()) || Strings.isBlank(aliMobile.getTemplateCode()) ||
                Strings.isBlank(aliMobile.getAccessKeyId()) || Strings.isBlank(aliMobile.getAccessKeySecret())) {
            throw new RuntimeException("短信模板需要的参数缺失。");
        }

        return aliMobile;
    }
}
