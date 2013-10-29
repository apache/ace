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

import static org.jclouds.compute.predicates.NodePredicates.runningInGroup;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Dictionary;
import java.util.Properties;
import java.util.Set;

import org.apache.ace.nodelauncher.NodeLauncher;
import org.apache.ace.nodelauncher.NodeLauncherConfig;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.jclouds.ec2.domain.InstanceType;
import org.jclouds.scriptbuilder.domain.Statements;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import com.google.common.io.Files;
/**
 * Simple NodeLauncher implementation that launches nodes based on a given AMI in Amazon EC2.
 * We expect the AMI we launch to have a java on its path, at least after bootstrap.<br><br>
 * <p/>
 * This service is configured using Config Admin; see the constants in the class for more information
 * about this.<br><br>
 * <p/>
 * After the node has been started up, this service will install a management agent on it. For this
 * to work, there should be an ace-launcher in the OBR of the server the node should connect to.
 */
public class AmazonNodeLauncher implements NodeLauncher, ManagedService {
    public static final String PID = "org.apache.ace.nodelauncher.amazon";

    /**
     * Configuration key: The ACE server the newly started nodes should connect to.
     */
    public static final String SERVER = "server";

    /**
     * Configuration key: The ID of the AMI to use. Note that this AMI should be available
     * in the location ("availability zone") you configure.
     */
    public static final String AMI_ID = "amiId";

    /**
     * Configuration key: The ID of the AMI owner to use. You need this when you want to use your own AMIs.
     */
    public static final String AMI_OWNER_ID = "amiOwnerId";

    /**
     * Configuration key: The location where the node should be started; this is an Amazon "availability zone",
     * something like "eu-west-1".
     */
    public static final String LOCATION = "location";

    /**
     * Configuration key: the Amazon access key ID.
     */
    public static final String ACCESS_KEY_ID = "accessKeyid";

    /**
     * Configuration key: The secret key that goes with your access key.
     */
    public static final String SECRET_ACCESS_KEY = "secretAccessKey";

    /**
     * Configuration key: The (optional) name of an existing keypair to use when creating a new node. If you
     * do not specify it, a new keypair will be created. Specifying an existing keypair makes it easier to
     * for example log into each node with SSH using your existing keypair.
     */
    public static final String KEYPAIR = "keypair";

    /**
     * Configuration key: A prefix to use for all nodes launched by this service. You can use this (a) allow
     * multiple nodes with the same ID, but launcher from different NodeLauncher services, or (b) to more
     * easily identify your nodes in the AWS management console.
     */
    public static final String TAG_PREFIX = "tagPrefix";

    /**
     * Configuration key: A piece of shell script that is run <em>before</em> the management agent is started.
     */
    public static final String NODE_BOOTSTRAP = "nodeBootstrap";

    /**
     * Configuration key: A set of VM options to pass to the JVM when starting the management agent, as a single string.
     */
    public static final String VM_OPTIONS = "vmOptions";

    /**
     * Configuration key: Any command line arguments you want to pass to the launcher; see the ace-launcher for
     * the possible options.
     */
    public static final String LAUNCHER_ARGUMENTS = "launcherArguments";

    /**
     * Configuration key: Extra ports to open on the nodes, besides the default ones (see DEFAULT_PORTS).
     */
    public static final String EXTRA_PORTS = "extraPorts";

    /**
     * Configuration key: Should we run the process as root?
     */
    public static final String RUN_AS_ROOT = "runAsRoot";

    /**
     * Configuration key: The hardware ID to use for the node.
     */
    public static final String HARDWARE_ID = "hardwareId";

    /**
     * Default set of ports to open on a node.
     */
    public static final int[] DEFAULT_PORTS = new int[]{22, 80, 8080};

    /**
     * Configuration key: The (optional) name of the JAR to launch to
     * bootstrap the OSGi framework (or whatever you want to run on the
     * node).
     */
    public static final String ACE_LAUNCHER = "aceLauncher";

    /**
     * Configuration key: An additional list of artifacts that must be
     * downloaded from the OBR when bootstrapping.
     */
    public static final String ADDITIONAL_OBR_DOWNLOADS = "additionalObrDownloads";

    /**
     * Configuration key: An additional list of URLs that must be downloaded when
     * bootstrapping.
     */
    public static final String EXTERNAL_DOWNLOAD_URLS = "externalDownloadUrls";

    /**
     * Configuration key: The (optional) ssh user to use when connecting to the node. Uses jclouds defaults by default,
     * which is root and ec2-user.
     */
    public static final String SSH_USER = "sshUser";

    /**
     * Configuration key: The (optional) private key file, which you must install on
     * the ACE server locally if you want it to be used when creating new nodes.
     */
    private static final String PRIVATE_KEY_FILE = "privateKeyFile";

    /**
     * Default configuration object. Properties are read from the nodelauncher configuration file.
     */
    private JcloudsNodeLauncherConfig m_defaultNodeConfig;

