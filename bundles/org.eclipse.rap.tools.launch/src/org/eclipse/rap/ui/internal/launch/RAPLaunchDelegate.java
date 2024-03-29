/*******************************************************************************
 * Copyright (c) 2007, 2012 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Innoopract Informationssysteme GmbH - initial API and implementation
 *    EclipseSource - ongoing development
 ******************************************************************************/
package org.eclipse.rap.ui.internal.launch;

import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.*;
import org.eclipse.debug.core.model.RuntimeProcess;
import org.eclipse.pde.internal.launching.launcher.LauncherUtils;
import org.eclipse.pde.ui.launcher.EquinoxLaunchConfiguration;
import org.eclipse.rap.ui.internal.launch.RAPLaunchConfig.BrowserMode;
import org.eclipse.rap.ui.internal.launch.util.ErrorUtil;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.*;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;


public final class RAPLaunchDelegate extends EquinoxLaunchConfiguration {

  // VM argument contants
  private static final String VMARG_PORT
    = "-Dorg.osgi.service.http.port="; //$NON-NLS-1$
  private static final String VMARG_DEVELOPMENT_MODE
    = "-Dorg.eclipse.rap.rwt.developmentMode="; //$NON-NLS-1$
  private static final String VMARG_SESSION_TIMEOUT
    = "-Dorg.eclipse.equinox.http.jetty.context.sessioninactiveinterval="; //$NON-NLS-1$
  private static final String VMARG_CONTEXT_PATH
    = "-Dorg.eclipse.equinox.http.jetty.context.path="; //$NON-NLS-1$

  private static final int CONNECT_TIMEOUT = 20000; // 20 Seconds


  private ILaunch launch;
  private RAPLaunchConfig config;
  private int port;
  private final boolean testMode;

  public RAPLaunchDelegate() {
    this( false );
  }

  public RAPLaunchDelegate( final boolean testMode ) {
    this.testMode = testMode;
  }

  public void launch( final ILaunchConfiguration config,
                      final String mode,
                      final ILaunch launch,
                      final IProgressMonitor monitor )
    throws CoreException
  {
    SubProgressMonitor subMonitor = doPreLaunch( config, launch, monitor );
    super.launch( config, mode, launch, subMonitor );
  }

  public SubProgressMonitor doPreLaunch( final ILaunchConfiguration config,
                                         final ILaunch launch,
                                         final IProgressMonitor monitor )
    throws CoreException
  {
    // As this is the first method that is called after creating an instance
    // of RAPLaunchDelegate, store the config and launch parameters to be
    // accessible from member methods
    // TODO [rh] find a better way
    this.launch = launch;
    this.config = new RAPLaunchConfig( config );
    SubProgressMonitor subMonitor;
    subMonitor = new SubProgressMonitor( monitor, IProgressMonitor.UNKNOWN );
    terminateIfRunning( subMonitor );
    subMonitor = new SubProgressMonitor( monitor, IProgressMonitor.UNKNOWN );
    warnIfPortBusy( subMonitor );
    subMonitor = new SubProgressMonitor( monitor, IProgressMonitor.UNKNOWN );
    port = determinePort( subMonitor );
    if( this.config.getOpenBrowser() ) {
      registerBrowserOpener();
    }
    return subMonitor;
  }

  ///////////////////////////////////////
  // EquinoxLaunchConfiguration overrides

  public String[] getVMArguments( final ILaunchConfiguration config )
    throws CoreException
  {
    List list = new ArrayList();
    // ORDER IS CRUCIAL HERE:
    // Override VM arguments that are specified manually with the values
    // necessary for the RAP launcher
    list.addAll( Arrays.asList( super.getVMArguments( config ) ) );
    list.addAll( Arrays.asList( getRAPVMArguments() ) );
    String[] result = new String[ list.size() ];
    list.toArray( result );
    return result;
  }

  public String[] getProgramArguments( ILaunchConfiguration configuration ) throws CoreException {
    List newProgrammArguments = new ArrayList();
    String[] originalPogramArguments = super.getProgramArguments( configuration );
    newProgrammArguments.addAll( Arrays.asList( originalPogramArguments ) );
    String[] dataLocationArgument = getDataLocationArgument();
    newProgrammArguments.addAll( Arrays.asList( dataLocationArgument ) );
    String[] result = new String[ newProgrammArguments.size() ];
    newProgrammArguments.toArray( result );
    return result;
  }

  private String[] getDataLocationArgument() throws CoreException {
    String[] result;
    String dataLocationResolved = getResolvedDataLoacation();
    if( dataLocationResolved.length() > 0 ) {
      result = new String[] { "-data", dataLocationResolved };
    } else {
      result = new String[ 0 ];
    }
    return result;
  }

