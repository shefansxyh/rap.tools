/*******************************************************************************
 * Copyright (c) 2007, 2011 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Innoopract Informationssysteme GmbH - initial API and implementation
 *     EclipseSource - ongoing development
 ******************************************************************************/
package org.eclipse.rap.ui.internal.launch.tab;

import java.io.Serializable;
import java.util.Comparator;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.rap.ui.internal.launch.Activator;
import org.eclipse.rap.ui.internal.launch.LaunchMessages;
import org.eclipse.rap.ui.internal.launch.util.Images;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;
import org.eclipse.ui.dialogs.SearchPattern;


final class EntryPointApplicationSelectionDialog
  extends FilteredItemsSelectionDialog
{

  private static final String SETTINGS_ID = Activator.PLUGIN_ID + ".ENTRY_POINT_SELECTION_DIALOG"; //$NON-NLS-1$
  private static final String HAS_TARGET_SCOPE = "hasTargetScope";

  private static final Comparator COMPARATOR = new EntryPointComparator();

  private AbstractExtension[] entryPoints;
  private boolean hasTargetScope;
  private final ToggleFilterScopeAction toggleFilterScopeAction;

  EntryPointApplicationSelectionDialog( Shell shell ) {
    super( shell );
    setTitle( LaunchMessages.EntryPointSelectionDialog_Title );
    String msg = LaunchMessages.EntryPointSelectionDialog_Message;
    toggleFilterScopeAction = new ToggleFilterScopeAction();
    setMessage( msg );
    setSelectionHistory( new EntryPointSelectionHistory() );
    setListLabelProvider( new EntryPointLabelProvider() );
    setDetailsLabelProvider( new EntryPointLabelProvider() );
  }

  //////////////////////////////////////////////////////////
  // FilteredItemsSelectionDialog overrides - UI adjustments

  protected Control createExtendedContentArea( Composite parent ) {
    return null;
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

  protected void fillContentProvider( AbstractContentProvider provider,
                                      ItemsFilter itemsFilter,
                                      IProgressMonitor monitor )
    throws CoreException
  {
    startMonitoring( monitor );
    prepareContent( monitor );
    addContentToProvider( provider, itemsFilter );
    finishMonitoring( monitor );
  }
  
  private void startMonitoring( IProgressMonitor monitor ) {
    if( monitor != null && entryPoints == null ) {
      String msg = LaunchMessages.EntryPointSelectionDialog_Searching;
      monitor.beginTask( msg, IProgressMonitor.UNKNOWN );
    }
  }

  private void prepareContent( IProgressMonitor monitor ) {
    if( entryPoints == null ) {
      createContent( monitor );
    }
  }
  
  private void createContent( IProgressMonitor monitor ) {
    AbstractExtension[] tempEntryPoints;
    AbstractExtension[] tempApplications;
    if( hasTargetScope ) {
      tempEntryPoints = EntryPointExtension.findAllActive( monitor );
      tempApplications = ApplicationExtension.findAllActive( monitor );
    }else {
      tempEntryPoints = EntryPointExtension.findInWorkspace( monitor );
      tempApplications = ApplicationExtension.findInWorkspace( monitor );
    }
    initializeEntryPoints( tempEntryPoints, tempApplications );
  }
  
  private void initializeEntryPoints( AbstractExtension[] tempEntryPoints,
                                      AbstractExtension[] tempApplications )
  {
    entryPoints = new AbstractExtension[   tempEntryPoints.length 
                                         + tempApplications.length ];
    System.arraycopy( tempEntryPoints, 0, entryPoints, 0, tempEntryPoints.length );
    System.arraycopy( tempApplications, 
                      0, 
                      entryPoints, 
                      tempEntryPoints.length, 
                      tempApplications.length );
  }
  
  private void addContentToProvider( AbstractContentProvider provider, ItemsFilter itemsFilter ) {
    for( int i = 0; i < entryPoints.length; i++ ) {
      provider.add( entryPoints[ i ], itemsFilter );
    }
  }
  
  private void finishMonitoring( IProgressMonitor monitor ) {
    if( monitor != null ) {
      monitor.done();
    }
  }

  protected ItemsFilter createFilter() {
    SearchPattern searchPattern = SelectionDialogUtil.createSearchPattern();
    return new EntryPointItemsFilter( searchPattern, hasTargetScope );
  }

  public String getElementName( Object element ) {
    AbstractExtension entryPoint = ( AbstractExtension )element;
    String project = entryPoint.getProject();
    String decorator = ""; //$NON-NLS-1$
    if( element instanceof EntryPointExtension ) {
      decorator = ( ( EntryPointExtension )entryPoint ).getParameter();
    } else if( element instanceof ApplicationExtension ) {
      decorator = ( ( ApplicationExtension )entryPoint ).getId();
    }
    return SelectionDialogUtil.getLabel( project, decorator );
  }

  protected Comparator getItemsComparator() {
    return COMPARATOR;
  }

  protected IStatus validateItem( Object item ) {
    return Status.OK_STATUS;
  }

  protected void fillViewMenu( IMenuManager menuManager ) {
    super.fillViewMenu( menuManager );
    menuManager.add( toggleFilterScopeAction );
  }

  protected void storeDialog( IDialogSettings settings ) {
    super.storeDialog( settings );
    settings.put( HAS_TARGET_SCOPE, toggleFilterScopeAction.isChecked() );
  }
  
  protected void restoreDialog( IDialogSettings settings ) {
    super.restoreDialog( settings );
    if ( settings.get( HAS_TARGET_SCOPE ) != null ) {
      toggleFilterScopeAction.setChecked( settings.getBoolean( HAS_TARGET_SCOPE ) );
      toggleFilterScopeAction.run();
    }
  }

  ////////////////
  // Inner classes

  private static final class EntryPointComparator 
    implements Comparator, Serializable 
  {

    private static final long serialVersionUID = 1L;

    public int compare( Object object1, Object object2 ) {
      int result = -1;
      AbstractExtension extension1 = ( AbstractExtension )object1;
      AbstractExtension extension2 = ( AbstractExtension )object2;
      if( extension1 instanceof EntryPointExtension && extension2 instanceof EntryPointExtension ) {
        result = handleEntryPointExtension( extension1, extension2 );
      } else if( extension1 instanceof ApplicationExtension 
                 && extension2 instanceof ApplicationExtension ) {
        result = handleApplicationExtension( extension1, extension2 );
      }
      return result;
    }

    private int handleEntryPointExtension( AbstractExtension extension1,
                                           AbstractExtension extension2 )
    {
      EntryPointExtension entryExtension1 = ( EntryPointExtension )extension1;
      String string1 = extension1.getProject() + entryExtension1.getParameter();
      EntryPointExtension entryExtension2 = ( EntryPointExtension )extension2;
      String string2 = extension2.getProject() + entryExtension2.getParameter();
      return string1.compareTo( string2 );
    }
    
    private int handleApplicationExtension( AbstractExtension extension1,
                                            AbstractExtension extension2 )
    {
      ApplicationExtension appExtension1 = ( ApplicationExtension )extension1;
      String string1 = extension1.getProject() + appExtension1.getId();
      ApplicationExtension appExtension2 = ( ApplicationExtension )extension2;
      String string2 = extension2.getProject() + appExtension2.getId();
      return string1.compareTo( string2 );
    }
    
  }

  private final class EntryPointItemsFilter extends ItemsFilter {

    private final boolean scope;

    public EntryPointItemsFilter( SearchPattern searchPattern, boolean scope )
    {
      super( searchPattern );
      this.scope = scope;
    }

    public boolean isConsistentItem( Object item ) {
      return true;
    }

    public boolean matchItem( Object item ) {
      boolean result = false;
      if( item instanceof EntryPointExtension ) {
        result = matches( ( ( EntryPointExtension )item ).getParameter() );
      } else if( item instanceof ApplicationExtension ) {
        result = matches( ( ( ApplicationExtension )item ).getId() );
      }
      return result;
    }
    
    public boolean isSubFilter( ItemsFilter filter ) {
      boolean result;
      if( scope != ( ( EntryPointItemsFilter )filter ).scope ) {
        result = false;
      } else{
        result = super.isSubFilter( filter );
      }
      return result;
    }
    
    public boolean equalsFilter( ItemsFilter filter ) {
      boolean result;
      if( scope != ( ( EntryPointItemsFilter )filter ).scope ) {
        result = false;
      }else {
        result = super.equalsFilter( filter );
      }
      return result;
    }
  }

  private static final class EntryPointLabelProvider extends LabelProvider {

    private final Image image = Images.EXTENSION.createImage();

    public String getText( Object element ) {
      String result = null;
      if( element != null ) {
        AbstractExtension extension = ( AbstractExtension )element;
        String project = extension.getProject();
        String decorator = ""; //$NON-NLS-1$
        if( extension instanceof EntryPointExtension ) {
          decorator = ( ( EntryPointExtension )extension ).getParameter();
        } else if( extension instanceof ApplicationExtension ) {
          decorator = ( ( ApplicationExtension )extension ).getId();
        }        
        result = SelectionDialogUtil.getLabel( project, decorator );
      }
      return result;
    }

    public Image getImage( Object element ) {
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
  private static final class EntryPointSelectionHistory extends SelectionHistory
  {

    protected Object restoreItemFromMemento( IMemento memento ) {
      return null;
    }

    protected void storeItemToMemento( Object item, IMemento memento )
    {
      // do nothing
    }
  }
  
  private class ToggleFilterScopeAction extends Action {

    public ToggleFilterScopeAction() {
      super( LaunchMessages.ServletNameSelectionDialog_WorkspaceFilterMsg, IAction.AS_CHECK_BOX );
      setChecked( hasTargetScope );
    }

    public void run() {
      hasTargetScope = isChecked();
      entryPoints = null;
      applyFilter();
    }
  }
  
}
