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
package org.apache.ace.discovery.upnp;

import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import org.apache.ace.discovery.Discovery;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPService;

/**
 * Simple implementation of the <code>Discovery</code> interface. It 'discovers'
 * the server by means of UPnP.
 */
public class UPnPBasedDiscovery implements Discovery {
    final static String DEVICE_TYPE = "urn:schemas-upnp-org:device:ProvisioningDevice:1";
    final static String SERVICE_ID = "urn:upnp-org:serviceId:LocationService:1";
    final static String ACTION_GET_LOCATION = "GetLocation";
    final static String ACTION_GET_TYPE = "GetServerType";
    final static String ACTION_GET_LOAD = "GetServerLoad";

    public volatile LogService m_log; /* will be injected by dependencymanager */
    private List m_devices;

    public void start() {
        m_devices = new ArrayList();
    }

    public void added(ServiceReference ref, Object device) {
        if (device instanceof UPnPDevice) {
            m_devices.add(device);
        }
    }

    public void removed(Object device) {
        m_devices.remove(device);
    }

    public synchronized URL discover() {
        try {
            return getLocation();
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_DEBUG, "unable to retrieve location property", e);
        }

        return null;
    }

    private URL getLocation() {
        UPnPAction action = getAction(ACTION_GET_LOCATION);
        try {
            Dictionary dict = action.invoke(null);
            String location = (String)dict.get(action.getOutputArgumentNames()[0]);
            return new URL(location);
        }
        catch (Exception e) {}
        return null;
    }

    private String getType() {
        UPnPAction action = getAction(ACTION_GET_TYPE);
        try {
            Dictionary dict = action.invoke(null);
            return (String)dict.get(action.getOutputArgumentNames()[0]);
        }
        catch (Exception e) {}
        return "Unknown";
    }

    private int getLoad() {
        UPnPAction action = getAction(ACTION_GET_LOAD);
        try {
            Dictionary dict = action.invoke(null);
            Integer val = (Integer)dict.get(action.getOutputArgumentNames()[0]);
            return val.intValue();
        }
        catch (Exception e) {
            //ignore, just report worst case
        }
        return 100;
    }

    private UPnPAction getAction(String name) {
        UPnPDevice device = null;

        //zero-order implementation
        if ( m_devices.size() > 0 ) {
            device = (UPnPDevice)m_devices.iterator().next();
        }

        if (device != null) {
                UPnPService svc = device.getService(SERVICE_ID);
                if (svc != null) {
                    return svc.getAction(name);
                }
        }
        return null;
    }
}
