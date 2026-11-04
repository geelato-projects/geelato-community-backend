package cn.geelato.mqltest.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 classpath 的 mql-test-cases/*.json 加载声明式测试用例。
 * <p>
 * 文件格式：
 * <pre>
 * [
 *   {
 *     "name": "用例名",
 *     "mql": "{\"mql_test_user\":{\"name|eq\":\"张三\"}}",
 *     "expectedSql": "select ... from ... where ...",
 *     "expectedParams": ["张三"],
 *     "expectedTypes": [12],
 *     "description": "可选说明"
 *   }
 * ]
 * </pre>
 */
public final class MqlTestCaseLoader {

    /** classpath 根目录 */
    private static final String CASES_DIR = "/mql-test-cases/";

    private MqlTestCaseLoader() {
    }

    /**
     * 加载指定文件中的全部用例。
     *
     * @param fileName 文件名（不含路径），如 "where-operators.json"
     */
    public static List<MqlTestCase> load(String fileName) {
        String path = CASES_DIR + fileName;
        try (InputStream is = MqlTestCaseLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("测试用例文件未找到: " + path);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONArray array = JSON.parseArray(content);
            List<MqlTestCase> cases = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                JSONObject jo = array.getJSONObject(i);
                cases.add(MqlTestCase.from(jo));
            }
            return cases;
        } catch (IOException e) {
            throw new IllegalStateException("读取测试用例文件失败: " + path, e);
        }
    }

    /**
     * 单个声明式测试用例。
     */
    public static class MqlTestCase {
        private final String name;
        private final String mql;
        private final String expectedSql;
        private final Object[] expectedParams;
        private final int[] expectedTypes;
        private final String description;

        public MqlTestCase(String name, String mql, String expectedSql,
                           Object[] expectedParams, int[] expectedTypes, String description) {
            this.name = name;
            this.mql = mql;
            this.expectedSql = expectedSql;
            this.expectedParams = expectedParams;
            this.expectedTypes = expectedTypes;
            this.description = description;
        }

        static MqlTestCase from(JSONObject jo) {
            String name = jo.getString("name");
            String mql = jo.getString("mql");
            String expectedSql = jo.getString("expectedSql");
            Object[] expectedParams = parseParams(jo.getJSONArray("expectedParams"));
            int[] expectedTypes = parseTypes(jo.getJSONArray("expectedTypes"));
            String description = jo.getString("description");
            return new MqlTestCase(name, mql, expectedSql, expectedParams, expectedTypes, description);
        }

        private static Object[] parseParams(JSONArray arr) {
            if (arr == null) {
                return null;
            }
            return arr.toArray();
        }

        private static int[] parseTypes(JSONArray arr) {
            if (arr == null) {
                return null;
            }
            int[] types = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                types[i] = arr.getIntValue(i);
            }
            return types;
        }

        public String getName() { return name; }
        public String getMql() { return mql; }
        public String getExpectedSql() { return expectedSql; }
        public Object[] getExpectedParams() { return expectedParams; }
        public int[] getExpectedTypes() { return expectedTypes; }
        public String getDescription() { return description; }

        @Override
        public String toString() {
            return name != null ? name : mql;
        }
    }
}
