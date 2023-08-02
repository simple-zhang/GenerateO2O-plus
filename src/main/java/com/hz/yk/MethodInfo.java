package com.hz.yk;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;

import java.util.List;

public class MethodInfo {

    private final String methodName;
    private final String returnClassName;
    private final List<PsiField> returnFields;
    private final PsiParameter psiParameter;
    private final List<PsiField> parameterFields;

    /**
     * @param methodName      方法名称
     * @param returnClassName 返回的值的class名称
     * @param psiParameter    方法参数第一个值
     * @param parameterFields  方法参数的class里field 列表
     */
    public MethodInfo(String methodName,
                      String returnClassName,
                      List<PsiField> returnFields,
                      PsiParameter psiParameter,
                      List<PsiField> parameterFields) {
        this.methodName = methodName;
        this.returnClassName = returnClassName;
        this.returnFields = returnFields;
        this.psiParameter = psiParameter;
        this.parameterFields = parameterFields;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReturnClassName() {
        return returnClassName;
    }

    public List<PsiField> getReturnFields() {
        return returnFields;
    }

    public PsiParameter getPsiParameter() {
        return psiParameter;
    }

    public List<PsiField> getParameterFields() {
        return parameterFields;
    }
}
