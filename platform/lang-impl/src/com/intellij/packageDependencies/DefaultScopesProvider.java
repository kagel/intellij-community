/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.packageDependencies;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.ProjectFilesScope;
import com.intellij.psi.search.scope.TestsScope;
import com.intellij.psi.search.scope.packageSet.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
public class DefaultScopesProvider extends CustomScopesProviderEx {
  private final NamedScope myProblemsScope;
  private final Project myProject;
  private final List<NamedScope> myScopes;

  public static DefaultScopesProvider getInstance(Project project) {
    return Extensions.findExtension(CUSTOM_SCOPES_PROVIDER, project, DefaultScopesProvider.class);
  }

  public DefaultScopesProvider(Project project) {
    myProject = project;
    final NamedScope projectScope = new ProjectFilesScope();
    final NamedScope nonProjectScope = new NonProjectFilesScope();
    final String text = FilePatternPackageSet.SCOPE_FILE + ":*//*";
    myProblemsScope = new NamedScope(IdeBundle.message("predefined.scope.problems.name"), new AbstractPackageSet(text) {
      public boolean contains(VirtualFile file, NamedScopesHolder holder) {
        return holder.getProject() == myProject
               && WolfTheProblemSolver.getInstance(myProject).isProblemFile(file);
      }
    });
    myScopes = Arrays.asList(projectScope, getProblemsScope(), getAllScope(), nonProjectScope);
  }

  @NotNull
  public List<NamedScope> getCustomScopes() {
    return myScopes;
  }

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  private static class AllScopeHolder {
    private static final String TEXT = FilePatternPackageSet.SCOPE_FILE + ":*//*";
    private static final NamedScope ALL = new NamedScope("All", new AbstractPackageSet(TEXT, 0) {
      public boolean contains(final VirtualFile file, final NamedScopesHolder scopesHolder) {
        return true;
      }
    });
  }

  public static NamedScope getAllScope() {
    return AllScopeHolder.ALL;
  }

  public NamedScope getProblemsScope() {
    return myProblemsScope;
  }

  public List<NamedScope> getAllCustomScopes() {
    final List<NamedScope> scopes = new ArrayList<NamedScope>();
    for (CustomScopesProvider provider : Extensions.getExtensions(CUSTOM_SCOPES_PROVIDER, myProject)) {
      scopes.addAll(provider.getCustomScopes());
    }
    return scopes;
  }

  @Nullable
  public NamedScope findCustomScope(String name) {
    for (NamedScope scope : getAllCustomScopes()) {
      if (name.equals(scope.getName())) {
        return scope;
      }
    }
    return null;
  }
}