    /**
     * Current configuration object. This reflects settings done from the UI for example.
     * This instance is set when a node is started.
     */
    private JcloudsNodeLauncherConfig m_currentConfig;


    public void start(String id) throws Exception {

        start(id, m_defaultNodeConfig);
    }

    public void start(String id, NodeLauncherConfig cfg) throws Exception {
        JcloudsNodeLauncherConfig config = (JcloudsNodeLauncherConfig) cfg;
        m_currentConfig = config;

        ComputeService computeService = config.getComputeService();
        TemplateBuilder template = computeService.templateBuilder()
                .imageId(config.getImageId())
                .hardwareId(config.getHardwareId())
                .locationId(config.getLocation());

        int[] extraPorts = parseExtraPorts(config.getExtraPorts());
        int[] inboundPorts = mergePorts(DEFAULT_PORTS, extraPorts);

        TemplateOptions options = new EC2TemplateOptions()
                .as(EC2TemplateOptions.class).inboundPorts(inboundPorts)
                .blockOnComplete(false)
                .runAsRoot(config.isRunAsRoot());

        if (useConfiguredKeyPair(config)) {
            options.as(EC2TemplateOptions.class).keyPair(config.getKeyPair());
        }

        template.options(options);

        Set<? extends NodeMetadata> tag = computeService.createNodesInGroup(config.getTagPrefix() + id, 1, template.build());
        if (!useConfiguredPrivateKey(config)) {
            System.out.println("In case you need it, this is the key to ssh to " + id + ":\n" + tag.iterator().next().getCredentials().credential);
        }

        LoginCredentials.Builder loginBuilder = LoginCredentials.builder();

        if (config.getSshUser() != null && config.getSshUser().length() > 0) {
            loginBuilder.user(config.getSshUser());
        } else {
            loginBuilder.user("ec2-user");
        }

        if (useConfiguredPrivateKey(config)) {
            loginBuilder.privateKey(Files.toString(new File(config.getPrivateKeyFile()), Charset.defaultCharset()));
        }

        computeService.runScriptOnNodesMatching(runningInGroup(config.getTagPrefix() + id),
                Statements.exec(buildStartupScript(id, config)),
                RunScriptOptions.Builder.blockOnComplete(false).overrideLoginCredentials(loginBuilder.build()));
    }

    private boolean useConfiguredPrivateKey(JcloudsNodeLauncherConfig config) {
        return config.getPrivateKeyFile() != null && config.getPrivateKeyFile().length() > 0;
    }

    private boolean useConfiguredKeyPair(JcloudsNodeLauncherConfig config) {
        return config.getKeyPair() != null && config.getKeyPair().length() > 0;
    }

    int[] mergePorts(int[] first, int[] last) {
        int[] result = new int[first.length + last.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (i < first.length) ? first[i] : last[i - first.length];
        }
        return result;
    }

    int[] parseExtraPorts(String[] extraPorts) {
        if(extraPorts == null || extraPorts.length == 0) {
            return new int[0];
        }

        int[] result = new int[extraPorts.length];

        for (int i = 0; i < extraPorts.length; i++) {
            result[i] = Integer.parseInt(extraPorts[i].trim());
        }

        return result;
    }

    private String buildStartupScript(String id, JcloudsNodeLauncherConfig config) throws MalformedURLException {
        StringBuilder script = new StringBuilder("cd ~; ");
        if (config.getNodeBootstrap() != null && config.getNodeBootstrap().length() > 0) {
            script.append(config.getNodeBootstrap()).append(" ; ");
        }

        script.append("wget ").append(new URL(config.getServer(), "/obr/" + config.getAceLauncher())).append(" ;");
        if (config.getAdditionalObrDownloads().length() > 0) {
            for (String additonalDownload : config.getAdditionalObrDownloads().split(",")) {
                script.append("wget ").append(new URL(config.getServer(), "/obr/" + additonalDownload.trim())).append(" ;");
            }
        }

        if (config.getExternalDownloadUrls().length() > 0) {
            for (String additonalDownload : config.getExternalDownloadUrls().split(",")) {
                script.append("wget ").append(additonalDownload.trim()).append(" ;");
            }
        }
        script.append("nohup java -jar ").append(config.getAceLauncher()).append(" ");
        script.append("discovery=").append(config.getServer().toExternalForm()).append(" ");
        script.append("identification=").append(id).append(" ");
        script.append(config.getVmOptions()).append(" ");
        script.append(config.getLauncherArguments());
        return script.toString();
    }

    public void stop(String id) {

        getActiveConfig().getComputeService().destroyNodesMatching(runningInGroup(getActiveConfig().getTagPrefix() + id));
    }

    public Properties getProperties(String id) throws Exception {
        Properties result = new Properties();

        JcloudsNodeLauncherConfig config = getActiveConfig();
        NodeMetadata nodeMetadata = getNodeMetadataForRunningNodeWithTag(config.getTagPrefix() + id, config);
        if (nodeMetadata == null) {
            return null;
        }
        result.put("id", id);
        result.put("node-id", nodeMetadata.getId());
        result.put("ip", nodeMetadata.getPublicAddresses().iterator().next());

        return result;
    }

