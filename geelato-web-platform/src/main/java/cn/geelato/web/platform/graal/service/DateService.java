package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import cn.geelato.utils.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

@GraalService(name = "date", built = "true")
public class DateService {

    /**
     * 格式化日期
     * <p>
     * 将给定的日期对象按照指定的格式字符串进行格式化，并返回格式化后的日期字符串。
     *
     * @param date   需要格式化的日期对象
     * @param format 日期格式字符串，例如 "yyyy-MM-dd"
     * @return 格式化后的日期字符串，如果输入参数无效则返回null
     */
    public String formatDate(Date date, String format) {
        if (date != null && StringUtils.isNotBlank(format)) {
            try {
                return new SimpleDateFormat(format).format(date);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 格式化时间戳
     * <p>
     * 根据指定的时间戳和格式，将时间戳格式化为指定格式的日期字符串。
     *
     * @param timeStamp 时间戳字符串
     * @param format    日期格式字符串，如 "yyyy-MM-dd HH:mm:ss"
     * @return 格式化后的日期字符串，如果输入参数无效或格式化失败则返回null
     */
    public String formatDate(String timeStamp, String format) {
        if (StringUtils.isNotBlank(timeStamp) && StringUtils.isNotBlank(format)) {
            try {
                long timestamp = Long.parseLong(timeStamp);
                return new SimpleDateFormat(format).format(new Date(timestamp));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
