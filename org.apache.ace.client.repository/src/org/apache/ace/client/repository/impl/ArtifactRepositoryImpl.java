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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.ArtifactResource;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.RepositoryConfiguration;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the ArtifactRepository. For 'what it does', see ArtifactRepository, for 'how it works', see
 * ObjectRepositoryImpl.<br>
 * <br>
 * This class has some extended functionality when compared to <code>ObjectRepositoryImpl</code>,
 * <ul>
 * <li>it keeps track of all <code>ArtifactHelper</code>s, and serves them to its inhabitants.
 * <li>it handles importing of artifacts.
 * </ul>
 */
public class ArtifactRepositoryImpl extends ObjectRepositoryImpl<ArtifactObjectImpl, ArtifactObject> implements ArtifactRepository {
    private final static String XML_NODE = "artifacts";

    // Injected by Dependency Manager
    private volatile BundleContext m_context;
    private volatile LogService m_log;
    private volatile ConnectionFactory m_connectionFactory;

    private final Map<String, ArtifactHelper> m_helpers = new HashMap<>();

    public ArtifactRepositoryImpl(ChangeNotifier notifier, RepositoryConfiguration repoConfig) {
        super(notifier, XML_NODE, repoConfig);
    }

    public List<ArtifactObject> getResourceProcessors() {
        try {
            return super.get(createFilter("(" + BundleHelper.KEY_RESOURCE_PROCESSOR_PID + "=*)"));
        }
        catch (InvalidSyntaxException e) {
            m_log.log(LogService.LOG_ERROR, "getResourceProcessors' filter returned an InvalidSyntaxException.", e);
        }
        return new ArrayList<>();
    }

    @Override
    public List<ArtifactObject> get(Filter filter) {
        // Note that this excludes any bundle artifacts which are resource processors.
        try {
            Filter extendedFilter = createFilter("(&" + filter.toString() + "(!(" + BundleHelper.KEY_RESOURCE_PROCESSOR_PID + "=*)))");
            return super.get(extendedFilter);
        }
        catch (InvalidSyntaxException e) {
            m_log.log(LogService.LOG_ERROR, "Extending " + filter.toString() + " resulted in an InvalidSyntaxException.", e);
        }
        return new ArrayList<>();
    }

    @Override
    public List<ArtifactObject> get() {
        // Note that this excludes any Bundle artifacts which are resource processors.
        try {
            // ??? should we do that to that key?
            return super.get(createFilter("(!(" + RepositoryUtil.escapeFilterValue(BundleHelper.KEY_RESOURCE_PROCESSOR_PID) + "=*))"));
        }
        catch (InvalidSyntaxException e) {
            m_log.log(LogService.LOG_ERROR, "get's filter returned an InvalidSyntaxException.", e);
        }
        return new ArrayList<>();
    }

