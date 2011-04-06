/*******************************************************************************
 * Copyright (c) 2007, 2011 Innoopract Informationssysteme GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Innoopract Informationssysteme GmbH - initial API and implementation
 ******************************************************************************/
package org.eclipse.rap.ui.internal.launch.tab;

import java.io.Serializable;
import java.util.Comparator;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.rap.ui.internal.launch.Activator;
import org.eclipse.rap.ui.internal.launch.LaunchMessages;
import org.eclipse.rap.ui.internal.launch.util.Images;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;
import org.eclipse.ui.dialogs.SearchPattern;


final class ServletNameSelectionDialog extends FilteredItemsSelectionDialog {

  private static final String SETTINGS_ID
    = Activator.PLUGIN_ID + ".SERVLET_NAME_SELECTION_DIALOG"; //$NON-NLS-1$

  private static final Comparator COMPARATOR = new BrandingComparator();

  private BrandingExtension[] brandings;
  private boolean hasWorkspaceScope;

  ServletNameSelectionDialog( final Shell shell ) {
    super( shell );
    setTitle( LaunchMessages.ServletNameSelectionDialog_Title );
    String msg
      = LaunchMessages.ServletNameSelectionDialog_Message;
    setMessage( msg );
    setSelectionHistory( new ServletNameSelectionHistory() );
    setListLabelProvider( new BrandingLabelProvider() );
    setDetailsLabelProvider( new BrandingLabelProvider() );
  }

  //////////////////////////////////////////////////////////
  // FilteredItemsSelectionDialog overrides - UI adjustments

  protected Control createExtendedContentArea( final Composite parent ) {
    final Button scopeButton = new Button( parent, SWT.CHECK );
    String text = LaunchMessages.ServletNameSelectionDialog_WorkspaceFilterMsg;
    scopeButton.setText( text );
    scopeButton.addSelectionListener( new SelectionAdapter() {
      public void widgetSelected(final SelectionEvent e) {
        hasWorkspaceScope = scopeButton.getSelection();
        brandings = null;
        applyFilter();
      }
    });
    return scopeButton;
  }

  protected IDialogSettings getDialogSettings() {
    IDialogSettings settings = Activator.getDefault().getDialogSettings();
    IDialogSettings section = settings.getSection( SETTINGS_ID );
    if( section == null ) {
      section = settings.addNewSection( SETTINGS_ID );
    }
    return section;
  }

  ///////////////////////////////////////////////////////////
  // FilteredItemsSelectionDialog overrides - item management

  protected void fillContentProvider( final AbstractContentProvider provider,
                                      final ItemsFilter itemsFilter,
                                      final IProgressMonitor monitor )
    throws CoreException
  {
    if( brandings == null ) {
      if( monitor != null ) {
        String msg = LaunchMessages.ServletNameSelectionDialog_Searching;
        monitor.beginTask( msg, IProgressMonitor.UNKNOWN );
      }
      if( hasWorkspaceScope ) {
        brandings = BrandingExtension.findInWorkspace( monitor );
      }else {
        brandings = BrandingExtension.findAllActive( monitor );
      }
    }
    for( int i = 0; i < brandings.length; i++ ) {
      provider.add( brandings[ i ], itemsFilter );
    }
    if( monitor != null ) {
      monitor.done();
    }
  }

  protected ItemsFilter createFilter() {
    return new BrandingItemsFilter( SelectionDialogUtil.createSearchPattern(),
                                    hasWorkspaceScope );
  }

  public String getElementName( final Object element ) {
    BrandingExtension branding = ( BrandingExtension )element;
    String project = branding.getProject();
    String servletName = branding.getServletName();
    return SelectionDialogUtil.getLabel( project, servletName );
  }

  protected Comparator getItemsComparator() {
    return COMPARATOR;
  }

  protected IStatus validateItem( final Object item ) {
    return Status.OK_STATUS;
  }

  ////////////////
  // Inner classes

  private static final class BrandingComparator 
    implements Comparator, Serializable 
  {

    private static final long serialVersionUID = 1L;

    public int compare( final Object object1, final Object object2 ) {
      BrandingExtension extension1 = ( BrandingExtension )object1;
      BrandingExtension extension2 = ( BrandingExtension )object2;
      String string1 = extension1.getProject() + extension1.getServletName();
      String string2 = extension2.getProject() + extension2.getServletName();
      return string1.compareTo( string2 );
    }
  }

  private final class BrandingItemsFilter extends ItemsFilter {

    private final boolean scope;

    public BrandingItemsFilter( final SearchPattern searchPattern, 
                                final boolean workspaceScope )
    {
      super( searchPattern );
      this.scope = workspaceScope;
    }

    public boolean isConsistentItem( final Object item ) {
      return true;
    }

    public boolean matchItem( final Object item ) {
      return matches( ( ( BrandingExtension )item ).getServletName() );
    }
    
    public boolean isSubFilter( final ItemsFilter filter ) {
      boolean result;
      if( scope != ((BrandingItemsFilter)filter).scope ) {
        result = false;
      } else{
        result = super.isSubFilter( filter );
      }
      return result;
    }
    
    public boolean equalsFilter( final ItemsFilter filter ) {
      boolean result;
      if( scope != ((BrandingItemsFilter)filter).scope ) {
        result = false;
      }else {
        result = super.equalsFilter( filter );
      }
      return result;
    }
  }
  
  private static final class BrandingLabelProvider
    extends LabelProvider
  {

    private final Image image = Images.EXTENSION.createImage();

    public String getText( final Object element ) {
      String result = null;
      if( element != null ) {
        BrandingExtension branding = ( BrandingExtension )element;
        String project = branding.getProject();
        String servletName = branding.getServletName();
        result = SelectionDialogUtil.getLabel( project, servletName );
      }
      return result;
    }

    public Image getImage( final Object element ) {
      return image;
    }

    public void dispose() {
      image.dispose();
      super.dispose();
    }
  }

  /* Empty SelectionHistory implementation, necessary to be able pass something
   * non-null to setSelectionHistory. Without calling it, an exception would
   * occur when the dialog is canceled. */
  private static final class ServletNameSelectionHistory
    extends SelectionHistory
  {

    protected Object restoreItemFromMemento( final IMemento memento ) {
      return null;
    }

    protected void storeItemToMemento( final Object item,
                                       final IMemento memento )
    {
      // do nothing
    }
  }
}
