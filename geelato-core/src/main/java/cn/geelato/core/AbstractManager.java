package cn.geelato.core;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author diabl
 */
public class AbstractManager {
    protected static final Lock lock = new ReentrantLock();
}
