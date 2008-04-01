package org.apache.maven.tools.plugin.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.w3c.tidy.Configuration;
import org.w3c.tidy.Tidy;

/**
 * Convenience methods to play with Maven plugins.
 *
 * @author jdcasey
 * @version $Id$
 */
public final class PluginUtils
{
    private PluginUtils()
    {
        // nop
    }

    /**
     * @param basedir
     * @param include
     * @return list of included files with default SCM excluded files
     */
    public static String[] findSources( String basedir, String include )
    {
        return PluginUtils.findSources( basedir, include, null );
    }

    /**
     * @param basedir
     * @param include
     * @param exclude
     * @return list of included files
     */
    public static String[] findSources( String basedir, String include, String exclude )
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( basedir );
        scanner.setIncludes( new String[] { include } );
        if ( !StringUtils.isEmpty( exclude ) )
        {
            scanner.setExcludes( new String[] { exclude, StringUtils.join( FileUtils.getDefaultExcludes(), "," ) } );
        }
        else
        {
            scanner.setExcludes( FileUtils.getDefaultExcludes() );
        }

        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * @param w not null writer
     * @param pluginDescriptor not null
     */
    public static void writeDependencies( XMLWriter w, PluginDescriptor pluginDescriptor )
    {
        w.startElement( "dependencies" );

        for ( Iterator it = pluginDescriptor.getDependencies().iterator(); it.hasNext(); )
        {
            ComponentDependency dep = (ComponentDependency) it.next();

            w.startElement( "dependency" );

            PluginUtils.element( w, "groupId", dep.getGroupId() );

            PluginUtils.element( w, "artifactId", dep.getArtifactId() );

            PluginUtils.element( w, "type", dep.getType() );

            PluginUtils.element( w, "version", dep.getVersion() );

            w.endElement();
        }

        w.endElement();
    }

    /**
     * @param dependencies not null list of <code>Dependency</code>
     * @return list of component dependencies
     */
    public static List toComponentDependencies( List dependencies )
    {
        List componentDeps = new LinkedList();

        for ( Iterator it = dependencies.iterator(); it.hasNext(); )
        {
            Dependency dependency = (Dependency) it.next();

            ComponentDependency cd = new ComponentDependency();

            cd.setArtifactId( dependency.getArtifactId() );
            cd.setGroupId( dependency.getGroupId() );
            cd.setVersion( dependency.getVersion() );
            cd.setType( dependency.getType() );

            componentDeps.add( cd );
        }

        return componentDeps;
    }

    /**
     * @param w not null writer
     * @param name
     * @param value
     */
    public static void element( XMLWriter w, String name, String value )
    {
        w.startElement( name );

        if ( value == null )
        {
            value = "";
        }

        w.writeText( value );

        w.endElement();
    }

    /**
     * @param impl a Mojo implementation, not null
     * @param project a MavenProject instance, could be null
     * @return <code>true</code> is the Mojo implementation implements <code>MavenReport</code>,
     * <code>false</code> otherwise.
     * @throws IllegalArgumentException if any
     */
    public static boolean isMavenReport( String impl, MavenProject project )
    {
        if ( impl == null )
        {
            throw new IllegalArgumentException( "mojo implementation should be declared" );
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if ( project != null )
        {
            List classPathStrings;
            try
            {
                classPathStrings = project.getCompileClasspathElements();
                if ( project.getExecutionProject() != null )
                {
                    classPathStrings.addAll( project.getExecutionProject().getCompileClasspathElements() );
                }
            }
            catch ( DependencyResolutionRequiredException e )
            {
                throw (RuntimeException) new IllegalArgumentException().initCause( e );
            }

            List urls = new ArrayList( classPathStrings.size() );
            for ( Iterator it = classPathStrings.iterator(); it.hasNext(); )
            {
                try
                {
                    urls.add( new File( ( (String) it.next() ) ).toURL() );
                }
                catch ( MalformedURLException e )
                {
                    throw (RuntimeException) new IllegalArgumentException().initCause( e );
                }
            }

            classLoader = new URLClassLoader( (URL[]) urls.toArray( new URL[urls.size()] ),
                                                                    classLoader );
        }

        Class clazz = null;
        try
        {
            clazz = Class.forName( impl, false, classLoader );
        }
        catch ( ClassNotFoundException e )
        {
            return false;
        }

        if ( MavenReport.class.isAssignableFrom( clazz ) )
        {
            return true;
        }

        return false;
    }

    /**
     * @param description Javadoc description with HTML tags
     * @return the description with valid HTML tags
     */
    public static String makeHtmlValid( String description )
    {
        if ( StringUtils.isEmpty( description ) )
        {
            return "";
        }

        String commentCleaned = decodeJavadocTags( description );

        // Using jTidy to clean comment
        Tidy tidy = new Tidy();
        tidy.setDocType( "loose" );
        tidy.setXHTML( true );
        tidy.setXmlOut( true );
        tidy.setCharEncoding( Configuration.UTF8 );
        tidy.setMakeClean( true );
        tidy.setNumEntities( true );
        tidy.setQuoteNbsp( false );
        tidy.setQuiet( true );
        tidy.setShowWarnings( false );
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream( commentCleaned.length() + 256 );
            tidy.parse( new ByteArrayInputStream( commentCleaned.getBytes( "UTF-8" ) ), out );
            commentCleaned = out.toString("UTF-8");
        }
        catch ( UnsupportedEncodingException e )
        {
            // cannot happen as every JVM must support UTF-8, see also class javadoc for java.nio.charset.Charset
        }

        if ( StringUtils.isEmpty( commentCleaned ) )
        {
            return "";
        }

        // strip the header/body stuff
        String LS = System.getProperty( "line.separator" );
        int startPos = commentCleaned.indexOf( "<body>" + LS ) + 6 + LS.length();
        int endPos = commentCleaned.indexOf( LS + "</body>" );
        commentCleaned = commentCleaned.substring( startPos, endPos );

        return commentCleaned;
    }

