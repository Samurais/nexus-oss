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

package org.sonatype.nexus.testsuite.p2.p2r03;

import java.io.File;

import org.sonatype.nexus.testsuite.p2.AbstractNexusP2GeneratorIT;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.sonatype.sisu.litmus.testsupport.hamcrest.FileMatchers.exists;

public class P2R0305AggregatedP2MetadataNotRemovedOnPassivateIT
    extends AbstractNexusP2GeneratorIT
{

  public P2R0305AggregatedP2MetadataNotRemovedOnPassivateIT() {
    super("p2r03");
  }

  /**
   * [NEXUS-5012]
   * When Nexus is stopped (p2 aggregated capability passivated) the p2 metadata files should still exist.
   */
  @Test
  public void test()
      throws Exception
  {
    final File artifactsXML = storageP2RepositoryArtifactsXML();
    final File contentXML = storageP2RepositoryContentXML();

    createP2RepositoryAggregatorCapability();

    assertThat("P2 artifacts.xml does exist", artifactsXML, exists());
    assertThat("P2 content.xml does exist", contentXML, exists());

    passivateP2RepositoryAggregatorCapability();

    assertThat("P2 artifacts.xml does exist", artifactsXML, exists());
    assertThat("P2 content.xml does exist", contentXML, exists());
  }

}
