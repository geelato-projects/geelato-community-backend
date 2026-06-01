package cn.geelato.web.platform.run.monitor.auxiliary;

public interface AuxiliarySuiteHealthParser {
    boolean supports(String parserType);

    AuxiliarySuiteHealthSnapshot parse(AuxiliarySuiteDefinition definition, Integer httpStatus, String responseBody, long checkedAt, long durationMs);
}
