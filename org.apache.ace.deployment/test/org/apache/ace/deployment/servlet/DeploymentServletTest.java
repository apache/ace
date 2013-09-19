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

import static org.apache.ace.test.utils.TestUtils.UNIT;
import static org.apache.ace.test.utils.TestUtils.configureObject;
import static org.apache.ace.test.utils.TestUtils.createMockObjectAdapter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.streamgenerator.StreamGenerator;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DeploymentServletTest {

    // the servlet under test
    private DeploymentServlet m_servlet;

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
    private Map<String, List<String>> m_providerVersions;

    // stream generator state
    private StreamGenerator m_generator;
    private String m_generatorId;
    private String m_generatorFromVersion;
    private String m_generatorToVersion;
    private InputStream m_generatorResultStream;

    @BeforeTest
    protected void setUpOnce() throws Exception {

        List<String> existingTargetVersions = new ArrayList<String>();
        existingTargetVersions.add("2.0.0");
        m_providerVersions = new HashMap<String, List<String>>();
        m_providerVersions.put("existing", existingTargetVersions);

        m_provider = new DeploymentProvider() {
            public List<ArtifactData> getBundleData(String targetId, String version) throws IllegalArgumentException {
                return null; // not used
            }

            public List<ArtifactData> getBundleData(String targetId, String versionFrom, String versionTo) throws IllegalArgumentException {
                return null; // not used
            }

            public List<String> getVersions(String targetId) throws IllegalArgumentException {
                if (m_providerVersions.containsKey(targetId)) {
                    return m_providerVersions.get(targetId);
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

            @SuppressWarnings("unused")
            public String getHeader(String name) {
                if (name.equals("Range")) {
                    return m_requestRangeHeader;
                }
                return null;
            }
        });

        // create a HttpServletResponse mock object
        m_response = createMockObjectAdapter(HttpServletResponse.class, new Object() {
            @SuppressWarnings("unused")
            public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        m_responseOutputStream.write(b);
                    }
                };
            }

            public void sendError(int status) {
                m_responseStatus = status;
            }

            @SuppressWarnings("unused")
            public void sendError(int status, String desc) {
                sendError(status);
            }

            public void setStatus(int status) {
                m_responseStatus = status;
            }

            @SuppressWarnings("unused")
            public void setStatus(int status, String desc) {
                setStatus(status);
            }

            @SuppressWarnings("unused")
            public void setHeader(String name, String value) {
                m_responseHeaders.put(name, value);
            }
        });

        // create the instance to test
        m_servlet = new DeploymentServlet();
        configureObject(m_servlet, LogService.class);
        configureObject(m_servlet, StreamGenerator.class, m_generator);
        configureObject(m_servlet, DeploymentProvider.class, m_provider);
    }

    @BeforeMethod
    protected void setUp() throws Exception {
        // set the default state
        m_generatorResultStream = new ByteArrayInputStream(new byte[100]);
        m_generatorId = null;
        m_generatorFromVersion = null;
        m_generatorToVersion = null;

        m_responseStatus = HttpServletResponse.SC_OK;
        m_responseHeaders = new HashMap<String, String>();
        m_responseOutputStream = new ByteArrayOutputStream();
    }

    @Test
    public void getDataForExistingTarget() throws Exception {
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseOutputSize(100);
        assertGeneratorTargetId("existing");
        assertGeneratorToVersion("2.0.0");
    }

    @Test
    public void getRangeDataForExistingTarget_first0lastOK() throws Exception {
        // valid range starting at 0
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=0-10";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertResponseHeaderValue("Content-Length", "11");
        assertResponseHeaderValue("Content-Range", "bytes 0-10/100");
    }

    @Test(groups = { UNIT })
    public void getRangeDataForExistingTarget_firstOKlastOK() throws Exception {
        // valid range not starting at 0
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=2-50";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertResponseHeaderValue("Content-Length", "49");
        assertResponseHeaderValue("Content-Range", "bytes 2-50/100");
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
        assertResponseHeaderValue("Content-Length", "98");
        assertResponseHeaderValue("Content-Range", "bytes 2-99/100");
        assertResponseOutputSize(98);
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
        assertResponseHeaderValue("Content-Length", "98");
        assertResponseHeaderValue("Content-Range", "bytes 2-99/100");
        assertResponseOutputSize(98);
    }

    @Test
    public void getRangeDataForExistingTarget_firstTooBiglastTooBig() throws Exception {
        // invalid range: start=toobig end=toobig
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestRangeHeader = "bytes=100-110";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        assertResponseHeaderValue("Content-Range", "bytes */100");
        assertResponseOutputSize(0);
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
        assertResponseOutputSize(100);
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
        assertResponseOutputSize(100);
    }

    @Test(groups = { UNIT })
    public void getFixPackageForExistingTarget() throws Exception {
        m_requestPathInfo = "/existing/versions/2.0.0";
        m_requestCurrentParameter = "1.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assertResponseOutputSize(100);
        assertGeneratorTargetId("existing");
        assertGeneratorToVersion("2.0.0");
        assertGeneratorFromVersion("1.0.0");
    }

    @Test
    public void getDataForNonExistingTarget() throws Exception {
        m_requestPathInfo = "/nonexisting/versions/2.0.0";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_NOT_FOUND);
    }

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
    public void getVersionsExistingTarget() throws Exception {
        m_requestPathInfo = "/existing/versions";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_OK);
        assert "2.0.0\n".equals(m_responseOutputStream.toString()) : "Expected to get version 2.0.0 in the response";
    }

    @Test
    public void getVersionsNonExistingTarget() throws Exception {
        m_requestPathInfo = "/nonexisting/versions";
        m_servlet.doGet(m_request, m_response);
        assertResponseCode(HttpServletResponse.SC_NOT_FOUND);
        assertResponseOutputSize(0);
    }

    private void assertResponseCode(int value) throws Exception {
        assert m_responseStatus == value : "We should have got response code " + value + " but got " + m_responseStatus;
    }

    private void assertResponseHeaderValue(String name, String value) throws Exception {
        assert m_responseHeaders.containsKey(name) : "Expected response " + name + " header to be set";
        assert m_responseHeaders.get(name).equals(value) : "Expected " + name + " header with value '" + value + "' and got '" + m_responseHeaders.get(name) + "'";
    }

    private void assertResponseOutputSize(long size) throws Exception {
        assert m_responseOutputStream.size() == size : "We should have got a (dummy) deployment package of " + size + " bytes but got " + m_responseOutputStream.size();
    }

    private void assertGeneratorTargetId(String id) {
        assert m_generatorId.equals(id) : "Wrong target ID.";
    }

    private void assertGeneratorToVersion(String version) {
        assert m_generatorToVersion.equals(version) : "Wrong version.";
    }

    private void assertGeneratorFromVersion(String version) {
        assert m_generatorFromVersion.equals(version) : "Wrong version.";
    }
}