    /**
     * Decodes javadoc inline tags into equivalent HTML tags. For instance, the inline tag "{@code <A&B>}" should be
     * rendered as "<code>&lt;A&amp;B&gt;</code>".
     *
     * @param description The javadoc description to decode, may be <code>null</code>.
     * @return The decoded description, never <code>null</code>.
     */
    static String decodeJavadocTags( String description )
    {
        if ( StringUtils.isEmpty( description ) )
        {
            return "";
        }

        StringBuffer decoded = new StringBuffer( description.length() + 1024 );

        Matcher matcher = Pattern.compile( "\\{@(\\w+)\\s*([^\\}]*)\\}" ).matcher( description );
        while ( matcher.find() )
        {
            String tag = matcher.group( 1 );
            String text = matcher.group( 2 );
            text = StringUtils.replace( text, "&", "&amp;" );
            text = StringUtils.replace( text, "<", "&lt;" );
            text = StringUtils.replace( text, ">", "&gt;" );
            if ( "code".equals( tag ) )
            {
                text = "<code>" + text + "</code>";
            }
            else if ( "link".equals( tag ) || "linkplain".equals( tag ) || "value".equals( tag ) )
            {
                String pattern = "(([^#\\.\\s]+\\.)*([^#\\.\\s]+))?" + "(#([^\\(\\s]*)(\\([^\\)]*\\))?\\s*(\\S.*)?)?";
                final int LABEL = 7;
                final int CLASS = 3;
                final int MEMBER = 5;
                final int ARGS = 6;
                Matcher link = Pattern.compile( pattern ).matcher( text );
                if ( link.matches() )
                {
                    text = link.group( LABEL );
                    if ( StringUtils.isEmpty( text ) )
                    {
                        text = link.group( CLASS );
                        if ( StringUtils.isEmpty( text ) )
                        {
                            text = "";
                        }
                        if ( StringUtils.isNotEmpty( link.group( MEMBER ) ) )
                        {
                            if ( StringUtils.isNotEmpty( text ) )
                            {
                                text += '.';
                            }
                            text += link.group( MEMBER );
                            if ( StringUtils.isNotEmpty( link.group( ARGS ) ) )
                            {
                                text += "()";
                            }
                        }
                    }
                }
                if ( !"linkplain".equals( tag ) )
                {
                    text = "<code>" + text + "</code>";
                }
            }
            matcher.appendReplacement( decoded, ( text != null ) ? quoteReplacement( text ) : "" );
        }
        matcher.appendTail( decoded );

        return decoded.toString();
    }

    /**
     * Returns a literal replacement <code>String</code> for the specified <code>String</code>. This method
     * produces a <code>String</code> that will work as a literal replacement <code>s</code> in the
     * <code>appendReplacement</code> method of the {@link Matcher} class. The <code>String</code> produced will
     * match the sequence of characters in <code>s</code> treated as a literal sequence. Slashes ('\') and dollar
     * signs ('$') will be given no special meaning. TODO: copied from Matcher class of Java 1.5, remove once target
     * platform can be upgraded
     *
     * @see <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/regex/Matcher.html">java.util.regex.Matcher</a>
     * @param s The string to be literalized
     * @return A literal string replacement
     */
    private static String quoteReplacement( String s )
    {
        if ( ( s.indexOf( '\\' ) == -1 ) && ( s.indexOf( '$' ) == -1 ) )
            return s;
        StringBuffer sb = new StringBuffer();
        for ( int i = 0; i < s.length(); i++ )
        {
            char c = s.charAt( i );
            if ( c == '\\' )
            {
                sb.append( '\\' );
                sb.append( '\\' );
            }
            else if ( c == '$' )
            {
                sb.append( '\\' );
                sb.append( '$' );
            }
            else
            {
                sb.append( c );
            }
        }
        return sb.toString();
    }

}
