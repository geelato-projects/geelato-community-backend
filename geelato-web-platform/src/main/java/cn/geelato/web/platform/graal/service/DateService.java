package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import cn.geelato.utils.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

@GraalService(name = "date", built = "true")
public class DateService {

    /**
     * 格式化日期
     *
     * @param date
     * @param format
     * @return
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
     *
     * @param timeStamp
     * @param format
     * @return
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
