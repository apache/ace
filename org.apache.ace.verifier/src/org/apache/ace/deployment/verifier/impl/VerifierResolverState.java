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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;

import org.apache.felix.framework.capabilityset.CapabilitySet;
import org.apache.felix.framework.capabilityset.SimpleFilter;
import org.apache.felix.framework.resolver.CandidateComparator;
import org.apache.felix.framework.resolver.ResolveException;
import org.apache.felix.framework.resolver.Resolver;
import org.apache.felix.framework.resolver.Resolver.ResolverState;
import org.apache.felix.framework.util.Util;
import org.apache.felix.framework.util.manifestparser.R4Library;
import org.apache.felix.framework.wiring.BundleRequirementImpl;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

/**
 * Provides a custom {@link ResolverState} implementation to hold all state during resolving.
 */
public class VerifierResolverState implements Resolver.ResolverState {

	// Set of all revisions.
	private final Set<BundleRevision> m_revisions;
	// Set of all fragments.
	private final Set<BundleRevision> m_fragments;
	// Capability sets.
	private final Map<String, CapabilitySet> m_capSets;
	// Execution environment.
	private final String m_fwkExecEnvStr;
	// Parsed framework environments
	private final Set<String> m_fwkExecEnvSet;

	/**
	 * Creates a new {@link VerifierResolverState} instance.
	 * 
	 * @param fwkExecEnvStr the framework execution environment, can be <code>null</code>.
	 */
	public VerifierResolverState(String fwkExecEnvStr) {
		m_revisions = new HashSet<>();
		m_fragments = new HashSet<>();
		m_capSets = new HashMap<>();

		m_fwkExecEnvStr = (fwkExecEnvStr != null) ? fwkExecEnvStr.trim() : null;
		m_fwkExecEnvSet = parseExecutionEnvironments(fwkExecEnvStr);

		List<String> indices = new ArrayList<>();
		indices.add(BundleRevision.BUNDLE_NAMESPACE);
		m_capSets.put(BundleRevision.BUNDLE_NAMESPACE, new CapabilitySet(indices, true));

		indices = new ArrayList<>();
		indices.add(BundleRevision.PACKAGE_NAMESPACE);
		m_capSets.put(BundleRevision.PACKAGE_NAMESPACE, new CapabilitySet(indices, true));

		indices = new ArrayList<>();
		indices.add(BundleRevision.HOST_NAMESPACE);
		m_capSets.put(BundleRevision.HOST_NAMESPACE, new CapabilitySet(indices, true));
	}

	synchronized Set<BundleRevision> getUnresolvedRevisions() {
		Set<BundleRevision> unresolved = new HashSet<>();
		for (BundleRevision revision : m_revisions) {
			if (revision.getWiring() == null) {
				unresolved.add(revision);
			}
		}
		return unresolved;
	}

	synchronized void addRevision(BundleRevision br) {
		// Always attempt to remove the revision, since
		// this method can be used for re-indexing a revision
		// after it has been resolved.
		removeRevision(br);

		// Add the revision and index its declared or resolved
		// capabilities depending on whether it is resolved or
		// not.
		m_revisions.add(br);
		List<BundleCapability> caps = (br.getWiring() == null) ? br
				.getDeclaredCapabilities(null) : br.getWiring()
				.getCapabilities(null);
		if (caps != null) {
			for (BundleCapability cap : caps) {
				// If the capability is from a different revision, then
				// don't index it since it is a capability from a fragment.
				// In that case, the fragment capability is still indexed.
				if (cap.getRevision() == br) {
					CapabilitySet capSet = m_capSets.get(cap.getNamespace());
					if (capSet == null) {
						capSet = new CapabilitySet(null, true);
						m_capSets.put(cap.getNamespace(), capSet);
					}
					capSet.addCapability(cap);
				}
			}
		}

		if (Util.isFragment(br)) {
			m_fragments.add(br);
		}
	}

