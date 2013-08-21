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
package org.apache.ace.agent.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.SortedSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.RetryAfterException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.util.tracker.ServiceTracker;

public class AgentUpdateHandlerImpl extends UpdateHandlerBase implements AgentUpdateHandler {

    private static final int TIMEOUT = 15000;
    private static final String UPDATER_VERSION = "1.0.0";
    private static final String UPDATER_SYMBOLICNAME = "org.apache.ace.agent.updater";

    private BundleContext m_bundleContext;

    public AgentUpdateHandlerImpl(BundleContext bundleContext) {
        m_bundleContext = bundleContext;
    }

    @Override
    public void onStart() throws Exception {
        // at this point we know the agent has started, so any updater bundle that
        // might still be running can be uninstalled
        uninstallUpdaterBundle();
    }

    private void uninstallUpdaterBundle() throws BundleException {
        for (Bundle b : m_bundleContext.getBundles()) {
            if (UPDATER_SYMBOLICNAME.equals(b.getSymbolicName())) {
                try {
                    b.uninstall();
                }
                catch (BundleException e) {
                    b.stop();
                    throw e;
                }
            }
        }
    }

    @Override
    public Version getInstalledVersion() {
        return m_bundleContext.getBundle().getVersion();
    }

    @Override
    public SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException {
        return getAvailableVersions(getEndpoint(getServerURL(), getIdentification()));
    }

    @Override
    public InputStream getInputStream(Version version) throws RetryAfterException, IOException {
        return getInputStream(getEndpoint(getServerURL(), getIdentification(), version));
    }

    @Override
    public DownloadHandle getDownloadHandle(Version version) throws RetryAfterException, IOException {
        return getDownloadHandle(getEndpoint(getServerURL(), getIdentification(), version));
    }

    @Override
    public long getSize(Version version) throws RetryAfterException, IOException {
        return getPackageSize(getEndpoint(getServerURL(), getIdentification(), version));
    }

    @Override
    public void install(InputStream stream) throws IOException {
        try {
            InputStream currentBundleVersion = getInputStream(m_bundleContext.getBundle().getVersion());
            Bundle bundle = m_bundleContext.installBundle("agent-updater", generateBundle());
            bundle.start();
            ServiceTracker st = new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(" + Constants.OBJECTCLASS + "=org.apache.ace.agent.updater.Activator)"), null);
            st.open(true);
            Object service = st.waitForService(TIMEOUT);
            if (service != null) {
                Method method = service.getClass().getMethod("update", Bundle.class, InputStream.class, InputStream.class);
                System.out.println("Method: " + method);
                try {
                    method.invoke(service, m_bundleContext.getBundle(), currentBundleVersion, stream);
                }
                catch (InvocationTargetException e) {
                    bundle.uninstall();
                }
                finally {
                    st.close();
                }
            }
            else {
                throw new IOException("No update service found after launching temporary bundle.");
            }
        }
        catch (Exception e) {
            throw new IOException("Could not update management agent.", e);
        }
    }

    /** Generates an input stream that contains a complete bundle containing our update code for the agent. */
    private InputStream generateBundle() throws IOException {
        InputStream is = null;
        JarOutputStream jos = null;
        ByteArrayOutputStream baos;
        try {
            baos = new ByteArrayOutputStream();
            Manifest manifest = new Manifest();
            Attributes main = manifest.getMainAttributes();
            main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            main.put(new Attributes.Name("Bundle-ManifestVersion"), "2");
            main.put(new Attributes.Name("Bundle-SymbolicName"), UPDATER_SYMBOLICNAME);
            main.put(new Attributes.Name("Bundle-Version"), UPDATER_VERSION);
            main.put(new Attributes.Name("Import-Package"), "org.osgi.framework");
            main.put(new Attributes.Name("Bundle-Activator"), "org.apache.ace.agent.updater.Activator");
            jos = new JarOutputStream(baos, manifest);
            jos.putNextEntry(new JarEntry("org/apache/ace/agent/updater/Activator.class"));
            is = getClass().getResourceAsStream("/org/apache/ace/agent/updater/Activator.class");
            byte[] buffer = new byte[1024];
            int bytes;
            while ((bytes = is.read(buffer)) != -1) {
                jos.write(buffer, 0, bytes);
            }
            jos.closeEntry();
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                }
                catch (IOException e) {
                }
            }
            if (jos != null) {
                try {
                    jos.close();
                }
                catch (IOException e) {
                }
            }
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        return bais;
    }

    private URL getEndpoint(URL serverURL, String identification) {
        return getEndpoint(serverURL, identification, null);
    }

    private URL getEndpoint(URL serverURL, String identification, Version version) {
        try {
            return new URL(serverURL, "agent/" + identification + "/" + m_bundleContext.getBundle().getSymbolicName() + "/versions/" + (version == null ? "" : version.toString()));
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }
}
