package cn.geelato.core.gql.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.gql.command.CommandType;
import cn.geelato.core.gql.command.CommandValidator;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.meta.model.parser.FunctionParser;
import cn.geelato.utils.UIDGenerator;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * @author geelato
 * 解析json字符串，并返回参数map
 */
@Slf4j
public class JsonTextSaveParser extends JsonTextParser {

    private final static String SUB_ENTITY_FLAG = "#";
    private final static String KW_BIZ = "@biz";


    /**
     * 解析传入的JSON文本，并返回一个SaveCommand对象。
     * 该方法首先使用JSON.parseObject将传入的JSON文本解析为JSONObject对象。
     * 如果JSONObject中包含KW_BIZ键，则将其移除。
     * 然后，从JSONObject中获取第一个键（即实体名称），并使用该实体名称和对应的JSONObject对象，以及一个CommandValidator对象，
     * 调用另一个parse方法进行解析，最终返回一个SaveCommand对象。
     *
     * @param jsonText   待解析的JSON文本
     * @param sessionCtx 会话上下文对象
     * @return 解析后的SaveCommand对象
     */
    public SaveCommand parse(String jsonText, SessionCtx sessionCtx) {
        JSONObject jo = JSON.parseObject(jsonText);
        CommandValidator validator = new CommandValidator();
        if (jo.containsKey(KW_BIZ)) {
            jo.remove(KW_BIZ);
        }
        String entityName = jo.keySet().iterator().next();
        return parse(sessionCtx, entityName, jo.getJSONObject(entityName), validator);
    }


    public List<SaveCommand> parseBatch(String jsonText, SessionCtx sessionCtx) {
        JSONObject jo = JSON.parseObject(jsonText);
        CommandValidator validator = new CommandValidator();
        if (jo.containsKey(KW_BIZ)) {
            jo.remove(KW_BIZ);
        }
        String entityName = jo.keySet().iterator().next();
        return parseBatch(sessionCtx, entityName, jo.getJSONArray(entityName), validator);
    }

    public List<SaveCommand> parseMulti(String jsonText, SessionCtx sessionCtx) {
        JSONObject jo = JSON.parseObject(jsonText);
        CommandValidator validator = new CommandValidator();
        if (jo.containsKey(KW_BIZ)) {
            jo.remove(KW_BIZ);
        }
        return parseMulti(sessionCtx, jo, validator);
    }

    private List<SaveCommand> parseMulti(SessionCtx sessionCtx, JSONObject jo, CommandValidator validator) {
        List<SaveCommand> saveCommandList = new ArrayList<>();
        for (String entityName : jo.keySet()) {
            String value = jo.getString(entityName);
            JSONObject o = JSONObject.parseObject(value);
            SaveCommand saveCommand = parse(sessionCtx, entityName, o, validator);
            saveCommandList.add(saveCommand);
        }
        return saveCommandList;
    }

    private List<SaveCommand> parseBatch(SessionCtx sessionCtx, String commandName, JSONArray jsonArray, CommandValidator validator) {
        List<SaveCommand> saveCommandList = new ArrayList<>();
        for (Object o : jsonArray) {
            SaveCommand saveCommand = parse(sessionCtx, commandName, (JSONObject) o, validator);
            saveCommandList.add(saveCommand);
        }
        return saveCommandList;
    }

