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
import org.eclipse.aether.repository.RemoteRepository;

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
 *         <p/>
 *         TODO: factor out upload to mojo.
 */
@Named
public class PayloadEventSpy extends AbstractEventSpy
{
    private static final String PAYLOAD_FILENAME = "build.payload";

    /**
     * When enabled, this spy will trace dependencies requested during builds in file target/PAYLOAD_FILENAME.
     */
    public static String PROPERTY_ENABLED = "payload.enabled";

    @Inject
    private Logger logger;

    @Inject RepositorySystem repoSystem;

    private MavenProject m_reactorProject;

    private List<RepositoryEvent> m_eventLog = new ArrayList<>();

    private MavenExecutionRequest execRequest;

    private boolean enabled = false;

    private List<String> m_payloadGavs;

    @Override public void onEvent( Object event ) throws Exception
    {
        super.onEvent( event );
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
                m_eventLog.add( ( RepositoryEvent ) event );
            }

        }
        catch ( Exception e )
        {
            logger.error( "Problem!", e );
        }
    }

    @Override public void close() throws Exception
    {
        if ( enabled && m_reactorProject != null )
        {
            writeDependencyList( getPayloadFile(), synth( m_eventLog ) );
        }
        super.close();
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
        return new File( new File( m_reactorProject.getBuild().getOutputDirectory() ).getParent(), PAYLOAD_FILENAME );
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
            logger.info( "Reading payload " + input.getAbsolutePath() );
            while ( ( line = reader.readLine() ) != null )
            {
                sortedArtifacts.add( line );
            }
        }
        catch ( IOException e )
        {
            logger.error( "Cannot parse payload: " + input.getAbsolutePath(), e );
            return null;
        }

        return sortedArtifacts;
    }
}
