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
package org.apache.ace.deployment.verifier.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.felix.framework.Logger;
import org.apache.felix.framework.util.manifestparser.ManifestParser;
import org.apache.felix.framework.util.manifestparser.R4Library;
import org.apache.felix.framework.wiring.BundleCapabilityImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 *
 */
public class VerifierBundleRevision implements BundleRevision {
    
	private final String m_symbolicName;
	private final Version m_version;
	private final List<BundleCapability> m_declaredCaps;
	private final List<BundleRequirement> m_declaredReqs;
	private final int m_type;
	private final Bundle m_bundle;
	private final List<R4Library> m_declaredLibs;
	private final Map<String, String> m_headers;

	/**
	 * @param log
	 * @param bundle
	 * @param config
	 * @param headers
	 * @throws BundleException
	 */
	public VerifierBundleRevision(Logger log, Bundle bundle, Map<String, String> config, Map<String, String> headers) throws BundleException {
		m_bundle = bundle;
		m_headers = Collections.unmodifiableMap(headers);
		ManifestParser parser = new ManifestParser(log, config, this, headers);
		m_symbolicName = parser.getSymbolicName();
		m_version = parser.getBundleVersion();
		m_declaredCaps = (m_bundle.getBundleId() != 0) ? parser.getCapabilities() : aliasSymbolicName(parser.getCapabilities());
		m_declaredReqs = parser.getRequirements();
		m_type = headers.containsKey(Constants.FRAGMENT_HOST) ? BundleRevision.TYPE_FRAGMENT : 0;
		m_declaredLibs = parser.getLibraries();
	}

    /**
     * Takes a given list of bundle capabilities and patches all symbolic names to be marked as system bundles.
     * 
     * @param capabilities the capabilities to patch, may be <code>null</code>.
     * @return the patched capabilities, or an emtpy list in case the given capabilities was <code>null</code>.
     */
    private static List<BundleCapability> aliasSymbolicName(List<BundleCapability> capabilities)
    {
        if (capabilities == null)
        {
            return Collections.emptyList();
        }

        List<BundleCapability> aliasCaps = new ArrayList<>(capabilities);

        for (int capIdx = 0; capIdx < aliasCaps.size(); capIdx++)
        {
            BundleCapability capability = aliasCaps.get(capIdx);
            
            // Get the attributes and search for bundle symbolic name.
            Map<String, Object> attributes = capability.getAttributes();
            
            for (Entry<String, Object> entry : attributes.entrySet())
            {
                // If there is a bundle symbolic name attribute, add the
                // standard alias as a value.
                if (Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE.equalsIgnoreCase(entry.getKey()))
                {
                    // Make a copy of the attribute array.
                    Map<String, Object> aliasAttrs = new HashMap<>(attributes);
                    // Add the aliased value.
                    aliasAttrs.put(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE, new String[] { (String) entry.getValue(), Constants.SYSTEM_BUNDLE_SYMBOLICNAME });
                    // Create the aliased capability to replace the old capability.
                    aliasCaps.set(capIdx, new BundleCapabilityImpl(capability.getRevision(), capability.getNamespace(), capability.getDirectives(), aliasAttrs));
                    // Continue with the next capability.
                    break;
                }
            }
        }

        return aliasCaps;
    }

	/**
	 * {@inheritDoc}
	 */
	public Bundle getBundle() {
		return m_bundle;
	}

    /**
     * {@inheritDoc}
     */
	public String getSymbolicName() {
		return m_symbolicName;
	}

    /**
     * {@inheritDoc}
     */
	public Version getVersion() {
		return m_version;
	}

    /**
     * {@inheritDoc}
     */
	public List<BundleCapability> getDeclaredCapabilities(String namespace) {
		return m_declaredCaps;
	}
	
    /**
     * {@inheritDoc}
     */
	public List<Capability> getCapabilities(String namespace) {
	    return new ArrayList<Capability>(m_declaredCaps);
	}

    /**
     * {@inheritDoc}
     */
	public List<BundleRequirement> getDeclaredRequirements(String namespace) {
		return m_declaredReqs;
	}

    /**
     * {@inheritDoc}
     */
	public List<Requirement> getRequirements(String namespace) {
	    return new ArrayList<Requirement>(m_declaredReqs);
	}
	
    /**
     * {@inheritDoc}
     */
	public List<R4Library> getDeclaredNativeLibraries() {
		return m_declaredLibs;
	}

    /**
     * {@inheritDoc}
     */
	public int getTypes() {
		return m_type;
	}

    /**
     * {@inheritDoc}
     */
	public BundleWiring getWiring() {
		return null;
	}

    /**
     * {@inheritDoc}
     */
	public Map<String, String> getHeaders() {
		return m_headers;
	}
	
	/**
	 * Returns the required execution environment, if defined.
	 * 
	 * @return the required execution environment, can be <code>null</code> if not defined.
	 */
	@SuppressWarnings("deprecation")
    public String getRequiredExecutionEnvironment() {
	    String result = getHeaders().get(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT);
        return result == null ? null : result.trim();
	}
	
    /**
     * {@inheritDoc}
     */
	public int hashCode() {
		return (int) getBundle().getBundleId();
	}
	
    /**
     * {@inheritDoc}
     */
	public boolean equals(Object o) {
		if (o instanceof VerifierBundleRevision) {
			return o.hashCode() == hashCode();
		}
		return false;
	}
	
    /**
     * {@inheritDoc}
     */
	public String toString() {
		return m_symbolicName + ";"+ Constants.VERSION_ATTRIBUTE + "=" + m_version + "(id=" + getBundle().getBundleId() + ")";
	}
}
