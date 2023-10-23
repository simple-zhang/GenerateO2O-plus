package com.hz.yk;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author wuzheng.yk
 * @date 2021/5/12
 */
public class GenerateAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        
        final PsiElement psiElement = getPsiElement(event);
        if (psiElement == null) {
            return;
        }
        
        generateO2OBlock(psiElement);
    }

    private void generateO2OBlock(@NotNull PsiElement psiElement) {
        WriteCommandAction.runWriteCommandAction(psiElement.getProject(), () -> createBlock(psiElement));
    }

    private void createBlock(PsiElement psiElement) {
        final String codeBlockStr = getCodeBlockStr(psiElement);
        if (codeBlockStr == null) {
            return;
        }
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiElement.getProject());
        final PsiCodeBlock newCodeBlock = elementFactory.createCodeBlockFromText(codeBlockStr, psiElement);
        final PsiCodeBlock psiCodeBlock = getPsiCodeBlock(psiElement);
        if (Objects.nonNull(psiCodeBlock)) {
            psiCodeBlock.replace(newCodeBlock);
        }
    }

    /**
     * 获取所在method的信息
     */
    @Nullable
    private MethodInfo getMethodInfo(PsiElement psiElement) {
        final PsiMethod psiMethod = getPsiMethod(psiElement);
        if (psiMethod == null) {
            return null; 
        }
        String methodName = psiMethod.getName();
        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null) {
            return null;
        }
        List<PsiField> returnFields = this.getReturnFields(returnType);
        String returnClassName = returnType.getPresentableText();
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[0];
        //带package的class名称
        String parameterClassWithPackage = psiParameter.getType().getCanonicalText(false);
        //为了解析字段，这里需要加载参数的class
        JavaPsiFacade facade = JavaPsiFacade.getInstance(psiMethod.getProject());
        PsiClass parameterClass = facade
                .findClass(parameterClassWithPackage, GlobalSearchScope.allScope(psiMethod.getProject()));
        if (parameterClass == null) {
            return null;
        }

        //把原来的getFields 替换成getAllFields ，支持父类的field
        List<PsiField> parameterFields = new CollectionListModel<>(parameterClass.getAllFields()).getItems();
        return new MethodInfo(methodName, returnClassName, returnFields, psiParameter, parameterFields);
    }

    private List<PsiField> getReturnFields(PsiType returnType) {
        List<PsiField> fields = new ArrayList<>();

        if (returnType instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) returnType;
            PsiClass psiClass = classType.resolve();

            while (psiClass != null) {
                for (PsiField field : psiClass.getFields()) {
                    if (hasSetterMethod(psiClass, field)) {
                        fields.add(field);
                    }
                }

                psiClass = psiClass.getSuperClass();
            }
        }

        return fields;
    }

    private boolean hasSetterMethod(PsiClass psiClass, PsiField field) {
        String setterName = "set" + capitalizeFirstLetter(field.getName());

        for (PsiMethod method : psiClass.getMethods()) {
            if (setterName.equals(method.getName())
                    && method.getParameterList().getParametersCount() == 1) {
                return true;
            }
        }

        return false;
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 获取方法体
     */
    @Nullable
    private String getCodeBlockStr(@NotNull PsiElement psiElement) {
        final MethodInfo methodInfo = getMethodInfo(psiElement);
        if (methodInfo == null) {
            return null;
        }

        final String returnClassName = methodInfo.getReturnClassName();
        String returnObjName = returnClassName.substring(0, 1).toLowerCase() + returnClassName.substring(1);
        String parameterName = methodInfo.getPsiParameter().getName();
        StringBuilder builder = new StringBuilder();
        builder.append("{\n if ( ").append(parameterName).append("== null ){\n").append("return null;\n}")
                .append(returnClassName).append(" ").append(returnObjName).append("= new ").append(returnClassName)
                .append("();\n");
        if (CollectionUtils.isEmpty(methodInfo.getReturnFields())) {
            return builder.toString();
        }
        Set<String> parameterFieldSet = methodInfo.getParameterFields().stream().map(PsiField::getName).collect(Collectors.toSet());
        for (PsiField field : methodInfo.getReturnFields()) {
            builder.append(returnObjName).append(".set").append(getFirstUpperCase(field.getName())).append("(");
            if (parameterFieldSet.contains(field.getName())) {
                PsiModifierList modifierList = field.getModifierList();
                if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.STATIC) || modifierList
                        .hasModifierProperty(PsiModifier.FINAL) || modifierList
                        .hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                    continue;
                }
                builder.append(parameterName).append(".get").append(getFirstUpperCase(field.getName())).append("()");
            }
            builder.append(");\n");
        }

        builder.append("return ").append(returnObjName).append(";\n}");
        return builder.toString();
    }

    private String getFirstUpperCase(String oldStr) {
        return oldStr.substring(0, 1).toUpperCase() + oldStr.substring(1);
    }

    @Nullable
    private PsiMethod getPsiMethod(@NotNull PsiElement elementAt) {
        return PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    }

    /**
     * 获取代码块
     */
    private PsiCodeBlock getPsiCodeBlock(PsiElement elementAt) {
        PsiElement targetElement = elementAt;
        while (!(targetElement instanceof PsiMethod) && targetElement != null) {
            targetElement = targetElement.getParent();
        }
        if (targetElement != null) {
            return ((PsiMethod) targetElement).getBody();
        } else {
            return null;
        }
    }

    @Nullable
    private PsiElement getPsiElement(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            e.getPresentation().setEnabled(false);
            return null;
        }
        //用来获取当前光标处的PsiElement
        int offset = editor.getCaretModel().getOffset();
        return psiFile.findElementAt(offset);
    }
}
