package cn.geelato.web.platform.m.excel.entity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author diabl
 * @description: 业务数据
 * @date 2023/10/15 10:53
 */
public class BusinessData {
    private int XIndex;
    private int YIndex;
    private Object value;
    private Object primevalValue;
    private Set<Object> transitionValue = new LinkedHashSet<>();
    private String[] multiValue;
    private BusinessTypeData businessTypeData;
    private Set<String> errorMsg = new LinkedHashSet<>();

    public int getXIndex() {
        return XIndex;
    }

    public void setXIndex(int XIndex) {
        this.XIndex = XIndex;
    }

    public int getYIndex() {
        return YIndex;
    }

    public void setYIndex(int YIndex) {
        this.YIndex = YIndex;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Object getPrimevalValue() {
        return primevalValue;
    }

    public void setPrimevalValue(Object primevalValue) {
        this.primevalValue = primevalValue;
    }

    public Set<Object> getTransitionValue() {
        return transitionValue;
    }

    public Set<String> getTransitionValueString() {
        Set<String> stringSet = new LinkedHashSet<>();
        if (this.transitionValue != null && !this.transitionValue.isEmpty()) {
            for (Object obj : this.transitionValue) {
                stringSet.add(String.valueOf(obj));
            }
        }

        return stringSet;
    }

    public void setTransitionValue(Set<Object> transitionValue) {
        this.transitionValue = transitionValue;
    }

    public void setTransitionValue(Object transitionValue) {
        if (this.transitionValue == null) {
            this.transitionValue = new LinkedHashSet<>();
        }
        this.transitionValue.add(transitionValue);
    }

    public void setTransitionValues(Set<Object> transitionValues) {
        if (this.transitionValue == null) {
            this.transitionValue = new LinkedHashSet<>();
        }
        this.transitionValue.addAll(transitionValues);
    }

    public String[] getMultiValue() {
        return multiValue;
    }

    public void setMultiValue(String[] multiValue) {
        this.multiValue = multiValue;
    }

    public BusinessTypeData getBusinessTypeData() {
        return businessTypeData;
    }

    public void setBusinessTypeData(BusinessTypeData businessTypeData) {
        this.businessTypeData = businessTypeData;
    }

    public Set<String> getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(Set<String> errorMsg) {
        this.errorMsg = errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        if (this.errorMsg == null) {
            this.errorMsg = new LinkedHashSet<>();
        }
        this.errorMsg.add(errorMsg);
    }

    public void setErrorMsgs(Set<String> errorMsg) {
        if (this.errorMsg == null) {
            this.errorMsg = new LinkedHashSet<>();
        }
        this.errorMsg.addAll(errorMsg);
    }

    public boolean isValidate() {
        return !(this.errorMsg != null && this.errorMsg.size() > 0);
    }
}
