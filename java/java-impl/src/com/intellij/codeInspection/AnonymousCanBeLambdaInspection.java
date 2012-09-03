/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class AnonymousCanBeLambdaInspection extends BaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + AnonymousCanBeLambdaInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Anonymous type can be replaced with lambda";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2Lambda";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(PsiAnonymousClass aClass) {
        super.visitAnonymousClass(aClass);
        if (PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8)) {
          final PsiClassType baseClassType = aClass.getBaseClassType();
          final String functionalInterfaceErrorMessage = LambdaUtil.checkInterfaceFunctional(baseClassType);
          if (functionalInterfaceErrorMessage == null) {
            final PsiMethod[] methods = aClass.getMethods();
            if (methods.length == 1) {
              final PsiCodeBlock body = methods[0].getBody();
              if (body != null) {
                final boolean [] recursive = new boolean[1];
                body.accept(new JavaRecursiveElementWalkingVisitor() {
                  @Override
                  public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
                    super.visitMethodCallExpression(methodCallExpression);
                    if (methodCallExpression.resolveMethod() == methods[0]) {
                      recursive[0] = true;
                    }
                  }
                });
                if (!recursive[0]) {
                  holder.registerProblem(aClass.getBaseClassReference(), "Anonymous #ref #loc can be replaced with lambda",
                                         ProblemHighlightType.LIKE_UNUSED_SYMBOL, new ReplaceWithLambdaFix());
                }
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithLambdaFix implements LocalQuickFix, HighPriorityAction {
    @NotNull
    @Override
    public String getName() {
      return "Replace with lambda";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        final PsiAnonymousClass anonymousClass = PsiTreeUtil.getParentOfType(element, PsiAnonymousClass.class);
        LOG.assertTrue(anonymousClass != null);

        boolean validContext = LambdaUtil.isValidLambdaContext(anonymousClass.getParent().getParent());
        final String canonicalText = anonymousClass.getBaseClassType().getCanonicalText();
        final PsiMethod method = anonymousClass.getMethods()[0];
        LOG.assertTrue(method != null);

        final String lambdaWithTypesDeclared = composeLambdaText(method, true);
        final String withoutTypesDeclared = composeLambdaText(method, false);
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiLambdaExpression lambdaExpression =
          (PsiLambdaExpression)elementFactory.createExpressionFromText(withoutTypesDeclared, anonymousClass);
        final PsiNewExpression newExpression = (PsiNewExpression)anonymousClass.getParent();
        lambdaExpression = (PsiLambdaExpression)newExpression.replace(lambdaExpression);
        if (!validContext) {
          lambdaExpression.replace(elementFactory.createExpressionFromText("((" + canonicalText + ")" + withoutTypesDeclared + ")", lambdaExpression));
          return;
        }
        PsiType interfaceType = lambdaExpression.getFunctionalInterfaceType();
        if (isInferenced(lambdaExpression, interfaceType)) {
          lambdaExpression = (PsiLambdaExpression)lambdaExpression.replace(elementFactory.createExpressionFromText(lambdaWithTypesDeclared, lambdaExpression));

          interfaceType = lambdaExpression.getFunctionalInterfaceType();
          if (isInferenced(lambdaExpression, interfaceType)) {
            lambdaExpression.replace(elementFactory.createExpressionFromText("(" + canonicalText + ")" + withoutTypesDeclared, lambdaExpression));
          }
        }
      }
    }

    private static boolean isInferenced(PsiLambdaExpression lambdaExpression, PsiType interfaceType) {
      return interfaceType == null || !LambdaUtil.isLambdaFullyInferred(lambdaExpression, interfaceType) || LambdaUtil.checkInterfaceFunctional(interfaceType) != null;
    }

    private static String composeLambdaText(PsiMethod method, final boolean appendType) {
      final StringBuilder buf = new StringBuilder();
      if (appendType) {
        buf.append(method.getParameterList().getText());
      } else {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 1) {
          buf.append("(");
        }
        buf.append(StringUtil.join(parameters,
                                   new Function<PsiParameter, String>() {
                                     @Override
                                     public String fun(PsiParameter parameter) {
                                       String parameterName = parameter.getName();
                                       if (parameterName == null) {
                                         parameterName = "";
                                       }
                                       return parameterName;
                                     }
                                   }, ","));
        if (parameters.length != 1) {
          buf.append(")");
        }
      }
      buf.append("->");
      final PsiCodeBlock body = method.getBody();
      LOG.assertTrue(body != null);
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
        PsiExpression value = ((PsiReturnStatement)statements[0]).getReturnValue();
        if (value != null) {
          buf.append(value.getText());
          return buf.toString();
        }
      }
      buf.append(body.getText());
      return buf.toString();
    }
  }
}