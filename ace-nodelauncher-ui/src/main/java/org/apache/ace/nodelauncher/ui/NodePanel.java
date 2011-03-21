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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.vaadin.ui.*;
import org.osgi.service.log.LogService;

import com.vaadin.ui.Button.ClickListener;

@SuppressWarnings("serial")
public class NodePanel extends Panel {
    private final String m_targetId;
    private final Lock m_targetLock = new ReentrantLock();
    private final NodeStatusHandler m_handler = new NodeStatusHandler();
    private final NodeLauncherPanelFactory m_factory;
    private final Map<Action, Runnable> m_runners = new HashMap<Action, Runnable>();
    private final Runnable m_statusGetter = new StatusGetter();
    
    {
        m_runners.put(Action.Start, new StartRunner());
        m_runners.put(Action.Stop, new StopRunner());
    }

    public NodePanel(NodeLauncherPanelFactory factory, String target) {
        m_factory = factory;
        m_targetId = target;
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
        panel.addComponent(propertiesTable);

        m_factory.submit(m_statusGetter);
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
        public void run() {
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
                m_factory.getCloudService().start(m_targetId);
                m_handler.setStatus(NodeStatus.INITIALIZING);
            }
            catch (Exception e) {
                m_handler.setStatus(NodeStatus.ERROR);
                m_factory.getLogService().log(LogService.LOG_ERROR, "Error starting node with ID " + m_targetId, e);
            }
            m_factory.submit(m_statusGetter);
        }
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
