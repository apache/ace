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
package org.apache.ace.client.repository.helper.base;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

/**
 * This class can be used as a 'default' artifact preprocessor, using the Velocity template engine to preprocess
 * the artifact.
 */
public class VelocityArtifactPreprocessor extends ArtifactPreprocessorBase {

    private static final int BUFFER_SIZE = 1024;
    private Map<String, byte[]> m_cachedArtifacts = new HashMap<String, byte[]>();
    private Map<String, String> m_cachedHashes = new HashMap<String, String>();

    private static Object m_initLock = new Object();
    private static boolean m_velocityInitialized = false;

    private void init() throws IOException {
        if (m_velocityInitialized) {
            return;
        }
        else {
            synchronized (m_initLock) {
                if (!m_velocityInitialized) {
                    try {
                        Velocity.init();
                        m_velocityInitialized = true;
                    }
                    catch (Exception e) {
                        // Something went seriously bad initializing velocity.
                        throw new IOException("Error initializing Velocity: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public String preprocess(String url, PropertyResolver props, String gatewayID, String version, URL obrBase) throws IOException {
        init();
        // first, get the original data.
        byte[] input = null;
        try {
            input = getArtifactAsBytes(url);
        }
        catch (IOException ioe) {
            throw new IOException("Error retrieving the original artifact for preprocessing: " + ioe.getMessage());
        }

        // process the template
        byte[] result = process(input, props);

        if (Arrays.equals(result, input)) {
            return url;
        }
        else {
            try {
                String name = getFilename(url, gatewayID, version);
                OutputStream output = upload(name, obrBase);
                output.write(result);
                output.close();
                setHashForVersion(url, gatewayID, version, hash(result));
                return determineNewUrl(name, obrBase).toString();
            }
            catch (IOException ioe) {
                throw new IOException("Error storing the processed: " + ioe.getMessage());
            }
        }
    }

    private String getFilename(String url, String gatewayID, String version) throws MalformedURLException {
        return new File(new URL(url).getFile()).getName() + "-" + gatewayID + "-" + version;
    }

    private String getFullUrl(String url, String gatewayID, String version) throws MalformedURLException {
        return url + "-" + gatewayID + "-" + version;
    }

    private String getHashForVersion(String url, String gateway, String version) {
        String key = new StringBuilder().append('[')
        .append(url)
        .append("][")
        .append(gateway)
        .append("][")
        .append(version)
        .append(']').toString();

        if (m_cachedHashes.containsKey(key)) {
            return m_cachedHashes.get(key);
        }
        else {
            byte[] processedTemplate;
            try {
                processedTemplate = getBytesFromUrl(getFullUrl(url, gateway, version));
            }
            catch (IOException e) {
                // we cannot retrieve the artifact, so we cannot say anything about it.
                return null;
            }
            String result = hash(processedTemplate);

            m_cachedHashes.put(key, result);
            return result;
        }
    }

    private void setHashForVersion(String url, String gateway, String version, String hash) {
        String key = new StringBuilder().append('[')
        .append(url)
        .append("][")
        .append(gateway)
        .append("][")
        .append(version)
        .append(']').toString();

        m_cachedHashes.put(key, hash);
    }

    private byte[] process(byte[] input, PropertyResolver props) throws IOException {
        try {
            VelocityContext context = new VelocityContext();
            context.put("context", props);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(baos);
            Velocity.evaluate(context, writer, "", new InputStreamReader(new ByteArrayInputStream(input)));
            writer.flush();
            return baos.toByteArray();
        }
        catch (IOException ioe) {
            throw new IOException("Error processing the artifact: " + ioe.getMessage());
        }
    }

    /**
     * Helper method, which reads all information from a stream, and returns that as a
     * byte array. The byte array is not to be changed.
     */
    private byte[] getArtifactAsBytes(String url) throws IOException {
        if (m_cachedArtifacts.containsKey(url)) {
            return m_cachedArtifacts.get(url);
        }
        else {
            return getBytesFromUrl(url);
        }
    }

    private byte[] getBytesFromUrl(String url) throws IOException, MalformedURLException {
        ByteArrayOutputStream found = new ByteArrayOutputStream();
        InputStream in = new URL(url).openStream();

        byte[] buf = new byte[BUFFER_SIZE];
        for (int count = in.read(buf); count != -1; count = in.read(buf)) {
            found.write(buf, 0, count);
        }
        in.close();
        byte[] result = found.toByteArray();
        m_cachedArtifacts.put(url, result);
        return result;
    }

    @Override
    public boolean needsNewVersion(String url, PropertyResolver props, String gatewayID, String fromVersion) {
        try {
            init();
        }
        catch (IOException e) {
            // problem initializing velocity... we cannot say anything.
            return true;
        }
        // get the tempate
        byte[] input = null;
        byte[] result = null;
        try {
            input = getArtifactAsBytes(url);
            result = process(input, props);
        }
        catch (IOException ioe) {
            // we cannot retrieve the original artifact, or process it; we can't say anyting now.
            return true;
        }

        // process the template

        // first check: did we need any processing at all?
        if (Arrays.equals(result, input)) {
            return false;
        }

        // hash the processed template
        String newHash = hash(result);

        // find the hash for the previous version
        String oldHash = getHashForVersion(url, gatewayID, fromVersion);

        // Note: we do not cache any previously created processed templates, since the call that asks us to approve a new version
        // may cross a pending needsNewVersion call.
        return !newHash.equals(oldHash);
    }

    private String hash(byte[] input) {
        try {
            return new String(MessageDigest.getInstance("MD5").digest(input));
        }
        catch (NoSuchAlgorithmException e) {
            // Will not happen: MD5 is a standard algorithm.
        }
        return null;
    }
}
