/*******************************************************************************
 * Copyright (c) 2007, 2012 Innoopract Informationssysteme GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Innoopract Informationssysteme GmbH - initial API and implementation
 *    EclipseSource - ongoing development
 ******************************************************************************/
package org.eclipse.rap.ui.internal.launch.junit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.pde.ui.launcher.JUnitWorkbenchLaunchShortcut;
import org.eclipse.pde.ui.launcher.OSGiLaunchShortcut;
import org.eclipse.rap.ui.internal.launch.RAPLaunchConfig;


public final class RAPJUnitLaunchShortcut extends JUnitWorkbenchLaunchShortcut {

  private static final String LAUNCH_CONFIGURATION_TYPE
    = "org.eclipse.rap.ui.launch.RAPJUnitTestLauncher"; //$NON-NLS-1$
  private OSGiLaunchShortcutExtension ols = new OSGiLaunchShortcutExtension();


  private static final class OSGiLaunchShortcutExtension extends OSGiLaunchShortcut {
    public void initializeConfiguration( ILaunchConfigurationWorkingCopy configuration ) {
      super.initializeConfiguration( configuration );
    }
  }


  protected String getLaunchConfigurationTypeId() {
    return LAUNCH_CONFIGURATION_TYPE;
  }

  protected ILaunchConfigurationWorkingCopy createLaunchConfiguration( IJavaElement element )
    throws CoreException
  {
    ILaunchConfigurationWorkingCopy result = super.createLaunchConfiguration( element );
    ols.initializeConfiguration( result );
    result.setAttribute( RAPLaunchConfig.SERVLET_PATH, "/rapjunit" ); //$NON-NLS-1$
    return result;
  }
}