  private String getResolvedDataLoacation() throws CoreException {
    String dataLocation = config.getDataLocation();
    String dataLocationResolved = resolveDataLocation( dataLocation );
    return dataLocationResolved;
  }

  private String resolveDataLocation( String dataLocation ) throws CoreException {
    final VariablesPlugin variablePlugin = VariablesPlugin.getDefault();
    IStringVariableManager stringVariableManager = variablePlugin.getStringVariableManager();
    return stringVariableManager.performStringSubstitution( dataLocation );
  }

  private String[] getRAPVMArguments() throws CoreException {
    List<String> list = new ArrayList<String>();
    list.add( VMARG_PORT + port );
    list.add( VMARG_DEVELOPMENT_MODE + config.getDevelopmentMode() );
    if( config.getUseSessionTimeout() ) {
      list.add( VMARG_SESSION_TIMEOUT + config.getSessionTimeout() );
    } else {
      list.add( VMARG_SESSION_TIMEOUT + RAPLaunchConfig.MIN_SESSION_TIMEOUT );
    }
    if( config.getUseManualContextPath() ) {
      String contextPath = config.getContextPath();
      if( !contextPath.startsWith( URLBuilder.SLASH ) ) {
        contextPath = URLBuilder.SLASH + contextPath;
      }
      if( contextPath.endsWith( URLBuilder.SLASH ) ) {
        contextPath = contextPath.substring( 0, contextPath.length() - 1 );
      }
      list.add( VMARG_CONTEXT_PATH + contextPath );
    }
    String[] result = new String[ list.size() ];
    list.toArray( result );
    return result;
  }

  ////////////////////////////////////////
  // Helping methods to manage port number

  private void warnIfPortBusy( SubProgressMonitor monitor ) throws CoreException
  {
    String taskName = LaunchMessages.RAPLaunchDelegate_CheckPortTaskName;
    monitor.beginTask( taskName, IProgressMonitor.UNKNOWN );
    try {
      if( config.getUseManualPort() && isPortBusy( config.getPort() ) ) {
        DebugPlugin debugPlugin = DebugPlugin.getDefault();
        IStatusHandler prompter = debugPlugin.getStatusHandler( promptStatus );
        if( prompter != null ) {
          IStatus status = PortBusyStatusHandler.STATUS;
          Object resolution = prompter.handleStatus( status, config );
          if( Boolean.FALSE.equals( resolution ) ) {
            String text
              = LaunchMessages.RAPLaunchDelegate_PortInUse;
            Object[] args = new Object[] {
              new Integer( config.getPort() ),
              config.getName()
            };
            String msg = MessageFormat.format( text, args );
            String pluginId = Activator.PLUGIN_ID;
            Status infoStatus = new Status( IStatus.INFO, pluginId, msg );
            throw new CoreException( infoStatus );
          }
        }
      }
    } finally {
      monitor.done();
    }
  }

  private int determinePort( final IProgressMonitor monitor )
    throws CoreException
  {
    int result;
    String taskName = LaunchMessages.RAPLaunchDelegate_DeterminePortTaskName;
    monitor.beginTask( taskName, IProgressMonitor.UNKNOWN );
    try {
      if( config.getUseManualPort() ) {
        result = config.getPort();
      } else {
        result = findFreePort();
      }
    } finally {
      monitor.done();
    }
    return result;
  }

  private static int findFreePort() throws CoreException {
    try {
      ServerSocket server = new ServerSocket( 0 );
      try {
        return server.getLocalPort();
      } finally {
        server.close();
      }
    } catch( IOException e ) {
      String msg = "Could not obtain a free port number."; //$NON-NLS-1$
      String pluginId = Activator.getPluginId();
      Status status = new Status( IStatus.ERROR, pluginId, msg, e );
      throw new CoreException( status );
    }
  }

  private static boolean isPortBusy( final int port ) {
    ServerSocket server = null;
    try {
      server = new ServerSocket( port );
    } catch( IOException e1 ) {
      // assume that port is occupied when getting here
    }
    if( server != null ) {
      try {
        server.close();
      } catch( IOException e ) {
        // ignore
      }
    }
    return server == null;
  }

  //////////////////////////////////
  // Helping method to construct URL

  private URL getUrl() throws CoreException {
    try {
      String url = URLBuilder.fromLaunchConfig( config, port, testMode );
      return new URL( url );
    } catch( MalformedURLException e ) {
      String msg = "Invalid URL."; //$NON-NLS-1$
      String pluginId = Activator.getPluginId();
      Status status = new Status( IStatus.ERROR, pluginId, 0, msg, e );
      throw new CoreException( status );
    }
  }

