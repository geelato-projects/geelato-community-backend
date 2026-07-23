package cn.geelato.ide.entitydef.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体定义校验结果（/ide/entity/validate 返回）。
 *
 * @author geelato
 */
@Data
public class IdeEntityValidateResult {

    private boolean valid;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public static IdeEntityValidateResult ok() {
        IdeEntityValidateResult r = new IdeEntityValidateResult();
        r.valid = true;
        return r;
    }

    public static IdeEntityValidateResult fail(String error) {
        IdeEntityValidateResult r = new IdeEntityValidateResult();
        r.valid = false;
        r.errors.add(error);
        return r;
    }

    public IdeEntityValidateResult addError(String msg) {
        this.valid = false;
        this.errors.add(msg);
        return this;
    }

    public IdeEntityValidateResult addWarning(String msg) {
        this.warnings.add(msg);
        return this;
    }
}
