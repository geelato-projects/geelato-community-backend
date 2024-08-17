package cn.geelato.core.meta;

/**
 * @author geemeta
 */

import cn.geelato.utils.DateUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

public class DateTimeConverter implements Converter {

    private static final Log log = LogFactory.getLog(DateTimeConverter.class);

    @Override
    public Object convert(Class type, Object value) {
        return toDate(type, value);
    }

    public static Object toDate(Class type, Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return DateUtils.asDate((LocalDateTime) value);
        } else if (value instanceof LocalDate) {
            return DateUtils.asDate((LocalDate) value);
        }
        if (value instanceof String) {
            String dateValue = value.toString().trim();
            int length = dateValue.length();
            try {
                if (type.equals(java.util.Date.class)) {

                    DateFormat formatter = null;
                    if (length <= 10) {
                        formatter = new SimpleDateFormat(DateUtils.DATE, new DateFormatSymbols(Locale.CHINA));
                        return formatter.parse(dateValue);
                    }
                    if (length <= 19) {
                        formatter = new SimpleDateFormat(DateUtils.DATETIME, new DateFormatSymbols(Locale.CHINA));
                        return formatter.parse(dateValue);
                    }
                    if (length <= 23) {
                        formatter = new SimpleDateFormat(DateUtils.TIMESTAMP, new DateFormatSymbols(Locale.CHINA));
                        return formatter.parse(dateValue);
                    }

                } else if (type.equals(java.time.LocalDate.class)) {
                    log.info("java.time.LocalDate类型，value:" + value);
                    return new SimpleDateFormat(DateUtils.DATE, new DateFormatSymbols(Locale.CHINA)).parse(dateValue);
                } else if (type.equals(java.time.LocalDateTime.class)) {
                    log.info("java.time.LocalDateTime类型，value:" + value);
                    return new SimpleDateFormat(DateUtils.DATETIME, new DateFormatSymbols(Locale.CHINA)).parse(dateValue);
                } else if (type.equals(java.time.LocalTime.class)) {
                    log.info("java.time.LocalTime类型，value:" + value);
                    return value;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        log.info("【！！！】未识别的日期类型，type:" + type.toString() + " value:" + value);
        return value;
    }


}