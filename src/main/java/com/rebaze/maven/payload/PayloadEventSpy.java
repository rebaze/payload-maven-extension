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

import org.apache.maven.MavenExecutionException;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositoryEvent;

import javax.inject.Named;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * A dependency tracing spy.
 * Dumps all relevant repository interaction to a file so we can provision
 * repositories with dependencies.
 *
 * @author Toni Menzel (toni.menzel@rebaze.com)
 */
@Named
public class PayloadEventSpy extends AbstractEventSpy
{
    public static final String PAYLOAD_FILENAME = "build.payload";

    private List<RepositoryEvent> m_eventLog = new ArrayList<>();
    private MavenProject m_reactorProject;

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
                    if (m_reactorProject == null)
                    {
                        m_eventLog = new ArrayList<>();
                        m_reactorProject = exec.getProject();
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
            throw new MavenExecutionException( "Problem!", e );
        }
    }

    @Override public void close() throws Exception
    {
        writeDependencyList( getPayloadFile(), synth( m_eventLog ) );
    }

    private File writeDependencyList( File f, List<String> sorted ) throws MavenExecutionException
    {
        if ( !f.getParentFile().exists() )
        {
            f.getParentFile().mkdirs();
        }

        try ( BufferedWriter writer = new BufferedWriter( new FileWriter( f, true ) ) )
        {
            for ( String s : sorted )
            {
                writer.append( s );
                writer.newLine();
            }
        }
        catch ( IOException e )
        {
            throw new MavenExecutionException( "Problem writing payload file to " + f.getAbsolutePath(), e );
        }
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
}