  ///////////////////////////////////////////////////
  // Helping methods to detect already running launch

  private void terminateIfRunning( IProgressMonitor monitor ) throws CoreException {
    String taskName = LaunchMessages.RAPLaunchDelegate_TerminatePreviousTaskName;
    monitor.beginTask( taskName, IProgressMonitor.UNKNOWN );
    try {
      final ILaunch runningLaunch = findRunning();
      if( runningLaunch != null ) {
        terminate( runningLaunch );
      }
    } finally {
      monitor.done();
    }
  }

  private ILaunch findRunning() {
    ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
    ILaunch[] runningLaunches = launchManager.getLaunches();
    ILaunch result = null;
    for( int i = 0; result == null && i < runningLaunches.length; i++ ) {
      ILaunch runningLaunch = runningLaunches[ i ];
      if(    runningLaunch != launch
          && config.getName().equals( getLaunchName( runningLaunch ) )
          && !runningLaunch.isTerminated() )
      {
        result = runningLaunches[ i ];
      }
    }
    return result;
  }

  private static String getLaunchName( final ILaunch launch ) {
    ILaunchConfiguration launchConfiguration = launch.getLaunchConfiguration();
    // the launch config might be null (e.g. if deleted) even though there
    // still exists a launch for that config
    return launchConfiguration == null ? null : launchConfiguration.getName();
  }

  private static void terminate( final ILaunch previousLaunch )
    throws DebugException
  {
    final Object signal = new Object();
    final boolean[] terminated = { false };
    DebugPlugin debugPlugin = DebugPlugin.getDefault();
    debugPlugin.addDebugEventListener( new IDebugEventSetListener() {
      public void handleDebugEvents( final DebugEvent[] events ) {
        for( int i = 0; i < events.length; i++ ) {
          DebugEvent event = events[ i ];
          if( isTerminateEventFor( event, previousLaunch ) ) {
            DebugPlugin.getDefault().removeDebugEventListener( this );
            synchronized( signal ) {
              terminated[ 0 ] = true;
              signal.notifyAll();
            }
          }
        }
      }
    } );
    previousLaunch.terminate();
    try {
      synchronized( signal ) {
        if( !terminated[ 0 ] ) {
          signal.wait();
        }
      }
    } catch( InterruptedException e ) {
      // ignore
    }
  }

  ////////////////////////////////////////////
  // Helping methods to evaluate debug events

  private static boolean isCreateEventFor( final DebugEvent event,
                                           final ILaunch launch )
  {
    Object source = event.getSource();
    return    event.getKind() == DebugEvent.CREATE
           && source instanceof RuntimeProcess
           && ( ( RuntimeProcess ) source ).getLaunch() == launch;
  }

  private static boolean isTerminateEventFor( final DebugEvent event,
                                              final ILaunch launch )
  {
    boolean result = false;
    if(    event.getKind() == DebugEvent.TERMINATE
        && event.getSource() instanceof RuntimeProcess )
    {
      RuntimeProcess process = ( RuntimeProcess )event.getSource();
      result = process.getLaunch() == launch;
    }
    return result;
  }

  /////////////////////////////////////
  // Helping methods to test connection

  private void waitForHttpService( final IProgressMonitor monitor ) {
    SubProgressMonitor subMonitor = new SubProgressMonitor( monitor, 1 );
    String taskName = LaunchMessages.RAPLaunchDelegate_WaitForHTTPTaskName;
    subMonitor.beginTask( taskName, IProgressMonitor.UNKNOWN );
    try {
      long start = System.currentTimeMillis();
      boolean canConnect = false;
      boolean interrupted = false;
      while(    System.currentTimeMillis() - start <= CONNECT_TIMEOUT
             && !canConnect
             && !interrupted
             && !monitor.isCanceled()
             && !launch.isTerminated() )
      {
        try {
          Socket socket = new Socket( URLBuilder.getHost(), port );
          socket.close();
          canConnect = true;
        } catch( Exception e ) {
          // http service not yet started - wait a bit
          try {
            Thread.sleep( 200 );
          } catch( InterruptedException ie ) {
            interrupted = true;
          }
        }
      }
    } finally {
      subMonitor.done();
    }
  }

  public void clear( ILaunchConfiguration configuration, IProgressMonitor monitor )
    throws CoreException
  {
    clearDataLocation( configuration, monitor );
    super.clear( configuration, monitor );
  }

