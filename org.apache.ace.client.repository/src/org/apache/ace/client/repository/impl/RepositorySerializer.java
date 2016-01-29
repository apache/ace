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
package org.apache.ace.client.repository.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * Helper class that takes a RepositorySet<br>
 * TODO We might move out xstream at some time in the future; before that time, it could be a smart idea to wrap
 * xstream's writer in a delegate object, so this will not require changes to the repositories and objects.
 */
class RepositorySerializer implements Converter {
    private final Map<String, ObjectRepositoryImpl<?, ?>> m_tagToRepo = new HashMap<>();

    private final RepositorySet m_set;

    private final XStream m_stream;

    RepositorySerializer(RepositorySet set) {
        m_set = set;
        for (ObjectRepositoryImpl<?, ?> repo : m_set.getRepos()) {
            m_tagToRepo.put(repo.getXmlNode(), repo);
        }
        m_stream = new XStream();
        m_stream.alias("repository", getClass());
        m_stream.registerConverter(this);
    }

    public void marshal(Object target, HierarchicalStreamWriter writer, MarshallingContext context) {
        for (ObjectRepositoryImpl<?, ?> repo : m_set.getRepos()) {
            repo.marshal(writer);
        }
    }

    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            String nodeName = reader.getNodeName();
            ObjectRepositoryImpl<?, ?> o = m_tagToRepo.get(nodeName);
            o.unmarshal(reader);
            reader.moveUp();
        }
        return this;
    }

    public boolean canConvert(Class target) {
        return target == getClass();
    }

    public void toXML(OutputStream out) throws IOException {
        for (ObjectRepositoryImpl<?, ?> repo : m_set.getRepos()) {
            repo.setBusy(true);
        }
        try {
            GZIPOutputStream zout = new GZIPOutputStream(out);
            m_stream.toXML(this, zout);
            zout.finish();
        }
        finally {
            // Ensure all busy flags are reset at all times...
            for (ObjectRepositoryImpl<?, ?> repo : m_set.getRepos()) {
                repo.setBusy(false);
            }
        }
    }

    /**
     * Reads the repositories with which this RepositoryRoot had been initialized with from the given XML file.
     * 
     * @param in
     *            The input stream.
     */
    public void fromXML(InputStream in) {
        // The repositories get cleared, since a user *could* add stuff before
        // checking out.
        for (ObjectRepositoryImpl<?, ?> repo : m_set.getRepos()) {
            repo.setBusy(true);
            repo.removeAll();
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            if (in != null && in.available() > 0) {
                in = new GZIPInputStream(in);
                m_stream.fromXML(in, this);
            }
        }
        catch (IOException e) {
            // This means the stream has been closed before we got it.
            // Since the repository is now in a consistent state, just move on.
            e.printStackTrace();
        }
        finally {
            Thread.currentThread().setContextClassLoader(cl);
            // Ensure all busy flags are reset at all times...
            for (ObjectRepositoryImpl<?, ?> repo : m_set.getRepos()) {
                repo.setBusy(false);
            }
        }
    }
}
