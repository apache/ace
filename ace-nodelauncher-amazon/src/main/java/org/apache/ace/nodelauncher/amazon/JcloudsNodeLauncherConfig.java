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
package org.apache.ace.nodelauncher.amazon;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import org.apache.ace.nodelauncher.NodeLauncherConfig;
import org.jclouds.aws.ec2.reference.AWSEC2Constants;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.sshj.config.SshjSshClientModule;

import java.net.URL;
import java.util.Properties;
import java.util.Set;

public class JcloudsNodeLauncherConfig implements NodeLauncherConfig {
    private ComputeServiceContext m_computeServiceContext;
    
    private URL m_server;
    private String m_hardwareId;
    private String m_ImageId;
    private String m_ImageOwnerId;
    private String m_location;
    private String m_tagPrefix;
    private String[] m_extraPorts;
    private boolean m_runAsRoot;
    private String m_keyPair;
    private String m_privateKeyFile;
    private String m_vmOptions;
    private String m_launcherArguments;

    private String m_accessKeyId;
    private String m_secretAccessKey;
    
    private String m_nodeBootstrap;
    private String m_aceLauncher;
    private String m_additionalObrDownloads;
    private String m_externalDownloadUrls;
    private String m_sshUser;

    public URL getServer() {
        return m_server;
    }

    public JcloudsNodeLauncherConfig setServer(URL server) {
        m_server = server;
        return this;
    }

    public String getHardwareId() {
        return m_hardwareId;
    }

    public JcloudsNodeLauncherConfig setHardwareId(String hardwareId) {
        m_hardwareId = hardwareId;
        return this;
    }

    public String getImageId() {
        return m_ImageId;
    }

    public JcloudsNodeLauncherConfig setImageId(String m_ImageId) {
        this.m_ImageId = m_ImageId;
        return this;
    }

    public String getImageOwnerId() {
        return m_ImageOwnerId;
    }

    public String getTagPrefix() {
        return m_tagPrefix;
    }

    public JcloudsNodeLauncherConfig setTagPrefix(String tagPrefix) {
        this.m_tagPrefix = tagPrefix;
        return this;
    }

    public String getLocation() {
        return m_location;
    }

    public JcloudsNodeLauncherConfig setLocation(String location) {
        m_location = location;
        return this;
    }

    public String[] getExtraPorts() {
        return m_extraPorts;
    }

    public JcloudsNodeLauncherConfig setExtraPorts(String... extraPorts) {
        m_extraPorts = extraPorts;
        return this;
    }

    public boolean isRunAsRoot() {
        return m_runAsRoot;
    }

    public JcloudsNodeLauncherConfig setRunAsRoot(boolean runAsRoot) {
        m_runAsRoot = runAsRoot;
        return this;
    }

    public String getKeyPair() {
        return m_keyPair;
    }

    public JcloudsNodeLauncherConfig setKeyPair(String keyPair) {
        m_keyPair = keyPair;
        return this;
    }

    public String getPrivateKeyFile() {
        return m_privateKeyFile;
    }

    public JcloudsNodeLauncherConfig setPrivateKeyFile(String privateKeyFile) {
        m_privateKeyFile = privateKeyFile;
        return this;
    }

    public String getVmOptions() {
        return m_vmOptions;
    }

    public JcloudsNodeLauncherConfig setVmOptions(String vmOptions) {
        m_vmOptions = vmOptions;
        return this;
    }

    public String getLauncherArguments() {
        return m_launcherArguments;
    }

    public JcloudsNodeLauncherConfig setLauncherArguments(String launcherArguments) {
        m_launcherArguments = launcherArguments;
        return this;
    }

    public Set<? extends Hardware> listHardwareIds() {
        return m_computeServiceContext.getComputeService().listHardwareProfiles();
    }

    public Set<? extends Image> listImages() {
        return m_computeServiceContext.getComputeService().listImages();
    }

    public JcloudsNodeLauncherConfig setImageOwnerId(String imageOwnerId) {
        m_ImageOwnerId = imageOwnerId;
        createComputeServiceContext();

        return this;
    }

    public String getAccessKeyId() {
        return m_accessKeyId;
    }

    public JcloudsNodeLauncherConfig setAccessKeyId(String accessKeyId) {
        m_accessKeyId = accessKeyId;
        return this;
    }

    public String getSecretAccessKey() {
        return m_secretAccessKey;
    }

    public JcloudsNodeLauncherConfig setSecretAccessKey(String secretAccessKey) {
        m_secretAccessKey = secretAccessKey;
        return this;
    }

    public String getNodeBootstrap() {
        return m_nodeBootstrap;
    }

    public JcloudsNodeLauncherConfig setNodeBootstrap(String nodeBootstrap) {
        m_nodeBootstrap = nodeBootstrap;
        return this;
    }

    public String getAceLauncher() {
        return m_aceLauncher;
    }

    public JcloudsNodeLauncherConfig setAceLauncher(String aceLauncher) {
        m_aceLauncher = aceLauncher;
        return this;
    }

    public String getAdditionalObrDownloads() {
        return m_additionalObrDownloads;
    }

    public JcloudsNodeLauncherConfig setAdditionalObrDownloads(String additionalObrDownloads) {
        m_additionalObrDownloads = additionalObrDownloads;
        return this;
    }

    public String getExternalDownloadUrls() {
        return m_externalDownloadUrls;
    }

    public JcloudsNodeLauncherConfig setExternalDownloadUrls(String externalDownloadUrls) {
        m_externalDownloadUrls = externalDownloadUrls;
        return this;
    }

    public String getSshUser() {
        return m_sshUser;
    }

    public JcloudsNodeLauncherConfig setSshUser(String sshUser) {
        this.m_sshUser = sshUser;
        return this;
    }

    /**
     * Recreate the ComputeServiceContext. This is required after setting some properties to have an effect (e.g. changing ImageOwnerId).
     * This is an expensive operation, only call when required.
     */
    public void createComputeServiceContext() {
        if (m_computeServiceContext != null) {
            m_computeServiceContext.close();
        }

        Properties props = new Properties();
        if (m_ImageOwnerId != null && m_ImageOwnerId.length() > 0) {
            props.setProperty(AWSEC2Constants.PROPERTY_EC2_AMI_QUERY, "owner-id=" + m_ImageOwnerId + ";state=available;image-type=machine;root-device-type=ebs");
            props.setProperty(AWSEC2Constants.PROPERTY_EC2_CC_AMI_QUERY, "");
        }

        m_computeServiceContext = new ComputeServiceContextFactory().createContext("aws-ec2", m_accessKeyId, m_secretAccessKey, ImmutableSet.<Module>of(new SshjSshClientModule()), props);
    }

    public ComputeService getComputeService() {
        return m_computeServiceContext.getComputeService();
    }

    public void close() {
        if(m_computeServiceContext != null) {
            m_computeServiceContext.close();
        }
    }
}
