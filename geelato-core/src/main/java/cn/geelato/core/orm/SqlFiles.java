package cn.geelato.core.orm;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import cn.geelato.utils.UIDGenerator;
import org.slf4j.log;
import org.slf4j.logFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 加载sql文件并执行，用于数据初始化sql脚本的执行
 *
 * @author geemeta
 */
@Slf4j
public class SqlFiles {
    private static final Pattern newIdPattern = Pattern.compile("\\$newId[ ]*\\([ ]*\\)");


    public static void loadAndExecute(String[] lines, JdbcTemplate jdbcTemplate, boolean isWinOS) {
        if (lines != null) {
            StringBuffer sb = new StringBuffer();
            for (String line : lines) {
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                line = line.trim();
                int index = line.trim().indexOf("--");
                if (index >= 0) {
                    //新的语句开始，若存在老的语句，执行老的语句并清空
                    if (sb.length() > 1) {
                        log.debug("execute sql :{}", sb);
                        jdbcTemplate.execute(sb.toString());
                    }
                    sb = new StringBuffer();
                } else {
                    sb.append(line);
                    if (isWinOS) {
                        sb.append("\r\n");
                    } else {
                        sb.append("\n");
                    }
                }
            }
            if (sb.length() > 1) {
                log.debug("execute sql :{}", sb);
                jdbcTemplate.execute(sb.toString());
            }
        }
    }

    /**
     * 文件格式说明：每条语句之间必须用注释“--”进行分割
     * 自动生成id替换$id
     *
     * @param is           SQL文件的输入流
     * @param jdbcTemplate
     * @param isWinOS
     */
    public static void loadAndExecute(InputStream is, JdbcTemplate jdbcTemplate, boolean isWinOS) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            List<String> lineList = new ArrayList<>();
            String line = null;
            while ((line = reader.readLine()) != null) {
                lineList.add(parseLine(line));
                log.debug("line:{}", line);
            }
            loadAndExecute(lineList, jdbcTemplate, isWinOS);
        } catch (IOException e) {
            log.error("加载SQL流文件并执行出错！{}", e);
        }
    }

    /**
     * 解析sql，并执行本框架定义的方法，目前方案有：
     * </br>$newId()：生成long型UID，并替换sql语句相应的位置
     *
     * @param line 解析后的语句
     * @return
     */
    public static String parseLine(String line) {
        Matcher matcher = newIdPattern.matcher(line);
        if (matcher.find()) {
            return matcher.replaceAll(String.valueOf(UIDGenerator.generate()));
        }
        return line;
    }


    public static void loadAndExecute(List<String> lineList, JdbcTemplate jdbcTemplate, boolean isWinOS) {
        String[] lines = new String[lineList.size()];
        lineList.toArray(lines);
        loadAndExecute(lines, jdbcTemplate, isWinOS);
    }

    /**
     * 文件格式说明：每条语句之间必须用注释“--”进行分割
     *
     * @param file         SQL文件
     * @param jdbcTemplate
     * @param isWinOS
     */
    public static void loadAndExecute(File file, JdbcTemplate jdbcTemplate, boolean isWinOS) {
        loadAndExecute(file.list(), jdbcTemplate, isWinOS);
    }

    /**
     * 文件格式说明：每条语句之间必须用注释“--”进行分割
     *
     * @param path         SQL文件物理位置,无法加载fatjar中的文件
     * @param jdbcTemplate
     * @param isWinOS
     */
    public static void loadAndExecute(String path, JdbcTemplate jdbcTemplate, boolean isWinOS) {
        try {
            if (Files.isExecutable(Paths.get(path))) {
                List<String> lines = Files.readAllLines(Paths.get(path));
                loadAndExecute(lines, jdbcTemplate, isWinOS);
            }
        } catch (IOException e) {
            log.error("加载SQL文件并执行出错！\r\n文件：{}\r\n", path, e);
        }
    }
}