    /**
     * 递归解析保存操作命令，里面变更在执行期再解析，不在此解析
     */
    private SaveCommand parse(SessionCtx sessionCtx, String commandName, JSONObject jo, CommandValidator validator) {
        Assert.isTrue(validator.validateEntity(commandName), validator.getMessage());
        SaveCommand command = new SaveCommand();
        command.setEntityName(commandName);
        EntityMeta entityMeta = metaManager.getByEntityName(commandName);
        Map<String, Object> params = new HashMap<>();

        jo.keySet().forEach(key -> {
            if (key.startsWith(SUB_ENTITY_FLAG)) {
                Object sub = jo.get(key);
                CommandValidator subValidator = new CommandValidator();
                if (sub instanceof JSONObject) {
                    SaveCommand subCommand = parse(sessionCtx, key.substring(1), (JSONObject) sub, subValidator);
                    subCommand.setParentCommand(command);
                    command.getCommands().add(subCommand);
                } else if (sub instanceof JSONArray) {
                    ((JSONArray) sub).forEach(subJo -> {
                        SaveCommand subCommand = parse(sessionCtx, key.substring(1), (JSONObject) subJo, subValidator);
                        subCommand.setParentCommand(command);
                        command.getCommands().add(subCommand);
                    });
                } else {
                    validator.appendMessage(key + "的值应为object或array");
                }
            } else {
                // 字段
                validator.validateField(key, "字段");
                // 对于boolean类型的值，转为数值，以值存到数据库
                FieldMeta fieldMeta = entityMeta.getFieldMeta(key);

                if (FunctionParser.isFunction(jo.getString(key))) {
                    String afterRefaceExpression = FunctionParser.reconstruct(jo.getString(key), entityMeta.getEntityName());
                    params.put(key, new FunctionFieldValue(fieldMeta, afterRefaceExpression));
                } else {
                    if (fieldMeta != null && (boolean.class.equals(fieldMeta.getFieldType())
                            || Boolean.class.equals(fieldMeta.getFieldType())
                            || "delStatus".equals(fieldMeta.getFieldName())
                            || "enableStatus".equals(fieldMeta.getFieldName())
                    )) {
                        String v = jo.getString(key).toLowerCase();
                        params.put(key, "true".equals(v) ? 1 : ("false".equals(v) ? 0 : v));
                    } else {
                        params.put(key, jo.getString(key));
                    }
                }
            }
        });
        Assert.isTrue(validator.isSuccess(), validator.getMessage());

        String[] fields = new String[params.keySet().size()];
        params.keySet().toArray(fields);
        String PK = validator.getPK();
        if (validator.hasPK(fields) && StringUtils.hasText(jo.getString(PK)) && !StringUtils.hasText(jo.getString("forceId"))) {
            // update
            FilterGroup fg = new FilterGroup();
            fg.addFilter(PK, jo.getString(PK));
            command.setWhere(fg);
            command.setCommandType(CommandType.Update);
            params.remove(PK);
            putUpdateDefaultField(params, sessionCtx);
            String[] updateFields = new String[params.keySet().size()];
            params.keySet().toArray(updateFields);
            command.setFields(updateFields);
            command.setValueMap(params);
            command.setPK(jo.getString(PK));
        } else {
            // insert
            command.setCommandType(CommandType.Insert);
            Map<String, Object> entityMap = metaManager.newDefaultEntityMap(commandName);
            if (params.containsKey("forceId")) {
                entityMap.put(PK, params.get("forceId"));
                params.remove("forceId");
            } else {
                entityMap.put(PK, UIDGenerator.generate());
                params.remove(PK);
            }
            entityMap.putAll(params);
            putInsertDefaultField(entityMap, sessionCtx);
            String[] insertFields = new String[entityMap.size()];
            entityMap.keySet().toArray(insertFields);
            command.setFields(insertFields);
            command.setValueMap(entityMap);
            command.setPK(entityMap.get(PK).toString());
        }
        return command;
    }


    private void putUpdateDefaultField(Map<String, Object> entity, SessionCtx sessionCtx) {
        if (entity.containsKey("updateAt")) {
            entity.put("updateAt", simpleDateFormat.format(new Date()));
        }
        if (entity.containsKey("updater")) {
            entity.put("updater", SessionCtx.getUserId());
        }
        if (entity.containsKey("updaterName")) {
            entity.put("updaterName", SessionCtx.getUserName());
        }
    }

    private void putInsertDefaultField(Map<String, Object> entity, SessionCtx sessionCtx) {
        if (entity.containsKey("createAt")) {
            entity.put("createAt", simpleDateFormat.format(new Date()));
        }
        if (entity.containsKey("creator")) {
            entity.put("creator", SessionCtx.getUserId());
        }
        if (entity.containsKey("creatorName")) {
            entity.put("creatorName", SessionCtx.getUserName());
        }
        if (entity.containsKey("tenantCode")) {
            entity.put("tenantCode", SessionCtx.getCurrentTenantCode());
        }
        if (entity.containsKey("buId")) {
            entity.put("buId", SessionCtx.getCurrentUser().getBuId());
        }
        if (entity.containsKey("deptId")) {
            entity.put("deptId", SessionCtx.getCurrentUser().getDefaultOrgId());
        }
        putUpdateDefaultField(entity, sessionCtx);
    }
}
