package cn.geelato.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author geemeta
 *
 */
@Slf4j
public class UIDGenerator {

    private static final long BEGIN_DATE = new Date(116, 6, 6, 6, 6, 6).getTime();
    private static final int localMachineAppend = Integer.parseInt(ip());
    private static AtomicInteger atomicInteger = new AtomicInteger(0);
    private static final SnowFlake snowFlake=new SnowFlake(1,1);
    public static long generate() {
        return snowFlake.nextId();
    }
    /**
     * 39 bit
     */
    private static String wrapTimeBinaryStr() {
        long currentTime = System.currentTimeMillis();
        long timeElapse = currentTime - BEGIN_DATE;
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
