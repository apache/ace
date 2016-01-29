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
package org.apache.ace.deployment.streamgenerator.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.provider.OverloadedException;
import org.apache.ace.deployment.streamgenerator.StreamGenerator;

/**
 * Stream generator for deployment packages. Communicates with a data provider to get the meta data for the streams. Part of the
 * meta
 */
public class StreamGeneratorImpl implements StreamGenerator {
    private volatile DeploymentProvider m_provider;
    private volatile ConnectionFactory m_connectionFactory;

    @Override
    public InputStream getDeploymentPackage(String id, String version) throws OverloadedException, IOException {
        List<ArtifactData> data = m_provider.getBundleData(id, version);
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();

        main.putValue("Manifest-Version", "1.0");
        main.putValue("DeploymentPackage-SymbolicName", id);
        main.putValue("DeploymentPackage-Version", version);

        // Note: getEntries() returns a map. This means that the order of the entries
        // in the manifest is _not_ defined; this should be fine, as far as the
        // deployment admin spec goes.
        for (ArtifactData bd : data) {
            manifest.getEntries().put(bd.getFilename(), bd.getManifestAttributes(false));
        }

        return DeploymentPackageStream.createStreamForThread(m_connectionFactory, manifest, data.iterator(), false);
    }

    @Override
    public InputStream getDeploymentPackage(String id, String fromVersion, String toVersion) throws OverloadedException, IOException {
        //return execute(new WorkerFixPackage(id, fromVersion, toVersion));
        List<ArtifactData> data = m_provider.getBundleData(id, fromVersion, toVersion);
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();

        main.putValue("Manifest-Version", "1.0");
        main.putValue("DeploymentPackage-SymbolicName", id);
        main.putValue("DeploymentPackage-Version", toVersion);
        main.putValue("DeploymentPackage-FixPack", "[" + fromVersion + "," + toVersion + ")");

        for (ArtifactData bd : data) {
            manifest.getEntries().put(bd.getFilename(), bd.getManifestAttributes(true));
        }

        return DeploymentPackageStream.createStreamForThread(m_connectionFactory, manifest, data.iterator(), true);
    }

    private static final class DeploymentPackageStream extends InputStream {
        private byte[] m_readBuffer;
        private byte[] m_buffer;
        private JarOutputStream m_output;
        private Iterator<ArtifactData> m_iter;
        private InputStream m_current = null;
        private int m_pos = 0;
        private int m_max = 0;
        private boolean m_fixPack;
        
        private final OutputBuffer m_outputBuffer = new OutputBuffer(this);
        private final ConnectionFactory m_connectionFactory;

        private DeploymentPackageStream(ConnectionFactory connectionFactory) {
            this(connectionFactory, 64 * 1024);
        }

        private DeploymentPackageStream(ConnectionFactory connectionFactory, int bufferSize) {
            m_connectionFactory = connectionFactory;
            m_buffer = new byte[bufferSize];
            m_readBuffer = new byte[bufferSize];
        }

        private static final ThreadLocal<SoftReference<DeploymentPackageStream>> m_cache = new ThreadLocal<>();

        static DeploymentPackageStream createStreamForThread(ConnectionFactory connectionFactory, Manifest man, Iterator<ArtifactData> iter, boolean fixpack) throws IOException {
            SoftReference<DeploymentPackageStream> ref = m_cache.get();
            DeploymentPackageStream dps = null;
            if (ref != null) {
                dps = ref.get();
            }

            if (dps == null) {
                dps = new DeploymentPackageStream(connectionFactory);
                m_cache.set(new SoftReference<>(dps));
            }

            if (dps.isInUse()) {
                dps = new DeploymentPackageStream(connectionFactory);
            }

            dps.init(man, iter, fixpack);

            return dps;
        }

        private boolean isInUse() {
            return m_output == null;
        }

        private void init(Manifest man, Iterator<ArtifactData> iter, boolean fixPack) throws IOException {
            m_max = 0;
            m_pos = 0;
            m_output = new JarOutputStream(m_outputBuffer, man);
            m_output.flush();
            m_iter = iter;
            m_fixPack = fixPack;
            next();
        }

        private void next() throws IOException {
            ArtifactData current = (m_iter.hasNext()) ? m_iter.next() : null;

            if (current == null) {
                m_output.close();
            }
            else if (!m_fixPack || current.hasChanged()) {
                m_current = openStream(current);
                m_output.putNextEntry(new ZipEntry(current.getFilename()));
            }
            else {
                next();
            }
        }

        private InputStream openStream(ArtifactData data) throws IOException {
            URLConnection conn = m_connectionFactory.createConnection(data.getUrl());
            return conn.getInputStream();
        }

        @Override
        public int read() throws IOException {
            while (m_pos == m_max) {
                if (m_current == null) {
                    if (m_output != null) {
                        m_output.close();
                    }
                    m_output = null;
                    m_iter = null;
                    return -1;
                }
                m_pos = 0;
                m_max = 0;
                int len = m_current.read(m_readBuffer);
                if (len != -1) {
                    m_output.write(m_readBuffer, 0, len);
                    m_output.flush();
                }
                else {
                    try {
                        m_current.close();
                    }
                    catch (Exception ex) {
                        // Not much we can do
                    }
                    m_current = null;
                    m_output.closeEntry();
                    m_output.flush();
                    next();
                }
            }

            return m_buffer[m_pos++] & 0xFF;
        }

        void write(int b) {
            if (m_max == m_buffer.length) {
                byte[] tmp = new byte[m_buffer.length + 8192];
                System.arraycopy(m_buffer, 0, tmp, 0, m_buffer.length);
                m_buffer = tmp;
            }
            m_buffer[m_max++] = (byte) b;
        }

        @Override
        public void close() {
            if (m_output != null) {
                try {
                    m_output.close();
                    m_output = null;
                }
                catch (Exception ex) {
                    // Not much we can do
                }
            }
            if (m_current != null) {
                try {
                    m_current.close();
                    m_current = null;
                }
                catch (Exception ex) {
                    // Not much we can do
                }
            }
            m_iter = null;
        }

        private static final class OutputBuffer extends OutputStream {
            private final DeploymentPackageStream m_stream;

            public OutputBuffer(DeploymentPackageStream stream) {
                m_stream = stream;
            }

            @Override
            public void write(int b) {
                m_stream.write(b);
            }
        }
    }
}