    @Override
    ArtifactObjectImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        ArtifactHelper helper = getHelper(attributes.get(ArtifactObject.KEY_MIMETYPE));
        ArtifactObjectImpl ao = new ArtifactObjectImpl(helper.checkAttributes(attributes), helper.getMandatoryAttributes(), tags, this, this);
        return ao;
    }

    @Override
    ArtifactObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new ArtifactObjectImpl(reader, this, this);
    }

    /**
     * Helper method for this repository's inhabitants, which finds the necessary helpers.
     * 
     * @param mimetype
     *            The mimetype for which a helper should be found.
     * @return An artifact helper for the given mimetype.
     * @throws IllegalArgumentException
     *             when the mimetype is invalid, or no helpers are available.
     */
    ArtifactHelper getHelper(String mimetype) {
        synchronized (m_helpers) {
            if ((mimetype == null) || (mimetype.length() == 0)) {
                throw new IllegalArgumentException("Without a mimetype, we cannot find a helper.");
            }

            ArtifactHelper helper = m_helpers.get(mimetype.toLowerCase());

            if (helper == null) {
                throw new IllegalArgumentException("There are no ArtifactHelpers known for type '" + mimetype + "'.");
            }

            return helper;
        }
    }

    /**
     * Method intended for adding artifact helpers by the bundle's activator.
     */
    void addHelper(String mimetype, ArtifactHelper helper) {
        synchronized (m_helpers) {
            if ((mimetype == null) || (mimetype.length() == 0)) {
                m_log.log(LogService.LOG_WARNING, "An ArtifactHelper has been published without a proper mimetype.");
            }
            else {
                m_helpers.put(mimetype.toLowerCase(), helper);
            }
        }
    }

    /**
     * Method intended for removing artifact helpers by the bundle's activator.
     */
    void removeHelper(String mimetype, ArtifactHelper helper) {
        synchronized (m_helpers) {
            if ((mimetype == null) || (mimetype.length() == 0)) {
                m_log.log(LogService.LOG_WARNING, "An ArtifactHelper is being removed without a proper mimetype.");
            }
            else {
                m_helpers.remove(mimetype.toLowerCase());
            }
        }
    }

    /**
     * Utility function that takes either a URL or a String representing a mimetype, and returns the corresponding
     * <code>ArtifactHelper</code>, <code>ArtifactRecognizer</code> and, if not specified, the mimetype.
     * 
     * @param input
     *            Either a <code>URL</code> pointing to a physical artifact, or a <code>String</code> representing a
     *            mime type.
     * @return A mapping from a class (<code>ArtifactRecognizer</code>, <code>ArtifactHelper</code> or
     *         <code>String</code> to an instance of that class as a result.
     */
    protected Map<Class<?>, Object> findRecognizerAndHelper(Object input) throws IllegalArgumentException {
        // check input.
        URL url = null;
        String mimetype = null;
        if (input instanceof URL) {
            url = (URL) input;
        }
        else if (input instanceof String) {
            mimetype = (String) input;
        }
        else {
            throw new IllegalArgumentException("findRecognizer received an unrecognized input.");
        }

        // Get all published ArtifactRecognizers.
        List<ServiceReference<ArtifactRecognizer>> refs = new ArrayList<>();
        try {
            Collection<ServiceReference<ArtifactRecognizer>> tmpRefs = m_context.getServiceReferences(ArtifactRecognizer.class, null);
            refs.addAll(tmpRefs);
        }
        catch (InvalidSyntaxException e) {
            // We do not pass in a filter, so this should not happen.
            m_log.log(LogService.LOG_WARNING, "A null filter resulted in an InvalidSyntaxException from getServiceReferences.");
        }

        if (refs.isEmpty()) {
            throw new IllegalArgumentException("There are no artifact recognizers available.");
        }

        // Sort the references by service ranking.
        Collections.sort(refs, Collections.reverseOrder());

        ArtifactResource resource = convertToArtifactResource(url);

        // Check all referenced services to find one that matches our input.
        ArtifactRecognizer recognizer = null;
        String foundMimetype = null;
        for (ServiceReference<ArtifactRecognizer> ref : refs) {
            ArtifactRecognizer candidate = m_context.getService(ref);
            try {
                if (mimetype != null) {
                    if (candidate.canHandle(mimetype)) {
                        recognizer = candidate;
                        break;
                    }
                }
                else {
                    String candidateMime = candidate.recognize(resource);
                    if (candidateMime != null) {
                        foundMimetype = candidateMime;
                        recognizer = candidate;
                        break;
                    }
                }
            }
            finally {
                m_context.ungetService(ref);
            }
        }

        if (recognizer == null) {
            throw new IllegalArgumentException("There is no artifact recognizer that recognizes artifact " + ((mimetype != null) ? mimetype : url));
        }

        // Package the results in the map.
        Map<Class<?>, Object> result = new HashMap<>();
        result.put(ArtifactRecognizer.class, recognizer);
        if (mimetype == null) {
            result.put(ArtifactHelper.class, getHelper(foundMimetype));
            result.put(String.class, foundMimetype);
        }
        else {
            result.put(ArtifactHelper.class, getHelper(mimetype));
        }

        return result;
    }

    public boolean recognizeArtifact(URL artifact) {
        try {
            Map<Class<?>, Object> fromArtifact = findRecognizerAndHelper(artifact);
            String mimetype = (String) fromArtifact.get(String.class);
            return mimetype != null;
        }
        catch (Exception e) {
            // too bad... Nothing to do now.
            return false;
        }
    }

    /**
     * @return the OBR base URL to use, can be <code>null</code>.
     */
    public URL getObrBase() {
        return getRepositoryConfiguration().getOBRLocation();
    }

    public ArtifactObject importArtifact(URL artifact, boolean upload) throws IllegalArgumentException, IOException {
        if ((artifact == null) || (artifact.toString().length() == 0)) {
            throw new IllegalArgumentException("The URL to import cannot be null or empty.");
        }
        checkURL(artifact);
        
        Map<Class<?>, Object> fromArtifact = findRecognizerAndHelper(artifact);
        ArtifactRecognizer recognizer = (ArtifactRecognizer) fromArtifact.get(ArtifactRecognizer.class);
        ArtifactHelper helper = (ArtifactHelper) fromArtifact.get(ArtifactHelper.class);
        String mimetype = (String) fromArtifact.get(String.class);
        
        return importArtifact(artifact, recognizer, helper, mimetype, false, upload);
    }

    public ArtifactObject importArtifact(URL artifact, String mimetype, boolean upload) throws IllegalArgumentException, IOException {
        if ((artifact == null) || (artifact.toString().length() == 0)) {
            throw new IllegalArgumentException("The URL to import cannot be null or empty.");
        }
        if ((mimetype == null) || (mimetype.length() == 0)) {
            throw new IllegalArgumentException("The mimetype of the artifact to import cannot be null or empty.");
        }
        
        checkURL(artifact);
        
        Map<Class<?>, Object> fromMimetype = findRecognizerAndHelper(mimetype);
        ArtifactRecognizer recognizer = (ArtifactRecognizer) fromMimetype.get(ArtifactRecognizer.class);
        ArtifactHelper helper = (ArtifactHelper) fromMimetype.get(ArtifactHelper.class);
        
        return importArtifact(artifact, recognizer, helper, mimetype, true, upload);
    }

    private ArtifactObject importArtifact(URL artifact, ArtifactRecognizer recognizer, ArtifactHelper helper, String mimetype, boolean overwrite, boolean upload) throws IOException {
        ArtifactResource resource = convertToArtifactResource(artifact);

        Map<String, String> attributes = recognizer.extractMetaData(resource);
        Map<String, String> tags = new HashMap<>();

        helper.checkAttributes(attributes);
        attributes.put(ArtifactObject.KEY_ARTIFACT_DESCRIPTION, "");
        attributes.put(ArtifactObject.KEY_SIZE, Long.toString(resource.getSize()));
        if (overwrite) {
            attributes.put(ArtifactObject.KEY_MIMETYPE, mimetype);
        }

        String artifactURL = artifact.toString();
        attributes.put(ArtifactObject.KEY_URL, artifactURL);

        if (upload) {
            String location = upload(artifact, attributes.get("filename"), mimetype);
            attributes.put(ArtifactObject.KEY_URL, location);
        }

        ArtifactObject result = create(attributes, tags);
        return result;
    }

    /**
     * Helper method which checks a given URL for 'validity', that is, does this URL point to something that can be
     * read.
     * 
     * @param artifact
     *            A URL pointing to an artifact.
     * @throws IllegalArgumentException
     *             when the URL does not point to a valid file.
     */

    private void checkURL(URL artifact) throws IllegalArgumentException {
        URLConnection connection = null;
        // First, check whether we can actually reach something from this URL.
        InputStream is = null;
        try {
            connection = m_connectionFactory.createConnection(artifact);
            is = connection.getInputStream();
        }
        catch (IOException ioe) {
            throw new IllegalArgumentException("Artifact " + artifact + " does not point to a valid file.");
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                }
                catch (IOException ioe) {
                    // Too bad, nothing to do.
                }
            }
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
        }

        // Then, check whether the name is legal.
        String artifactName = artifact.toString();
        for (byte b : artifactName.substring(artifactName.lastIndexOf('/') + 1).getBytes()) {
            if (!(((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z')) || ((b >= '0') && (b <= '9')) || (b == '.') || (b == '-') || (b == '_'))) {
                throw new IllegalArgumentException("Artifact " + artifactName + "'s name contains an illegal character '" + new String(new byte[] { b }) + "'");
            }
        }
    }

    /**
     * Uploads an artifact to the OBR.
     * 
     * @param artifact
     *            URL pointing to the local artifact.
     * @param filename
     *            The filenmame parameter, may be <code>null</code>.
     * @param mimetype
     *            The mimetype of this artifact.
     * @return The persistent location of this artifact.
     * @throws IOException
     *             for any problem uploading the artifact.
     */
    private String upload(URL artifact, String filename, String mimetype) throws IOException {
        URL obrBase = getObrBase();
        if (obrBase == null) {
            throw new IOException("There is no storage available for this artifact.");
        }

        URLConnection inputConn = null;
        URLConnection outputConn = null;
        InputStream input = null;
        OutputStream output = null;
        URL url = obrBase;
        String location = null;

        int blockSize = 8192;

        try {
            inputConn = m_connectionFactory.createConnection(artifact);
            input = inputConn.getInputStream();

            if (filename != null) {
                url = new URL(obrBase, "?filename=" + filename);
            }

            outputConn = m_connectionFactory.createConnection(url);
            outputConn.setDoOutput(true);
            outputConn.setDoInput(true);
            outputConn.setUseCaches(false);
            outputConn.setRequestProperty("Content-Type", mimetype);
            if (outputConn instanceof HttpURLConnection) {
                // ACE-294: enable streaming mode causing only small amounts of memory to be
                // used for this commit. Otherwise, the entire input stream is cached into
                // memory prior to sending it to the server...
                ((HttpURLConnection) outputConn).setChunkedStreamingMode(blockSize);
            }

            output = outputConn.getOutputStream();

            byte[] buffer = new byte[blockSize];
            for (int count = input.read(buffer); count != -1; count = input.read(buffer)) {
                output.write(buffer, 0, count);
            }

            output.close();

            if (outputConn instanceof HttpURLConnection) {
                int responseCode = ((HttpURLConnection) outputConn).getResponseCode();
                switch (responseCode) {
                    case HttpURLConnection.HTTP_CREATED:
                        location = outputConn.getHeaderField("Location");
                        break;
                    case HttpURLConnection.HTTP_CONFLICT:
                        throw new ArtifactAlreadyExistsException(artifact, filename);
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:
                        throw new IOException("The storage server returned an internal server error.");
                    default:
                        throw new IOException("The storage server returned code " + responseCode + " writing to " + url.toString());
                }
            }
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                }
                catch (Exception ex) {
                    // Not much we can do
                }
            }
            if (inputConn instanceof HttpURLConnection) {
                ((HttpURLConnection) inputConn).disconnect();
            }

            if (output != null) {
                try {
                    output.close();
                }
                catch (Exception ex) {
                    // Not much we can do
                }
            }
            if (outputConn instanceof HttpURLConnection) {
                ((HttpURLConnection) outputConn).disconnect();
            }
        }

        return location;
    }

    public String preprocessArtifact(ArtifactObject artifact, TargetObject target, String targetID, String version) throws IOException {
        ArtifactPreprocessor preprocessor = getHelper(artifact.getMimetype()).getPreprocessor();
        if (preprocessor == null) {
            return artifact.getURL();
        }
        else {
            return preprocessor.preprocess(artifact.getURL(), new TargetPropertyResolver(target), targetID, version, getObrBase());
        }
    }

    public boolean needsNewVersion(ArtifactObject artifact, TargetObject target, String targetID, String fromVersion) {
        ArtifactPreprocessor preprocessor = getHelper(artifact.getMimetype()).getPreprocessor();
        if (preprocessor == null) {
            return false;
        }
        else {
            return preprocessor.needsNewVersion(artifact.getURL(), new TargetPropertyResolver(target), targetID, fromVersion);
        }
    }

    /**
     * Converts a given URL to a {@link ArtifactResource} that abstracts the way we access the contents of the URL away
     * from the URL itself. This way, we can avoid having to pass authentication credentials, or a
     * {@link ConnectionFactory} to the artifact recognizers.
     * 
     * @param url
     *            the URL to convert, can be <code>null</code> in which case <code>null</code> is returned.
     * @return an {@link ArtifactResource}, or <code>null</code> if the given URL was <code>null</code>.
     */
    private ArtifactResource convertToArtifactResource(final URL url) {
        if (url == null) {
            return null;
        }

        return new ArtifactResource() {
            public URL getURL() {
                return url;
            }

            @Override
            public long getSize() throws IOException {
                // Take care of the fact that an URL could need credentials to be accessible!!!
                URLConnection conn = m_connectionFactory.createConnection(getURL());
                conn.setUseCaches(true);
                return conn.getContentLength();
            }

            @Override
            public InputStream openStream() throws IOException {
                // Take care of the fact that an URL could need credentials to be accessible!!!
                URLConnection conn = m_connectionFactory.createConnection(getURL());
                conn.setUseCaches(true);
                return conn.getInputStream();
            }
        };
    }
}
