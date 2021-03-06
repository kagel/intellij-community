/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class UrlClassLoader extends ClassLoader {
  private final ClassPath myClassPath;
  private final List<URL> myURLs;
  @NonNls static final String CLASS_EXTENSION = ".class";
  protected static final boolean myDebugTime = false;
  protected static final long NS_THRESHOLD = 10000000;

  public UrlClassLoader(@NotNull ClassLoader parent) {
    this(Arrays.asList(((URLClassLoader)parent).getURLs()), parent.getParent(), true, true);
  }

  public UrlClassLoader(List<URL> urls, @Nullable ClassLoader parent) {
    this(urls, parent, false, false);
  }

  public UrlClassLoader(URL[] urls, @Nullable ClassLoader parent) {
    this(Arrays.asList(urls), parent, false, false);
  }

  public UrlClassLoader(List<URL> urls, @Nullable ClassLoader parent, boolean canLockJars, boolean canUseCache) {
    this(urls, parent, canLockJars, canUseCache, false, true);
  }

  public UrlClassLoader(List<URL> urls, @Nullable ClassLoader parent, boolean canLockJars, boolean canUseCache, boolean acceptUnescapedUrls, final boolean preloadJarContents) {
    super(parent);

    List<URL> list = ContainerUtil.map(urls, new Function<URL, URL>() {
      @Override
      public URL fun(URL url) {
        return internProtocol(url);
      }
    });
    myClassPath = new ClassPath(list.toArray(new URL[list.size()]), canLockJars, canUseCache, acceptUnescapedUrls, preloadJarContents);
    myURLs = list;
  }

  @NotNull
  public static URL internProtocol(@NotNull URL url) {
    try {
      if ("file".equals(url.getProtocol())) {
        return new URL("file", url.getHost(), url.getPort(), url.getFile());
      }
      if ("jar".equals(url.getProtocol())) {
        return new URL("jar", url.getHost(), url.getPort(), url.getFile());
      }
      return url;
    }
    catch (MalformedURLException e) {
      LOG.error(e);
      return null;
    }
  }

  public void addURL(URL url) {
    myClassPath.addURL(url);
    myURLs.add(url);
  }

  public List<URL> getUrls() {
    return Collections.unmodifiableList(myURLs);
  }

  @Override
  protected Class findClass(final String name) throws ClassNotFoundException {
    Resource res = myClassPath.getResource(name.replace('.', '/').concat(CLASS_EXTENSION), false);
    if (res == null) {
      throw new ClassNotFoundException(name);
    }

    try {
      return defineClass(name, res);
    }
    catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }
  }


  @Override
  protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
    return super.loadClass(name, resolve);
  }

  @Nullable
  protected Class _findClass(@NotNull String name) {
    Resource res = myClassPath.getResource(name.replace('.', '/').concat(CLASS_EXTENSION), false);
    if (res == null) {
      return null;
    }

    try {
      return defineClass(name, res);
    }
    catch (IOException e) {
      return null;
    }
  }

  private Class defineClass(String name, Resource res) throws IOException {
    int i = name.lastIndexOf('.');
    if (i != -1) {
      String pkgname = name.substring(0, i);
      // Check if package already loaded.
      Package pkg = getPackage(pkgname);
      if (pkg == null) {
        try {
          definePackage(pkgname, null, null, null, null, null, null, null);
        }
        catch (IllegalArgumentException e) {
          // do nothing, package already defined by some other thread
        }
      }
    }

    byte[] b = res.getBytes();
    return _defineClass(name, b);
  }

  protected Class _defineClass(final String name, final byte[] b) {
    return defineClass(name, b, 0, b.length);
  }

  @Override
  @Nullable  // Accessed from PluginClassLoader via reflection // TODO do we need it?
  public URL findResource(final String name) {
    final long started = myDebugTime ? System.nanoTime():0;

    try {
      return findResourceImpl(name);
    } finally {
      long doneFor = myDebugTime ? (System.nanoTime() - started):0;
      if (doneFor > NS_THRESHOLD) {
        System.out.println((doneFor / 1000000) + " ms for UrlClassLoader.getResource, resource:"+name);
      }
    }
  }

  protected URL findResourceImpl(final String name) {
    Resource res = _getResource(name);
    if (res == null) return null;
    return res.getURL();
  }

  @Nullable
  private Resource _getResource(final String name) {
    String n = name;

    if (n.startsWith("/")) n = n.substring(1);
    return myClassPath.getResource(n, true);
  }

  @Nullable
  @Override
  public InputStream getResourceAsStream(final String name) {
    try {
      Resource res = _getResource(name);
      if (res == null) return null;
      return res.getInputStream();
    }
    catch (IOException e) {
      return null;
    }
  }

  // Accessed from PluginClassLoader via reflection // TODO do we need it?
  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    return myClassPath.getResources(name, true);
  }
  
  static final boolean doDebug = System.getProperty("idea.classloading.debug") != null;
  private static final Logger LOG = Logger.getInstance("idea.UrlClassLoader");

  static void debug(String s) {
    System.out.println(s); // TODO: remove
    LOG.debug(s);
  }
}
