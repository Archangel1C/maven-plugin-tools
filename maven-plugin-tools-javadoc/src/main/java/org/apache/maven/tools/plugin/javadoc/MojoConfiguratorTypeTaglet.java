package org.apache.maven.tools.plugin.javadoc;

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

import java.util.Map;

import org.apache.maven.tools.plugin.extractor.java.JavadocMojoAnnotation;

import com.sun.tools.doclets.Taglet;

/**
 * The <tt>@configurator</tt> tag is used to inject parameter values into the Mojo and has annotation parameter.
 * <br/>
 * The following is a sample declaration:
 * <pre>
 * &#x2f;&#x2a;&#x2a;
 * &#x20;&#x2a; Dummy Mojo.
 * &#x20;&#x2a;
 * &#x20;&#x2a; &#64;configurator &lt;roleHint&gt;
 * &#x20;&#x2a; ...
 * &#x20;&#x2a;&#x2f;
 * public class MyMojo extends AbstractMojo{}
 * </pre>
 * To use it, calling the <code>Javadoc</code> tool with the following:
 * <pre>
 * javadoc ... -taglet 'org.apache.maven.tools.plugin.javadoc.MojoConfiguratorTypeTaglet'
 * </pre>
 * <b>Note</b>: This taglet is similar to call the <code>Javadoc</code> tool with the following:
 * <pre>
 * javadoc ... -tag 'configurator:t:Is configured to the role hint:'
 * </pre>
 *
 * @see <a href="package-summary.html#package_description">package-summary.html</a>
 *
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id$
 */
public class MojoConfiguratorTypeTaglet
    extends AbstractMojoTypeTaglet
{
    /** The Javadoc annotation */
    private static final String NAME = JavadocMojoAnnotation.CONFIGURATOR;

    /** The Javadoc text which will be added to the generated page. */
    protected static final String HEADER = "Is configured to the role hint";

    /**
     * @return By default, return the string defined in {@linkplain #HEADER}.
     * @see org.apache.maven.tools.plugin.javadoc.AbstractMojoTaglet#getHeader()
     * @see #HEADER
     */
    public String getHeader()
    {
        return HEADER;
    }

    /**
     * @return <code>"*"</code> since <code>@configurator</code> has a value.
     * @see org.apache.maven.tools.plugin.javadoc.AbstractMojoTaglet#getAllowedValue()
     */
    public String getAllowedValue()
    {
        return "*";
    }

    /**
     * @return <code>null</code> since <code>@configurator</code> has no parameter.
     * @see org.apache.maven.tools.plugin.javadoc.AbstractMojoTaglet#getAllowedParameterNames()
     */
    public String[] getAllowedParameterNames()
    {
        return null;
    }

    /**
     * @return By default, return the name of this taglet.
     * @see com.sun.tools.doclets.Taglet#getName()
     * @see MojoConfiguratorTypeTaglet#NAME
     */
    public String getName()
    {
        return NAME;
    }

    /**
     * Register this Taglet.
     *
     * @param tagletMap the map to register this tag to.
     */
    public static void register( Map<String, Taglet> tagletMap )
    {
        MojoConfiguratorTypeTaglet tag = new MojoConfiguratorTypeTaglet();
        Taglet t = tagletMap.get( tag.getName() );
        if ( t != null )
        {
            tagletMap.remove( tag.getName() );
        }
        tagletMap.put( tag.getName(), tag );
    }
}
