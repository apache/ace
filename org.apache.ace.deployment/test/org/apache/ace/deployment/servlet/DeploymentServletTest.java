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
package org.apache.ace.deployment.servlet;

import static org.apache.ace.test.utils.TestUtils.configureObject;
import static org.apache.ace.test.utils.TestUtils.createMockObjectAdapter;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.streamgenerator.StreamGenerator;
import org.easymock.IAnswer;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeploymentServletTest {

    // the servlet under test
    private DeploymentServlet m_servlet;

    private long m_artifactSize;

    // request state
    private HttpServletRequest m_request;
    private String m_requestCurrentParameter;
    private String m_requestRangeHeader;
    private String m_requestPathInfo;

    // response state
    private HttpServletResponse m_response;
    private ByteArrayOutputStream m_responseOutputStream;
    private int m_responseStatus;
    private Map<String, String> m_responseHeaders;

    // deployment provider state
    private DeploymentProvider m_provider;

    // stream generator state
    private StreamGenerator m_generator;
    private String m_generatorId;
    private String m_generatorFromVersion;
    private String m_generatorToVersion;
    private InputStream m_generatorResultStream;

    @Test
    public void getDataForBadURL() throws Exception {
        HttpServletRequest garbage = createMockObjectAdapter(HttpServletRequest.class, new Object() {
            @SuppressWarnings("unused")
            public String getPathInfo() {
                return "/";
            }
        });
        m_servlet.doGet(garbage, m_response);
        assertResponseCode(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    public void getDataForExistingTarget() throws Exception {
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseOutput(0, 100);
        assertGeneratorTargetId("existing");
        assertGeneratorToVersion("2.0.0");
    }

    @Test
    public void getDataForNonExistingTarget() throws Exception {
        m_requestPathInfo = "/nonexisting/versions/2.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test()
    public void getDowngradeFixPackageWithNonExistingToVersion() throws Exception {
        // try to request a version range with a non-existing from-version, should cause a complete (non-fix) package to
        // be returned...
        m_requestPathInfo = "/existing/versions/1.0.0";
        m_requestCurrentParameter = "2.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void getRangeDataForExistingTarget_badHeaderValue() throws Exception {
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.16
        // If the server ignores a byte-range-spec because it is syntactically invalid, the server SHOULD treat the
        // request as if the invalid Range header field did not exist. (Normally, this means return a 200 response
        // containing the full entity).
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=a-1";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseOutput(0, 100);
    }

    @Test
    public void getRangeDataForExistingTarget_first0lastOK() throws Exception {
        // valid range starting at 0
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=0-10";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseHeaderValue("Content-Range", "bytes 0-10/*");
    }

    @Test
    public void getRangeDataForExistingTarget_firstOKlastANY() throws Exception {
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.35.1
        // If the last-byte-pos value is absent, or if the value is greater than or equal to the current length of the
        // entity-body, last-byte-pos is taken to be equal to one less than the current length of the entity- body in
        // bytes.
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=2-";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseHeaderValue("Content-Range", "bytes 2-/*");
        assertResponseOutput(2, 98);
    }

    @Test()
    public void getRangeDataForExistingTarget_firstOKlastOK() throws Exception {
        // valid range not starting at 0
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=2-50";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseHeaderValue("Content-Range", "bytes 2-50/*");
        assertResponseOutput(2, 49);
    }

    @Test
    public void getRangeDataForExistingTarget_firstOKlastOK2() throws Exception {
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.35.1
        // If the last-byte-pos value is absent, or if the value is greater than or equal to the current length of the
        // entity-body, last-byte-pos is taken to be equal to one less than the current length of the entity- body in
        // bytes.
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=2-99";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseHeaderValue("Content-Range", "bytes 2-99/*");
        assertResponseOutput(2, 98);
    }

    @Test
    public void getRangeDataForExistingTarget_firstOKlastTooBig() throws Exception {
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.35.1
        // If the last-byte-pos value is absent, or if the value is greater than or equal to the current length of the
        // entity-body, last-byte-pos is taken to be equal to one less than the current length of the entity- body in
        // bytes.
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=2-100";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseHeaderValue("Content-Range", "bytes 2-100/*");
        assertResponseOutput(2, 98);
    }

    @Test
    public void getRangeDataForExistingTarget_firstOKlastTooSmall() throws Exception {
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.35.1
        // If the last-byte-pos value is present, it MUST be greater than or equal to the first-byte-pos in that
        // byte-range-spec, or the byte- range-spec is syntactically invalid.

        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.16
        // If the server ignores a byte-range-spec because it is syntactically invalid, the server SHOULD treat the
        // request as if the invalid Range header field did not exist. (Normally, this means return a 200 response
        // containing the full entity).
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=2-1";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseOutput(0, 100);
    }

    @Test
    public void getRangeDataForExistingTarget_firstTooBiglastTooBig() throws Exception {
        // invalid range: start=toobig end=toobig
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=100-110";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertResponseHeaderValue("Content-Range", "bytes 100-110/*");
        assertResponseHeaderNotPresent("Content-Length");
        assertResponseOutput(-1, 0);
    }

    @Test
    public void getSizeForExistingTargetWithKnownSize() throws Exception {
        m_artifactSize = 10;
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_servlet.doHead(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseHeaderValue("X-ACE-DPSize", "11"); // 10 + 10%
    }

    @Test
    public void getSizeForExistingTargetWithUnknownSize() throws Exception {
        m_artifactSize = -1;
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_servlet.doHead(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseHeaderNotPresent("X-ACE-DPSize");
    }

    @Test
    public void getSizeForFixPackageExistingTargetWithKnownSize() throws Exception {
        m_artifactSize = 10;
        m_requestCurrentParameter = "2.0.0";
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_servlet.doHead(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseHeaderValue("X-ACE-DPSize", "22"); // 20 + 10%
    }

    @Test
    public void getSizeForNonExistingTarget() throws Exception {
        m_artifactSize = 10;

        m_requestPathInfo = "/existing/versions/1.0.0";
        m_servlet.doHead(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseHeaderNotPresent("X-ACE-DPSize");
    }

    @Test()
    public void getUpgradeFixPackageWithExistingFromVersion() throws Exception {
        // try to request a version range with an existing from-version, should cause a fix package to be returned...
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestCurrentParameter = "2.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseOutput(0, 100);
        assertGeneratorTargetId("existing");
        assertGeneratorToVersion("2.0.0");
        assertGeneratorFromVersion("2.0.0");
    }

    @Test()
    public void getUpgradeFixPackageWithNonExistingFromVersion() throws Exception {
        // try to request a version range with a non-existing from-version, should cause a complete (non-fix) package to
        // be returned...
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestCurrentParameter = "1.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseOutput(0, 100);
        assertGeneratorTargetId("existing");
        assertGeneratorToVersion("2.0.0");
        assertGeneratorFromVersion(null);
    }

    @Test()
    public void getUpgradeFixPackageWithNonExistingToVersion() throws Exception {
        // try to request a version range with a non-existing from-version, should cause a complete (non-fix) package to
        // be returned...
        m_requestPathInfo = "/existing/versions/3.0.0";
        m_requestCurrentParameter = "2.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test()
    public void getUpgradeWithExistingToVersion() throws Exception {
        // try to request a version range with a non-existing from-version, should cause a complete (non-fix) package to
        // be returned...
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestCurrentParameter = null;
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseOutput(0, 100);
        assertGeneratorTargetId("existing");
        assertGeneratorToVersion("2.0.0");
        assertGeneratorFromVersion(null);
    }

    @Test()
    public void getUpgradeWithNonExistingToVersion() throws Exception {
        // try to request a version range with a non-existing from-version, should cause a complete (non-fix) package to
        // be returned...
        m_requestPathInfo = "/existing/versions/3.0.0";
        m_requestCurrentParameter = null;
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void getVersionsExistingTarget() throws Exception {
        m_requestPathInfo = "/existing/versions";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertEquals(m_responseOutputStream.toString(), "2.0.0\n", "Expected to get version 2.0.0 in the response");
    }

    @Test
    public void getVersionsNonExistingTarget() throws Exception {
        m_requestPathInfo = "/nonexisting/versions";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_NOT_FOUND);
        assertResponseOutput(-1, 0);
    }

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        final Map<String, List<String>> providerVersions = new HashMap<>();
        providerVersions.put("existing", Arrays.asList("2.0.0"));

        final ArtifactData artifactData = createMock(ArtifactData.class);
        expect(artifactData.getSize()).andAnswer(new IAnswer<Long>() {
            @Override
            public Long answer() throws Throwable {
                return DeploymentServletTest.this.m_artifactSize;
            }
        }).anyTimes();
        replay(artifactData);

        m_provider = new DeploymentProvider() {
            public List<ArtifactData> getBundleData(String targetId, String version) throws IllegalArgumentException {
                List<String> versions = providerVersions.get(targetId);
                if (versions != null && versions.contains(version)) {
                    return Arrays.asList(artifactData);
                }
                return Collections.emptyList();
            }

            public List<ArtifactData> getBundleData(String targetId, String versionFrom, String versionTo) throws IllegalArgumentException {
                List<String> versions = providerVersions.get(targetId);
                if (versions != null && versions.contains(versionFrom) && versions.contains(versionTo)) {
                    return Arrays.asList(artifactData, artifactData);
                }
                return Collections.emptyList();
            }

            public List<String> getVersions(String targetId) throws IllegalArgumentException {
                if (providerVersions.containsKey(targetId)) {
                    return providerVersions.get(targetId);
                }
                throw new IllegalArgumentException();
            }
        };

        m_generator = new StreamGenerator() {
            public InputStream getDeploymentPackage(String id, String version) throws IOException {
                if (m_generatorResultStream == null) {
                    throw new IOException("No data for " + id + " " + version);
                }
                m_generatorId = id;
                m_generatorToVersion = version;
                return m_generatorResultStream;
            }

            public InputStream getDeploymentPackage(String id, String fromVersion, String toVersion) throws IOException {
                if (m_generatorResultStream == null) {
                    throw new IOException("No delta for " + id + " " + fromVersion + " " + toVersion);
                }
                m_generatorId = id;
                m_generatorFromVersion = fromVersion;
                m_generatorToVersion = toVersion;
                return m_generatorResultStream;
            }
        };

        // create a HttpServletRequest mock object
        m_request = createMockObjectAdapter(HttpServletRequest.class, new Object() {
            @SuppressWarnings("unused")
            public String getHeader(String name) {
                if (name.equals("Range")) {
                    return m_requestRangeHeader;
                }
                return null;
            }

            @SuppressWarnings("unused")
            public String getParameter(String param) {
                if (param.equals(DeploymentServlet.CURRENT)) {
                    return m_requestCurrentParameter;
                }
                return null;
            }

            @SuppressWarnings("unused")
            public String getPathInfo() {
                return m_requestPathInfo;
            }
        });

        // create a HttpServletResponse mock object
        m_response = createMockObjectAdapter(HttpServletResponse.class, new Object() {
            @SuppressWarnings("unused")
            public void addHeader(String name, String value) {
                m_responseHeaders.put(name, value);
            }

            @SuppressWarnings("unused")
            public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        m_responseOutputStream.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener l) {
                        // nop
                    }
                };
            }

            public synchronized void sendError(int status) {
                m_responseStatus = status;
            }

            @SuppressWarnings("unused")
            public void sendError(int status, String desc) {
                sendError(status);
            }

            @SuppressWarnings("unused")
            public void setHeader(String name, String value) {
                m_responseHeaders.put(name, value);
            }

            public void setStatus(int status) {
                m_responseStatus = status;
            }

            @SuppressWarnings("unused")
            public void setStatus(int status, String desc) {
                setStatus(status);
            }
        });

        // create the instance to test
        m_servlet = new DeploymentServlet();
        configureObject(m_servlet, LogService.class);
        configureObject(m_servlet, StreamGenerator.class, m_generator);
        configureObject(m_servlet, DeploymentProvider.class, m_provider);

        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i + 1);
        }

        // set the default state
        m_generatorResultStream = new ByteArrayInputStream(data);
        m_requestCurrentParameter = null;
        m_generatorId = null;
        m_generatorFromVersion = null;
        m_generatorToVersion = null;

        m_responseStatus = HttpServletResponse.SC_OK;
        m_responseHeaders = new HashMap<>();
        m_requestRangeHeader = null;
        m_responseOutputStream = new ByteArrayOutputStream();
    }

    private void assertGeneratorFromVersion(String version) {
        assertEquals(m_generatorFromVersion, version, "Wrong from-version");
    }

    private void assertGeneratorTargetId(String id) {
        assertEquals(m_generatorId, id, "Wrong target ID");
    }

    private void assertGeneratorToVersion(String version) {
        assertEquals(m_generatorToVersion, version, "Wrong to-version");
    }

    private void assertResponseCode(int value) throws Exception {
        assertEquals(m_responseStatus, value, "Incorrect response code from server");
    }

    private void assertResponseHeaderNotPresent(String name) throws Exception {
        assertFalse(m_responseHeaders.containsKey(name), "Expected response " + name + " header to NOT be set");
    }

    private void assertResponseHeaderValue(String name, String value) throws Exception {
        assertTrue(m_responseHeaders.containsKey(name), "Expected response " + name + " header to be set");
        assertEquals(m_responseHeaders.get(name), value, "Unexpected response header");
    }

    private void assertResponseOutput(int offset, int size) throws Exception {
        byte[] data = m_responseOutputStream.toByteArray();
        assertEquals(data.length, size, "We should have got a (dummy) deployment package of");
        for (int i = 0; i < data.length; i++) {
            assertEquals(data[i], i + offset + 1);
        }
    }
}
