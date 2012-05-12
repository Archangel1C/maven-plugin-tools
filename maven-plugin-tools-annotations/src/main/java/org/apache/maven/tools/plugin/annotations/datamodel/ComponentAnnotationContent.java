package org.apache.maven.tools.plugin.annotations.datamodel;

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

import org.apache.maven.plugins.annotations.Component;

import java.lang.annotation.Annotation;

/**
 * @author Olivier Lamy
 * @since 3.0
 */
public class ComponentAnnotationContent
    extends AnnotatedField
    implements Component
{
    private String role;

    private String roleHint;

    private boolean required = false;

    private boolean readonly = false;

    public ComponentAnnotationContent( String fieldName )
    {
        super( fieldName );
    }

    public ComponentAnnotationContent( String fieldName, String role, String roleHint )
    {
        this( fieldName );
        this.role = role;
        this.roleHint = roleHint;
    }

    public String role()
    {
        return role == null ? "" : role;
    }

    public void role( String role )
    {
        this.role = role;
    }

    public String roleHint()
    {
        return roleHint == null ? "" : roleHint;
    }

    public void roleHint( String roleHint )
    {
        this.roleHint = roleHint;
    }

    public Class<? extends Annotation> annotationType()
    {
        return null;
    }

    public boolean required()
    {
        return required;
    }

    public void required( boolean required )
    {
        this.required = required;
    }

    public boolean readonly()
    {
        return readonly;
    }

    public void readonly( boolean readonly )
    {
        this.readonly = readonly;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append( super.toString() );
        sb.append( "ComponentAnnotationContent" );
        sb.append( "{role='" ).append( role ).append( '\'' );
        sb.append( ", roleHint='" ).append( roleHint ).append( '\'' );
        sb.append( '}' );
        return sb.toString();
    }
}
