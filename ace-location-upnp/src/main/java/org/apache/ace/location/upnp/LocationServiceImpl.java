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
package org.apache.ace.location.upnp;

import java.net.URL;
import java.util.Dictionary;

import org.apache.ace.location.LocationService;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

/**
 * The actual implementation for the Location Service.
 */
public class LocationServiceImpl implements LocationService, ManagedService {
    public  final static String PID = "org.apache.ace.location.upnp.LocationService";
    private final static String ENDPOINT_URL = "endpoint.url";
    private final static String ENDPOINT_TYPE = "endpoint.type";
	private final String m_host;
	private final int m_port;
	private URL m_location;
	private String m_serverType;

	public LocationServiceImpl(String host, int port) {
	    m_host = host;
	    m_port = port;
	}

    private String get(Dictionary dict, String key) throws ConfigurationException {
        Object val = dict.get(key);

        if (val == null) {
            throw new ConfigurationException(key, "no such key defined");
        }

        if (val instanceof String) {
            return (String)val;
        }
        throw new ConfigurationException(key, "invalid format (not a String)");
    }

    public synchronized void updated(Dictionary dict) throws ConfigurationException {
        if (dict == null) {
            return;
        }

        String serverType = get(dict, ENDPOINT_TYPE);
        String url = get(dict, ENDPOINT_URL);

        URL location = null;

        try {
            //we expect something like:
            //http://<host>:<port>/xyz

            url = url.replaceFirst("<host>", m_host);
            url = url.replaceFirst("<port>", "" + m_port);
            location = new URL(url);

        }
        catch (Exception e) {
            throw new ConfigurationException(null, e.getMessage());
        }

        //all's well: apply
        m_serverType = serverType;
        m_location = location;

    }

	public URL getLocation() {
		return m_location;
	}

	public String getServerType() {
		return m_serverType;
	}

	public int getServerLoad() {
		return (int) (40 + Math.random()*10);
	}
}