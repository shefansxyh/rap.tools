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
package org.eclipse.rap.internal.ui.templates;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

/**
 * Helper class for template creation.
 */
public final class TemplateUtil {

  public static final String PLUGIN_ID = "org.eclipse.rap.tools.templates"; //$NON-NLS-1$
  public static final String BUSINESS_THEME_ID
    = "org.eclipse.rap.design.example.business.theme"; //$NON-NLS-1$
  public static final String FANCY_THEME_ID
    = "org.eclipse.rap.design.example.fancy.theme"; //$NON-NLS-1$
  public static final String BUSINESS_FACTORY_ID
    = "org.eclipse.rap.design.example.business.factory"; //$NON-NLS-1$
  public static final String BUSINESS_LAYOUT_ID
    = "org.eclipse.rap.design.example.business.layout"; //$NON-NLS-1$
  public static final String STACK_ID
    = "org.eclipse.rap.design.example.stack.view"; //$NON-NLS-1$

  private TemplateUtil() {
    // prevent instantiation
  }

  public static URL getInstallURL() {
    return getBundle().getEntry( "/" ); //$NON-NLS-1$
  }

  public static ResourceBundle getPluginResourceBundle() {
    return Platform.getResourceBundle( getBundle() );
  }

  public static String getFormattedPackageName( final String id ) {
    StringBuffer buffer = new StringBuffer();
    for( int i = 0; i < id.length(); i++ ) {
      char ch = id.charAt( i );
      if( buffer.length() == 0 ) {
        if( Character.isJavaIdentifierStart( ch ) ) {
          buffer.append( Character.toLowerCase( ch ) );
        }
      } else {
        if( Character.isJavaIdentifierPart( ch ) || ch == '.' ) {
          buffer.append( ch );
        }
      }
    }
    return buffer.toString().toLowerCase( Locale.ENGLISH );
  }

  public static void log( final IStatus status ) {
    Platform.getLog( getBundle() ).log( status );
  }

  ///////////////////
  // helping methods

  private static Bundle getBundle() {
    return Platform.getBundle( PLUGIN_ID );
  }
}
