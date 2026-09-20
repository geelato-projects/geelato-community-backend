package cn.geelato.core.orm;

import cn.geelato.core.util.EncryptUtils;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 仅对集合内(元数据标记加密)的列解密,其余列原样返回(含形如 "aes:xxx" 的明文);
 * 空集与 null 集合均不做任何解密。
 */
class DecryptingRowMapperTest {

    private static final String SECRET_PLAIN = "s3cret-机密";
    private static final String SELECT = "select secret_col as \"secret_col\", note_col as \"note_col\" from t_decrypt_gate";
    private static JdbcTemplate jdbcTemplate;
    private static String storedCipher;

    @BeforeAll
    static void setUp() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new Driver(), "jdbc:h2:mem:decryptingRowMapperTest;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create table t_decrypt_gate (secret_col varchar(256), note_col varchar(256))");
        // note_col 存的是用户手打的明文，恰好以 aes: 开头——历史行为会抛 IllegalStateException
        storedCipher = EncryptUtils.encrypt(SECRET_PLAIN);
        jdbcTemplate.update("insert into t_decrypt_gate values (?, ?)", storedCipher, "aes:not-a-cipher");
    }

    @Test
    void decryptsOnlyMarkedColumns() {
        List<Map<String, Object>> rows = jdbcTemplate.query(SELECT, new DecryptingRowMapper(Set.of("secret_col")));

        assertEquals(1, rows.size());
        assertEquals(SECRET_PLAIN, rows.get(0).get("secret_col"));
        assertEquals("aes:not-a-cipher", rows.get(0).get("note_col"));
    }

    @Test
    void emptySetSkipsDecryptEntirely() {
        List<Map<String, Object>> rows = jdbcTemplate.query(SELECT, new DecryptingRowMapper(Set.of()));

        assertEquals(1, rows.size());
        assertEquals(storedCipher, rows.get(0).get("secret_col"));
        assertEquals("aes:not-a-cipher", rows.get(0).get("note_col"));
    }

    @Test
    void nullSetSkipsDecryptLikeEmptySet() {
        // null 容错与空集同义：元数据不可用即无加密列，不做任何解密
        List<Map<String, Object>> rows = jdbcTemplate.query(SELECT, new DecryptingRowMapper(null));

        assertEquals(1, rows.size());
        assertEquals(storedCipher, rows.get(0).get("secret_col"));
        assertEquals("aes:not-a-cipher", rows.get(0).get("note_col"));
    }
}
