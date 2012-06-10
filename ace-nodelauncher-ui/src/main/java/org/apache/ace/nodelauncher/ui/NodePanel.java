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
package org.apache.ace.nodelauncher.ui;

import com.vaadin.data.Property;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickListener;
import org.apache.ace.nodelauncher.amazon.JcloudsNodeLauncherConfig;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.osgi.service.log.LogService;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("serial")
public class NodePanel extends Panel {
    private final String m_targetId;
    private final Lock m_targetLock = new ReentrantLock();
    private final NodeStatusHandler m_handler = new NodeStatusHandler();
    private final NodeLauncherPanelFactory m_factory;
    private final Map<Action, Runnable> m_runners = new HashMap<Action, Runnable>();
    private final Runnable m_statusGetter = new StatusGetter();
    private TextField m_locationInput;
    private Select m_hardwareIdSelect;
    private TextField m_ImageOwnerIdInput;
    private Select m_ImageIdSelect;
    private TextField m_keyPairInput;
    private TextField m_tagPrefixInput;
    private TextField m_extraPortsInput;
    private CheckBox m_runAsRootCheckbox;
    private TextField m_privateKeyFileInput;
    private TextField m_vmOptionsInput;
    private TextField m_launcherArgumentsInput;
    private TextField m_nodeBootstrapInput;
    private TextField m_aceLauncherInput;
    private TextField m_additionalObrDownloadsInput;
    private TextField m_externalDownloadsInput;
    private TextField m_sshUserInput;

    private JcloudsNodeLauncherConfig config;

    {
        m_runners.put(Action.Start, new StartRunner());
        m_runners.put(Action.Stop, new StopRunner());
    }

    public NodePanel(NodeLauncherPanelFactory factory, String target) {
        m_factory = factory;
        m_targetId = target;
        config = (JcloudsNodeLauncherConfig)m_factory.getCloudService().getDefaultConfig();
        setContent();
        setCaption(m_targetId);
    }

    private void setContent() {
        VerticalLayout panel = new VerticalLayout();
        setContent(panel);
        panel.setMargin(true);
        panel.setSpacing(true);
        setWidth("100%");

        Label status = new NodeLabel(m_handler);

        Button startButton = new ActionButton(m_handler, Action.Start);
        Button stopButton = new ActionButton(m_handler, Action.Stop);
        HorizontalLayout buttonPanel = new HorizontalLayout();
        buttonPanel.addComponent(startButton);
        buttonPanel.addComponent(stopButton);
        buttonPanel.setSpacing(true);
        

        Table propertiesTable = new PropertiesTable(m_handler);
        propertiesTable.setWidth("100%");

        panel.addComponent(status);
        panel.addComponent(buttonPanel);

        m_locationInput = new TextField("Location", config.getLocation());
        panel.addComponent(m_locationInput);

        Set<? extends Hardware> hardwares = config.listHardwareIds();

        m_hardwareIdSelect = new Select("Node type");
        createHardwareItems(hardwares);
        panel.addComponent(m_hardwareIdSelect);

        m_ImageOwnerIdInput = new TextField("Image owner ID", config.getImageOwnerId());

        panel.addComponent(m_ImageOwnerIdInput);

        m_ImageIdSelect = new Select("Image ID");
        createImageItems();
        panel.addComponent(m_ImageIdSelect);

        m_ImageOwnerIdInput.addListener(new Property.ValueChangeListener() {
            public void valueChange(Property.ValueChangeEvent valueChangeEvent) {
                config.setImageOwnerId((String) valueChangeEvent.getProperty().getValue());
                m_ImageIdSelect.removeAllItems();

                createImageItems();
            }
        });


        m_keyPairInput = new TextField("Keypair", config.getKeyPair());
        panel.addComponent(m_keyPairInput);

        m_tagPrefixInput = new TextField("Tag prefix", config.getTagPrefix());
        panel.addComponent(m_tagPrefixInput);

        String[] extraPorts = config.getExtraPorts();
        StringBuilder extraPortsValue = new StringBuilder();
        for (String extraPort : extraPorts) {
            extraPortsValue.append(extraPort).append(",");
        }
        m_extraPortsInput = new TextField("Extra ports (comma separated", extraPortsValue.toString());
        panel.addComponent(m_extraPortsInput);

        m_runAsRootCheckbox = new CheckBox("Run as root", config.isRunAsRoot());
        panel.addComponent(m_runAsRootCheckbox);

        m_privateKeyFileInput = new TextField("Private key file", config.getPrivateKeyFile());
        panel.addComponent(m_privateKeyFileInput);

        m_vmOptionsInput = new TextField("VM options", config.getVmOptions());
        panel.addComponent(m_vmOptionsInput);

        m_launcherArgumentsInput = new TextField("Launcher arguments", config.getLauncherArguments());
        panel.addComponent(m_launcherArgumentsInput);

        m_nodeBootstrapInput = new TextField("Node bootstrap script", config.getNodeBootstrap());
        panel.addComponent(m_nodeBootstrapInput);

        m_aceLauncherInput = new TextField("ACE launcher", config.getAceLauncher());
        panel.addComponent(m_aceLauncherInput);

        m_additionalObrDownloadsInput = new TextField("Additional OBR downloads (comma separated", config.getAdditionalObrDownloads());
        panel.addComponent(m_additionalObrDownloadsInput);

        m_externalDownloadsInput = new TextField("External download urls (comman separated", config.getExternalDownloadUrls());
        panel.addComponent(m_externalDownloadsInput);

        m_sshUserInput = new TextField("SSH username", "ec2-user");
        panel.addComponent(m_sshUserInput);

        panel.addComponent(propertiesTable);

        m_factory.submit(m_statusGetter);
    }

