package cn.geelato.web.platform.m.site.entity;

import cn.geelato.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

public class FileInfoComparator implements Comparator<FileInfo> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DateUtils.DATETIME);

    @Override
    public int compare(FileInfo f1, FileInfo f2) {
        // 1. 文件夹优先
        if (f1.isDirectory() && !f2.isDirectory()) {
            return -1;
        } else if (!f1.isDirectory() && f2.isDirectory()) {
            return 1;
        }
        // 2. 都同为文件夹或文件时，按最后修改时间逆序排序
        try {
            Date date1 = DATE_FORMAT.parse(f1.getLastModified());
            Date date2 = DATE_FORMAT.parse(f2.getLastModified());
            return date2.compareTo(date1); // 逆序排序
        } catch (Exception e) {
            return 0; // 如果日期解析失败，保持原顺序
        }
    }
}