	synchronized void removeRevision(BundleRevision br) {
		if (m_revisions.remove(br)) {
			// We only need be concerned with declared capabilities here,
			// because resolved capabilities will be a subset.
			List<BundleCapability> caps = br.getDeclaredCapabilities(null);
			if (caps != null) {
				for (BundleCapability cap : caps) {
					CapabilitySet capSet = m_capSets.get(cap.getNamespace());
					if (capSet != null) {
						capSet.removeCapability(cap);
					}
				}
			}

			if (Util.isFragment(br)) {
				m_fragments.remove(br);
			}
		}
	}

	synchronized Set<BundleRevision> getFragments() {
		return new HashSet<>(m_fragments);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isEffective(BundleRequirement req) {
		String effective = req.getDirectives().get(Constants.EFFECTIVE_DIRECTIVE);
		return ((effective == null) || effective.equals(Constants.EFFECTIVE_RESOLVE));
	}

    /**
     * {@inheritDoc}
     */
	public synchronized SortedSet<BundleCapability> getCandidates(BundleRequirement req, boolean obeyMandatory) {
//		BundleRevision reqRevision = req.getRevision();
		SortedSet<BundleCapability> result = new TreeSet<>(new CandidateComparator());

		CapabilitySet capSet = m_capSets.get(req.getNamespace());
		if (capSet != null) {
			// Get the requirement's filter; if this is our own impl we
			// have a shortcut to get the already parsed filter, otherwise
			// we must parse it from the directive.
			SimpleFilter sf = null;
			if (req instanceof BundleRequirementImpl) {
				sf = ((BundleRequirementImpl) req).getFilter();
			} else {
				String filter = req.getDirectives().get(
						Constants.FILTER_DIRECTIVE);
				if (filter == null) {
					sf = new SimpleFilter(null, null, SimpleFilter.MATCH_ALL);
				} else {
					sf = SimpleFilter.parse(filter);
				}
			}

			// Find the matching candidates.
			Set<BundleCapability> matches = capSet.match(sf, obeyMandatory);
			for (BundleCapability cap : matches) {
				/* TODO: karl - is this correct?
				 * if (System.getSecurityManager() != null) {
					if (req.getNamespace().equals(
							BundleRevision.PACKAGE_NAMESPACE)
							&& (!((BundleProtectionDomain) ((BundleRevisionImpl) cap
									.getRevision()).getProtectionDomain())
									.impliesDirect(new PackagePermission(
											(String) cap
													.getAttributes()
													.get(BundleRevision.PACKAGE_NAMESPACE),
											PackagePermission.EXPORTONLY)) || !((reqRevision == null) || ((BundleProtectionDomain) reqRevision
									.getProtectionDomain())
									.impliesDirect(new PackagePermission(
											(String) cap
													.getAttributes()
													.get(BundleRevision.PACKAGE_NAMESPACE),
											cap.getRevision().getBundle(),
											PackagePermission.IMPORT))))) {
						if (reqRevision != cap.getRevision()) {
							continue;
						}
					} else if (req.getNamespace().equals(
							BundleRevision.BUNDLE_NAMESPACE)
							&& (!((BundleProtectionDomain) ((BundleRevisionImpl) cap
									.getRevision()).getProtectionDomain())
									.impliesDirect(new BundlePermission(cap
											.getRevision().getSymbolicName(),
											BundlePermission.PROVIDE)) || !((reqRevision == null) || ((BundleProtectionDomain) reqRevision
									.getProtectionDomain())
									.impliesDirect(new BundlePermission(
											reqRevision.getSymbolicName(),
											BundlePermission.REQUIRE))))) {
						continue;
					} else if (req.getNamespace().equals(
							BundleRevision.HOST_NAMESPACE)
							&& (!((BundleProtectionDomain) reqRevision
									.getProtectionDomain())
									.impliesDirect(new BundlePermission(
											reqRevision.getSymbolicName(),
											BundlePermission.FRAGMENT)) || !((BundleProtectionDomain) ((BundleRevisionImpl) cap
									.getRevision()).getProtectionDomain())
									.impliesDirect(new BundlePermission(cap
											.getRevision().getSymbolicName(),
											BundlePermission.HOST)))) {
						continue;
					}
				}*/

				if (req.getNamespace().equals(BundleRevision.HOST_NAMESPACE)
						&& (cap.getRevision().getWiring() != null)) {
					continue;
				}

				result.add(cap);
			}
		}

		// If we have resolver hooks, then we may need to filter our results
		// based on a whitelist and/or fine-grained candidate filtering.
		/*TODO: karl - is this correct?
		 * if (!result.isEmpty() && !m_hooks.isEmpty()) {
		 
			// It we have a whitelist, then first filter out candidates
			// from disallowed revisions.
			if (m_whitelist != null) {
				for (Iterator<BundleCapability> it = result.iterator(); it
						.hasNext();) {
					if (!m_whitelist.contains(it.next().getRevision())) {
						it.remove();
					}
				}
			}

			// Now give the hooks a chance to do fine-grained filtering.
			ShrinkableCollection<BundleCapability> shrinkable = new ShrinkableCollection<BundleCapability>(
					result);
			for (ResolverHook hook : m_hooks) {
				try {
					Felix.m_secureAction.invokeResolverHookMatches(hook, req,
							shrinkable);
				} catch (Throwable th) {
					m_logger.log(Logger.LOG_WARNING,
							"Resolver hook exception.", th);
				}
			}
		}*/

		return result;
	}

    /**
     * {@inheritDoc}
     */
	public void checkExecutionEnvironment(BundleRevision revision) throws ResolveException {
		String bundleExecEnvStr = ((VerifierBundleRevision) revision).getRequiredExecutionEnvironment();
		if (bundleExecEnvStr != null) {
			// If the bundle has specified an execution environment and the
			// framework has an execution environment specified, then we must
			// check for a match.
			if (!bundleExecEnvStr.equals("") && (m_fwkExecEnvStr != null) && (m_fwkExecEnvStr.length() > 0)) {
				StringTokenizer tokens = new StringTokenizer(bundleExecEnvStr, ",");

				boolean found = false;
				while (tokens.hasMoreTokens() && !found) {
					if (m_fwkExecEnvSet.contains(tokens.nextToken().trim())) {
						found = true;
					}
				}
				
				if (!found) {
					throw new ResolveException("Execution environment not supported: " + bundleExecEnvStr, revision, null);
				}
			}
		}
	}

    /**
     * {@inheritDoc}
     */
	public void checkNativeLibraries(BundleRevision revision) throws ResolveException {
		// Next, try to resolve any native code, since the revision is
		// not resolvable if its native code cannot be loaded.
		List<R4Library> libs = ((VerifierBundleRevision) revision).getDeclaredNativeLibraries();
		// If we have a zero-length native library array, then
		// this means no native library class could be selected
		// so we should fail to resolve.
		if ((libs != null) && libs.isEmpty()) {
			throw new ResolveException("No matching native libraries found.", revision, null);
		}
	}

	/**
	 * Updates the framework wide execution environment string and a cached Set
	 * of execution environment tokens from the comma delimited list specified
	 * by the system variable 'org.osgi.framework.executionenvironment'.
	 * 
	 * @param fwkExecEnvStr
	 *            Comma delimited string of provided execution environments
	 * @return the parsed set of execution environments
	 **/
	private static Set<String> parseExecutionEnvironments(String fwkExecEnvStr) {
		Set<String> newSet = new HashSet<>();
		if (fwkExecEnvStr != null) {
			StringTokenizer tokens = new StringTokenizer(fwkExecEnvStr, ",");
			while (tokens.hasMoreTokens()) {
				newSet.add(tokens.nextToken().trim());
			}
		}
		return newSet;
	}

}
