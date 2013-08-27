/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.nexus.testsuite.p2.meclipse0393;

import java.io.IOException;

import org.sonatype.nexus.test.utils.TestProperties;
import org.sonatype.nexus.testsuite.p2.AbstractNexusProxyP2IT;

import org.junit.Test;

public class MECLIPSE0393P2ProxyCompositeWithMirrorIT
    extends AbstractNexusProxyP2IT
{

  public MECLIPSE0393P2ProxyCompositeWithMirrorIT() {
    super("meclipse0393");
  }

  @Override
  public void copyTestResources()
      throws IOException
  {
    super.copyTestResources();

    final String proxyRepoBaseUrl = TestProperties.getString("proxy.repo.base.url");

    replaceInFile(
        localStorageDir + "/meclipse0393/memberrepo1/artifacts.xml", "${proxy-repo-base-url}", proxyRepoBaseUrl
    );
    replaceInFile(
        localStorageDir + "/meclipse0393/memberrepo2/artifacts.xml", "${proxy-repo-base-url}", proxyRepoBaseUrl
    );
    replaceInFile(
        localStorageDir + "/meclipse0393/memberrepo1/mirrors.xml", "${proxy-repo-base-url}", proxyRepoBaseUrl
    );
    replaceInFile(
        localStorageDir + "/meclipse0393/memberrepo2/mirrors.xml", "${proxy-repo-base-url}", proxyRepoBaseUrl
    );
  }

  @Test
  public void test()
      throws Exception
  {
    installAndVerifyP2Feature();
  }

}
