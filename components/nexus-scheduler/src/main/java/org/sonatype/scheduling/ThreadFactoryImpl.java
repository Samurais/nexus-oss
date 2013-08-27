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

package org.sonatype.scheduling;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadFactoryImpl
    implements ThreadFactory
{
  private static final AtomicInteger poolNumber = new AtomicInteger(1);

  private final AtomicInteger threadNumber = new AtomicInteger(1);

  private final String namePrefix;

  private final ThreadGroup schedulerThreadGroup;

  private final int threadPriority;

  public ThreadFactoryImpl() {
    this(Thread.MIN_PRIORITY);
  }

  public ThreadFactoryImpl(final int threadPriority) {
    int poolNum = poolNumber.getAndIncrement();
    this.schedulerThreadGroup = new ThreadGroup("Sisu scheduler #" + poolNum);
    this.namePrefix = "pxpool-" + poolNum + "-thread-";
    this.threadPriority = threadPriority;
  }

  public Thread newThread(final Runnable r) {
    final Thread result = new Thread(getSchedulerThreadGroup(), r, namePrefix + threadNumber.getAndIncrement());
    result.setDaemon(false);
    result.setPriority(this.threadPriority);
    return result;
  }

  public ThreadGroup getSchedulerThreadGroup() {
    return this.schedulerThreadGroup;
  }
}