    private void createHardwareItems(Set<? extends Hardware> hardwares) {
        for (Hardware hardware : hardwares) {
            m_hardwareIdSelect.addItem(hardware.getId());
            m_hardwareIdSelect.setItemCaption(hardware.getId(), hardware.getName());
            if(hardware.getId().equals(config.getHardwareId())) {
                m_hardwareIdSelect.select(hardware.getId());
            }
        }
    }

    private void createImageItems() {
        Set<? extends Image> images = config.listImages();
        for (Image image : images) {
            m_ImageIdSelect.addItem(image.getId());
            m_ImageIdSelect.setItemCaption(image.getId(), image.getName());
            if(image.getId().endsWith(config.getImageId())) {
                m_ImageIdSelect.select(image.getId());
            }
        }
    }

    @SuppressWarnings("serial")
    private class ActionButton extends Button implements NodeStatusHandler.NodeStatusListener, ClickListener {
        private final Action m_action;

        public ActionButton(NodeStatusHandler handler, Action action) {
            m_action = action;
            handler.addListener(this);
            addListener((ClickListener) this);
            nodeStatusChanged(NodeStatus.UNKNOWN, null);
            setCaption(m_action.toString());
        }

        public void nodeStatusChanged(NodeStatus status, Properties info) {
            setEnabled(status.getNextAction().equals(m_action));
        }

        public void buttonClick(ClickEvent event) {
            Runnable runner = m_runners.get(m_action);
            if (runner != null) {
                m_factory.submit(runner);
            }
        }
    }

    @SuppressWarnings("serial")
    private static class NodeLabel extends Label implements NodeStatusHandler.NodeStatusListener {
        public NodeLabel(NodeStatusHandler handler) {
            handler.addListener(this);
            nodeStatusChanged(NodeStatus.UNKNOWN, null);
        }

        public void nodeStatusChanged(NodeStatus status, Properties info) {
            setValue(status.toString());
        }
    }

    private static class PropertiesTable extends Table implements NodeStatusHandler.NodeStatusListener {
        public PropertiesTable(NodeStatusHandler handler) {
            super("Properties");
            handler.addListener(this);
            nodeStatusChanged(NodeStatus.UNKNOWN, null);
            addContainerProperty("Key", String.class, null);
            addContainerProperty("Value", String.class, null);
            setPageLength(0);
        }

        public void nodeStatusChanged(NodeStatus status, Properties info) {
            removeAllItems();
            int index = 0;
            if (info != null) {
                Enumeration<Object> keys = info.keys();
                while (keys.hasMoreElements()) {
                    Object key = keys.nextElement();
                    addItem(new Object[] {key.toString(), info.get(key).toString()}, index++);
                }
            }
        }
    }

    private class StatusGetter extends LockedRunner {
        public void doRun() {
            try {
                Properties properties = m_factory.getCloudService().getProperties(m_targetId);
                if (properties != null) {
                    m_handler.setStatus(NodeStatus.RUNNING, properties);
                }
                else {
                    m_handler.setStatus(NodeStatus.STOPPED);
                }
            }
            catch (Exception e) {
                m_handler.setStatus(NodeStatus.UNKNOWN);
                m_factory.getLogService().log(LogService.LOG_ERROR,
                        "Error getting status for node with ID " + m_targetId ,e);
            }
        }
    }

