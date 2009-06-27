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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the ArtifactRepository. For 'what it does', see ArtifactRepository,
 * for 'how it works', see ObjectRepositoryImpl.<br>
 * <br>
 * This class has some extended functionality when compared to <code>ObjectRepositoryImpl</code>,
 * <ul>
 * <li> it keeps track of all <code>ArtifactHelper</code>s, and serves them to its inhabitants.
 * <li> it handles importing of artifacts.
 * </ul>
 */
public class ArtifactRepositoryImpl extends ObjectRepositoryImpl<ArtifactObjectImpl, ArtifactObject> implements ArtifactRepository {
    private final static String XML_NODE = "artifacts";
    private volatile BundleContext m_context; /*Injected by dependency manager*/
    private volatile LogService m_log; /*Injected by dependency manager*/
    private Map<String, ArtifactHelper> m_helpers = new HashMap<String, ArtifactHelper>();
    private URL m_obrBase;

    /**
     * Custom comparator which sorts service references by service rank, highest rank first.
     */
    private static Comparator<ServiceReference> SERVICE_RANK_COMPARATOR = new Comparator<ServiceReference>() {
        public int compare(ServiceReference o1, ServiceReference o2) {
            int rank1 = 0;
            int rank2 = 0;
            try {
                Object rankObject1 = o1.getProperty(Constants.SERVICE_RANKING);
                rank1 = (rankObject1 == null) ? 0 : ((Integer) rankObject1).intValue();
            }
            catch (ClassCastException cce) {
                // No problem.
            }
            try {
                Object rankObject2 = o2.getProperty(Constants.SERVICE_RANKING);
                rank1 = (rankObject2 == null) ? 0 : ((Integer) rankObject2).intValue();
            }
            catch (ClassCastException cce) {
                // No problem.
            }

            return rank1 - rank2;
        }
    };


    public ArtifactRepositoryImpl(ChangeNotifier notifier) {
        super(notifier, XML_NODE);
    }

    public List<ArtifactObject> getResourceProcessors() {
        try {
            return super.get(createFilter("(" + BundleHelper.KEY_RESOURCE_PROCESSOR_PID + "=*)"));
        }
        catch (InvalidSyntaxException e) {
            m_log.log(LogService.LOG_ERROR, "getResourceProcessors' filter returned an InvalidSyntaxException.", e);
        }
        return new ArrayList<ArtifactObject>();
    }

    @Override
    public List<ArtifactObject> get(Filter filter) {
        // Note that this excludes any Bundle artifacts which are resource processors.
        try {
            Filter extendedFilter = createFilter("(&" + filter.toString() + "(!(" + BundleHelper.KEY_RESOURCE_PROCESSOR_PID + "=*)))");
            return super.get(extendedFilter);
        }
        catch (InvalidSyntaxException e) {
            m_log.log(LogService.LOG_ERROR, "Extending " + filter.toString() + " resulted in an InvalidSyntaxException.", e);
        }
        return new ArrayList<ArtifactObject>();
    }

    @Override
    public List<ArtifactObject> get() {
        // Note that this excludes any Bundle artifacts which are resource processors.
        try {
            return super.get(createFilter("(!(" + RepositoryUtil.escapeFilterValue(BundleHelper.KEY_RESOURCE_PROCESSOR_PID) + "=*))"));
        }
        catch (InvalidSyntaxException e) {
            m_log.log(LogService.LOG_ERROR, "get's filter returned an InvalidSyntaxException.", e);
        }
        return new ArrayList<ArtifactObject>();
    }

    @Override
    ArtifactObjectImpl createNewInhabitant(Map<String, String> attributes) {
        ArtifactHelper helper = getHelper(attributes.get(ArtifactObject.KEY_MIMETYPE));
        return new ArtifactObjectImpl(helper.checkAttributes(attributes), helper.getMandatoryAttributes(), this, this);
    }

