/*******************************************************************************
 * Copyright (c) 2013, 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html).

 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 */
package internal

/**
 * @author Martin Lippert
 */
public class CloudSyncMetadataListener(private val repository: Repository) : IResourceChangeListener {

  public fun resourceChanged(event: IResourceChangeEvent) {
    try {
      event.getDelta().accept(object : IResourceDeltaVisitor() {
        throws(javaClass<CoreException>())
        public fun visit(delta: IResourceDelta): Boolean {
          repository.metadataChanged(delta)
          return true
        }
      })
    }
    catch (e: CoreException) {
      e.printStackTrace()
    }

  }

}
