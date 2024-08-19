package cn.geelato.web.platform.arco;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.web.platform.arco.select.SelectOptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 * @description: 系统枚举查询
 */
public class ArcoUtils {
    private static Map<String, Class<? extends Enum>> enumMap = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(ArcoUtils.class);

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
            logger.error(e.getMessage());
        }

        return optionDatas.toArray(new SelectOptionData[]{});
    }
}
