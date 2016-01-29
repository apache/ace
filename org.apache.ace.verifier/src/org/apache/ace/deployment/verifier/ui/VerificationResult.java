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

package org.apache.ace.deployment.verifier.ui;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ace.deployment.verifier.VerifierService.VerifyEnvironment;
import org.osgi.framework.BundleException;
import org.osgi.framework.wiring.BundleRevision;

/**
 * Provides the results of a verification.
 */
final class VerificationResult {
    private final Set<String> m_customizers = new HashSet<>();
    private final Set<String> m_processors = new HashSet<>();
    private final Set<BundleRevision> m_bundles = new HashSet<>();
    private final ByteArrayOutputStream m_output = new ByteArrayOutputStream();
    
    final PrintStream m_out = new PrintStream(m_output);

    public void addBundle(VerifyEnvironment env, Map<String, String> manifest) throws BundleException {
        m_bundles.add(env.addBundle(m_bundles.size(), manifest));
    }

    public void addCustomizer(String customizer) {
        m_customizers.add(customizer.trim());
    }

    public void addProcessor(String processor) {
        m_processors.add(processor.trim());
    }

    public boolean allCustomizerMatch() {
        return m_customizers.containsAll(m_processors);
    }

    /**
     * @return the bundles
     */
    public Set<BundleRevision> getBundles() {
        return m_bundles;
    }
    
    /**
     * @return the customizers
     */
    public Set<String> getCustomizers() {
        return m_customizers;
    }
    
    /**
     * @return the processors
     */
    public Set<String> getProcessors() {
        return m_processors;
    }
    
    public boolean hasCustomizers() {
        return !m_customizers.isEmpty() || !m_processors.isEmpty();
    }
    
    @Override
    public String toString() {
        return m_output.toString();
    }
}