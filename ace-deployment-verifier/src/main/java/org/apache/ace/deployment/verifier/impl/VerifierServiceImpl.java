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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.ace.deployment.verifier.VerifierService;
import org.apache.felix.framework.Logger;
import org.apache.felix.framework.resolver.Resolver;
import org.apache.felix.framework.resolver.ResolverImpl;
import org.apache.felix.framework.resolver.ResolverWire;
import org.apache.felix.framework.util.MapToDictionary;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.service.log.LogEntry;

public class VerifierServiceImpl implements VerifierService {
	public VerifyEnvironment createEnvironment(Map<String, String> config, VerifyReporter reporter) {
		return new VerifyEnvironmentImpl(config, reporter);
	}

	private static final class VerifyEnvironmentImpl implements
			VerifierService.VerifyEnvironment {
		final Logger m_log = new Logger();
		final VerifyReporter m_reporter;
		final Map<String, String> m_config;
		private final Map<Long, VerifierBundleRevision> m_bundles = new HashMap<Long, VerifierBundleRevision>();

		public VerifyEnvironmentImpl(Map<String, String> config,
				VerifyReporter reporter) {
			m_config = config;
			if (reporter == null) {
				m_reporter = new VerifyReporter() {

					public void reportWire(BundleRevision importer,
							BundleRequirement reqirement,
							BundleRevision exporter, BundleCapability capability) {
						// TODO Auto-generated method stub

					}

					public void reportLog(LogEntry entry) {
						// TODO Auto-generated method stub

					}

					public void reportException(Exception ex) {
						// TODO Auto-generated method stub

					}
				};
			} else {
				m_reporter = reporter;
			}
			m_log.setLogger(new Object() {
				@SuppressWarnings("unused")
				public void log(final ServiceReference ref, final int level,
						final String message, final Throwable t) {
					final long time = System.currentTimeMillis();
					m_reporter.reportLog(new LogEntry() {

						public long getTime() {
							return time;
						}

						public ServiceReference getServiceReference() {
							return ref;
						}

						public String getMessage() {
							return message;
						}

						public int getLevel() {
							return level;
						}

						public Throwable getException() {
							return t;
						}

						public Bundle getBundle() {
							return null;
						}
					});
				}
			});
			m_log.setLogLevel(Logger.LOG_DEBUG);
		}

		public boolean verifyResolve(Set<BundleRevision> mandatory,
				Set<BundleRevision> optional,
				Set<BundleRevision> ondemandFragments) {

			VerifierResolverState state = new VerifierResolverState(
					this.m_config.get(Constants.FRAMEWORK_EXECUTIONENVIRONMENT));
			for (VerifierBundleRevision rev : m_bundles.values()) {
				state.addRevision(rev);
			}
			Resolver resolver = new ResolverImpl(m_log);
			try {
				Map<BundleRevision, List<ResolverWire>> result = resolver
						.resolve(
								state,
								(mandatory == null) ? new HashSet<BundleRevision>()
										: mandatory,
								(optional == null) ? new HashSet<BundleRevision>()
										: optional,
								(ondemandFragments == null) ? new HashSet<BundleRevision>()
										: ondemandFragments);
				for (Entry<BundleRevision, List<ResolverWire>> entry : result
						.entrySet()) {
					for (ResolverWire wire : entry.getValue()) {
						m_reporter.reportWire(wire.getRequirer(),
								wire.getRequirement(), wire.getProvider(),
								wire.getCapability());
					}
				}
			} catch (Exception ex) {
				m_reporter.reportException(ex);
				return false;
			}
			return true;
		}

		public BundleRevision addBundle(final long id,
				final Map<String, String> manifest) throws BundleException {
			if (m_bundles.containsKey(id)) {
				throw new BundleException("Bundle already exists for id: " + id);
			}
			VerifierBundleRevision rev = null;
			m_bundles.put(id, (rev = new VerifierBundleRevision(m_log,
					new Bundle() {

						public int compareTo(Bundle o) {
							return (int) (o.getBundleId() - getBundleId());
						}

						public int getState() {
							return Bundle.INSTALLED;
						}

						public void start(int options) throws BundleException {
							// TODO Auto-generated method stub

						}

						public void start() throws BundleException {
							// TODO Auto-generated method stub

						}

						public void stop(int options) throws BundleException {
							// TODO Auto-generated method stub

						}

						public void stop() throws BundleException {
							// TODO Auto-generated method stub

						}

						public void update(InputStream input)
								throws BundleException {
							// TODO Auto-generated method stub

						}

						public void update() throws BundleException {
							// TODO Auto-generated method stub

						}

						public void uninstall() throws BundleException {
							// TODO Auto-generated method stub

						}

						public Dictionary<String, String> getHeaders() {
							return new MapToDictionary(manifest);
						}

						public long getBundleId() {
							return id;
						}

						public String getLocation() {
							// TODO Auto-generated method stub
							return null;
						}

						public ServiceReference<?>[] getRegisteredServices() {
							// TODO Auto-generated method stub
							return null;
						}

						public ServiceReference<?>[] getServicesInUse() {
							// TODO Auto-generated method stub
							return null;
						}

						public boolean hasPermission(Object permission) {
							// TODO Auto-generated method stub
							return false;
						}

						public URL getResource(String name) {
							// TODO Auto-generated method stub
							return null;
						}

						public Dictionary<String, String> getHeaders(
								String locale) {
							return getHeaders();
						}

						public String getSymbolicName() {
							// TODO Auto-generated method stub
							return null;
						}

						public Class<?> loadClass(String name)
								throws ClassNotFoundException {
							// TODO Auto-generated method stub
							return null;
						}

						public Enumeration<URL> getResources(String name)
								throws IOException {
							// TODO Auto-generated method stub
							return null;
						}

						public Enumeration<String> getEntryPaths(String path) {
							// TODO Auto-generated method stub
							return null;
						}

						public URL getEntry(String path) {
							// TODO Auto-generated method stub
							return null;
						}

						public long getLastModified() {
							// TODO Auto-generated method stub
							return 0;
						}

						public Enumeration<URL> findEntries(String path,
								String filePattern, boolean recurse) {
							// TODO Auto-generated method stub
							return null;
						}

						public BundleContext getBundleContext() {
							// TODO Auto-generated method stub
							return null;
						}

						public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(
								int signersType) {
							// TODO Auto-generated method stub
							return null;
						}

						public Version getVersion() {
							// TODO Auto-generated method stub
							return null;
						}

						public <A> A adapt(Class<A> type) {
							// TODO Auto-generated method stub
							return null;
						}

						public File getDataFile(String filename) {
							// TODO Auto-generated method stub
							return null;
						}

					}, m_config, manifest)));
			return rev;
		}

	}
}
