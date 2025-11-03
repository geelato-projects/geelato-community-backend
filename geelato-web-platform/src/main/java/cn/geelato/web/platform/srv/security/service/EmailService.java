package cn.geelato.web.platform.srv.security.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.meta.SysConfig;
import cn.geelato.web.platform.srv.platform.service.SysConfigService;
import cn.geelato.web.platform.srv.security.entity.AliEmail;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author diabl
 * 发送邮件服务类
 */
@Component
@Slf4j
public class EmailService {
    private static final String CONFIG_KEY_EMAIL_PORT = "emailPort";
    private static final String CONFIG_KEY_EMAIL_HOST = "emailHost";
    private static final String CONFIG_KEY_EMAIL_USERNAME = "emailUserName";
    private static final String CONFIG_KEY_EMAIL_PASSWORD = "emailPassWord";

    @Autowired
    @Qualifier("primaryDao")
    public Dao dao;

    /**
     * 发送HTML格式邮件
     * <p>
     * 根据提供的收件人、主题和内容，发送HTML格式的邮件。
     *
     * @param to      收件人的邮箱地址
     * @param subject 邮件的主题
     * @param content 邮件的正文内容，应为HTML格式
     * @return 如果邮件发送成功，则返回true；否则返回false
     */
    public boolean sendHtmlMail(String to, String subject, String content) {
        try {
            AliEmail aliEmail = getAliEmailBySysConfig(CONFIG_KEY_EMAIL_HOST, CONFIG_KEY_EMAIL_PORT, CONFIG_KEY_EMAIL_USERNAME, CONFIG_KEY_EMAIL_PASSWORD);
            JavaMailSenderImpl javaMailSender = setJavaMailSender(aliEmail);
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(message, true);
            messageHelper.setFrom(aliEmail.getUsername());
            InternetAddress[] internetAddressTo = InternetAddress.parse(to);
            messageHelper.setTo(internetAddressTo);
            message.setSubject(subject);
            messageHelper.setText(content, true);
            javaMailSender.send(message);
            log.info("邮件发送成功！");
            return true;
        } catch (Exception e) {
            log.error("发送邮件时发生异常！", e);
        }
        return false;
    }

    private JavaMailSenderImpl setJavaMailSender(AliEmail aliEmail) {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setPort(aliEmail.getPort());
        javaMailSender.setHost(aliEmail.getHost());
        javaMailSender.setUsername(aliEmail.getUsername());
        javaMailSender.setPassword(aliEmail.getPassword());
        javaMailSender.setDefaultEncoding("utf-8");
        javaMailSender.setProtocol(JavaMailSenderImpl.DEFAULT_PROTOCOL);
        Properties props = javaMailSender.getJavaMailProperties();
        props.put("mail.debug", true);
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.ssl.enable", true);
        props.put("mail.smtp.ssl.trust", aliEmail.getHost());
        props.put("mail.smtp.socketFactoryClass", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.starttls.enable", true);
        props.put("mail.smtp.starttls.required", true);

        return javaMailSender;
    }

    private AliEmail getAliEmailBySysConfig(String host, String port, String userName, String password) throws Exception {
        AliEmail aliEmail = new AliEmail();
        // 配置键
        List<String> configKeys = new ArrayList<>();
        configKeys.add(host);
        configKeys.add(port);
        configKeys.add(userName);
        configKeys.add(password);
        // 查询配置值
        FilterGroup filterGroup = new FilterGroup();
        filterGroup.addFilter(ColumnDefault.ENABLE_STATUS_FIELD, String.valueOf(ColumnDefault.ENABLE_STATUS_VALUE));
        filterGroup.addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
        filterGroup.addFilter("configKey", FilterGroup.Operator.in, String.join(",", configKeys));
        List<SysConfig> sysConfigs = dao.queryList(SysConfig.class, filterGroup, null);
        // 填充
        if (sysConfigs != null && !sysConfigs.isEmpty()) {
            for (SysConfig config : sysConfigs) {
                if (config == null || Strings.isBlank(config.getConfigKey())) {
                    continue;
                }
                config.afterSet();
                if (config.isEncrypted()) {
                    SysConfigService.decrypt(config);
                }
                String value = config.getConfigValue();
                if (config.getConfigKey().equals(host)) {
                    aliEmail.setHost(value);
                } else if (config.getConfigKey().equals(port)) {
                    try {
                        int pos = Strings.isNotBlank(value) ? Integer.parseInt(value) : JavaMailSenderImpl.DEFAULT_PORT;
                        aliEmail.setPort(pos > -1 ? pos : JavaMailSenderImpl.DEFAULT_PORT);
                    } catch (Exception ex) {
                        aliEmail.setPort(JavaMailSenderImpl.DEFAULT_PORT);
                    }
                } else if (config.getConfigKey().equals(userName)) {
                    aliEmail.setUsername(value);
                } else if (config.getConfigKey().equals(password)) {
                    aliEmail.setPassword(value);
                }
            }
        }
        // 校验
        if (Strings.isBlank(aliEmail.getHost()) || aliEmail.getPort() <= -1 || Strings.isBlank(aliEmail.getUsername()) || Strings.isBlank(aliEmail.getPassword())) {
            throw new RuntimeException("发送邮件需要的参数缺失。");
        }

        return aliEmail;
    }
}
