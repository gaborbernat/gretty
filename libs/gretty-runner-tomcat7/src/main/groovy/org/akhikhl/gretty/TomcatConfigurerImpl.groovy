/*
 * Gretty
 *
 * Copyright (C) 2013-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 * See the file "CONTRIBUTORS" for complete list of contributors.
 */
package org.akhikhl.gretty

import org.apache.catalina.Globals
import org.apache.catalina.core.StandardContext
import org.apache.catalina.deploy.WebXml
import org.apache.catalina.startup.ContextConfig
import org.apache.catalina.startup.Tomcat
import org.apache.naming.resources.ProxyDirContext
import org.apache.naming.resources.VirtualDirContext
import org.apache.tomcat.JarScanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class TomcatConfigurerImpl implements TomcatConfigurer {

  private static final Logger log = LoggerFactory.getLogger(TomcatConfigurerImpl)

  protected static boolean isServletApi(String filePath) {
    filePath.matches(/^.*servlet-api.*\.jar$/)
  }

  protected Set virtualWebInfLibs = new LinkedHashSet()

  @Override
  ContextConfig createContextConfig(URL[] classpathUrls) {

    new ContextConfig() {

      protected Map<String,WebXml> processJarsForWebFragments(WebXml application) {
        def fragments = super.processJarsForWebFragments(application)
        // here we enable annotation processing for non-jar urls on the classpath
        for(URL url in classpathUrls.findAll { !it.path.endsWith('.jar') && new File(it.path).exists() }) {
          WebXml fragment = new WebXml()
          fragment.setDistributable(true)
          fragment.setURL(url)
          fragment.setName(url.toString())
          fragments[fragment.getName()] = fragment
        }
        fragments
      }
    }
  }

  @Override
  JarScanner createJarScanner(JarScanner jarScanner, JarSkipPatterns skipPatterns) {
    new JarScannerEx(skipPatterns, virtualWebInfLibs)
  }

  @Override
  void setBaseDir(Tomcat tomcat, File baseDir) {
    tomcat.baseDir = baseDir.absolutePath
    tomcat.engine.baseDir = baseDir.absolutePath
    System.setProperty(Globals.CATALINA_BASE_PROP, baseDir.absolutePath)
  }

  @Override
  void setResourceBase(StandardContext context, Map webappParams) {

    context.setDocBase(webappParams.resourceBase)

    List extraResourcePaths = []

    if(webappParams.extraResourceBases)
      extraResourcePaths += webappParams.extraResourceBases.collect { "/=$it" }

    Set classpathJarParentDirs = new LinkedHashSet()

    webappParams.webappClassPath.findAll { it.endsWith('.jar') && !isServletApi(it) }.each {
      File jarFile = it.startsWith('file:') ? new File(new URI(it)) : new File(it)
      classpathJarParentDirs.add jarFile.parentFile.absolutePath
      virtualWebInfLibs.add('/WEB-INF/lib/' + jarFile.name)
    }

    extraResourcePaths += classpathJarParentDirs.collect { "/WEB-INF/lib=$it" }

    if(extraResourcePaths) {
      VirtualDirContext vdc = new VirtualDirContext()
      vdc.setExtraResourcePaths(extraResourcePaths.join(','))
      context.setResources(vdc)
    }
  }
}
