package org.apache.maven.tools.plugin.annotations;
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

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.DocletTag;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.descriptor.DuplicateParameterException;
import org.apache.maven.plugin.descriptor.InvalidPluginDescriptorException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.Requirement;
import org.apache.maven.project.MavenProject;
import org.apache.maven.tools.plugin.DefaultPluginToolsRequest;
import org.apache.maven.tools.plugin.ExtendedMojoDescriptor;
import org.apache.maven.tools.plugin.PluginToolsRequest;
import org.apache.maven.tools.plugin.annotations.datamodel.ComponentAnnotationContent;
import org.apache.maven.tools.plugin.annotations.datamodel.ExecuteAnnotationContent;
import org.apache.maven.tools.plugin.annotations.datamodel.MojoAnnotationContent;
import org.apache.maven.tools.plugin.annotations.datamodel.ParameterAnnotationContent;
import org.apache.maven.tools.plugin.annotations.scanner.MojoAnnotatedClass;
import org.apache.maven.tools.plugin.annotations.scanner.MojoAnnotationsScanner;
import org.apache.maven.tools.plugin.annotations.scanner.MojoAnnotationsScannerRequest;
import org.apache.maven.tools.plugin.extractor.ExtractionException;
import org.apache.maven.tools.plugin.extractor.MojoDescriptorExtractor;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Olivier Lamy
 * @since 3.0
 */
