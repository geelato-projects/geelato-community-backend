package cn.geelato.web.platform.m.security.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.security.entity.*;
import cn.geelato.web.platform.m.security.enums.EncodingItemTypeEnum;
import cn.geelato.web.platform.m.security.enums.EncodingSerialTypeEnum;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author diabl
 */
@Component
@Slf4j
public class EncodingService extends BaseService {
    private static final String ENCODING_LOCK_PREFIX = "ENCODING_LOCK";
    private static final String ENCODING_LIST_PREFIX = "ENCODING_LIST";
    private static final String ENCODING_ITEM_PREFIX = "ENCODING_ITEM_";
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private OrgService orgService;

    /**
     * 移除缓存
     * <p>
     * 根据提供的编码信息，从缓存中移除对应的编码项。
     *
     * @param encoding 要从缓存中移除的编码信息对象
     */
    public void redisTemplateEncodingDelete(Encoding encoding) {
        encoding.afterSet();
        String redisItemKey = ENCODING_ITEM_PREFIX + encoding.getId();
        List<Object> redisItemKeys = redisTemplate.opsForList().range(ENCODING_LIST_PREFIX, 0, -1);
        // 清理
        if (redisItemKeys != null && redisItemKeys.contains(redisItemKey)) {
            redisTemplate.delete(redisItemKey);
            redisTemplate.opsForList().remove(ENCODING_LIST_PREFIX, 1, redisItemKey);
        }
    }

    /**
     * 更新缓存
     * <p>
     * 根据提供的编码对象，更新缓存中的相关信息。
     *
     * @param encoding 编码对象，包含需要更新的编码信息
     */
    public void redisTemplateEncodingUpdate(Encoding encoding) {
        encoding.afterSet();
        String redisItemKey = ENCODING_ITEM_PREFIX + encoding.getId();
        List<Object> redisItemKeys = redisTemplate.opsForList().range(ENCODING_LIST_PREFIX, 0, -1);
        // 设置缓存
        if (redisItemKeys == null || redisItemKeys.isEmpty()) {
            redisItemKeys = redisTemplateEncoding();
        }
        // 清理
        if (redisItemKeys.contains(redisItemKey)) {
            redisTemplate.delete(redisItemKey);
            redisTemplate.opsForList().remove(ENCODING_LIST_PREFIX, 1, redisItemKey);
        }
        // 重新获取
        redisTemplateEncodingItem(encoding);
    }

    /**
     * 设置缓存
     * <p>
     * 将指定的编码对象及其对应的流水号列表存储到Redis缓存中，并设置缓存的过期时间。
     *
     * @param encoding 编码对象，包含编码的ID和日期类型等信息
     */
    private void redisTemplateEncodingItem(Encoding encoding) {
        String redisItemKey = ENCODING_ITEM_PREFIX + encoding.getId();
        List<Object> serials = querySerialsByEncodingLog(encoding);
        log.info("{} 流水号：{}", redisItemKey, JSON.toJSONString(serials));
        redisTemplateListRightPush(redisItemKey, serials);
        redisTemplate.expire(redisItemKey, DateUtils.timeInterval(encoding.getDateType()), TimeUnit.SECONDS);
    }

    /**
     * 获取Redis中的编码模板项
     * <p>
     * 从Redis中获取编码模板项列表，如果Redis中没有存储，则从数据库中查询并存储到Redis中。
     *
     * @return 返回包含编码模板项Redis键的列表
     */
    public List<Object> redisTemplateEncoding() {
        List<Object> redisItemKeys = redisTemplate.opsForList().range(ENCODING_LIST_PREFIX, 0, -1);
        if (redisItemKeys == null || redisItemKeys.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("enableStatus", ColumnDefault.ENABLE_STATUS_VALUE);
            List<Encoding> encodingList = queryModel(Encoding.class, params);
            for (Encoding encoding : encodingList) {
                encoding.afterSet();
                String redisItemKey = ENCODING_ITEM_PREFIX + encoding.getId();
                redisTemplateEncodingItem(encoding);
                if (redisItemKeys != null) {
                    redisItemKeys.add(redisItemKey);
                }
            }
            if (redisItemKeys != null && !redisItemKeys.isEmpty()) {
                log.info("编码模板：" + JSON.toJSONString(redisItemKeys));
                redisTemplateListRightPush(ENCODING_LIST_PREFIX, redisItemKeys);
                redisTemplate.expire(ENCODING_LIST_PREFIX, 1, TimeUnit.DAYS);
            }
        }

        return redisItemKeys;
    }

