package cn.geelato.web.platform.graal.service;

import cn.geelato.core.gql.GqlManager;
import cn.geelato.core.gql.command.QueryCommand;
import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.meta.User;
import cn.geelato.web.platform.graal.ApplicationContextProvider;
import cn.geelato.web.platform.graal.entity.EntityField;
import cn.geelato.web.platform.graal.entity.EntityGraal;
import cn.geelato.web.platform.graal.entity.EntityOrder;
import cn.geelato.web.platform.graal.entity.EntityParams;
import cn.geelato.web.platform.graal.utils.GraalUtils;
import cn.geelato.web.platform.graal.utils.NumbChineseUtils;
import cn.geelato.web.platform.srv.platform.service.RuleService;
import cn.geelato.web.platform.srv.security.service.UserService;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@GraalService(name = "fn", built = "true", descrption = "帮助方法")
public class FnService {

    private final RuleService ruleService;

    public FnService() {
        GraalUtils.getCurrentTenantCode();
        // 使用ApplicationContextProvider获取RuleService
        this.ruleService = ApplicationContextProvider.getBean(RuleService.class);
    }

    @GraalFunction(example = "$gl.fn.getUser({userId})", description = "根据用户ID获取用户信息")
    public User getUser(String userId) {
        UserService userService = new UserService();
        return userService.getModel(User.class, userId);
    }

    @GraalFunction(example = "$gl.fn.toChineseCurrency({digit})", description = "将数字金额转换为中文大写")
    public String toChineseCurrency(String digit) {
        return NumbChineseUtils.byOldChinese(digit);
    }

    @GraalFunction(example = "$gl.fn.dateText({format},{dateStr})", description = "按格式输出日期文本，dateStr为空则取当前时间")
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

    /**
     * 对数据进行分组求和操作。
     *
     * @param data       要处理的数据数组
     * @param groupField 分组依据的字段名
     * @param sumFields  需要求和的字段名数组
     * @return 返回一个包含分组求和结果的Map，键为分组依据的字段值，值为对应字段求和的结果
     */
    @GraalFunction(example = "$gl.fn.groupSum({data},{groupField},{sumFields})", description = "对列表数据按照字段分组并进行求和")
    public Map<String, Object> groupSum(Object[] data, String groupField, String[] sumFields) {
        Map<String, Object> map = new HashMap<>();
        return map;
    }

    /**
     * 实体保存方法
     * <p>
     * 将传入的实体参数和附加参数转换为实体保存请求，并调用保存服务进行保存。
     *
     * @param entityParams 包含实体信息的参数Map，用于指定要保存的实体及其字段值
     * @param params       附加参数Map，可能包含一些额外的配置或参数
     * @return 返回保存操作的结果字符串
     * @throws RuntimeException 如果实体名称或字段为空，则抛出运行时异常
     */
    @GraalFunction(example = "$gl.fn.convertEntitySaver({entityParams},{params})", description = "将实体保存参数转换为规则引擎保存请求并执行")
    public String convertEntitySaver(Map<String, Object> entityParams, Map<String, Object> params) {
        EntityGraal entitySaver = JSON.parseObject(JSON.toJSONString(entityParams), EntityGraal.class);
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
        // 切换数据源，根据实体名称获取对应的数据源
        switchDataSource(entitySaver.getEntity());
        return ruleService.save("0", sb.toString());
    }

    /**
     * 实体查询
     * <p>
     * 根据提供的实体参数执行查询，并返回查询结果。
     *
     * @param entityParams 包含查询所需参数的Map，包括实体名称、字段列表、排序规则、分页信息和查询条件等
     * @return 返回查询结果，结果以Map列表的形式表示
     * @throws RuntimeException 如果实体名称或字段列表为空，则抛出此异常
     */
    @GraalFunction(example = "$gl.fn.convertEntityReader({entityParams},{params})", description = "将实体查询参数转换为规则引擎查询并返回结果")
    public Object convertEntityReader(Map<String, Object> entityParams, Map<String, Object> params) {
        EntityGraal entityReader = JSON.parseObject(JSON.toJSONString(entityParams), EntityGraal.class);
        if (StringUtils.isBlank(entityReader.getEntity()) || entityReader.getFields() == null || entityReader.getFields().isEmpty()) {
            throw new RuntimeException("entityName or fields is empty");
        }
        StringBuffer entity = new StringBuffer();
        entity.append("{\"").append(entityReader.getEntity()).append("\":{");
        // 构建查询字段，@fs
        List<String> fields = new ArrayList<>();
        for (EntityField field : entityReader.getFields()) {
            StringBuffer sb = new StringBuffer();
            if (StringUtils.isBlank(field.getName())) {
                continue;
            }
            sb.append(field.getName());
            if (StringUtils.isNotBlank(field.getAlias())) {
                sb.append(" ").append(field.getAlias());
            }
            fields.add(sb.toString());
        }
        entity.append("\"@fs\":\"").append(StringUtils.join(fields, ",")).append("\",");
        // 构建查询排序，@order
        if (entityReader.getOrder() != null && !entityReader.getOrder().isEmpty()) {
            StringBuffer sb = new StringBuffer();
            for (EntityOrder order : entityReader.getOrder()) {
                if (StringUtils.isBlank(order.getField()) || StringUtils.isBlank(order.getOrder())) {
                    continue;
                }
                sb.append(order.getField()).append("|").append(order.getOrder()).append(",");
            }
            entity.append("\"@order\":\"").append(sb.deleteCharAt(sb.length() - 1)).append("\",");
        }
        // 构建分页查询，@p
        if (entityReader.getPageSize() != null && entityReader.getPageSize() > 0) {
            entity.append("\"@p\":\"").append("1").append(",").append(entityReader.getPageSize()).append("\",");
        }
        // 构建查询条件，@q
        if (entityReader.getParams() != null && !entityReader.getParams().isEmpty()) {
            for (EntityParams param : entityReader.getParams()) {
                if (StringUtils.isBlank(param.getName()) || StringUtils.isBlank(param.getCop()) || StringUtils.isBlank(param.getValueExpression())) {
                    continue;
                }
                entity.append("\"").append(param.getName()).append("|").append(param.getCop()).append("\":\"").append(param.getValueExpression()).append("\",");
            }
        }
        entity.deleteCharAt(entity.length() - 1);
        entity.append("}}");
        // 切换数据链接，执行查询
        switchDataSource(entityReader.getEntity());
        return ruleService.queryForMapList(entity.toString(), true);
    }

    @GraalFunction(example = "$gl.fn.queryForMapList({gql},{withMeta})", description = "执行GQL查询，返回带或不带元数据的分页结果")
    public ApiPagedResult<List<Map<String, Object>>> queryForMapList(String gql, boolean withMeta) {
        QueryCommand command = GqlManager.singleInstance().generateQuerySql(gql);
        switchDataSource(command.getEntityName());
        return ruleService.queryForMapList(gql, withMeta);
    }

    private void switchDataSource(String entityName) {
        if (Strings.isBlank(entityName)) {
            throw new RuntimeException("entityName is empty");
        }
        EntityMeta entityMeta = MetaManager.singleInstance().getByEntityName(entityName);
        if (entityMeta == null) {
            throw new RuntimeException("The model does not exist in memory");
        }
        DynamicDataSourceHolder.setDataSourceKey(entityMeta.getTableMeta().getConnectId());
    }
}
