package cn.geelato.web.platform.m.flow.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 列表的操作服务
 */
@Component
public class ListService {

    /**
     * 列表分组求和计算出新的列表
     *
     * @param list
     * @param sumFields    求和的列，多个用","分割
     * @param groupByField 分组的一个字段
     * @return
     */
    public ArrayList sum(List<Map> list, String sumFields, String groupByField) {
        String[] fields = sumFields.split(",");
        Map<String, Map<String, Double>> newGroupList = new HashMap<>();
        for (Map map : list) {
            if (!map.containsKey(groupByField)) {
                continue;
            }
            String groupName = map.get(groupByField).toString();
            Map<String, Double> groupMap = newGroupList.computeIfAbsent(groupName, k -> new HashMap());
            for (String sumField : fields) {
                double sumFieldValue = Double.parseDouble(map.getOrDefault(sumField, "0").toString());
                sumFieldValue += groupMap.getOrDefault(sumField, 0.0);
                groupMap.put(sumField, sumFieldValue);
            }
        }
        ArrayList newList = new ArrayList(newGroupList.size());
        newGroupList.forEach((key, map) -> {
            Map newMap = new HashMap();
            newMap.put(groupByField, key);
            newMap.putAll(map);
            newList.add(newMap);
        });
        return newList;
    }

    /**
     * 列表求和计算出新的列表，该列表为一行
     *
     * @param list
     * @param sumFields 求和的列，多个用","分割
     * @return
     */
    public ArrayList sum(List<Map> list, String sumFields) {
        String[] fields = sumFields.split(",");
        Map<String, Double> sumMap = new HashMap<>();
        for (Map map : list) {
            for (String sumField : fields) {
                double sumFieldValue = Double.parseDouble(map.getOrDefault(sumField, "0").toString());
                sumFieldValue += sumMap.getOrDefault(sumField, 0.0);
                sumMap.put(sumField, sumFieldValue);
            }
        }
        ArrayList newList = new ArrayList(1);
        newList.add(sumMap);
        return newList;
    }

    /**
     * @param list
     * @param concatFields
     * @param concatFlag
     * @return
     */
    public ArrayList concat(List<Map> list, String concatFields, String concatFlag) {
        String flag = concatFlag == null ? "," : concatFlag;
        String[] fields = concatFields.split(",");
        Map<String, String> concatMap = new HashMap<>();
        for (Map map : list) {
            for (String concatField : fields) {
                String concatFieldValue = map.getOrDefault(concatField, "").toString().trim();
                if (concatFieldValue.length() == 0) {
                    continue;
                }
                if (concatMap.containsKey(concatField)) {
                    String newValue = concatMap.get(concatField) + flag + concatFieldValue;
                    concatMap.put(concatField, newValue);
                } else {
                    concatMap.put(concatField, concatFieldValue);
                }
            }
        }
        ArrayList newList = new ArrayList(1);
        newList.add(concatMap);
        return newList;
    }

    /**
     * 拼接一个字段
     *
     * @param list
     * @param concatField
     * @param concatFlag
     * @param deduplicate 是否需要去重
     * @return
     */
    public String concatOne(List<Map> list, String concatField, String concatFlag, boolean deduplicate) {
        if (list.size() == 0) {
            return "";
        }
        Map concatFieldValueMapForDeduplicate = new HashMap(list.size());
        String flag = concatFlag == null ? "," : concatFlag;
        String result = "";
        for (Map map : list) {
            String concatFieldValue = map.getOrDefault(concatField, "").toString().trim();
            if (concatFieldValue.length() == 0) {
                continue;
            }
            if (result.length() != 0) {
                // 需要去重且已存在相同的，则跳过
                if (deduplicate && concatFieldValueMapForDeduplicate.containsKey(concatFieldValue)) {
                    continue;
                }
                result = result + flag + concatFieldValue;
            } else {
                result = concatFieldValue;
            }
            concatFieldValueMapForDeduplicate.put(concatFieldValue, true);
        }
        return result;
    }
}
