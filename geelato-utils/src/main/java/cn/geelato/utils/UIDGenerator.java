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

    public static class SnowFlake {
        /**
         * 开始时间截 (2019-01-01)
         */
        private final long twepoch = 1561902155808L;

        /**
         * 机器id所占的位数
         */
        private final long workerIdBits = 5L;

        /**
         * 数据标识id所占的位数
         */
        private final long dataCenterIdBits = 5L;

        /**
         * 序列在id中占的位数
         */
        private final long sequenceBits = 12L;

        /**
         * 时间戳
         */
        private final long timeIdBits = 41L;

        /**
         * 支持的最大机器id，结果是31 (这个移位算法可以很快地计算出几位二进制数所能表示的最大十进制数)
         */
        private final long maxWorkerId = ~(-1L << workerIdBits);

        /**
         * 支持的最大数据标识id，结果是31
         */
        private final long maxDataCenterId = ~(-1L << dataCenterIdBits);

        /**
         * 生成序列的掩码，这里为4095 (0b111111111111=0xfff=4095)
         */
        private final long sequenceMask = ~(-1L << sequenceBits);


        /**
         * 机器ID向左移12位
         */
        private final long workerIdShift = sequenceBits;

        /**
         * 数据标识id向左移17位(12+5)
         */
        private final long datacenterIdShift = sequenceBits + workerIdBits;

        /**
         * 时间截向左移22位(5+5+12)
         */
        private final long timestampLeftShift = sequenceBits + workerIdBits + dataCenterIdBits;


        /**
         * 工作机器ID(0~31)
         */
        private final long workerId;


        /**
         * 数据中心ID(0~31)
         */
        private final long dataCenterId;


        /**
         * 毫秒内序列(0~4095)
         */
        private long sequence = 0L;

        /**
         * 上次生成ID的时间截
         */
        private long lastTimestamp = -1L;

        /**
         * 构造函数
         *
         * @param workerId     工作ID (0~31)
         * @param dataCenterId 数据中心ID (0~31)
         */

        public SnowFlake(long workerId, long dataCenterId) {
            if (workerId > maxWorkerId || workerId < 0) {
                throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
            }
            if (dataCenterId > maxDataCenterId || dataCenterId < 0) {
                throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDataCenterId));
            }
            this.workerId = workerId;
            this.dataCenterId = dataCenterId;
        }

        /**
         * 将2进制结果用作UUID
         * 将生成的下一个ID转换为2进制字符串，并在前面添加"0"字符，然后返回该字符串作为UUID。
         *
         * @return 转换后的2进制字符串作为UUID
         */
        public String nextIdBinary() {
            return "0" + Long.toBinaryString(this.nextId());
        }

        /**
         * 获得下一个ID (该方法是线程安全的)
         *
         * @return SnowflakeId
         */
        public synchronized long nextId() {
            long timestamp = timeGen();

            // 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过这个时候应当抛出异常
            if (timestamp < lastTimestamp) {
                throw new RuntimeException(
                        String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
            }

            // 如果是同一时间生成的，则进行毫秒内序列
            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & sequenceMask;
                // 毫秒内序列溢出
                if (sequence == 0) {
                    // 阻塞到下一个毫秒,获得新的时间戳
                    timestamp = tilNextMillis(lastTimestamp);
                }
            }
            // 时间戳改变，毫秒内序列重置
            else {
                sequence = 0L;
            }

            // 上次生成ID的时间截
            lastTimestamp = timestamp;

            return ((timestamp - twepoch) << (timestampLeftShift + (timeIdBits - Long.toBinaryString(timestamp - twepoch).length())))
                    | (dataCenterId << datacenterIdShift)
                    | (workerId << workerIdShift)
                    | sequence;
        }

        /**
         * 阻塞到下一个毫秒，直到获得新的时间戳
         *
         * @param lastTimestamp 上次生成ID的时间截
         * @return 当前时间戳
         */
        private long tilNextMillis(long lastTimestamp) {
            long timestamp = timeGen();
            while (timestamp <= lastTimestamp) {
                timestamp = timeGen();
            }
            return timestamp;
        }

        /**
         * 返回以毫秒为单位的当前时间
         *
         * @return 当前时间(毫秒)
         */
        private long timeGen() {
            return System.currentTimeMillis();
        }

    }
}
