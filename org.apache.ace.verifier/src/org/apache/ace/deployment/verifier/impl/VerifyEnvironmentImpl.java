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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.ace.deployment.verifier.VerifierService;
import org.apache.ace.deployment.verifier.VerifierService.VerifyEnvironment;
import org.apache.ace.deployment.verifier.VerifierService.VerifyReporter;
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

/**
 * Provides a {@link VerifyEnvironment} implementation.
 */
public class VerifyEnvironmentImpl implements VerifierService.VerifyEnvironment {

    /**
     * Provides a dummy bundle implementation.
     */
    private static final class DummyBundle implements Bundle {
        private final Map<String, String> m_manifest;
        private final long m_id;

        /**
         * @param id
         * @param manifest
         */
        private DummyBundle(long id, Map<String, String> manifest) {
            m_id = id;
            m_manifest = manifest;
        }

        public <A> A adapt(Class<A> type) {
            return null;
        }

        public int compareTo(Bundle o) {
            return (int) (o.getBundleId() - getBundleId());
        }

        public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
            return null;
        }

        public BundleContext getBundleContext() {
            return null;
        }

        public long getBundleId() {
            return m_id;
        }

        public File getDataFile(String filename) {
            return null;
        }

        public URL getEntry(String path) {
            return null;
        }

        public Enumeration<String> getEntryPaths(String path) {
            return null;
        }

        @SuppressWarnings("unchecked")
        public Dictionary<String, String> getHeaders() {
            return new MapToDictionary(m_manifest);
        }

        public Dictionary<String, String> getHeaders(String locale) {
            return getHeaders();
        }

        public long getLastModified() {
            return 0;
        }

        public String getLocation() {
            return null;
        }

        public ServiceReference<?>[] getRegisteredServices() {
            return null;
        }

        public URL getResource(String name) {
            return null;
        }

        public Enumeration<URL> getResources(String name) throws IOException {
            return null;
        }

        public ServiceReference<?>[] getServicesInUse() {
            return null;
        }

        public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
            return null;
        }

        public int getState() {
            return Bundle.INSTALLED;
        }

        public String getSymbolicName() {
            return null;
        }

        public Version getVersion() {
            return null;
        }

        public boolean hasPermission(Object permission) {
            return false;
        }

        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return null;
        }

        public void start() throws BundleException {
            // Nop
        }

        public void start(int options) throws BundleException {
            // Nop
        }

        public void stop() throws BundleException {
            // Nop
        }

        public void stop(int options) throws BundleException {
            // Nop
        }

        public void uninstall() throws BundleException {
            // Nop
        }

        public void update() throws BundleException {
            // Nop
        }

        public void update(InputStream input) throws BundleException {
            // Nop
        }
    }

    /**
     * Provides a dummy {@link VerifyReporter} implementation that does nothing.
     */
    private static final class DummyReporter implements VerifyReporter {
        public void reportException(Exception ex) {
            // Nop
        }

        public void reportLog(LogEntry entry) {
            // Nop
        }

        public void reportWire(BundleRevision importer, BundleRequirement reqirement, BundleRevision exporter,
            BundleCapability capability) {
            // Nop
        }
    }

    /**
     * Provides a logger implementation for intercepting all framework logging calls.
     */
    private static final class FrameworkLogger {
        private final VerifyReporter m_reporter;
        
        /**
         * Creates a new {@link FrameworkLogger} instance.
         * 
         * @param reporter the {@link VerifyReporter} to route all logging to, cannot be <code>null</code>.
         */
        public FrameworkLogger(VerifyReporter reporter) {
            m_reporter = reporter;
        }
        
        @SuppressWarnings("unused")
        public void log(final ServiceReference<?> ref, final int level, final String message, final Throwable t) {
            final long time = System.currentTimeMillis();

            m_reporter.reportLog(new LogEntry() {
                public Bundle getBundle() {
                    return null;
                }

                public Throwable getException() {
                    return t;
                }

                public int getLevel() {
                    return level;
                }

                public String getMessage() {
                    return message;
                }

                public ServiceReference<?> getServiceReference() {
                    return ref;
                }

                public long getTime() {
                    return time;
                }
            });
        }
    }

    private final Logger m_log;
    private final VerifyReporter m_reporter;
    private final Map<String, String> m_config;
    private final ConcurrentMap<Long, VerifierBundleRevision> m_bundles;

    /**
     * Creates a new {@link VerifyEnvironmentImpl} instance.
     * 
     * @param config the configuration to use, cannot be <code>null</code>;
     * @param reporter the {@link VerifyReporter} to use, can be <code>null</code> in which case a dummy reporter will be used.
     */
    public VerifyEnvironmentImpl(Map<String, String> config, VerifyReporter reporter) {

        m_config = config;
        m_reporter = (reporter == null) ? new DummyReporter() : reporter;
        m_bundles = new ConcurrentHashMap<>();
        
        m_log = new Logger();
        m_log.setLogger(new FrameworkLogger(m_reporter));
        m_log.setLogLevel(Logger.LOG_DEBUG);
    }

    /**
     * {@inheritDoc}
     */
    public BundleRevision addBundle(final long id, final Map<String, String> manifest) throws BundleException {
        VerifierBundleRevision rev = new VerifierBundleRevision(m_log, new DummyBundle(id, manifest), m_config, manifest);

        if (m_bundles.putIfAbsent(id, rev) != null) {
            throw new BundleException("Bundle already exists for id: " + id);
        }

        return rev;
    }

    /**
     * {@inheritDoc}
     */
    public boolean verifyResolve(Set<BundleRevision> mandatory, Set<BundleRevision> optional, Set<BundleRevision> ondemandFragments) {

        VerifierResolverState state = new VerifierResolverState(getFrameworkExecutionEnvironment());

        for (VerifierBundleRevision rev : m_bundles.values()) {
            state.addRevision(rev);
        }

        Resolver resolver = new ResolverImpl(m_log);

        try {
            Map<BundleRevision, List<ResolverWire>> result = resolver.resolve(
                state,
                (mandatory == null) ? new HashSet<BundleRevision>() : mandatory,
                (optional == null) ? new HashSet<BundleRevision>() : optional,
                (ondemandFragments == null) ? new HashSet<BundleRevision>() : ondemandFragments);

            for (Entry<BundleRevision, List<ResolverWire>> entry : result.entrySet()) {
                for (ResolverWire wire : entry.getValue()) {
                    m_reporter.reportWire(wire.getRequirer(), wire.getRequirement(), wire.getProvider(), wire.getCapability());
                }
            }
        }
        catch (Exception ex) {
            m_reporter.reportException(ex);
            return false;
        }

        return true;
    }

    /**
     * @return the framework execution environment, can be <code>null</code>.
     */
    @SuppressWarnings("deprecation")
    private String getFrameworkExecutionEnvironment() {
        return this.m_config.get(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);
    }

}