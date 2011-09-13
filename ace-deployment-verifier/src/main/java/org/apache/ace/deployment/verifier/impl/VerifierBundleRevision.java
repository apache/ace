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

public class VerifierBundleRevision implements BundleRevision {
	private final String m_symbolicName;
	private final Version m_version;
	private final List<BundleCapability> m_declaredCaps;
	private final List<BundleRequirement> m_declaredReqs;
	private final int m_type;
	private final Bundle m_bundle;
	private final List<R4Library> m_declaredLibs;
	private final Map<String, String> m_headers;

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

	private static List<BundleCapability> aliasSymbolicName(List<BundleCapability> caps)
    {
        if (caps == null)
        {
            return new ArrayList<BundleCapability>(0);
        }

        List<BundleCapability> aliasCaps = new ArrayList<BundleCapability>(caps);

        for (int capIdx = 0; capIdx < aliasCaps.size(); capIdx++)
        {
            // Get the attributes and search for bundle symbolic name.
            for (Entry<String, Object> entry : aliasCaps.get(capIdx).getAttributes().entrySet())
            {
                // If there is a bundle symbolic name attribute, add the
                // standard alias as a value.
                if (entry.getKey().equalsIgnoreCase(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE))
                {
                    // Make a copy of the attribute array.
                    Map<String, Object> aliasAttrs =
                        new HashMap<String, Object>(aliasCaps.get(capIdx).getAttributes());
                    // Add the aliased value.
                    aliasAttrs.put(
                        Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE,
                        new String[] {
                            (String) entry.getValue(),
                            Constants.SYSTEM_BUNDLE_SYMBOLICNAME});
                    // Create the aliased capability to replace the old capability.
                    aliasCaps.set(capIdx, new BundleCapabilityImpl(
                        caps.get(capIdx).getRevision(),
                        caps.get(capIdx).getNamespace(),
                        caps.get(capIdx).getDirectives(),
                        aliasAttrs));
                    // Continue with the next capability.
                    break;
                }
            }
        }

        return aliasCaps;
    }
	
	public Bundle getBundle() {
		return m_bundle;
	}

	public String getSymbolicName() {
		return m_symbolicName;
	}

	public Version getVersion() {
		return m_version;
	}

	public List<BundleCapability> getDeclaredCapabilities(String namespace) {
		return m_declaredCaps;
	}

	public List<BundleRequirement> getDeclaredRequirements(String namespace) {
		return m_declaredReqs;
	}
	
	public List<R4Library> getDeclaredNativeLibraries() {
		return m_declaredLibs;
	}

	public int getTypes() {
		return m_type;
	}

	public BundleWiring getWiring() {
		return null;
	}

	public Map<String, String> getHeaders() {
		return m_headers;
	}
	
	public int hashCode() {
		return (int) getBundle().getBundleId();
	}
	
	public boolean equals(Object o) {
		if (o instanceof VerifierBundleRevision) {
			return o.hashCode() == hashCode();
		}
		return false;
	}
	
	public String toString() {
		return m_symbolicName + ";"+ Constants.VERSION_ATTRIBUTE + "=" + m_version + "(id=" + getBundle().getBundleId() + ")";
	}
}