    private JcloudsNodeLauncherConfig getActiveConfig() {
        return m_currentConfig != null ? m_currentConfig : m_defaultNodeConfig;
    }

    private NodeMetadata getNodeMetadataForRunningNodeWithTag(String tag, JcloudsNodeLauncherConfig config) {

        for (ComputeMetadata node : config.getComputeService().listNodes()) {
            NodeMetadata candidate = config.getComputeService().getNodeMetadata(node.getId());
            if (tag.equals(candidate.getGroup()) && candidate.getStatus().equals(NodeMetadata.Status.RUNNING)) {
                return candidate;
            }
        }
        return null;
    }

    public void updated(@SuppressWarnings("rawtypes") Dictionary properties) throws ConfigurationException {
        if (properties != null) {
            URL server;
            try {
                server = new URL(getConfigProperty(properties, SERVER));
            } catch (MalformedURLException e) {
                throw new ConfigurationException(SERVER, getConfigProperty(properties, SERVER) + " is not a valid URL.", e);
            }
            String amiId = getConfigProperty(properties, AMI_ID);
            String amiOwnerId = getConfigProperty(properties, AMI_OWNER_ID, "");
            String location = getConfigProperty(properties, LOCATION);
            String hardwareId = getConfigProperty(properties, HARDWARE_ID, InstanceType.C1_MEDIUM);
            String accessKeyId = getConfigProperty(properties, ACCESS_KEY_ID);
            String secretAccessKey = getConfigProperty(properties, SECRET_ACCESS_KEY);
            String keyPair = getConfigProperty(properties, KEYPAIR, "");
            String privateKeyFile = getConfigProperty(properties, PRIVATE_KEY_FILE, "");
            String vmOptions = getConfigProperty(properties, VM_OPTIONS, "");
            String nodeBootstrap = getConfigProperty(properties, NODE_BOOTSTRAP, "");
            String tagPrefix = getConfigProperty(properties, TAG_PREFIX, "");
            String launcherArguments = getConfigProperty(properties, LAUNCHER_ARGUMENTS, "");
            String extraPorts = getConfigProperty(properties, EXTRA_PORTS, "");
            String runAsRoot = getConfigProperty(properties, RUN_AS_ROOT, "false");
            String aceLauncher = getConfigProperty(properties, ACE_LAUNCHER, "ace-launcher.jar");
            String additionalObrDownloads = getConfigProperty(properties, ADDITIONAL_OBR_DOWNLOADS, "");
            String externalDownloadUrls = getConfigProperty(properties, EXTERNAL_DOWNLOAD_URLS, "");
            String sshUser = getConfigProperty(properties, SSH_USER, "ec2-user");

            m_defaultNodeConfig = new JcloudsNodeLauncherConfig()
                    .setAccessKeyId(accessKeyId)
                    .setSecretAccessKey(secretAccessKey)
                    .setServer(server)
                    .setImageId(amiId)
                    .setImageOwnerId(amiOwnerId)
                    .setLocation(location)
                    .setHardwareId(hardwareId)
                    .setKeyPair(keyPair)
                    .setPrivateKeyFile(privateKeyFile)
                    .setTagPrefix(tagPrefix)
                    .setVmOptions(vmOptions)
                    .setLauncherArguments(launcherArguments)
                    .setExtraPorts(extraPorts)
                    .setRunAsRoot(Boolean.parseBoolean(runAsRoot))
                    .setAccessKeyId(accessKeyId)
                    .setSecretAccessKey(secretAccessKey)
                    .setNodeBootstrap(nodeBootstrap)
                    .setAceLauncher(aceLauncher)
                    .setAdditionalObrDownloads(additionalObrDownloads)
                    .setExternalDownloadUrls(externalDownloadUrls)
                    .setSshUser(sshUser);

            m_defaultNodeConfig.createComputeServiceContext();
        }
    }


    private String getConfigProperty(@SuppressWarnings("rawtypes") Dictionary settings, String id) throws ConfigurationException {
        return getConfigProperty(settings, id, null);
    }

    private String getConfigProperty(@SuppressWarnings("rawtypes") Dictionary settings, String id, String defaultValue) throws ConfigurationException {
        String result = (String) settings.get(id);
        if (result == null) {
            if (defaultValue == null) {
                throw new ConfigurationException(id, "key missing");
            } else {
                return defaultValue;
            }
        }
        return result;
    }

    public void stop() {
        if (m_currentConfig != null) {
            m_currentConfig.close();
        }
    }

    public NodeLauncherConfig getDefaultConfig() {
        return m_defaultNodeConfig;
    }

    public JcloudsNodeLauncherConfig getCurrentConfig() {
        return m_currentConfig;
    }
}