    private class StartRunner extends LockedRunner {
        @Override
        protected void doRun() {
            try {
                m_handler.setStatus(NodeStatus.STARTING);

                String sshUser = (String) m_sshUserInput.getValue();
                String location = (String) m_locationInput.getValue();
                String hardwareId = (String) m_hardwareIdSelect.getValue();
                String imageOwnerId = (String) m_ImageOwnerIdInput.getValue();
                String imageId = (String) m_ImageIdSelect.getValue();
                String keyPair = (String) m_keyPairInput.getValue();
                String tagPrefix = (String) m_tagPrefixInput.getValue();
                String[] extraPorts = ((String)m_extraPortsInput.getValue()).split(",");
                boolean runAsRoot = m_runAsRootCheckbox.booleanValue();
                String privateKeyFile = (String)m_privateKeyFileInput.getValue();
                String vmOptions = (String)m_vmOptionsInput.getValue();
                String laucherArgs = (String)m_launcherArgumentsInput.getValue();
                String nodeBootStrap = (String)m_nodeBootstrapInput.getValue();
                String aceLauncher = (String)m_aceLauncherInput.getValue();
                String additionalObrDownloads = (String)m_additionalObrDownloadsInput.getValue();
                String externalDownloads = (String)m_externalDownloadsInput.getValue();

                config.setSshUser(sshUser);
                config.setLocation(location);
                config.setHardwareId(hardwareId);

                if(imageOwnerId.length() > 0) {
                    config.setImageOwnerId(imageOwnerId);
                }

                config.setImageId(imageId);
                config.setKeyPair(keyPair);
                config.setTagPrefix(tagPrefix);
                config.setExtraPorts(extraPorts);
                config.setRunAsRoot(runAsRoot);
                config.setPrivateKeyFile(privateKeyFile);
                config.setVmOptions(vmOptions);
                config.setLauncherArguments(laucherArgs);
                config.setNodeBootstrap(nodeBootStrap);
                config.setAceLauncher(aceLauncher);
                config.setAdditionalObrDownloads(additionalObrDownloads);
                config.setExternalDownloadUrls(externalDownloads);

                m_factory.getCloudService().start(m_targetId, config);
                m_handler.setStatus(NodeStatus.INITIALIZING);
                disableInputFields();

            }
            catch (Exception e) {
                m_handler.setStatus(NodeStatus.ERROR);
                m_factory.getLogService().log(LogService.LOG_ERROR, "Error starting node with ID " + m_targetId, e);
            }
            m_factory.submit(m_statusGetter);
        }
    }

    private void disableInputFields() {
        m_locationInput.setEnabled(false);
        m_hardwareIdSelect.setEnabled(false);
        m_ImageOwnerIdInput.setEnabled(false);
        m_ImageIdSelect.setEnabled(false);
        m_keyPairInput.setEnabled(false);
        m_tagPrefixInput.setEnabled(false);
        m_extraPortsInput.setEnabled(false);
        m_runAsRootCheckbox.setEnabled(false);
        m_privateKeyFileInput.setEnabled(false);
        m_vmOptionsInput.setEnabled(false);
        m_launcherArgumentsInput.setEnabled(false);
        m_nodeBootstrapInput.setEnabled(false);
        m_aceLauncherInput.setEnabled(false);
        m_additionalObrDownloadsInput.setEnabled(false);
        m_externalDownloadsInput.setEnabled(false);
    }

    private class StopRunner extends LockedRunner {
        @Override
        protected void doRun() {
            try {
                m_handler.setStatus(NodeStatus.STOPPING);
                m_factory.getCloudService().stop(m_targetId);
            }
            catch (Exception e) {
                m_handler.setStatus(NodeStatus.ERROR);
                m_factory.getLogService().log(LogService.LOG_ERROR, "Error stopping node with ID " + m_targetId, e);
            }
            m_factory.submit(m_statusGetter);
        }
    }
    
    private abstract class LockedRunner implements Runnable {
        public final void run() {
            if (m_targetLock.tryLock()) {
                try {
                    doRun();
                }
                finally {
                    m_targetLock.unlock();
                }
            }
        }
        
        protected abstract void doRun();
    }
    
    private static class NodeStatusHandler {
        private final List<NodeStatusListener> m_listeners = new CopyOnWriteArrayList<NodeStatusListener>();

        public interface NodeStatusListener {
            void nodeStatusChanged(NodeStatus status, Properties info);
        }

        private void addListener(NodeStatusListener listener) {
            m_listeners.add(listener);
        }

        public void setStatus(NodeStatus status) {
            setStatus(status, null);
        }

        public void setStatus(NodeStatus status, Properties info) {
            for (NodeStatusListener listener : m_listeners) {
                listener.nodeStatusChanged(status, info);
            }
        }
    }

    private enum NodeStatus {
        UNKNOWN("Getting status...", Action.None),
        ERROR("Error", Action.Start),
        STOPPED("Stopped", Action.Start),
        STARTING("Starting...", Action.None),
        INITIALIZING("Initializing...", Action.None),
        RUNNING("Running", Action.Stop),
        STOPPING("Stopping...", Action.None);

        private final String m_label;
        private final Action m_nextAction;

        NodeStatus(String label, Action nextAction) {
            m_label = label;
            m_nextAction = nextAction;
        }

        public String toString() {
            return m_label;
        }
        
        public Action getNextAction() {
            return m_nextAction;
        }
    }

    private enum Action {
        Start,
        Stop,
        None;
    }

}