    /**
     * 编码记录，查询流水号
     * <p>
     * 根据提供的编码信息，查询对应的流水号记录。
     *
     * @param encoding 包含编码信息的Encoding对象
     * @return 返回包含查询到的流水号记录的List集合
     */
    public List<Object> querySerialsByEncodingLog(Encoding encoding) {
        List<Object> serials = new ArrayList<>();
        // 查询日志
        Map<String, Object> logParams = new HashMap<>();
        logParams.put("encodingId", encoding.getId());
        logParams.put("template", encoding.getFormatExample());
        logParams.put("enableStatus", ColumnDefault.ENABLE_STATUS_VALUE);
        if (Strings.isNotBlank(encoding.getDateType())) {
            logParams.put("exampleDate", new SimpleDateFormat(encoding.getDateType()).format(new Date()));
        }
        log.info("logParams：" + JSON.toJSONString(logParams));
        List<EncodingLog> encodingLogList = queryModel(EncodingLog.class, logParams);
        if (encodingLogList != null && !encodingLogList.isEmpty()) {
            for (EncodingLog log : encodingLogList) {
                if (Strings.isNotBlank(log.getExampleSerial())) {
                    serials.add(log.getExampleSerial());
                }
            }
            serials = formatSerialList(serials);
        }
        return serials;
    }

    /**
     * 编码实例生成
     * <p>
     * 根据提供的编码表单和参数，生成编码实例。
     *
     * @param form     编码表单对象，包含编码模板等信息
     * @param argument 包含生成编码实例所需参数的Map对象
     * @return 返回生成的编码实例字符串
     */
    public String generate(Encoding form, Map<String, Object> argument) {
        redisTemplateEncoding();
        if (Strings.isBlank(form.getTemplate())) {
            return null;
        }
        List<EncodingItem> itemList = JSON.parseArray(form.getTemplate(), EncodingItem.class);
        if (itemList == null || itemList.isEmpty()) {
            return null;
        }
        form.afterSet();
        String redisItemKey = ENCODING_ITEM_PREFIX + form.getId();
        // 记录
        EncodingLog encodingLog = new EncodingLog();
        encodingLog.setEncodingId(form.getId());
        encodingLog.setEnableStatus(ColumnDefault.ENABLE_STATUS_VALUE);
        encodingLog.setTemplate(form.getFormatExample());
        // 系统变量
        Map<String, Object> variableParams = getVariable(itemList, argument.get("appId") == null ? null : String.valueOf(argument.get("appId")));
        // 编码实例
        List<String> examples = new ArrayList<>();
        for (EncodingItem item : itemList) {
            if (EncodingItemTypeEnum.CONSTANT.getValue().equals(item.getItemType())) {
                // 常量
                if (Strings.isNotBlank(item.getConstantValue())) {
                    examples.add(item.getConstantValue());
                }
            } else if (EncodingItemTypeEnum.ARGUMENT.getValue().equals(item.getItemType())) {
                if (Strings.isNotBlank(item.getConstantValue())) {
                    Object value = argument.get(item.getConstantValue());
                    if (value != null) {
                        examples.add(String.valueOf(value));
                    }
                }
            } else if (EncodingItemTypeEnum.VARIABLE.getValue().equals(item.getItemType())) {
                if (Strings.isNotBlank(item.getConstantValue())) {
                    Object value = variableParams.get(item.getConstantValue());
                    if (value != null && Strings.isNotBlank(String.valueOf(value))) {
                        examples.add(String.valueOf(value));
                    }
                }
            } else if (EncodingItemTypeEnum.SERIAL.getValue().equals(item.getItemType())) {
                // 序列号
                String serial = getSerialByRedisLock(redisItemKey, item);
                if (Strings.isBlank(serial)) {
                    throw new RuntimeException(ApiErrorMsg.SERIAL_USE_UP);
                }
                encodingLog.setExampleSerial(serial);
                examples.add(serial);
            } else if (EncodingItemTypeEnum.DATE.getValue().equals(item.getItemType())) {
                // 日期
                if (Strings.isNotBlank(item.getDateType())) {
                    try {
                        String date = new SimpleDateFormat(item.getDateType()).format(new Date());
                        examples.add(date);
                        encodingLog.setExampleDate(date);
                    } catch (Exception ex) {
                        log.error("日期解析失败", ex);
                    }
                }
            }
        }
        String separator = Strings.isNotBlank(form.getSeparators()) ? form.getSeparators() : "";
        encodingLog.setExample(String.join(separator, examples));
        log.info("{} 记录：{}", redisItemKey, JSON.toJSONString(encodingLog));
        if (Strings.isBlank(encodingLog.getId()) && Strings.isBlank(encodingLog.getTenantCode())) {
            encodingLog.setTenantCode(getSessionTenantCode());
        }
        dao.save(encodingLog);
        if (Strings.isNotBlank(encodingLog.getExampleSerial())) {
            redisTemplate.opsForList().rightPush(redisItemKey, encodingLog.getExampleSerial());
        }

        return encodingLog.getExample();
    }

