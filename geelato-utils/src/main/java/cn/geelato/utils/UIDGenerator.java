package cn.geelato.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author geemeta
 */
@Slf4j
public class UIDGenerator {
    private static final int localMachineAppend = Integer.parseInt(ip());
    private static final SnowFlake snowFlake = new SnowFlake(1, 1);
    private static AtomicInteger atomicInteger = new AtomicInteger(0);

    public static long generate() {
        return snowFlake.nextId();
    }

    private static long getBeginDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, 1916); // 注意年份需要正确设置，这里假设你想要的年份是1916年
        calendar.set(Calendar.MONTH, Calendar.JUNE); // 月份使用Calendar常量
        calendar.set(Calendar.DAY_OF_MONTH, 6);
        calendar.set(Calendar.HOUR_OF_DAY, 6);
        calendar.set(Calendar.MINUTE, 6);
        calendar.set(Calendar.SECOND, 6);
        return calendar.getTime().getTime();
    }

    /**
     * 39 bit
     */
    private static String wrapTimeBinaryStr() {
        long currentTime = System.currentTimeMillis();
        long timeElapse = currentTime - getBeginDate();
        return StringUtils.leftPad(Long.toBinaryString(timeElapse), 39, '0');
    }

    /**
     * 10 bit
     */
    private static String wrapMachineBinaryStr(int machineIp) {
        return StringUtils.leftPad(Integer.toBinaryString(machineIp), 10, '0');
    }

    /**
     * 5 bit
     */
    private static String wrapBusinessBinaryStr(int businessType) {
        return StringUtils.leftPad(Integer.toBinaryString(businessType), 5, '0');
    }

    /**
     * 4 bit
     */
    private static String wrapRoomBinaryStr(int room) {
        return StringUtils.leftPad(Integer.toBinaryString(room), 4, '0');
    }

    /**
     * 5 bit
     */
    private static String wrapSequencePeyMachine() {
        if (atomicInteger.get() == Integer.MAX_VALUE) {
            atomicInteger = new AtomicInteger();
        }
        return StringUtils.leftPad(Integer.toBinaryString(atomicInteger.incrementAndGet() % 32), 5, '0');
    }

    private static String ip() {
        try {
            return InetAddress.getLocalHost().getHostAddress().split("\\.")[3];
        } catch (UnknownHostException e) {
            log.error(e.getMessage(), e);
        }
        return "1";
    }

}
