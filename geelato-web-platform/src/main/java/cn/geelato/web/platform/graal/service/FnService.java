package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.utils.NumbChineseUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.graal.ApplicationContextProvider;
import cn.geelato.web.platform.graal.GraalUtils;
import cn.geelato.web.platform.graal.entity.EntityField;
import cn.geelato.web.platform.graal.entity.EntitySaver;
import cn.geelato.web.platform.m.base.service.RuleService;
import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.m.security.service.UserService;
import com.alibaba.fastjson2.JSON;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@GraalService(name = "fn", built = "true")
public class FnService {

    private RuleService ruleService;

    public FnService() {
        // 使用ApplicationContextProvider获取RuleService
        this.ruleService = ApplicationContextProvider.getBean(RuleService.class);
    }

    public User getUser(String userId) {
        UserService userService = new UserService();
        return userService.getModel(User.class, userId);
    }

    public String toChineseCurrency(String digit) {
        return NumbChineseUtils.byOldChinese(digit);
    }

    public String dateText(String targetFormat, String dateStr) throws ParseException {
        String formatDate = null;
        Date date = null;
        SimpleDateFormat targetDateFormat = new SimpleDateFormat(targetFormat);
        if (StringUtils.isEmpty(dateStr)) {
            date = new Date();
        } else {
            date = targetDateFormat.parse(dateStr);
        }
        formatDate = targetDateFormat.format(date);
        return formatDate;
    }

    public String convertEntitySaver(Map<String, Object> entityParams, Map<String, Object> params) {
        EntitySaver entitySaver = JSON.parseObject(JSON.toJSONString(entityParams), EntitySaver.class);
        if (StringUtils.isBlank(entitySaver.getEntity()) || entitySaver.getFields() == null || entitySaver.getFields().isEmpty()) {
            throw new RuntimeException("entityName or fields is empty");
        }
        Map<String, Object> paramsMap = new HashMap<>();
        for (EntityField field : entitySaver.getFields()) {
            if (StringUtils.isNotBlank(field.getName())) {
                if (StringUtils.isNotBlank(field.getValue())) {
                    paramsMap.put(field.getName(), field.getValue());
                } else if (StringUtils.isNotBlank(field.getValueExpression())) {
                    if (params != null && params.containsKey(field.getValueExpression())) {
                        paramsMap.put(field.getName(), params.get(field.getValueExpression()));
                    } else {
                        Object value = JsProvider.executeExpression(field.getValueExpression(), params);
                        paramsMap.put(field.getName(), value);
                    }
                }
            }
        }
        StringBuffer sb = new StringBuffer();
        sb.append("{\"@biz\":\"0\",\"").append(entitySaver.getEntity()).append("\":{");
        for (Map.Entry<String, Object> entry : paramsMap.entrySet()) {
            sb.append("\"").append(entry.getKey()).append("\":").append(JSON.toJSONString(entry.getValue())).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("}}");
        return ruleService.save("0", sb.toString());
    }
}