    /**
     * 通过Redis锁获取流水号
     * <p>
     * 根据提供的Redis项键和编码项信息，通过Redis锁机制安全地获取流水号。
     *
     * @param redisItemKey Redis项键，用于标识需要获取流水号的Redis项
     * @param item         编码项信息，包含流水号的类型和位数等信息
     * @return 返回生成的流水号字符串，如果获取失败则返回null
     */
    private String getSerialByRedisLock(String redisItemKey, EncodingItem item) {
        // 获取锁 加上uuid防止误删除锁
        String uuid = System.currentTimeMillis() + UUID.randomUUID().toString().replaceAll("-", "");
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(ENCODING_LOCK_PREFIX, uuid, 10, TimeUnit.SECONDS);
        // 如果获取到锁执行步骤 最后释放锁
        String serial = null;
        if (lock) {
            if (EncodingSerialTypeEnum.ORDER.getValue().equals(item.getSerialType())) {
                // 顺序
                serial = getOrderSerial(redisItemKey, item.getSerialDigit(), item.isCoverPos());
            } else if (EncodingSerialTypeEnum.RANDOM.getValue().equals(item.getSerialType())) {
                // 随机
                serial = getRandomSerial(redisItemKey, item.getSerialDigit(), item.getRandomRange());
            }
            // 在极端情况下仍然会误删除锁
            // 因此使用lua脚本的方式来防止误删除
            String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1]\n" + "then\n" + "    return redis.call(\"del\",KEYS[1])\n" + "else\n" + "    return 0\n" + "end";
            DefaultRedisScript defaultRedisScript = new DefaultRedisScript();
            defaultRedisScript.setScriptText(script);
            defaultRedisScript.setResultType(Long.class);
            redisTemplate.execute(defaultRedisScript, List.of(ENCODING_LOCK_PREFIX), uuid);
        } else {
            // 如果没有获取到锁 重试
            try {
                Thread.sleep(100);
                serial = getSerialByRedisLock(redisItemKey, item);
            } catch (InterruptedException e) {
                log.error("redisLockError", e);
            }
        }

        return serial;
    }

    /**
     * 获取顺序流水号
     * <p>
     * 根据提供的Redis键、流水号位数和是否覆盖前导零的参数，获取下一个顺序流水号。
     *
     * @param redisItemKey Redis键，用于在Redis中存储流水号信息
     * @param serialDigit  流水号的位数
     * @param coverPos     是否覆盖前导零
     * @return 返回下一个顺序流水号字符串
     */
    private String getOrderSerial(String redisItemKey, int serialDigit, boolean coverPos) {
        List<Object> redisSerials = redisTemplate.opsForList().range(redisItemKey, 0, -1);
        if (redisSerials == null || redisSerials.isEmpty()) {
            return coverPos ? String.format("%0" + serialDigit + "d", 1) : String.valueOf(1);
        }
        redisSerials = formatSerialList(redisSerials);
        long max = Long.parseLong(String.valueOf(redisSerials.get(redisSerials.size() - 1)));
        long radius = Long.parseLong(UUIDUtils.generateFixation(serialDigit, 9));
        return radius > max ? (coverPos ? String.format("%0" + serialDigit + "d", max + 1) : String.valueOf((max + 1))) : null;
    }

    /**
     * 生成随机流水号
     * <p>
     * 根据提供的Redis键、流水号位数和可选的字符范围，生成一个随机的流水号。
     *
     * @param redisItemKey Redis中的键，用于检查生成的流水号是否已经存在
     * @param serialDigit  流水号的位数
     * @param range        可选的字符范围，默认为"0123456789"，如果传入空字符串则使用默认值
     * @return 返回生成的随机流水号字符串，如果无法生成则返回null
     */
    private String getRandomSerial(String redisItemKey, int serialDigit, String range) {
        range = Strings.isBlank(range) ? "0123456789" : range;
        List<Object> redisSerials = redisTemplate.opsForList().range(redisItemKey, 0, -1);
        String serial = null;
        long radius = Long.parseLong(UUIDUtils.generateFixation(serialDigit, range.length() - 1));
        for (int i = 0; i < radius; i++) {
            serial = UUIDUtils.generate(serialDigit, range);
            if (redisSerials != null && Strings.isNotBlank(serial) && !redisSerials.contains(serial)) {
                break;
            }
            serial = null;
        }
        return serial;
    }

    /**
     * 删除redis中的列表，并重新批量添加
     * <p>
     * 删除指定Redis键对应的列表，然后重新批量添加新的元素到该列表中。
     *
     * @param key  要操作的Redis键
     * @param list 包含要添加到列表中的元素列表
     */
    private void redisTemplateListRightPush(String key, List<Object> list) {
        redisTemplate.delete(key);
        if (Strings.isNotBlank(key) && list != null) {
            for (Object item : list) {
                redisTemplate.opsForList().rightPush(key, item);
            }
        }
    }

