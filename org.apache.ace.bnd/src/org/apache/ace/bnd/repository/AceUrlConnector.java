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
package org.apache.ace.bnd.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.StringTokenizer;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.useradmin.User;

import aQute.bnd.service.Plugin;
import aQute.bnd.service.url.TaggedData;
import aQute.bnd.service.url.URLConnector;
import aQute.service.reporter.Reporter;

/**
 * BND URLConnector plugin based on the Ace ConnectionFactory
 * 
 * Can be configured as a bnd plugin with a configs configuration property that contains 
 * comma separated list of connection factory configurations
 * 
 * Example: 
 *  -plugin: org.apache.ace.bnd.repository.AceUrlConnector; \
 *      configs=${workspace}/run-server-allinone/conf/org.apache.ace.connectionfactory/obr.cfg;
 * 
 * Note: The default ace configurations contain placeholders in the baseURL property this doesn't 
 * work when the configuration is used in this plugin as these placeholders are not replaced by bnd.
 */
public class AceUrlConnector implements URLConnector, Plugin, ConnectionFactory {
    
    private static final String HEADER_IF_NONE_MATCH    = "If-None-Match";
    private static final String HEADER_ETAG             = "ETag";
    private static final int    RESPONSE_NOT_MODIFIED   = 304;
    
    private ConnectionFactory m_connectionFactory;
    
    public AceUrlConnector() {
        m_connectionFactory = getConnectionFactory();
    }
    
    public AceUrlConnector(ConnectionFactory connectionFactory) {
        m_connectionFactory = connectionFactory;
    }

    private ConnectionFactory getConnectionFactory() {
        ServiceLoader<ConnectionFactory> loader = ServiceLoader.load(ConnectionFactory.class, getClass().getClassLoader());
        return loader.iterator().next();
    }
    
    @Override
    public TaggedData connectTagged(URL url, String tag) throws IOException {
        TaggedData result;
        URLConnection connection = m_connectionFactory.createConnection(url);
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            
            httpConnection.setUseCaches(true);
            if (tag != null) {
                httpConnection.setRequestProperty(HEADER_IF_NONE_MATCH, tag);
            }
            int responseCode = httpConnection.getResponseCode();
            if (responseCode == RESPONSE_NOT_MODIFIED) {
                result = null;
                httpConnection.disconnect();
            } else {
                String responseTag = httpConnection.getHeaderField(HEADER_ETAG);
                result = new TaggedData(responseTag, connection.getInputStream());
            }
        } else {
            // Non-HTTP so ignore all this tagging malarky
            result = new TaggedData(null, connection.getInputStream());
        }
        
        return result;
    }

    @Override
    public TaggedData connectTagged(URL url) throws IOException {
        return connectTagged(url, null);
    }

    @Override
    public InputStream connect(URL url) throws IOException {
        return m_connectionFactory.createConnection(url).getInputStream();
    }
    
    @Override
    public URLConnection createConnection(URL url) throws IOException {
        return m_connectionFactory.createConnection(url);
    }
    
    @Override
    public URLConnection createConnection(URL url, User user) throws IOException {
        return m_connectionFactory.createConnection(url, user);
    }

    @Override
    public void setProperties(Map<String, String> map) {
        String configFileList = map.get("configs");
        if (configFileList == null) {
            throw new IllegalArgumentException("'configs' must be specified on HttpBasicAuthURLConnector");
        }
        
        ConnectionFactory connectionFactory = getConnectionFactory();
        ManagedServiceFactory managedServiceFactory = (ManagedServiceFactory) connectionFactory;
        
        StringTokenizer tokenizer = new StringTokenizer(configFileList, ",");
        while (tokenizer.hasMoreTokens()) {
            String configFileName = tokenizer.nextToken().trim();

            File file = new File(configFileName);
            if (file.exists()) {
                
                try {
                    Properties properties = new Properties();
                    properties.load(new FileInputStream(file));
                    
                    Dictionary<String, String> dict = new Hashtable<>();
                    for (Object key : properties.keySet()) {
                        String value = (String) properties.get(key);
                        dict.put((String) key, value);
                    }
                    
                    managedServiceFactory.updated(file.getAbsolutePath(), dict);
                } catch (IOException | ConfigurationException e) {
                    
                }
            }
            
            m_connectionFactory = connectionFactory;
        }
    }

    @Override
    public void setReporter(Reporter reporter) {
        
    }

}
