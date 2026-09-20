package cn.geelato.archive.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CUSTOM 策略 WHERE 条件静态安全校验的纯逻辑测试。
 * 安全模型：条件仅参与"选 id"，搬移/删除按 id IN；校验目标是杜绝多语句与文件读写类注入。
 */
class WhereConditionValidatorTest {

    // ==================== 合法条件 ====================

    @Test
    void legalSimpleComparison() {
        assertNull(WhereConditionValidator.validate("batch < 'B2024-001'"));
    }

    @Test
    void legalInClause() {
        assertNull(WhereConditionValidator.validate("status in ('done', 'archived')"));
    }

    @Test
    void legalCompoundCondition() {
        assertNull(WhereConditionValidator.validate("amount > 100 and type = 'X'"));
    }

    /** 带下划线的列名（update_at/set_value）不与禁词误伤：\b 视下划线为词字符 */
    @Test
    void legalUnderscoreColumnNameNotConfusedWithKeyword() {
        assertNull(WhereConditionValidator.validate("update_at < '2024-01-01'"));
        assertNull(WhereConditionValidator.validate("set_value = 3"));
        assertNull(WhereConditionValidator.validate("creator_name = 'zhang' and deleted_at is not null"));
    }

    @Test
    void legalNullCheck() {
        assertNull(WhereConditionValidator.validate("finished_at is null or finished_at < '2024-06-01'"));
    }

    @Test
    void nullOrBlankPasses() {
        assertNull(WhereConditionValidator.validate(null));
        assertNull(WhereConditionValidator.validate("  "));
    }

    // ==================== 非法条件 ====================

    @Test
    void semicolonRejected() {
        assertNotNull(WhereConditionValidator.validate("a = 1; DROP TABLE platform_user"));
    }

    @Test
    void lineCommentRejected() {
        assertNotNull(WhereConditionValidator.validate("a = 1 -- comment"));
    }

    @Test
    void blockCommentRejected() {
        assertNotNull(WhereConditionValidator.validate("a = 1 /* block */"));
    }

    @Test
    void hashCommentRejected() {
        assertNotNull(WhereConditionValidator.validate("a = 1 # comment"));
    }

    @Test
    void dmlKeywordRejected() {
        assertNotNull(WhereConditionValidator.validate("1=1) DELETE FROM platform_user WHERE (1=1"));
        assertNotNull(WhereConditionValidator.validate("1=1) UPDATE platform_user SET password = 'x' WHERE (1=1"));
        assertNotNull(WhereConditionValidator.validate("1=1) INSERT INTO t VALUES (1) WHERE (1=1"));
        assertNotNull(WhereConditionValidator.validate("1=1) TRUNCATE TABLE t WHERE (1=1"));
    }

    @Test
    void fileReadPathRejected() {
        // into outfile / load_file / load data 类文件读写路径
        assertNotNull(WhereConditionValidator.validate("1=1) INTO OUTFILE '/tmp/x' WHERE (1=1"));
        assertNotNull(WhereConditionValidator.validate("load_file('/etc/passwd') is not null"));
        assertNotNull(WhereConditionValidator.validate("1=1) LOAD DATA INFILE 'x' WHERE (1=1"));
    }

    @Test
    void systemSchemaRejected() {
        assertNotNull(WhereConditionValidator.validate("id in (select id from information_schema.tables)"));
    }

    @Test
    void unbalancedQuoteRejected() {
        assertNotNull(WhereConditionValidator.validate("name = 'zhang"));
    }

    @Test
    void rejectMessageContainsKeyword() {
        String reason = WhereConditionValidator.validate("1=1) delete from t where (1=1");
        assertNotNull(reason);
        assertTrue(reason.contains("delete"));
    }
}
