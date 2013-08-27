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

package org.sonatype.security.model.upgrade;

import java.util.List;

import org.sonatype.configuration.upgrade.ConfigurationIsCorruptedException;
import org.sonatype.security.model.v2_0_2.CUser;
import org.sonatype.security.model.v2_0_2.CUserRoleMapping;
import org.sonatype.security.model.v2_0_2.Configuration;

public class MockSecurityDataUpgrader
    extends AbstractDataUpgrader<Configuration>
    implements SecurityDataUpgrader
{

  @Override
  public void doUpgrade(Configuration configuration)
      throws ConfigurationIsCorruptedException
  {
    // replace the admin user's name with admin-user
    for (CUser user : (List<CUser>) configuration.getUsers()) {
      if (user.getId().equals("admin")) {
        user.setId("admin-user");
      }
    }

    for (CUserRoleMapping roleMapping : (List<CUserRoleMapping>) configuration.getUserRoleMappings()) {
      if (roleMapping.getUserId().equals("admin")) {
        roleMapping.setUserId("admin-user");
      }
    }

  }

}
