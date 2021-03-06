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
package com.intellij.compiler.server.impl;

import com.intellij.compiler.server.CompileServerPathProvider;
import com.intellij.compiler.server.CompileServerPlugin;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class CompileServerClasspathManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.impl.CompileServerClasspathManager");

  private List<String> myCompileServerPluginsClasspath;

  public List<String> getCompileServerPluginsClasspath(Project project) {
    List<String> staticClasspath = getStaticClasspath();
    List<String> dynamicClasspath = getDynamicClasspath(project);

    if (dynamicClasspath.isEmpty()) {
      return staticClasspath;
    }
    else {
      dynamicClasspath.addAll(staticClasspath);
      return dynamicClasspath;
    }
  }

  private List<String> getStaticClasspath() {
    if (myCompileServerPluginsClasspath == null) {
      myCompileServerPluginsClasspath = computeCompileServerPluginsClasspath();
    }
    return myCompileServerPluginsClasspath;
  }

  private static List<String> computeCompileServerPluginsClasspath() {
    final List<String> classpath = ContainerUtil.newArrayList();
    for (CompileServerPlugin serverPlugin : CompileServerPlugin.EP_NAME.getExtensions()) {
      final PluginId pluginId = serverPlugin.getPluginDescriptor().getPluginId();
      final IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
      LOG.assertTrue(plugin != null, pluginId);
      final File baseFile = plugin.getPath();
      if (baseFile.isFile()) {
        classpath.add(baseFile.getPath());
      }
      else if (baseFile.isDirectory()) {
        for (String relativePath : StringUtil.split(serverPlugin.getClasspath(), ";")) {
          final File jarFile = new File(new File(baseFile, "lib"), relativePath);
          File classesDir = new File(baseFile, "classes");
          if (jarFile.exists()) {
            classpath.add(jarFile.getPath());
          }
          else if (classesDir.isDirectory()) {
            //'plugin run configuration': all module output are copied to 'classes' folder
            classpath.add(classesDir.getPath());
          }
          else {
            //development mode: add directory out/classes/production/<jar-name> to classpath, assuming that jar-name is equal to module name
            final String moduleName = FileUtil.getNameWithoutExtension(PathUtil.getFileName(relativePath));
            final File dir = new File(baseFile.getParentFile(), moduleName);
            if (dir.exists()) {
              classpath.add(dir.getPath());
            }
            else {
              //looks like <jar-name> refers to a library, try to find it under <plugin-dir>/lib
              File pluginDir = getPluginDir(plugin);
              if (pluginDir != null) {
                File libraryFile = new File(pluginDir, "lib" + File.separator + PathUtil.getFileName(relativePath));
                if (libraryFile.exists()) {
                  classpath.add(libraryFile.getPath());
                }
                else {
                  LOG.error("Cannot add plugin '" + plugin.getName() + "' to external compiler classpath: " +
                            "library " + libraryFile.getAbsolutePath() + " not found");
                }
              }
              else {
                LOG.error("Cannot add plugin '" + plugin.getName() + "' to external compiler classpath: home directory of plugin not found");
              }
            }
          }
        }
      }
    }
    return classpath;
  }

  @Nullable
  private static File getPluginDir(IdeaPluginDescriptor plugin) {
    String pluginDirName = StringUtil.getShortName(plugin.getPluginId().getIdString());
    List<String> roots = Arrays.asList(new File(PathManager.getHomePath(), "plugins").getPath(),
                                       new File(PathManager.getHomePath(), "community/plugins").getPath());
    String extraDir = System.getProperty("idea.external.build.development.plugins.dir");
    if (extraDir != null) {
      roots.add(extraDir);
    }
    for (String root : roots) {
      File pluginDir = new File(root, pluginDirName);
      if (pluginDir.isDirectory()) {
        return pluginDir;
      }
    }
    return null;
  }

  private static List<String> getDynamicClasspath(Project project) {
    List<String> classpath = ContainerUtil.newArrayList();
    for (CompileServerPathProvider provider : project.getExtensions(CompileServerPathProvider.EP_NAME)) {
      classpath.addAll(provider.getClassPath());
    }
    return classpath;
  }
}
