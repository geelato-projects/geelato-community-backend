package cn.geelato.utils.entity;

import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.UIDGenerator;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class FileIS {
    private String fileName;
    private InputStream inputStream;

    public FileIS() {
    }

    public FileIS(String fileName, InputStream inputStream) {
        this.fileName = fileName;
        this.inputStream = inputStream;
    }

    public static List<FileIS> repeatFileNameToNewFileName(List<FileIS> list) {
        Map<String, Integer> nameMap = new HashMap<>();
        if (list != null && !list.isEmpty()) {
            for (FileIS fileIs : list) {
                String name = fileIs.getFileName();
                if (nameMap.containsKey(name)) {
                    int count = (nameMap.get(name) == null ? 1 : nameMap.get(name).intValue()) + 1;
                    String repeatName = FileUtils.spliceFileName(fileIs.getFileName(), null, " repeat-" + count);
                    if (StringUtils.isNotBlank(repeatName)) {
                        fileIs.setFileName(repeatName);
                    } else {
                        fileIs.setFileName(String.format("%s %s", name, UIDGenerator.generate()));
                    }
                    nameMap.put(name, count);
                } else {
                    nameMap.put(name, 0);
                }
            }
        }
        return list;
    }
}