    /**
     * 集合格式化
     * <p>
     * 对传入的集合进行格式化处理，去除空值，并去除重复项。
     *
     * @param list 待格式化的集合
     * @param <T>  集合元素的类型
     * @return 格式化后的集合
     */
    private <T> List<T> formatList(List<T> list) {
        List<T> nList = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                T obj = list.get(i);
                if (obj != null && Strings.isNotBlank(obj.toString())) {
                    if (!nList.contains(obj)) {
                        nList.add(obj);
                    }
                }
            }
        }

        return nList;
    }

    /**
     * 流水号集合排序
     * <p>
     * 对提供的流水号集合进行去重、去空和排序处理。
     *
     * @param list 包含流水号的列表
     * @return 返回处理后的流水号列表
     */
    private List<Object> formatSerialList(List<Object> list) {
        if (list != null && !list.isEmpty()) {
            // 去重、去空
            list = formatList(list);
            // 排序
            list.sort((o1, o2) -> Long.parseLong(String.valueOf(o1)) > Long.parseLong(String.valueOf(o2)) ? 1 : 0);
        }

        return list;
    }

    /**
     * 获取系统变量
     * <p>
     * 根据提供的编码项列表和应用ID，获取对应的系统变量信息。
     *
     * @param itemList 编码项列表，包含需要获取系统变量的编码项
     * @param appId    应用ID，用于获取应用相关的系统变量
     * @return 返回包含系统变量信息的Map对象
     */
    private Map<String, Object> getVariable(List<EncodingItem> itemList, String appId) {
        Map<String, Object> resultMap = new HashMap<>();
        List<String> variableKeys = new ArrayList<>();
        List<String> variableValues = new ArrayList<>();
        for (EncodingItem item : itemList) {
            if (EncodingItemTypeEnum.VARIABLE.getValue().equals(item.getItemType())) {
                if (Strings.isNotBlank(item.getConstantValue())) {
                    String[] keys = item.getConstantValue().split("\\.");
                    if (keys.length == 2 && Strings.isNotBlank(keys[0]) && Strings.isNotBlank(keys[1])) {
                        variableKeys.add(keys[0]);
                        variableValues.add(keys[1]);
                    }
                }
            }
        }
        if (!variableKeys.isEmpty() && !variableValues.isEmpty()) {
            if (variableKeys.contains("tenant")) {
                String tenantCode = SessionCtx.getCurrentTenantCode();
                if (Strings.isNotBlank(tenantCode)) {
                    List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList("SELECT * FROM platform_tenant WHERE 1=1 AND del_status = 0 AND code = ?", new Object[]{tenantCode});
                    if (!mapList.isEmpty()) {
                        resultMap.put("tenant.id", mapList.get(0).get("id"));
                        resultMap.put("tenant.code", mapList.get(0).get("code"));
                        resultMap.put("tenant.name", mapList.get(0).get("company_name"));
                        resultMap.put("tenant.domain", mapList.get(0).get("company_domain"));
                        resultMap.put("tenant.corpId", mapList.get(0).get("corp_id"));
                        resultMap.put("tenant.corpToken", mapList.get(0).get("corp_token"));
                        resultMap.put("tenant.email", mapList.get(0).get("main_email"));
                    }
                }
            }
            if (variableKeys.contains("app") && Strings.isNotBlank(appId)) {
                Map<String, Object> model = dao.queryForMap(App.class, "id", appId);
                if (model != null) {
                    for (Map.Entry<String, Object> entry : model.entrySet()) {
                        resultMap.put(String.format("app.%s", entry.getKey()), entry.getValue());
                    }
                }
            }
            if (variableKeys.contains("user")) {
                cn.geelato.core.env.entity.User user = SessionCtx.getCurrentUser();
                if (user != null && Strings.isNotBlank(user.getUserId())) {
                    Map<String, Object> model = dao.queryForMap(User.class, "id", user.getUserId());
                    if (model != null) {
                        for (Map.Entry<String, Object> entry : model.entrySet()) {
                            resultMap.put(String.format("user.%s", entry.getKey()), entry.getValue());
                        }
                        if (variableValues.contains("companyId") || variableValues.contains("companyName")) {
                            Object orgId = model.get("orgId");
                            if (orgId != null && Strings.isNotBlank(String.valueOf(orgId))) {
                                Org org = orgService.getCompany(String.valueOf(orgId));
                                if (org != null) {
                                    resultMap.put("user.companyId", org.getId());
                                    resultMap.put("user.companyName", org.getName());
                                }
                            }
                        }
                    }
                }
            }
        }

        return resultMap;
    }
}
