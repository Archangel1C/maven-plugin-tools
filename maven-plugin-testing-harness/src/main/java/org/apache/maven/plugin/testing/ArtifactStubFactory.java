package org.apache.maven.plugin.testing;

/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.war.WarArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.ReflectionUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * This class creates artifacts to be used for testing purposes. It can optionally create actual files on the local disk
 * for things like copying. It can create these files as archives with named files inside to be used for testing things
 * like unpack. Also provided are some utility methods to quickly get a set of artifacts distinguished by various things
 * like group,artifact,type,scope, etc It was originally developed for the dependency plugin, but can be useful in other
 * plugins that need to simulate artifacts for unit tests.
 * 
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class ArtifactStubFactory
{
    File workingDir;

    boolean createFiles;

    File srcFile;

    boolean createUnpackableFile;

    ArchiverManager archiverManager;

    /**
     * Default constructor. This should be used only if real files aren't needed...just the artifact objects
     */
    public ArtifactStubFactory()
    {
        this.workingDir = null;
        this.createFiles = false;
    }

    /**
     * This constructor is to be used if files are needed and to set a working dir
     * 
     * @param workingDir
     * @param createFiles
     */
    public ArtifactStubFactory( File workingDir, boolean createFiles )
    {
        this.workingDir = new File( workingDir, "localTestRepo" );
        this.createFiles = createFiles;
    }

    /**
     * If set, the file will be created as a zip/jar/war with a file inside that can be checked to exist after
     * unpacking.
     * 
     * @param archiverManager
     */
    public void setUnpackableFile( ArchiverManager archiverManager )
    {
        this.createUnpackableFile = true;
        this.archiverManager = archiverManager;
    }

    public Artifact createArtifact( String groupId, String artifactId, String version )
        throws IOException
    {
        return createArtifact( groupId, artifactId, version, Artifact.SCOPE_COMPILE, "jar", "" );
    }

    public Artifact createArtifact( String groupId, String artifactId, String version, String scope )
        throws IOException
    {
        return createArtifact( groupId, artifactId, version, scope, "jar", "" );
    }

    public Artifact createArtifact( String groupId, String artifactId, String version, String scope, String type,
                                    String classifier )
        throws IOException
    {
        VersionRange vr = VersionRange.createFromVersion( version );
        return createArtifact( groupId, artifactId, vr, scope, type, classifier, false );
    }

    public Artifact createArtifact( String groupId, String artifactId, VersionRange versionRange, String scope,
                                    String type, String classifier, boolean optional )
        throws IOException
    {
        ArtifactHandler ah = new DefaultArtifactHandlerStub( type, classifier );

        Artifact artifact =
            new DefaultArtifact( groupId, artifactId, versionRange, scope, type, classifier, ah, optional );

        // i have no idea why this needs to be done manually when isSnapshot is able to figure it out.
        artifact.setRelease( !artifact.isSnapshot() );

        if ( createFiles )
        {
            setArtifactFile( artifact,this.workingDir,this.srcFile,this.createUnpackableFile );
        }
        return artifact;
    }

    /**
     * Creates a new empty file and attaches it to the artifact.
     * 
     * @param artifact to attach the file to.
     * @param workingDir where to locate the new file
     * @throws IOException
     */
    public void setArtifactFile( Artifact artifact, File workingDir )
        throws IOException
    {
        setArtifactFile( artifact, workingDir, null, false );
    }

    /**
     * Copyies the srcFile to the workingDir and then attaches it to the artifact. If srcFile is null, a new empty file
     * will be created.
     * 
     * @param artifact to attach
     * @param workingDir where to copy the srcFile.
     * @param srcFile file to be attached.
     * @throws IOException
     */
    public void setArtifactFile( Artifact artifact, File workingDir, File srcFile )
        throws IOException
    {
        setArtifactFile( artifact, workingDir, srcFile, false );
    }

    /**
     * Creates an unpackable file (zip,jar etc) containing an empty file.
     * 
     * @param artifact to attach
     * @param workingDir where to create the file.
     * @throws IOException
     */
    public void setUnpackableArtifactFile( Artifact artifact, File workingDir )
        throws IOException
    {
        setArtifactFile( artifact, workingDir, null, true );
    }

    /**
     * Creates an unpackable file (zip,jar etc) containing the srcFile. If srcFile is null, a new empty file will be
     * created.
     * 
     * @param artifact to attach
     * @param workingDir where to create the file.
     * @throws IOException
     */
    public void setUnpackableArtifactFile( Artifact artifact, File workingDir, File srcFile )
        throws IOException
    {
        setArtifactFile( artifact, workingDir, srcFile, true );
    }

    /*
     * Creates a file that can be copied or unpacked based on the passed in artifact
     */
    private void setArtifactFile( Artifact artifact, File workingDir, File srcFile, boolean createUnpackableFile )
        throws IOException
    {
        if ( workingDir == null )
        {
            throw new IllegalArgumentException(
                                                "The workingDir must be set." );
        }

        String fileName = getFormattedFileName( artifact, false );

        File theFile = new File( workingDir, fileName );
        theFile.getParentFile().mkdirs();

        if ( srcFile == null )
        {
            theFile.createNewFile();
        }
        else if ( createUnpackableFile )
        {
            try
            {
                createUnpackableFile( artifact, theFile );
            }
            catch ( NoSuchArchiverException e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch ( ArchiverException e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch ( IOException e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        else
        {
            FileUtils.copyFile( srcFile, theFile );
        }

        artifact.setFile( theFile );
    }

    static public String getUnpackableFileName( Artifact artifact )
    {
        return "" + artifact.getGroupId() + "-" + artifact.getArtifactId() + "-" + artifact.getVersion() + "-" +
            artifact.getClassifier() + "-" + artifact.getType() + ".txt";
    }

    public void createUnpackableFile( Artifact artifact, File destFile )
        throws NoSuchArchiverException, ArchiverException, IOException
    {
        Archiver archiver = archiverManager.getArchiver( destFile );

        archiver.setDestFile( destFile );
        archiver.addFile( srcFile, getUnpackableFileName( artifact ) );

        try
        {
            setVariableValueToObject( archiver, "logger", new SilentLog() );
        }
        catch ( IllegalAccessException e )
        {
            System.out.println( "Unable to override logger with silent log." );
            e.printStackTrace();
        }
        if ( archiver instanceof WarArchiver )
        {
            WarArchiver war = (WarArchiver) archiver;
            // the use of this is counter-intuitive:
            // http://jira.codehaus.org/browse/PLX-286
            war.setIgnoreWebxml( false );
        }
        archiver.createArchive();
    }

    public Artifact getReleaseArtifact()
        throws IOException
    {
        return createArtifact( "testGroupId", "release", "1.0" );
    }

    public Artifact getSnapshotArtifact()
        throws IOException
    {
        return createArtifact( "testGroupId", "snapshot", "2.0-SNAPSHOT" );
    }

    public Set getReleaseAndSnapshotArtifacts()
        throws IOException
    {
        Set set = new HashSet();
        set.add( getReleaseArtifact() );
        set.add( getSnapshotArtifact() );
        return set;
    }

    public Set getScopedArtifacts()
        throws IOException
    {
        Set set = new HashSet();
        set.add( createArtifact( "g", "compile", "1.0", Artifact.SCOPE_COMPILE ) );
        set.add( createArtifact( "g", "provided", "1.0", Artifact.SCOPE_PROVIDED ) );
        set.add( createArtifact( "g", "test", "1.0", Artifact.SCOPE_TEST ) );
        set.add( createArtifact( "g", "runtime", "1.0", Artifact.SCOPE_RUNTIME ) );
        set.add( createArtifact( "g", "system", "1.0", Artifact.SCOPE_SYSTEM ) );
        return set;
    }

    public Set getTypedArtifacts()
        throws IOException
    {
        Set set = new HashSet();
        set.add( createArtifact( "g", "a", "1.0", Artifact.SCOPE_COMPILE, "war", null ) );
        set.add( createArtifact( "g", "b", "1.0", Artifact.SCOPE_COMPILE, "jar", null ) );
        set.add( createArtifact( "g", "c", "1.0", Artifact.SCOPE_COMPILE, "sources", null ) );
        set.add( createArtifact( "g", "d", "1.0", Artifact.SCOPE_COMPILE, "zip", null ) );
        set.add( createArtifact( "g", "e", "1.0", Artifact.SCOPE_COMPILE, "rar", null ) );
        return set;
    }

    public Set getClassifiedArtifacts()
        throws IOException
    {
        Set set = new HashSet();
        set.add( createArtifact( "g", "a", "1.0", Artifact.SCOPE_COMPILE, "jar", "one" ) );
        set.add( createArtifact( "g", "b", "1.0", Artifact.SCOPE_COMPILE, "jar", "two" ) );
        set.add( createArtifact( "g", "c", "1.0", Artifact.SCOPE_COMPILE, "jar", "three" ) );
        set.add( createArtifact( "g", "d", "1.0", Artifact.SCOPE_COMPILE, "jar", "four" ) );
        return set;
    }

    public Set getTypedArchiveArtifacts()
        throws IOException
    {
        Set set = new HashSet();
        set.add( createArtifact( "g", "a", "1.0", Artifact.SCOPE_COMPILE, "war", null ) );
        set.add( createArtifact( "g", "b", "1.0", Artifact.SCOPE_COMPILE, "jar", null ) );
        set.add( createArtifact( "g", "d", "1.0", Artifact.SCOPE_COMPILE, "zip", null ) );
        set.add( createArtifact( "g", "e", "1.0", Artifact.SCOPE_COMPILE, "rar", null ) );
        return set;
    }

    public Set getArtifactArtifacts()
        throws IOException
    {
        Set set = new HashSet();
        set.add( createArtifact( "g", "one", "1.0", Artifact.SCOPE_COMPILE, "jar", "a" ) );
        set.add( createArtifact( "g", "two", "1.0", Artifact.SCOPE_COMPILE, "jar", "a" ) );
        set.add( createArtifact( "g", "three", "1.0", Artifact.SCOPE_COMPILE, "jar", "a" ) );
        set.add( createArtifact( "g", "four", "1.0", Artifact.SCOPE_COMPILE, "jar", "a" ) );
        return set;
    }

    public Set getGroupIdArtifacts()
        throws IOException
    {
        Set set = new HashSet();
        set.add( createArtifact( "one", "group-one", "1.0", Artifact.SCOPE_COMPILE, "jar", "a" ) );
        set.add( createArtifact( "two", "group-two", "1.0", Artifact.SCOPE_COMPILE, "jar", "a" ) );
        set.add( createArtifact( "three", "group-three", "1.0", Artifact.SCOPE_COMPILE, "jar", "a" ) );
        set.add( createArtifact( "four", "group-four", "1.0", Artifact.SCOPE_COMPILE, "jar", "a" ) );
        return set;
    }

    public Set getMixedArtifacts()
        throws IOException
    {
        Set set = new HashSet();
        set.addAll( getTypedArtifacts() );
        set.addAll( getScopedArtifacts() );
        set.addAll( getReleaseAndSnapshotArtifacts() );
        return set;
    }

    /**
     * @return Returns the createFiles.
     */
    public boolean isCreateFiles()
    {
        return this.createFiles;
    }

    /**
     * @param createFiles The createFiles to set.
     */
    public void setCreateFiles( boolean createFiles )
    {
        this.createFiles = createFiles;
    }

    /**
     * @return Returns the workingDir.
     */
    public File getWorkingDir()
    {
        return this.workingDir;
    }

    /**
     * @param workingDir The workingDir to set.
     */
    public void setWorkingDir( File workingDir )
    {
        this.workingDir = workingDir;
    }

    /**
     * @return Returns the srcFile.
     */
    public File getSrcFile()
    {
        return this.srcFile;
    }

    /**
     * @param srcFile The srcFile to set.
     */
    public void setSrcFile( File srcFile )
    {
        this.srcFile = srcFile;
    }

    /**
     * convience method to set values to variables in objects that don't have setters
     * 
     * @param object
     * @param variable
     * @param value
     * @throws IllegalAccessException
     */
    public static void setVariableValueToObject( Object object, String variable, Object value )
        throws IllegalAccessException
    {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses( variable, object.getClass() );

        field.setAccessible( true );

        field.set( object, value );
    }

    /**
     * Builds the file name. If removeVersion is set, then the file name must be reconstructed from the artifactId,
     * Classifier (if used) and Type. Otherwise, this method returns the artifact file name.
     * 
     * @param artifact File to be formatted.
     * @param removeVersion Specifies if the version should be removed from the file name.
     * @return Formatted file name in the format artifactId-[version]-[classifier].[type]
     */
    public static String getFormattedFileName( Artifact artifact, boolean removeVersion )
    {
        String destFileName = null;

        // if there is a file and we aren't stripping the version, just get the
        // name directly
        if ( artifact.getFile() != null && !removeVersion )
        {
            destFileName = artifact.getFile().getName();
        }
        else
        // if offline
        {
            String versionString = null;
            if ( !removeVersion )
            {
                versionString = "-" + artifact.getVersion();
            }
            else
            {
                versionString = "";
            }

            String classifierString = "";

            if ( StringUtils.isNotEmpty( artifact.getClassifier() ) )
            {
                classifierString = "-" + artifact.getClassifier();
            }

            destFileName =
                artifact.getArtifactId() + versionString + classifierString + "." +
                    artifact.getArtifactHandler().getExtension();
        }
        return destFileName;
    }

}
