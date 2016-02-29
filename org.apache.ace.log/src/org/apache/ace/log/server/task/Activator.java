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
package org.apache.ace.log.server.task;

import static org.amdatu.scheduling.constants.Constants.DESCRIPTION;
import static org.amdatu.scheduling.constants.Constants.REPEAT_FOREVER;
import static org.amdatu.scheduling.constants.Constants.REPEAT_INTERVAL_PERIOD;
import static org.amdatu.scheduling.constants.Constants.REPEAT_INTERVAL_VALUE;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.amdatu.scheduling.Job;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.log.LogSync;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.log.server.task.LogSyncTask.Mode;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {
    private static final String KEY_LOG_NAME = "name";
    private static final String KEY_MODE = "mode";
    private static final String KEY_MODE_LOWEST_IDS = "mode-lowest-ids";
    private static final String KEY_TARGETID = "tid";
    private static final String KEY_SYNC_INTERVAL = "syncInterval";
    
    private final Map<String, Component> m_instances = new HashMap<>();
    private volatile DependencyManager m_manager;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, "org.apache.ace.log.server.task.factory");
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
        );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    public String getName() {
        return "Log Sync Task Factory";
    }

    public synchronized void updated(String pid, Dictionary<String, ?> dict) throws ConfigurationException {
        String name = (String) dict.get(KEY_LOG_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(KEY_LOG_NAME, "Log name has to be specified.");
        }
        Long syncInterval = 2000L;
        String interval = (String) dict.get(KEY_SYNC_INTERVAL);
        if (interval != null) {
            try {
                syncInterval = Long.valueOf(interval);
            } catch (NumberFormatException e) {
                throw new ConfigurationException(KEY_SYNC_INTERVAL, "Log sync interval has to be a valid long value.");
            }
        } else {
            throw new ConfigurationException(KEY_SYNC_INTERVAL, "Log sync interval has to be specified.");
        }
        
        Mode dataTransferMode = Mode.PUSH;
        String modeValue = (String) dict.get(KEY_MODE);
        if ("pull".equals(modeValue)) {
        	dataTransferMode = Mode.PULL;
        }
        else if ("pushpull".equals(modeValue)) {
        	dataTransferMode = Mode.PUSHPULL;
        }
        else if ("none".equals(modeValue)) {
        	dataTransferMode = Mode.NONE;
        }
        Mode lowestIDsMode = Mode.NONE;
        modeValue = (String) dict.get(KEY_MODE_LOWEST_IDS);
        if ("pull".equals(modeValue)) {
        	lowestIDsMode = Mode.PULL;
        }
        else if ("pushpull".equals(modeValue)) {
        	lowestIDsMode = Mode.PUSHPULL;
        }
        else if ("push".equals(modeValue)) {
        	lowestIDsMode = Mode.PUSH;
        }
        String targetID = (String) dict.get(KEY_TARGETID);

        Component oldComponent, newComponent;
        
        Properties props = new Properties();
        props.put(KEY_LOG_NAME, name);
        props.put(REPEAT_FOREVER, true);
        props.put(REPEAT_INTERVAL_PERIOD, "millisecond");
        props.put(REPEAT_INTERVAL_VALUE, syncInterval);
        props.put("taskName", LogSyncTask.class.getName());
        props.put(DESCRIPTION, "Syncs log (name=" + name + ", mode=" + dataTransferMode.toString() + (targetID == null ? "" : ", targetID=" + targetID) + ") with a server.");
        String filter = "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=" + name + "))";
        LogSyncTask service = new LogSyncTask(name, name, dataTransferMode, lowestIDsMode, targetID);
        newComponent = m_manager.createComponent()
    		.setInterface(new String[] { Job.class.getName(), LogSync.class.getName() }, props)
    		.setImplementation(service)
    		.add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
    		.add(createServiceDependency().setService(LogStore.class, filter).setRequired(true))
    		.add(createServiceDependency().setService(Discovery.class).setRequired(true))
    		.add(createServiceDependency().setService(LogService.class).setRequired(false));
        
        synchronized (m_instances) {
            oldComponent = m_instances.put(pid, newComponent);
        }
        if (oldComponent != null) {
        	m_manager.remove(oldComponent);
        }
        m_manager.add(newComponent);
    }

	public void deleted(String pid) {
		Component component;
		synchronized (m_instances) {
			component = m_instances.remove(pid);
		}
	    if (component != null) {
	        m_manager.remove(component);
	    }
	}
}