    @Override
    ArtifactObjectImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        ArtifactHelper helper = getHelper(attributes.get(ArtifactObject.KEY_MIMETYPE));
        return new ArtifactObjectImpl(helper.checkAttributes(attributes), helper.getMandatoryAttributes(), tags, this, this);
    }

    @Override
    ArtifactObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new ArtifactObjectImpl(reader, this, this);
    }

    /**
     * Helper method for this repository's inhabitants, which finds the necessary helpers.
     * @param mimetype The mimetype for which a helper should be found.
     * @return An artifact helper for the given mimetype.
     * @throws IllegalArgumentException when the mimetype is invalid, or no helpers are available.
     */
    ArtifactHelper getHelper(String mimetype) {
        synchronized(m_helpers) {
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
        synchronized(m_helpers) {
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
        synchronized(m_helpers) {
            if ((mimetype == null) || (mimetype.length() == 0)) {
                m_log.log(LogService.LOG_WARNING, "An ArtifactHelper is being removed without a proper mimetype.");
            }
            else {
                m_helpers.remove(mimetype.toLowerCase());
            }
        }
    }

    /**
     * Utility function that takes either a URL or a String representing a mimetype,
     * and returns the corresponding <code>ArtifactHelper</code>, <code>ArtifactRecognizer</code>
     * and, if not specified, the mimetype.
     * @param input Either a <code>URL</code> pointing to a physical artifact, or a <code>String</code>
     * representing a mime type.
     * @return A mapping from a class (<code>ArtifactRecognizer</code>, <code>ArtifactHelper</code> or
     * <code>String</code> to an instance of that class as a result.
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
        ServiceReference[] refs = null;
        try {
            refs = m_context.getServiceReferences(ArtifactRecognizer.class.getName(), null);
        }
        catch (InvalidSyntaxException e) {
            // We do not pass in a filter, so this should not happen.
            m_log.log(LogService.LOG_WARNING, "A null filter resulted in an InvalidSyntaxException from getServiceReferences.");
        }

        if (refs == null) {
            throw new IllegalArgumentException("There are no artifact recognizers available.");
        }

        // If available, sort the references by service ranking.
        Arrays.sort(refs, SERVICE_RANK_COMPARATOR);

        // Check all referenced services to find one that matches our input.
        ArtifactRecognizer recognizer = null;
        String foundMimetype = null;
        for (ServiceReference ref : refs) {
            ArtifactRecognizer candidate = (ArtifactRecognizer) m_context.getService(ref);
            if (mimetype != null) {
                if (candidate.canHandle(mimetype)) {
                    recognizer = candidate;
                    break;
                }
            }
            else {
                String candidateMime = candidate.recognize(url);
                if (candidateMime != null) {
                    foundMimetype = candidateMime;
                    recognizer = candidate;
                    break;
                }
            }
        }

        if (recognizer == null) {
            throw new IllegalArgumentException("There is no artifact recognizer that recognizes artifact " + ((url == null) ? "(null)" : url.toString()));
        }

        // Package the results in the map.
        Map<Class<?>, Object> result = new HashMap<Class<?>, Object>();
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
            //too bad... Nothing to do now.
            return false;
        }
    }

    public ArtifactObject importArtifact(URL artifact, boolean upload) throws IllegalArgumentException, IOException {
        try {
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
        catch (IllegalArgumentException iae) {
            m_log.log(LogService.LOG_INFO, "Error importing artifact: " + iae.getMessage());
            throw iae;
        }
        catch (IOException ioe) {
            m_log.log(LogService.LOG_INFO, "Error storing artifact: " + ioe.getMessage());
            throw ioe;
        }
    }

    public ArtifactObject importArtifact(URL artifact, String mimetype, boolean upload) throws IllegalArgumentException, IOException {
        try {
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
        catch (IllegalArgumentException iae) {
            m_log.log(LogService.LOG_INFO, "Error importing artifact: " + iae.getMessage());
            throw iae;
        }
        catch (IOException ioe) {
            m_log.log(LogService.LOG_INFO, "Error storing artifact: " + ioe.getMessage());
            throw ioe;
        }
    }

    private ArtifactObject importArtifact(URL artifact, ArtifactRecognizer recognizer, ArtifactHelper helper, String mimetype, boolean overwrite, boolean upload) throws IOException {
        Map<String, String> attributes = recognizer.extractMetaData(artifact);
        Map<String, String> tags = new HashMap<String, String>();

        helper.checkAttributes(attributes);

        URL location;
        if (upload) {
            location = upload(artifact, mimetype);
        }
        else {
            location = artifact;
        }

        attributes.put(ArtifactObject.KEY_URL, location.toString());
        attributes.put(ArtifactObject.KEY_ARTIFACT_DESCRIPTION, "");
        if (overwrite) {
            attributes.put(ArtifactObject.KEY_MIMETYPE, mimetype);
        }

        return create(attributes, tags);
    }

    /**
     * Helper method which checks a given URL for 'validity', that is, does this URL point
     * to something that can be read.
     * @param artifact A URL pointing to an artifact.
     * @throws IllegalArgumentException when the URL does not point to a valid file.
     */

    private void checkURL(URL artifact) throws IllegalArgumentException {
        // First, check whether we can actually reach something from this URL.
        InputStream is = null;
        try {
            is = artifact.openStream();
        }
        catch (IOException ioe) {
            throw new IllegalArgumentException("Artifact " + artifact.toString() + "does not point to a valid file.");
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
        }

        // Then, check whether the name is legal.
        for (byte b : artifact.toString().substring(artifact.toString().lastIndexOf('/') + 1).getBytes()) {
            if (!(((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z')) || ((b >= '0') && (b <= '9')) || (b == '.') || (b == '-') || (b == '_'))) {
                throw new IllegalArgumentException("Artifact " + artifact.toString() + "'s name contains an illegal character '" + new String(new byte[] {b}) + "'");
            }
        }

    }

    /**
     * Uploads an artifact to the OBR.
     * @param artifact URL pointing to the local artifact.
     * @param mimetype The mimetype of this artifact.
     * @return The persistent URL of this artifact.
     * @throws IOException for any problem uploading the artifact.
     */
    private URL upload(URL artifact, String mimetype) throws IOException {
        if (m_obrBase == null) {
            throw new IOException("There is no storage available for this artifact.");
        }

        InputStream input = null;
        OutputStream output = null;
        URL url = null;
        try {
            input = artifact.openStream();
            url = new URL(m_obrBase, new File(artifact.getFile()).getName());
            URLConnection connection = url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", mimetype);
            output = connection.getOutputStream();
            byte[] buffer = new byte[4 * 1024];
            for (int count = input.read(buffer); count != -1; count = input.read(buffer)) {
                output.write(buffer, 0, count);
            }
            output.close();
            if (connection instanceof HttpURLConnection) {
                int responseCode = ((HttpURLConnection) connection).getResponseCode();
                switch (responseCode) {
                    case HttpURLConnection.HTTP_OK :
                        break;
                    case HttpURLConnection.HTTP_CONFLICT:
                        throw new IOException("Artifact already exists in storage.");
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:
                        throw new IOException("The storage server returned an internal server error.");
                    default:
                        throw new IOException("The storage server returned code " + responseCode + " writing to " + url.toString());
                }
            }
        }
        catch (IOException ioe) {
            throw new IOException("Error importing artifact " + artifact.toString() + ": " + ioe.getMessage());
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
            if (output != null) {
                try {
                    output.close();
                }
                catch (Exception ex) {
                    // Not much we can do
                }
            }
        }

        return url;
    }

    public void setObrBase(URL obrBase) {
        m_obrBase = obrBase;
    }

    public String preprocessArtifact(ArtifactObject artifact, GatewayObject gateway, String gatewayID, String version) throws IOException {
        ArtifactPreprocessor preprocessor = getHelper(artifact.getMimetype()).getPreprocessor();
        if (preprocessor == null) {
            return artifact.getURL();
        }
        else {
            return preprocessor.preprocess(artifact.getURL(), new GatewayPropertyResolver(gateway), gatewayID, version, m_obrBase);
        }
    }

    public boolean needsNewVersion(ArtifactObject artifact, GatewayObject gateway, String gatewayID, String fromVersion) {
        ArtifactPreprocessor preprocessor = getHelper(artifact.getMimetype()).getPreprocessor();
        if (preprocessor == null) {
            return false;
        }
        else {
            return preprocessor.needsNewVersion(artifact.getURL(), new GatewayPropertyResolver(gateway), gatewayID, fromVersion);
        }
    }

    private static class GatewayPropertyResolver implements PropertyResolver {

        private final GatewayObject m_go;

        public GatewayPropertyResolver(GatewayObject go) {
            m_go = go;
        }

        public String get(String key) {
            return get(key, m_go);
        }

        private String get(String key, RepositoryObject ro) {
            // Is it in this object?
            String result = findKeyInObject(ro, key);
            if (result != null) {
                return result;
            }

            // Is it in one of the children?
            List<RepositoryObject> children = getChildren(ro);
            for (RepositoryObject child : children) {
                result = findKeyInObject(child, key);
                if (result != null) {
                    return result;
                }
            }

            // Not found yet? then continue to the next level recursively.
            for (RepositoryObject child : children) {
                result = get(key, child);
                if (result != null) {
                    return result;
                }
            }
            return result;
        }

        private List getChildren(RepositoryObject ob) {
            if (ob instanceof GatewayObject) {
                return ((GatewayObject) ob).getLicenses();
            }
            else if (ob instanceof LicenseObject) {
                return ((LicenseObject) ob).getGroups();
            }
            else if (ob instanceof GroupObject) {
                return ((GroupObject) ob).getArtifacts();
            }
            return new ArrayList();
        }

        private String findKeyInObject(RepositoryObject ro, String key) {
            String result;
            if ((result = ro.getAttribute(key)) != null) {
                return result;
            }
            if ((result = ro.getTag(key)) != null) {
                return result;
            }
            return null;
        }

    }

    public URL getObrBase() {
        return m_obrBase;
    }
}
