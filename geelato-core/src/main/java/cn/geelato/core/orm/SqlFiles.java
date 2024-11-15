package cn.geelato.core.orm;

import cn.geelato.utils.UIDGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
                    // 新的语句开始，若存在老的语句，执行老的语句并清空
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
     * 从输入流中加载SQL语句并执行。
     * 文件格式要求：每条SQL语句之间必须用注释“--”进行分割。
     * 在执行过程中，会自动将占位符$id替换为实际生成的id。
     *
     * @param is           SQL文件的输入流
     * @param jdbcTemplate JDBC模板对象，用于执行SQL语句
     * @param isWinOS      一个布尔值，指示操作系统是否为Windows
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
     * 解析SQL语句，并执行本框架定义的方法。
     * 目前支持的方案包括：
     * <br>$newId()：生成long型UID，并替换SQL语句中相应的位置。
     *
     * @param line 解析后的SQL语句
     * @return 处理后的SQL语句
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
     * 加载并执行SQL文件中的语句。
     * SQL文件格式要求：每条SQL语句之间必须用注释“--”进行分割。
     *
     * @param file         SQL文件对象
     * @param jdbcTemplate JdbcTemplate对象，用于执行SQL语句
     * @param isWinOS      是否为Windows操作系统
     */
    public static void loadAndExecute(File file, JdbcTemplate jdbcTemplate, boolean isWinOS) {
        loadAndExecute(file.list(), jdbcTemplate, isWinOS);
    }

    /**
     * 加载并执行SQL文件中的SQL语句。
     * <p>文件格式说明：每条SQL语句之间必须用注释“--”进行分割。</p>
     *
     * @param path         SQL文件的物理位置。注意，该方法无法加载fatjar中的文件。
     * @param jdbcTemplate JdbcTemplate对象，用于执行SQL语句。
     * @param isWinOS      指示操作系统是否为Windows。
     * @throws IOException 如果在读取SQL文件时发生I/O错误，将抛出此异常。
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