public class JavaAnnotationsMojoDescriptorExtractor
    extends AbstractLogEnabled
    implements MojoDescriptorExtractor
{

    /**
     * @requirement
     */
    private MojoAnnotationsScanner mojoAnnotationsScanner;

    public List<MojoDescriptor> execute( MavenProject project, PluginDescriptor pluginDescriptor )
        throws ExtractionException, InvalidPluginDescriptorException
    {
        return execute( new DefaultPluginToolsRequest( project, pluginDescriptor ) );
    }

    public List<MojoDescriptor> execute( PluginToolsRequest request )
        throws ExtractionException, InvalidPluginDescriptorException
    {

        MojoAnnotationsScannerRequest mojoAnnotationsScannerRequest = new MojoAnnotationsScannerRequest();

        mojoAnnotationsScannerRequest.setClassesDirectories(
            Arrays.asList( new File( request.getProject().getBuild().getOutputDirectory() ) ) );

        mojoAnnotationsScannerRequest.setDependencies( request.getDependencies() );

        mojoAnnotationsScannerRequest.setProject( request.getProject() );

        Map<String, MojoAnnotatedClass> mojoAnnotatedClasses =
            mojoAnnotationsScanner.scan( mojoAnnotationsScannerRequest );

        // found artifact from reactors to scan sources
        // we currently only scan sources from reactors
        List<MavenProject> mavenProjects = new ArrayList<MavenProject>();

        for ( MojoAnnotatedClass mojoAnnotatedClass : mojoAnnotatedClasses.values() )
        {
            if ( !StringUtils.equals( mojoAnnotatedClass.getArtifact().getArtifactId(),
                                      request.getProject().getArtifact().getArtifactId() ) )
            {
                MavenProject mavenProject =
                    getFromProjectReferences( mojoAnnotatedClass.getArtifact(), request.getProject() );
                if ( mavenProject != null )
                {
                    mavenProjects.add( mavenProject );
                }
            }
        }

        Map<String, JavaClass> javaClassesMap = new HashMap<String, JavaClass>();

        for ( MavenProject mavenProject : mavenProjects )
        {
            javaClassesMap.putAll( discoverClasses( request.getEncoding(), mavenProject ) );
        }

        javaClassesMap.putAll( discoverClasses( request ) );

        populateDataFromJavadoc( mojoAnnotatedClasses, javaClassesMap );

        return toMojoDescriptors( mojoAnnotatedClasses, request );

    }

    /**
     * from sources scan to get @since and @deprecated and description of classes and fields.
     *
     * @param mojoAnnotatedClasses
     * @param javaClassesMap
     */
    protected void populateDataFromJavadoc( Map<String, MojoAnnotatedClass> mojoAnnotatedClasses,
                                            Map<String, JavaClass> javaClassesMap )
    {

        for ( Map.Entry<String, MojoAnnotatedClass> entry : mojoAnnotatedClasses.entrySet() )
        {
            JavaClass javaClass = javaClassesMap.get( entry.getKey() );
            if ( javaClass != null )
            {
                MojoAnnotationContent mojoAnnotationContent = entry.getValue().getMojo();
                if ( mojoAnnotationContent != null )
                {
                    mojoAnnotationContent.setDescription( javaClass.getComment() );
                    DocletTag since = findInClassHierarchy( javaClass, "since" );
                    if ( since != null )
                    {
                        mojoAnnotationContent.setSince( since.getValue() );
                    }

                    DocletTag deprecated = findInClassHierarchy( javaClass, "deprecated" );
                    if ( deprecated != null )
                    {
                        mojoAnnotationContent.setDeprecated( deprecated.getValue() );
                    }
                }
                Map<String, JavaField> fieldsMap = extractFieldParameterTags( javaClass );
                for ( Map.Entry<String, ParameterAnnotationContent> parameter : entry.getValue().getParameters().entrySet() )
                {
                    JavaField javaField = fieldsMap.get( parameter.getKey() );
                    if ( javaField != null )
                    {
                        ParameterAnnotationContent parameterAnnotationContent = parameter.getValue();
                        DocletTag deprecated = javaField.getTagByName( "deprecated" );
                        if ( deprecated != null )
                        {
                            parameterAnnotationContent.setDeprecated( deprecated.getValue() );
                        }
                        DocletTag since = javaField.getTagByName( "since" );
                        if ( since != null )
                        {
                            parameterAnnotationContent.setSince( since.getValue() );
                        }
                        parameterAnnotationContent.setDescription( javaField.getComment() );
                    }
                }

                for ( Map.Entry<String, ComponentAnnotationContent> component : entry.getValue().getComponents().entrySet() )
                {
                    JavaField javaField = fieldsMap.get( component.getKey() );
                    if ( javaField != null )
                    {
                        ComponentAnnotationContent componentAnnotationContent = component.getValue();
                        DocletTag deprecated = javaField.getTagByName( "deprecated" );
                        if ( deprecated != null )
                        {
                            componentAnnotationContent.setDeprecated( deprecated.getValue() );
                        }
                        DocletTag since = javaField.getTagByName( "since" );
                        if ( since != null )
                        {
                            componentAnnotationContent.setSince( since.getValue() );
                        }
                        componentAnnotationContent.setDescription( javaField.getComment() );
                    }
                }

            }
        }

    }

    /**
     * @param javaClass not null
     * @param tagName   not null
     * @return docletTag instance
     */
    private static DocletTag findInClassHierarchy( JavaClass javaClass, String tagName )
    {
        DocletTag tag = javaClass.getTagByName( tagName );

        if ( tag == null )
        {
            JavaClass superClass = javaClass.getSuperJavaClass();

            if ( superClass != null )
            {
                tag = findInClassHierarchy( superClass, tagName );
            }
        }

        return tag;
    }

    /**
     * extract fields that are either parameters or components.
     *
     * @param javaClass not null
     * @return map with Mojo parameters names as keys
     */
    private Map<String, JavaField> extractFieldParameterTags( JavaClass javaClass )
    {
        Map<String, JavaField> rawParams;

        // we have to add the parent fields first, so that they will be overwritten by the local fields if
        // that actually happens...
        JavaClass superClass = javaClass.getSuperJavaClass();

        if ( superClass != null )
        {
            rawParams = extractFieldParameterTags( superClass );
        }
        else
        {
            rawParams = new TreeMap<String, JavaField>();
        }

        JavaField[] classFields = javaClass.getFields();

        if ( classFields != null )
        {
            for ( JavaField field : classFields )
            {
                rawParams.put( field.getName(), field );
            }
        }
        return rawParams;
    }

    protected Map<String, JavaClass> discoverClasses( final PluginToolsRequest request )
    {
        return discoverClasses( request.getEncoding(), request.getProject() );
    }

    protected Map<String, JavaClass> discoverClasses( final String encoding, MavenProject project )
    {
        JavaDocBuilder builder = new JavaDocBuilder();
        builder.setEncoding( encoding );

        for ( String source : (List<String>) project.getCompileSourceRoots() )
        {
            builder.addSourceTree( new File( source ) );
        }

        // TODO be more dynamic
        File generatedPlugin = new File( project.getBasedir(), "target/generated-sources/plugin" );
        if ( !project.getCompileSourceRoots().contains( generatedPlugin.getAbsolutePath() ) )
        {
            builder.addSourceTree( generatedPlugin );
        }

        JavaClass[] javaClasses = builder.getClasses();

        if ( javaClasses == null || javaClasses.length < 1 )
        {
            return Collections.emptyMap();
        }

        Map<String, JavaClass> javaClassMap = new HashMap<String, JavaClass>( javaClasses.length );

        for ( JavaClass javaClass : javaClasses )
        {
            javaClassMap.put( javaClass.getFullyQualifiedName(), javaClass );
        }

        return javaClassMap;
    }

    private List<File> toFiles( List<String> directories )
    {
        if ( directories == null )
        {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<File>( directories.size() );
        for ( String directory : directories )
        {
            files.add( new File( directory ) );
        }
        return files;
    }

    private List<MojoDescriptor> toMojoDescriptors( Map<String, MojoAnnotatedClass> mojoAnnotatedClasses,
                                                    PluginToolsRequest request )
        throws DuplicateParameterException
    {
        List<MojoDescriptor> mojoDescriptors = new ArrayList<MojoDescriptor>( mojoAnnotatedClasses.size() );
        for ( MojoAnnotatedClass mojoAnnotatedClass : mojoAnnotatedClasses.values() )
        {
            // no mojo so skip it
            if ( mojoAnnotatedClass.getMojo() == null )
            {
                continue;
            }

            ExtendedMojoDescriptor mojoDescriptor = new ExtendedMojoDescriptor();

            //mojoDescriptor.setRole( mojoAnnotatedClass.getClassName() );
            //mojoDescriptor.setRoleHint( "default" );
            mojoDescriptor.setImplementation( mojoAnnotatedClass.getClassName() );
            mojoDescriptor.setLanguage( "java" );

            MojoAnnotationContent mojo = mojoAnnotatedClass.getMojo();

            mojoDescriptor.setDescription( mojo.getDescription() );
            mojoDescriptor.setSince( mojo.getSince() );
            mojo.setDeprecated( mojo.getDeprecated() );

            mojoDescriptor.setAggregator( mojo.aggregator() );
            mojoDescriptor.setDependencyResolutionRequired( mojo.requiresDependencyResolution() );
            mojoDescriptor.setDependencyCollectionRequired( mojo.requiresDependencyCollection() );

            mojoDescriptor.setDirectInvocationOnly( mojo.requiresDirectInvocation() );
            mojoDescriptor.setDeprecated( mojo.getDeprecated() );
            mojoDescriptor.setThreadSafe( mojo.threadSafe() );

            ExecuteAnnotationContent execute = mojoAnnotatedClass.getExecute();

            if ( execute != null )
            {
                mojoDescriptor.setExecuteGoal( execute.goal() );
                mojoDescriptor.setExecuteLifecycle( execute.lifecycle() );
                mojoDescriptor.setExecutePhase( execute.phase().id() );
            }

            mojoDescriptor.setExecutionStrategy( mojo.executionStrategy() );
            // FIXME olamy wtf ?
            //mojoDescriptor.alwaysExecute(mojo.a)

            mojoDescriptor.setGoal( mojo.name() );
            mojoDescriptor.setOnlineRequired( mojo.requiresOnline() );

            mojoDescriptor.setPhase( mojo.defaultPhase().id() );

            Map<String, ParameterAnnotationContent> parameters =
                getParametersParentHierarchy( mojoAnnotatedClass, new HashMap<String, ParameterAnnotationContent>(),
                                              mojoAnnotatedClasses );

            for ( ParameterAnnotationContent parameterAnnotationContent : parameters.values() )
            {
                org.apache.maven.plugin.descriptor.Parameter parameter =
                    new org.apache.maven.plugin.descriptor.Parameter();
                parameter.setName( parameterAnnotationContent.getFieldName() );
                parameter.setAlias( parameterAnnotationContent.alias() );
                parameter.setDefaultValue( parameterAnnotationContent.defaultValue() );
                parameter.setDeprecated( parameterAnnotationContent.getDeprecated() );
                parameter.setDescription( parameterAnnotationContent.getDescription() );
                parameter.setEditable( !parameterAnnotationContent.readonly() );
                parameter.setExpression( parameterAnnotationContent.expression() );
                parameter.setType( parameterAnnotationContent.getClassName() );
                parameter.setRequired( parameterAnnotationContent.required() );

                mojoDescriptor.addParameter( parameter );
            }

            Map<String, ComponentAnnotationContent> components =
                getComponentsParentHierarchy( mojoAnnotatedClass, new HashMap<String, ComponentAnnotationContent>(),
                                              mojoAnnotatedClasses );

            for ( ComponentAnnotationContent componentAnnotationContent : components.values() )
            {
                org.apache.maven.plugin.descriptor.Parameter parameter =
                    new org.apache.maven.plugin.descriptor.Parameter();
                parameter.setName( componentAnnotationContent.getFieldName() );
                parameter.setRequirement(
                    new Requirement( componentAnnotationContent.role(), componentAnnotationContent.roleHint() ) );
                parameter.setEditable( false );

                mojoDescriptor.addParameter( parameter );
            }

            mojoDescriptor.setPluginDescriptor( request.getPluginDescriptor() );

            mojoDescriptors.add( mojoDescriptor );
        }
        return mojoDescriptors;
    }

    protected Map<String, ParameterAnnotationContent> getParametersParentHierarchy(
        MojoAnnotatedClass mojoAnnotatedClass, Map<String, ParameterAnnotationContent> parameters,
        Map<String, MojoAnnotatedClass> mojoAnnotatedClasses )
    {
        List<ParameterAnnotationContent> parameterAnnotationContents = new ArrayList<ParameterAnnotationContent>();

        parameterAnnotationContents =
            getParametersParent( mojoAnnotatedClass, parameterAnnotationContents, mojoAnnotatedClasses );

        // move to parent first to build the Map
        Collections.reverse( parameterAnnotationContents );

        Map<String, ParameterAnnotationContent> map =
            new HashMap<String, ParameterAnnotationContent>( parameterAnnotationContents.size() );

        for ( ParameterAnnotationContent parameterAnnotationContent : parameterAnnotationContents )
        {
            map.put( parameterAnnotationContent.getFieldName(), parameterAnnotationContent );
        }
        return map;
    }

    protected List<ParameterAnnotationContent> getParametersParent( MojoAnnotatedClass mojoAnnotatedClass,
                                                                    List<ParameterAnnotationContent> parameterAnnotationContents,
                                                                    Map<String, MojoAnnotatedClass> mojoAnnotatedClasses )
    {
        parameterAnnotationContents.addAll( mojoAnnotatedClass.getParameters().values() );
        String parentClassName = mojoAnnotatedClass.getParentClassName();
        if ( parentClassName != null )
        {
            MojoAnnotatedClass parent = mojoAnnotatedClasses.get( parentClassName );
            if ( parent != null )
            {
                return getParametersParent( parent, parameterAnnotationContents, mojoAnnotatedClasses );
            }
        }
        return parameterAnnotationContents;
    }


    protected Map<String, ComponentAnnotationContent> getComponentsParentHierarchy(
        MojoAnnotatedClass mojoAnnotatedClass, Map<String, ComponentAnnotationContent> components,
        Map<String, MojoAnnotatedClass> mojoAnnotatedClasses )
    {
        List<ComponentAnnotationContent> componentAnnotationContents = new ArrayList<ComponentAnnotationContent>();

        componentAnnotationContents =
            getComponentParent( mojoAnnotatedClass, componentAnnotationContents, mojoAnnotatedClasses );

        // move to parent first to build the Map
        Collections.reverse( componentAnnotationContents );

        Map<String, ComponentAnnotationContent> map =
            new HashMap<String, ComponentAnnotationContent>( componentAnnotationContents.size() );

        for ( ComponentAnnotationContent componentAnnotationContent : componentAnnotationContents )
        {
            map.put( componentAnnotationContent.getFieldName(), componentAnnotationContent );
        }
        return map;
    }

    protected List<ComponentAnnotationContent> getComponentParent( MojoAnnotatedClass mojoAnnotatedClass,
                                                                   List<ComponentAnnotationContent> componentAnnotationContents,
                                                                   Map<String, MojoAnnotatedClass> mojoAnnotatedClasses )
    {
        componentAnnotationContents.addAll( mojoAnnotatedClass.getComponents().values() );
        String parentClassName = mojoAnnotatedClass.getParentClassName();
        if ( parentClassName != null )
        {
            MojoAnnotatedClass parent = mojoAnnotatedClasses.get( parentClassName );
            if ( parent != null )
            {
                return getComponentParent( parent, componentAnnotationContents, mojoAnnotatedClasses );
            }
        }
        return componentAnnotationContents;
    }

    protected MavenProject getFromProjectReferences( Artifact artifact, MavenProject project )
    {
        if ( project.getProjectReferences() == null || project.getProjectReferences().isEmpty() )
        {
            return null;
        }
        Collection<MavenProject> mavenProjects = project.getProjectReferences().values();
        for ( MavenProject mavenProject : mavenProjects )
        {
            if ( StringUtils.equals( mavenProject.getId(), artifact.getId() ) )
            {
                return mavenProject;
            }
        }
        return null;
    }

}
