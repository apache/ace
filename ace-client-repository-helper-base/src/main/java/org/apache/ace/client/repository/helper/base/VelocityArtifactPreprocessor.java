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
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

/**
 * This class can be used as a 'default' artifact preprocessor, using the Velocity template engine to preprocess
 * the artifact.
 */
public class VelocityArtifactPreprocessor extends ArtifactPreprocessorBase {

    private static Object m_initLock = new Object();
    private static boolean m_velocityInitialized = false;

    private final Map<String, WeakReference<byte[]>> m_cachedArtifacts;
    private final Map<String, WeakReference<String>> m_cachedHashes;
    private final MessageDigest m_md5;

    /**
     * Creates a new {@link VelocityArtifactPreprocessor} instance.
     */
    public VelocityArtifactPreprocessor() {
        try {
            m_md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to create VelocityArtifactPreprocessor instance!", e);
        }

        m_cachedArtifacts = new ConcurrentHashMap<String, WeakReference<byte[]>>();
        m_cachedHashes = new ConcurrentHashMap<String, WeakReference<String>>();
    }

    @Override
    public boolean needsNewVersion(String url, PropertyResolver props, String targetID, String fromVersion) {
        // get the template
        byte[] input = null;
        byte[] result = null;
        
        try {
            init();
            
            input = getArtifactAsBytes(url);
            result = process(input, props);
        }
        catch (IOException ioe) {
            // problem initializing velocity, or we cannot retrieve the 
            // original artifact, or process it; we can't say anything now.
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
        String oldHash = getHashForVersion(url, targetID, fromVersion);

        // Note: we do not cache any previously created processed templates, since the call that asks us to approve a new version
        // may cross a pending needsNewVersion call.
        return !newHash.equals(oldHash);
    }

    @Override
    public String preprocess(String url, PropertyResolver props, String targetID, String version, URL obrBase) throws IOException {
        init();

        // first, get the original data.
        byte[] input = getArtifactAsBytes(url);
        // process the template
        byte[] result = process(input, props);

        // first check: did we need any processing at all?
        if (Arrays.equals(result, input)) {
            // template isn't modified; use direct URL instead...
            return url;
        }
        
        setHashForVersion(url, targetID, version, hash(result));
        
        String name = getFilename(url, targetID, version);
        
        OutputStream output = upload(name, obrBase);
        output.write(result);
        output.close();

        return determineNewUrl(name, obrBase).toString();
    }

    /**
     * Initializes this preprocessor by making sure {@link Velocity#init()} is called.
     * <p>This method may be called multiple times.</p>
     * 
     * @throws IOException in case of problems initializing Velocity.
     */
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

    /**
     * @param url
     * @param targetID
     * @param version
     * @return
     * @throws MalformedURLException
     */
    private String getFilename(String url, String targetID, String version) throws MalformedURLException {
        return new File(new URL(url).getFile()).getName() + "-" + targetID + "-" + version;
    }

    /**
     * @param url
     * @param targetID
     * @param version
     * @return
     * @throws MalformedURLException
     */
    private String getFullUrl(String url, String targetID, String version) throws MalformedURLException {
        return url + "-" + targetID + "-" + version;
    }

    /**
     * @param url
     * @param target
     * @param version
     * @return
     */
    private String getHashForVersion(String url, String target, String version) {
        String key = createHashKey(url, target, version);
        String result = null;

        WeakReference<String> ref = m_cachedHashes.get(key);
        if (ref == null || ((result = ref.get()) == null)) {
            try {
                result = hash(getBytesFromUrl(getFullUrl(url, target, version)));

                m_cachedHashes.put(key, new WeakReference<String>(result));
            }
            catch (IOException e) {
                // we cannot retrieve the artifact, so we cannot say anything about it.
            }
        }

        return result;
    }

    /**
     * @param url
     * @param target
     * @param version
     * @param hash
     */
    private void setHashForVersion(String url, String target, String version, String hash) {
        String key = createHashKey(url, target, version);

        m_cachedHashes.put(key, new WeakReference<String>(hash));
    }

    /**
     * Applies the template processor to the given byte array.
     * 
     * @param input the template (as byte array) to process;
     * @param props the {@link PropertyResolver} to use.
     * @return the processed template, never <code>null</code>.
     * @throws IOException in case of I/O problems.
     */
    private byte[] process(byte[] input, PropertyResolver props) throws IOException {
        VelocityContext context = new VelocityContext();
        context.put("context", props);

        try {
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
     * Reads all information from a given URL, and returns that as a byte array. The byte array is not to be changed, and could be potentially come from a cache.
     * 
     * @param url the URL to read the artifact from, cannot be <code>null</code>.
     * @return the read (or cached) bytes, can be <code>null</code>.
     * @throws IOException in case of I/O problems.
     */
    private byte[] getArtifactAsBytes(String url) throws IOException {
        byte[] result = null;

        WeakReference<byte[]> ref = m_cachedArtifacts.get(url);
        if (ref == null || ((result = ref.get()) == null)) {
            result = getBytesFromUrl(url);
        }
        return result;
    }

    /**
     * Reads all bytes from the given URL and caches its result.
     * 
     * @param url the URL to read the bytes for, cannot be <code>null</code>.
     * @return the read bytes from the given URL, can be <code>null</code> if the reading failed.
     * @throws IOException in case of I/O problems.
     */
    private byte[] getBytesFromUrl(String url) throws IOException {
        byte[] result = null;

        InputStream in = new URL(url).openStream();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            byte[] buf = new byte[BUFFER_SIZE];
            for (int count = in.read(buf); count != -1; count = in.read(buf)) {
                baos.write(buf, 0, count);
            }
            
            result = baos.toByteArray();
            
            m_cachedArtifacts.put(url, new WeakReference<byte[]>(result));
        }
        finally {
            silentlyClose(in);
        }

        return result;
    }

    /**
     * Creates a key for storing/retrieving a hash.
     * 
     * @param url
     * @param target
     * @param version
     * @return a hash key, never <code>null</code>.
     */
    private String createHashKey(String url, String target, String version) {
        return new StringBuilder().append('[')
            .append(url)
            .append("][")
            .append(target)
            .append("][")
            .append(version)
            .append(']').toString();
    }

    /**
     * Computes a hash for a given byte array.
     * 
     * @param input the byte array to compute the hash for.
     * @return a hash for the given byte array, never <code>null</code>.
     */
    private String hash(byte[] input) {
        return new String(m_md5.digest(input));
    }
}