  private void clearDataLocation( ILaunchConfiguration configuration, IProgressMonitor monitor )
    throws CoreException
  {
    String resolvedDataLocation = getResolvedDataLoacation();
    boolean isCleared = LauncherUtils.clearWorkspace( configuration, resolvedDataLocation, monitor );
    if( !isCleared ) {
      throw new CoreException( Status.CANCEL_STATUS );
    }
  }

  /////////////////////////////////////////////////
  // Helping methods to create browser and open URL

  private void registerBrowserOpener() {
    DebugPlugin debugPlugin = DebugPlugin.getDefault();
    debugPlugin.addDebugEventListener( new IDebugEventSetListener() {
      public void handleDebugEvents( final DebugEvent[] events ) {
        for( int i = 0; i < events.length; i++ ) {
          DebugEvent event = events[ i ];
          if( isCreateEventFor( event, launch ) ) {
            DebugPlugin.getDefault().removeDebugEventListener( this );
            // Start a separate job to wait for the http service and launch the
            // browser. Otherwise we would block the application on whose
            // service we are waiting for
            final String jobTaskName = LaunchMessages.RAPLaunchDelegate_StartClientTaskName;
            Job job = new Job( jobTaskName ) {
              protected IStatus run( final IProgressMonitor monitor ) {
                String taskName = jobTaskName;
                monitor.beginTask( taskName, 2 );
                try {
                  waitForHttpService( monitor );
                  monitor.worked( 1 );
                  if( !launch.isTerminated() ) {
                    openBrowser( monitor );
                  }
                } finally {
                  monitor.done();
                }
                return Status.OK_STATUS;
              }
            };
            job.schedule();
          }
        }
      }
    } );
  }

  private void openBrowser( final IProgressMonitor monitor ) {
    SubProgressMonitor subMonitor = new SubProgressMonitor( monitor, 1 );
    String taskName = LaunchMessages.RAPLaunchDelegate_StartClientTaskName;
    subMonitor.beginTask( taskName, IProgressMonitor.UNKNOWN );
    try {
      URL url = null;
      try {
        url = getUrl();
        IWebBrowser browser = getBrowser();
        openUrl( browser, url );
      } catch( final CoreException e ) {
        String text = LaunchMessages.RAPLaunchDelegate_OpenBrowserFailed;
        String msg = MessageFormat.format( text, new Object[]{ url } );
        ErrorUtil.show( msg, e );
      }
    } finally {
      subMonitor.done();
    }
  }

  private IWebBrowser getBrowser() throws CoreException {
    final IWebBrowser[] result = { null };
    final CoreException[] exception = { null };
    Display.getDefault().syncExec( new Runnable() {
      public void run() {
        try {
          IWorkbench workbench = PlatformUI.getWorkbench();
          IWorkbenchBrowserSupport support = workbench.getBrowserSupport();
          int style
            = IWorkbenchBrowserSupport.LOCATION_BAR
            | IWorkbenchBrowserSupport.NAVIGATION_BAR
            | IWorkbenchBrowserSupport.STATUS;
          if( BrowserMode.EXTERNAL.equals( config.getBrowserMode() ) ) {
            style |= IWorkbenchBrowserSupport.AS_EXTERNAL;
          } else {
            style |= IWorkbenchBrowserSupport.AS_EDITOR;
          }
          // Starting the same launch first with the external, then with the
          // internal browser without restarting the workbench will still open
          // an external browser.
          // The fix is to append the browserMode to the id
          String id = config.getName() + config.getBrowserMode();
          String name = config.getName();
          String toolTip = config.getName();
          result[ 0 ] = support.createBrowser( style, id, name, toolTip );
        } catch( CoreException e ) {
          exception[ 0 ] = e;
        }
      }
    } );
    if( exception[ 0 ] != null ) {
      throw exception[ 0 ];
    }
    return result[ 0 ];
  }

  private static void openUrl( final IWebBrowser browser, final URL url )
    throws PartInitException
  {
    final PartInitException[] exception = { null };
    Display.getDefault().asyncExec( new Runnable() {
      public void run() {
        try {
          browser.openURL( url );
        } catch( PartInitException e ) {
          String text = LaunchMessages.RAPLaunchDelegate_OpenUrlFailed;
          String msg = MessageFormat.format( text, new Object[] { url } );
          String pluginId = Activator.getPluginId();
          Status status = new Status( IStatus.ERROR, pluginId, msg, e );
          exception[ 0 ] = new PartInitException( status );
        }
      }
    } );
    if( exception[ 0 ] != null ) {
      throw exception[ 0 ];
    }
  }
}
