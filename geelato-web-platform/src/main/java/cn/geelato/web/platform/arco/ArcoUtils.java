package cn.geelato.web.platform.arco;

import cn.geelato.web.platform.arco.select.SelectOptionData;
import cn.geelato.core.enums.DeleteStatusEnum;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 * @description: 系统枚举查询
 * @date 2023/6/25 10:21
 */
public class ArcoUtils {
    private static Map<String, Class<? extends Enum>> enumMap = new HashMap<>();

    public static synchronized Class<? extends Enum> getEnum(String enumName) {
        if (ArcoUtils.enumMap.isEmpty()) {
            enumMap.put("deleteStatus", DeleteStatusEnum.class);
        }
        return ArcoUtils.enumMap.get(enumName);
    }

    public static SelectOptionData[] getEnumOptions(String enumName) {
        List<SelectOptionData> optionDatas = new ArrayList<>();

        Class<? extends Enum> enumClass = getEnum(enumName);

        try {
            Method m = enumClass.getMethod("values", null);
            Object[] values = (Object[]) m.invoke(enumClass, null);
            for (Object object : values) {
                Method getKey = object.getClass().getMethod("getKey", null);
                String keyString = (String) getKey.invoke(object, null);
                Method getName = object.getClass().getMethod("getName", null);
                String nameString = (String) getName.invoke(object, null);

                SelectOptionData optionData = new SelectOptionData();
                optionData.setLabel(nameString);
                optionData.setValue(keyString);
                optionDatas.add(optionData);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return optionDatas.toArray(new SelectOptionData[]{});
    }

    private static class KeyName {
        private String key;
        private String name;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
