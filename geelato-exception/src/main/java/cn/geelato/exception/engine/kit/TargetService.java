package cn.geelato.exception.engine.kit;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.kit.KitContext;
import cn.geelato.script.lang.GuestValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 升级目标 kit（$gl.target.targetInstance）：ESCALATE 规则按 bizId 取升级目标
 * （escalationOf 规则）在途实例，null=无在途。
 * 主库直查（JdbcTemplate 显式数据源，不依赖 ThreadLocal 上下文，脚本执行线程安全）。
 */
@Slf4j
@GraalService(name = "target", built = "true", descrption = "升级目标在途实例查询")
public class TargetService {

    private final JdbcTemplate jdbcTemplate;
    private final KitContext ctx;

    public TargetService(JdbcTemplate jdbcTemplate, KitContext ctx) {
        this.jdbcTemplate = jdbcTemplate;
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.target.targetInstance(r.biz_id)", description = "按 bizId 取升级目标（escalationOf 规则）在途实例 {id,status,firstAt,...}，无在途返回 null")
    public Object targetInstance(String bizId) {
        if (bizId == null || bizId.isBlank()) {
            return null;
        }
        String escalationOf = ctx.getAttr("escalationOf");
        if (escalationOf == null || escalationOf.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, status, first_at, participant_id, participant_name, level FROM platform_exception" +
                            " WHERE rule_code = ? AND biz_id = ? AND status IN ('OPEN','PROCESSING') AND del_status = 0" +
                            " ORDER BY first_at ASC LIMIT 1",
                    escalationOf, bizId);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", row.get("id"));
            out.put("status", row.get("status"));
            out.put("firstAt", format(row.get("first_at")));
            out.put("participantId", row.get("participant_id"));
            out.put("participantName", row.get("participant_name"));
            out.put("level", row.get("level"));
            return GuestValues.toGuest(out);
        } catch (Exception e) {
            log.warn("targetInstance 查询失败 ruleCode={} bizId={}: {}", escalationOf, bizId, e.getMessage());
            return null;
        }
    }

    private static String format(Object datetime) {
        if (datetime instanceof java.util.Date d) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
        }
        return datetime == null ? null : String.valueOf(datetime);
    }
}
