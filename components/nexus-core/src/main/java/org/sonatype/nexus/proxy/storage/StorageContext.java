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

package org.sonatype.nexus.proxy.storage;

import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.storage.local.LocalStorageContext;
import org.sonatype.nexus.proxy.storage.remote.RemoteStorageContext;

/**
 * The storage settings and context. Used as super class in {@link LocalStorageContext} and {@link RemoteStorageContext}
 * Used to store "contextual" information (tied to {@link Repository} as storages (both local and remote) are
 * stateless and singletons.
 * 
 * @author cstamas
 */
public interface StorageContext
{
  // change detection

  /**
   * Returns the timestamp of latest change. Will propagate to parent if it is more recently changed as this (and
   * parent is set).
   * 
   * @deprecated Do not use this method anymore for change detection, use {@link #getGeneration()} instead. This
   *             method uses millisecond "resolution" and is unreliable to properly detect changes (as the change
   *             happened within millisecond), and it resulted in loss of updates in some subsystems.
   */
  @Deprecated
  long getLastChanged();

  /**
   * Returns the "generation" (somewhat in ratio with count of changes) of this context, usable to detect changes
   * happened against it. Operations like {@link #putContextObject(String, Object)} and
   * {@link #removeContextObject(String)} increases this number, while other "read" operations does not. Also, even
   * if
   * this context did not change, but it's parent is, the count will grow. This method simply serves the
   * "change detection" purpose, as caller might store the initial value, and on next invocation simply compare them,
   * and if they differ, it would mean this context (or hierarchy, as parent is involved too) did change meanwhile.
   * 
   * @return the context generation, that grows by changes made against context (nothing guarantees next generation
   *         is
   *         previous increased by 1, it simply grows).
   * @since 2.1
   */
  int getGeneration();

  // parent

  /**
   * Returns the parent context, or null if not set.
   */
  StorageContext getParentStorageContext();

  /**
   * Sets the parent context, or nullify it. Note: as of Nexus 2.1 this method is deprecated and is made a NOOP.
   * 
   * @deprecated This method is NOOP, as changing parents are not possible, nor was ever properly supported, as it
   *             interfered with "change detection".
   */
  @Deprecated
  void setParentStorageContext(StorageContext parent);

  // modification

  /**
   * Gets an object from context. Will propagate to parent if not found in this context (and parent is set). Caller must
   * ensure what happens with returned object is correct (ie. mutating an object coming from parent might be an error).
   * To correctly access a value you want to mutate (and not affect "global" level objects) and ensure you are not about
   * to mutate a "global" object, do something as following:
   * 
   * <pre>
   *     final RemoteStorageContext rsc = ...
   *     final String KEY = "foo.bar";
   *     if (!rsc.hasContextObject(KEY)) {
   *       rsc.putContextObject(KEY, new FooBar());
   *     }
   *     rsc.getContextObject(KEY).setFoo();
   * </pre>
   * 
   * @see <a href="https://issues.sonatype.org/browse/NEXUS-5690">NEXUS-5690 Remove per repository http proxy
   *      configuration</a>
   */
  Object getContextObject(String key);

  /**
   * Puts an object into this context, potentially overriding same keyed object from parent.
   */
  void putContextObject(String key, Object value);

  /**
   * Removed an object from this context. Parent remains unchanged.
   */
  void removeContextObject(String key);

  /**
   * Returns true if this context has an object under the given key. This call does not propagate to parent.
   */
  boolean hasContextObject(String key);
}
