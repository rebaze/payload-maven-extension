/*******************************************************************************
 * Copyright (c) 2015 Rebaze GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache Software License v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/
 * <p/>
 * Contributors:
 * Rebaze
 *******************************************************************************/
package com.rebaze.maven.payload;

import com.rebaze.maven.support.AetherUtils;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.util.*;

/**
 * @author Toni Menzel (toni.menzel@rebaze.com)
 *         <p/>
 *         A dependency tracing spy.
 *         Dumps all relevant repository interaction to a file so we can provision
 *         repositories with dependencies.
 *
 *         TODO: factor out upload to mojo.
 */
@Named
public class PayloadEventSpy extends AbstractEventSpy
{
    private static final String PAYLOAD_FILENAME = "build.payload";

    /**
     * When set, this plugin will upload dependencies in target/PAYLOAD_FILENAME to the repo configured here.
     */
    public static String PROPERTY_FOCUS_REPO = "payload.repo";

    /**
     * When enabled, this spy will trace dependencies requested during builds in file target/PAYLOAD_FILENAME.
     */
    public static String PROPERTY_ENABLED = "payload.enabled";

    @Inject
    private Logger logger;

    @Inject RepositorySystem repoSystem;

    private RepositorySystemSession repoSession;

    private List<RemoteRepository> remoteRepos;

    private MavenProject m_reactorProject;

    private List<RepositoryEvent> m_eventLog = new ArrayList<>();

    private boolean m_sync = true;

    private MavenExecutionRequest execRequest;

    private String uploadRepo;

    private boolean enabled = false;

    private List<String> m_payloadGavs;

    private boolean m_deepResolve = false;

    @Override public void onEvent( Object event ) throws Exception
    {
        super.onEvent( event );

        //traceEvents( event );
        try
        {
            if ( event instanceof ExecutionEvent )
            {
                org.apache.maven.execution.ExecutionEvent exec = ( ExecutionEvent ) event;
                if ( exec.getProject() != null && exec.getProject().isExecutionRoot() )
                {
                    if ( m_reactorProject == null )
                    {
                        m_eventLog = new ArrayList<>();
                        m_reactorProject = exec.getProject();
                        m_payloadGavs = readInputBill( getPayloadFile() );
                    }
                }
            }
            else if ( event instanceof org.eclipse.aether.RepositoryEvent )
            {
                RepositoryEvent repositoryEvent = ( RepositoryEvent ) event;
                if ( enabled )
                {
                    m_eventLog.add( repositoryEvent );
                    if ( repoSession == null )
                    {
                        repoSession = repositoryEvent.getSession();
                    }
                }
            }
            else if ( event instanceof DefaultMavenExecutionResult )
            {
                DefaultMavenExecutionResult execResult = ( DefaultMavenExecutionResult ) event;
                remoteRepos = AetherUtils.toRepos( execResult.getProject().getRemoteArtifactRepositories() );
                if ( execResult.getDependencyResolutionResult() != null && execResult.getDependencyResolutionResult().getUnresolvedDependencies().size() > 0 )
                {
                    m_sync = false;
                }

                // MAYBE TRIGGER DEPLOY HERE??
            }
            else if ( event instanceof MavenExecutionRequest )
            {
                if ( execRequest == null )
                {
                    execRequest = ( MavenExecutionRequest ) event;
                    uploadRepo = execRequest.getUserProperties().getProperty( PROPERTY_FOCUS_REPO );
                    enabled = isEnabled( execRequest.getUserProperties().getProperty( PROPERTY_ENABLED ) );
                    if ( uploadRepo != null )
                    {
                        logger.info( "Set to deploy only!" );
                        execRequest.setRecursive( false );
                    }
                }
            }
            else if ( event instanceof SettingsBuildingRequest )
            {
                SettingsBuildingRequest settingsBuildingRequest = ( SettingsBuildingRequest ) event;

            }
            else if ( event instanceof SettingsBuildingResult )
            {
                //logger.info( "Overwrite repos for request: " + execRequest );
            }
            else
            {
                //logger.info( "Unrecognized event: " + event.getClass().getName() );
            }
        }
        catch ( Exception e )
        {
            logger.error( "Problem!", e );
        }
    }

    public static boolean isEnabled( String value )
    {
        return value != null && ( value.trim().equalsIgnoreCase( "true" ) || value.trim().isEmpty() );
    }

    @Override public void close() throws Exception
    {
        logger.info( "Finishing payload extension.. " + enabled );

        if ( enabled && m_reactorProject != null )
        {
            File payload = getPayloadFile();
            if ( uploadRepo != null && m_payloadGavs != null )
            {
                // rewrite old log
                writeDependencyList( payload, m_payloadGavs );
            }
            else
            {
                // write new
                writeDependencyList( payload, synth( m_eventLog ) );
            }

            if ( uploadRepo != null )
            {
                if ( m_sync )
                {
                    deployBill( readInputBill( payload ) );
                }
                else
                {
                    logger.info( "Won't deploy due to unresolved dependencies." );
                }
                // deploy.
            }
        }
        super.close();
    }

