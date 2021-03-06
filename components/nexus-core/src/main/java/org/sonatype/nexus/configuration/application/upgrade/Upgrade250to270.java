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

package org.sonatype.nexus.configuration.application.upgrade;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.sonatype.configuration.upgrade.ConfigurationIsCorruptedException;
import org.sonatype.configuration.upgrade.SingleVersionUpgrader;
import org.sonatype.configuration.upgrade.UpgradeMessage;
import org.sonatype.nexus.configuration.ModelloUtils;
import org.sonatype.nexus.configuration.model.CScheduledTask;
import org.sonatype.nexus.configuration.model.CSmtpConfiguration;
import org.sonatype.nexus.configuration.model.Configuration;
import org.sonatype.nexus.configuration.model.v2_7_0.upgrade.BasicVersionUpgrade;
import org.sonatype.nexus.logging.AbstractLoggingComponent;
import org.sonatype.nexus.tasks.descriptors.EmptyTrashTaskDescriptor;

import com.google.common.io.Closeables;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Upgrades configuration model from version 2.5.0 to 2.7.0.
 *
 * @since 2.7.0
 */
@Component(role = SingleVersionUpgrader.class, hint = "2.5.0")
public class Upgrade250to270
    extends AbstractLoggingComponent
    implements SingleVersionUpgrader
{

  public Object loadConfiguration(File file)
      throws IOException, ConfigurationIsCorruptedException
  {
    FileReader fr = null;

    org.sonatype.nexus.configuration.model.v2_5_0.Configuration conf = null;

    try {
      // reading without interpolation to preserve user settings as variables
      fr = new FileReader(file);

      org.sonatype.nexus.configuration.model.v2_5_0.io.xpp3.NexusConfigurationXpp3Reader reader =
          new org.sonatype.nexus.configuration.model.v2_5_0.io.xpp3.NexusConfigurationXpp3Reader();

      conf = reader.read(fr);
    }
    catch (XmlPullParserException e) {
      throw new ConfigurationIsCorruptedException(file.getAbsolutePath(), e);
    }
    finally {
      Closeables.closeQuietly(fr);
    }

    return conf;
  }

  public void upgrade(UpgradeMessage message)
      throws ConfigurationIsCorruptedException
  {
    org.sonatype.nexus.configuration.model.v2_5_0.Configuration oldc =
        (org.sonatype.nexus.configuration.model.v2_5_0.Configuration) message.getConfiguration();

    BasicVersionUpgrade versionConverter = new BasicVersionUpgrade();

    Configuration newc = versionConverter.upgradeConfiguration(oldc);
    newc.setVersion(Configuration.MODEL_VERSION);

    upgradeEmptyTrashTaskConfiguration(newc);
    updateSmtpSslTlsSettings(newc);

    message.setModelVersion(Configuration.MODEL_VERSION);
    message.setConfiguration(newc);
  }

  /**
   * Performs upgrade for EmptyTrashTask tasks.
   * 
   * @param conf the new model (2.7.0)
   * @see <a href="https://issues.sonatype.org/browse/NEXUS-4580">NEXUS-4580</a>
   */
  protected void upgradeEmptyTrashTaskConfiguration(final Configuration conf) {
    final List<CScheduledTask> tasks = conf.getTasks();
    for (CScheduledTask task : tasks) {
      if (EmptyTrashTaskDescriptor.ID.equals(task.getType())) {
        final Map<String, String> taskConfig = ModelloUtils.getMapFromConfigList(task.getProperties());
        if (!taskConfig.containsKey(EmptyTrashTaskDescriptor.REPO_OR_GROUP_FIELD_ID)) {
          taskConfig.put(EmptyTrashTaskDescriptor.REPO_OR_GROUP_FIELD_ID, "all_repo");
          task.setProperties(ModelloUtils.getConfigListFromMap(taskConfig));
        }
      }
    }
  }

  /**
   * Performs upgrade for SMTP SSL/TLS settings
   * 
   * @param conf the new model (2.7.0)
   * @see <a href="https://issues.sonatype.org/browse/NEXUS-4997">NEXUS-4997</a>
   */
  protected void updateSmtpSslTlsSettings(final Configuration conf) {
    final CSmtpConfiguration smtp = conf.getSmtpConfiguration();
    if (smtp != null) {
      if (smtp.isSslEnabled() && smtp.isTlsEnabled()) {
        smtp.setSslEnabled(true);
        smtp.setTlsEnabled(false);
        getLogger()
            .warn(
                "SMTP related configuration change happened: both SSL and TLS was set, updated to use SSL only, assuming that SMTP server port is set to a port where remote SMTP server accepts SSL connections.");
      }
    }
  }
}
