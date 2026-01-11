package cn.geelato.core.mql.parser;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.CommandValidator;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.keyword.SaveKeyword;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.meta.model.parser.FunctionParser;
import cn.geelato.core.util.EncryptUtils;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UIDGenerator;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 *
 * 解析保存命令所需的 JSON 文本结构，并生成 {@code SaveCommand}。
 *
 * <p>输入 JSON 的通用规则：</p>
 * <ul>
 *   <li>顶层为一个对象；如果包含特殊键 {@code @biz}，会被忽略。</li>
 *   <li>顶层的键为实体名称（例如：{@code user}、{@code sys_org}）。</li>
 *   <li>字段值统一按字符串读取；布尔/状态型字段会进行值规范化。</li>
 *   <li>支持子实体，键以 {@code #} 开头（例如：{@code #address}）。</li>
 *   <li>支持函数表达式字段，按 {@link cn.geelato.core.meta.model.parser.FunctionParser} 规则识别。</li>
 *   <li>主键与强制主键：
 *     <ul>
 *       <li>若请求中包含实体主键字段且有值，并且未设置 {@code forceId}，解析为更新（Update）。</li>
 *       <li>否则解析为插入（Insert）。插入时若存在 {@code forceId}，其值作为主键；否则自动生成。</li>
 *     </ul>
 *   </li>
 *   <li>默认字段填充：插入/更新时会按实体默认字段（如：{@code createAt}、{@code creator}、{@code updater} 等）进行填充。</li>
 *   <li>加密：当开启全局加密选项且列声明为加密时，字段值会进行加密，原始值保留在 {@code originValueMap}。</li>
 * </ul>
 *
 * <p>三种输入场景与结构：</p>
 * <ol>
 *   <li>
 *     单实体保存（对应 {@link #parse(String, cn.geelato.core.SessionCtx)}）：
 *     <pre>
 *     {
 *       "user": {
 *         "id": "U1001",               // 有值且未设置 forceId → Update；否则 Insert
 *         "name": "Alice",
 *         "enabled": "true",            // 布尔/状态型："true"→1，"false"→0
 *         "forceId": "U1001X",          // 仅 Insert 生效，作为主键
 *         "age": "${now()+1}"           // 函数字段示例（由 FunctionParser 解析）
 *         ,"#address": {                  // 子实体（对象）
 *           "city": "Shanghai",
 *           "street": "Nanjing Rd"
 *         }
 *         ,"#roles": [                    // 子实体（数组）
 *           {"code": "admin"},
 *           {"code": "user"}
 *         ]
 *       }
 *     }
 *     </pre>
 *   </li>
 *   <li>
 *     批量保存（对应 {@link #parseBatch(String, cn.geelato.core.SessionCtx)}）：
 *     <pre>
 *     {
 *       "user": [
 *         {"name": "Alice", "enabled": "true"},
 *         {"name": "Bob",   "enabled": "false"}
 *       ]
 *     }
 *     </pre>
 *   </li>
 *   <li>
 *     多实体保存（对应 {@link #parseMulti(String, cn.geelato.core.SessionCtx)}）：
 *     顶层为多个实体键；注意每个值必须是一个 <b>JSON 字符串</b>，其内容为该实体的对象结构。
 *     <pre>
 *     {
 *       "user": "{\"name\":\"Alice\",\"enabled\":\"true\"}",
 *       "dept": "{\"name\":\"R&D\"}"
 *     }
 *     </pre>
 *   </li>
 * </ol>
 *
 * <p>字段解析与行为说明：</p>
 * <ul>
 *   <li>布尔/状态型字段：当字段类型为 {@code boolean/Boolean} 或字段名等于内部状态字段（如删除/启用状态），
 *       字符串 {@code "true"} 映射为 {@code 1}，{@code "false"} 映射为 {@code 0}，其他保留原值。</li>
 *   <li>函数表达式：若值匹配函数语法，则重构为绑定实体名的表达式并以 {@code FunctionFieldValue} 形式保存。</li>
 *   <li>子实体：键以 {@code #} 开头，后续部分为子实体名称；
 *       值可为对象或数组，分别生成一个或多个子 {@code SaveCommand}，并关联到父命令。</li>
 *   <li>更新条件：更新命令的 {@code where} 仅包含主键等于请求值的过滤。</li>
 * </ul>
 *
 * <p>注意：</p>
 * <ul>
 *   <li>所有校验通过 {@code CommandValidator} 进行；字段/实体非法时会收集并抛出校验信息。</li>
 *   <li>默认字段值依赖 {@code SessionCtx}（如用户、租户、组织）与系统时间。</li>
 *   <li>顶层的 {@code @biz} 键（若存在）在解析前会被移除，不参与命令生成。</li>
 * </ul>
 *
 * @author geelato
 */
@Slf4j
public class JsonTextSaveParser extends JsonTextParser {

    private final static String SUB_ENTITY_FLAG = "#";
    private final static String KW_BIZ = "@biz";
    private final static String Force_ID = "forceId";

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
        if (!validator.validateEntity(commandName)) {
            logAndThrow(validator.getMessage(), "validateEntity failed: {}", commandName);
        }
        SaveCommand command = new SaveCommand();
        command.setEntityName(commandName);
        EntityMeta entityMeta = metaManager.getByEntityName(commandName);
        Map<String, Object> params = new HashMap<>();

        jo.keySet().forEach(key -> {
            if (key.startsWith(KEYWORD_FLAG) && StringUtils.hasText(jo.getString(key))) {
                String value = jo.getString(key);
                SaveKeyword kw = SaveKeyword.fromKey(key);
                if (kw != null) {
                    kw.handle(jo, key, value, command, validator, null, commandName);
                } else {
                    validator.appendMessage("[");
                    validator.appendMessage(key);
                    validator.appendMessage("]");
                    validator.appendMessage("不支持;");
                }
            } else if (key.startsWith(SUB_ENTITY_FLAG)) {
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
                // fields operate
                validator.validateField(key, "字段");
                FieldMeta fieldMeta = entityMeta.getFieldMeta(key);

                if (FunctionParser.isFunction(jo.getString(key))) {
                    String afterRefaceExpression = FunctionParser.reconstruct(jo.getString(key), entityMeta.getEntityName());
                    params.put(key, new FunctionFieldValue(fieldMeta, afterRefaceExpression));
                } else {
                    if (fieldMeta != null && (boolean.class.equals(fieldMeta.getFieldType())
                            || Boolean.class.equals(fieldMeta.getFieldType())
                            || FN_DEL_STATUS.equals(fieldMeta.getFieldName())
                            || FN_ENABLE_STATUS.equals(fieldMeta.getFieldName())
                    )) {
                        Object o = jo.get(key);
                        if (o instanceof Boolean) {
                            params.put(key, ((Boolean) o) ? 1 : 0);
                        } else if (o instanceof Number) {
                            int n = ((Number) o).intValue();
                            params.put(key, n);
                        } else {
                            String v = jo.getString(key).toLowerCase();
                            params.put(key, "true".equals(v) || "1".equals(v) ? 1 : ("false".equals(v) || "0".equals(v) ? 0 : v));
                        }
                    } else {
                        params.put(key, jo.getString(key));
                    }
                }
            }
        });
        if (!validator.isSuccess()) {
            logAndThrow(validator.getMessage(), "final validation failed (save)");
        }
        String[] fields = new String[params.size()];
        params.keySet().toArray(fields);
        String PK = validator.getPK();
        if (validator.hasPK(fields) && StringUtils.hasText(jo.getString(PK))
                && !StringUtils.hasText(jo.getString(Force_ID))) {
            generateUpdateCommand(sessionCtx, jo.getString(PK), PK,command, params);
        } else {
            generateInsertCommand(sessionCtx, commandName, command, params, PK);
        }
        if(GlobalContext.getColumnEncryptOption()){
            EncryptInner(command, entityMeta);
        }
        return command;
    }

    private void generateInsertCommand(SessionCtx sessionCtx, String commandName, SaveCommand command, Map<String, Object> params, String PK) {
        command.setCommandType(CommandType.Insert);
        Map<String, Object> entityMap = metaManager.newDefaultEntityMap(commandName);
        if (params.containsKey(Force_ID)) {
            entityMap.put(PK, params.get(Force_ID));
            params.remove(Force_ID);
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

    private void generateUpdateCommand(SessionCtx sessionCtx, String  pkValue, String pkKey, SaveCommand command, Map<String, Object> params) {
        FilterGroup fg = new FilterGroup();
        fg.addFilter(pkKey ,pkValue);
        command.setWhere(fg);
        command.setCommandType(CommandType.Update);
        params.remove(pkValue);
        putUpdateDefaultField(metaManager.newDefaultEntityMap(command.getEntityName()),params,sessionCtx);
        String[] updateFields = new String[params.size()];
        params.keySet().toArray(updateFields);
        command.setFields(updateFields);
        command.setValueMap(params);
        command.setPK(pkValue);
    }

    private static void EncryptInner(SaveCommand command, EntityMeta entityMeta) {
        Map<String, Object> originValueMap = command.getValueMap();
        command.setOriginValueMap(originValueMap);
        Map<String, Object> newValueMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : command.getValueMap().entrySet()) {
            boolean isEncrypt = entityMeta.getFieldMeta(entry.getKey()).getColumnMeta().isEncrypted();
            if (isEncrypt && entry.getValue() != null) {
                newValueMap.put(entry.getKey(), EncryptUtils.encrypt(entry.getValue().toString()));
            } else {
                newValueMap.put(entry.getKey(), entry.getValue());
            }
        }
        command.setValueMap(newValueMap);
    }


    private void putUpdateDefaultField(Map<String, Object> entity,Map<String, Object> params, SessionCtx sessionCtx) {
        if (entity.containsKey(FN_UPDATE_AT)) {
            params.put(FN_UPDATE_AT, simpleDateFormat.format(new Date()));
        }
        if (entity.containsKey(FN_UPDATER)) {
            params.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (entity.containsKey(FN_UPDATER_NAME)) {
            params.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
    }

    private void putInsertDefaultField(Map<String, Object> entity, SessionCtx sessionCtx) {
        if (entity.containsKey(FN_CREATE_AT)) {
            entity.put(FN_CREATE_AT, simpleDateFormat.format(new Date()));
        }
        if (entity.containsKey(FN_CREATOR)) {
            entity.put(FN_CREATOR, SessionCtx.getUserId());
        }
        if (entity.containsKey(FN_CREATOR_NAME)) {
            entity.put(FN_CREATOR_NAME, SessionCtx.getUserName());
        }
        if (entity.containsKey(FN_TENANT_CODE)) {
            entity.put(FN_TENANT_CODE, SessionCtx.getCurrentTenantCode());
        }
        if (entity.containsKey(FN_BU_ID)) {
            entity.put(FN_BU_ID, SessionCtx.getCurrentUser().getBuId());
        }
        if (entity.containsKey(FN_DEPT_ID)) {
            entity.put(FN_DEPT_ID, SessionCtx.getCurrentUser().getDefaultOrgId());
        }
        if (entity.containsKey(FN_UPDATE_AT)) {
            entity.put(FN_UPDATE_AT, simpleDateFormat.format(new Date()));
        }
        if (entity.containsKey(FN_UPDATER)) {
            entity.put(FN_UPDATER, SessionCtx.getUserId());
        }
        if (entity.containsKey(FN_UPDATER_NAME)) {
            entity.put(FN_UPDATER_NAME, SessionCtx.getUserName());
        }
        if (entity.containsKey(FN_DELETE_AT)) {
            entity.put(FN_DELETE_AT, DateUtils.DEFAULT_DELETE_AT);
        }
    }
}