    private void deployBill( List<String> sortedArtifacts )
    {
        RemoteRepository targetRepository = selectTargetRepo();
        List<RemoteRepository> allowedRepositories = calculateAllowedRepositories();
        try
        {
            List<Artifact> listOfArtifacts = parseAndResolveArtifacts( sortedArtifacts, allowedRepositories );
            DeployRequest deployRequest = new DeployRequest();
            deployRequest.setRepository( targetRepository );

            for ( Artifact artifact : listOfArtifacts )
            {
                assert ( artifact.getFile() != null );
                deployRequest.addArtifact( artifact );
            }
            getLog().info( "Deployment of " + deployRequest.getArtifacts().size() + " artifacts .." );

            DeployResult result = repoSystem.deploy( repoSession, deployRequest );
            getLog().info( "Deployment Result: " + result.getArtifacts().size() );
        }
        catch ( DeploymentException e )
        {
            getLog().error( "Problem deploying set..!", e );
        }
        catch ( ArtifactResolutionException e )
        {
            getLog().error( "Problem resolving artifact(s)..!", e );
        }
    }

    private File writeDependencyList( File f, List<String> sorted )
    {
        if ( !f.getParentFile().exists() )
        {
            f.getParentFile().mkdirs();
        }

        int finalSize = -1;
        try ( BufferedWriter writer = new BufferedWriter( new FileWriter( f, true ) ) )
        {
            finalSize = sorted.size();
            for ( String s : sorted )
            {
                writer.append( s );
                writer.newLine();
            }
        }
        catch ( IOException e )
        {
            logger.error( "Problem writing file.", e );
        }
        logger.info( "Halo written (count=" + finalSize + "): " + f.getAbsolutePath() );

        return f;
    }

    private List<String> synth( List<RepositoryEvent> events )
    {
        Set<String> content = new HashSet<>();
        for ( RepositoryEvent repositoryEvent : events )
        {
            if ( repositoryEvent.getArtifact() != null )
            {
                content.add( repositoryEvent.getArtifact().toString() );
            }
        }
        List<String> sorted = new ArrayList<>( content );
        Collections.sort( sorted );
        return sorted;
    }

    private File getPayloadFile()
    {
        File f = new File( new File( m_reactorProject.getBuild().getOutputDirectory() ).getParent(), PAYLOAD_FILENAME );
        return f;
    }

    private RemoteRepository selectTargetRepo()
    {
        logger.info( "Repositories configured: " + this.remoteRepos.size() );
        for ( RemoteRepository repo : this.remoteRepos )
        {
            getLog().info( "Using repo: " + repo );
            if ( repo.getId().equals( uploadRepo ) )
            {
                return repo;
            }
        }
        throw new IllegalArgumentException( "Target Repository ID " + uploadRepo + " is unkown. Is it configured?" );
    }

    private List<String> readInputBill( File input )
    {
        if ( input == null || !input.exists() )
        {
            return null;
        }
        List<String> sortedArtifacts = new ArrayList<>();
        try ( BufferedReader reader = new BufferedReader( new FileReader( input ) ) )
        {
            String line = null;
            getLog().info( "Reading payload " + input.getAbsolutePath() );
            while ( ( line = reader.readLine() ) != null )
            {
                sortedArtifacts.add( line );
            }
        }
        catch ( IOException e )
        {
            getLog().error( "Cannot parse payload: " + input.getAbsolutePath(), e );
            return null;
        }

        return sortedArtifacts;
    }

    private List<Artifact> parseAndResolveArtifacts( Collection<String> artifacts, List<RemoteRepository> allowedRepositories ) throws ArtifactResolutionException
    {
        List<Artifact> artifactList = new ArrayList<>();
        for ( String a : artifacts )
        {
            Artifact artifact = new DefaultArtifact( a );
            if ( !artifact.isSnapshot() )
            {
                artifactList.add( artifact );
            }
        }
        return resolve( artifactList, allowedRepositories );
    }

    private List<RemoteRepository> calculateAllowedRepositories()
    {
        List<RemoteRepository> result = new ArrayList<>();
        result = this.remoteRepos;
        return result;
    }

    private Logger getLog()
    {
        return logger;
    }

    private List<Artifact> resolve( Collection<Artifact> artifacts, List<RemoteRepository> allowedRepositories ) throws ArtifactResolutionException
    {
        Collection<ArtifactRequest> artifactRequests = new ArrayList<>();
        List<Artifact> result = new ArrayList<>( artifacts.size() );

        for ( Artifact a : artifacts )
        {
            ArtifactRequest request = new ArtifactRequest( a, allowedRepositories, null );
            artifactRequests.add( request );
            result.add( a );
        }

        List<ArtifactResult> reply = repoSystem.resolveArtifacts( repoSession, artifactRequests );
        for ( ArtifactResult res : reply )
        {
            if ( !res.isMissing() )
            {
                result.add( res.getArtifact() );
            }
            else
            {
                getLog().warn( "Artifact " + res.getArtifact() + " is still missing." );
            }

        }
        return result;
    }

    private List<String> sort( Set<String> artifacts )
    {
        List<String> sortedArtifacts = new ArrayList<>( artifacts );
        Collections.sort( sortedArtifacts );
        return sortedArtifacts;
    }
}
