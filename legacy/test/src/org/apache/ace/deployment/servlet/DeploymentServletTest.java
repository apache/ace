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
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.streamgenerator.StreamGenerator;
import org.osgi.service.log.LogService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeploymentServletTest {

    private DeploymentServlet m_servlet;

    private HttpServletRequest m_request;
    private String m_requestCurrentParameter;
    private String m_requestPathInfo;

    private HttpServletResponse m_response;
    private ByteArrayOutputStream m_responseOutputStream;
    private int m_responseStatus;

    private DeploymentProvider m_provider;
    private List<String> m_providerVersions;

    private StreamGenerator m_generator;
    private InputStream m_generatorResultStream;
    private String m_generatorId;
    private String m_generatorFromVersion;
    private String m_generatorToVersion;


    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        // resets variables that store results of tests
        m_generatorResultStream = null;
        m_generatorId = null;
        m_generatorFromVersion = null;
        m_generatorToVersion = null;
        m_providerVersions = null;

        // create mock stream generator
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


        // create mock deployment provider
        m_provider = new DeploymentProvider() {
            public List<ArtifactData> getBundleData(String gatewayId, String version) throws IllegalArgumentException {
                return null; // not used
            }
            public List<ArtifactData> getBundleData(String gatewayId, String versionFrom, String versionTo) throws IllegalArgumentException {
                return null; // not used
            }
            public List<String> getVersions(String gatewayId) throws IllegalArgumentException {
                if (m_providerVersions == null) {
                    throw new IllegalArgumentException();
                }
                return m_providerVersions;
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
//                "/" + m_requestGatewayID + "/versions/" + m_requestRequestedVersion;
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
            @SuppressWarnings("unused")
            public void sendError(int status) {
                m_responseStatus = status;
            }
            @SuppressWarnings("unused")
            public void sendError(int status, String desc) {
                sendError(status);
            }
        });

        m_responseStatus = HttpServletResponse.SC_OK;
        m_responseOutputStream = new ByteArrayOutputStream();

        // create the instance to test
        m_servlet = new DeploymentServlet();
        configureObject(m_servlet, LogService.class);
        configureObject(m_servlet, StreamGenerator.class, m_generator);
        configureObject(m_servlet, DeploymentProvider.class, m_provider);

    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
    }

    @Test(groups = { UNIT })
    public void getDataForExistingGateway() throws Exception {
        m_requestPathInfo = "/GW1/versions/2.0.0";
        m_generatorResultStream = new ByteArrayInputStream(new byte[10]);
        m_providerVersions = new ArrayList<String>();
        m_providerVersions.add("2.0.0");
        m_servlet.doGet(m_request, m_response);

        // make sure the request went fine
        assert m_responseStatus == HttpServletResponse.SC_OK : "We should have got response code " + HttpServletResponse.SC_OK + " and we got " + m_responseStatus;
        assert m_responseOutputStream.size() == 10 : "We should have got a (dummy) deployment package of 10 bytes.";
        assert m_generatorId.equals("GW1") : "Wrong gateway ID.";
        assert m_generatorToVersion.equals("2.0.0") : "Wrong version.";
    }

    @Test(groups = { UNIT })
    public void getFixPackageForExistingGateway() throws Exception {
        m_requestPathInfo = "/GW1/versions/2.0.0";
        m_requestCurrentParameter = "1.0.0";
        m_generatorResultStream = new ByteArrayInputStream(new byte[10]);
        m_providerVersions = new ArrayList<String>();
        m_providerVersions.add("2.0.0");
        m_servlet.doGet(m_request, m_response);

        // make sure the request went fine
        assert m_responseStatus == HttpServletResponse.SC_OK : "We should have got response code " + HttpServletResponse.SC_OK + " and we got " + m_responseStatus;
        assert m_responseOutputStream.size() == 10 : "We should have got a (dummy) deployment package of 10 bytes.";
        assert m_generatorId.equals("GW1") : "Wrong gateway ID.";
        assert m_generatorToVersion.equals("2.0.0") : "Wrong version.";
        assert m_generatorFromVersion.equals("1.0.0") : "Wrong current version.";
    }

    @Test(groups = { UNIT })
    public void getDataForNonExistingGateway() throws Exception {
        m_requestPathInfo = "/GW?/versions/2.0.0";
        m_servlet.doGet(m_request, m_response);
        assert m_responseStatus == HttpServletResponse.SC_NOT_FOUND : "We should have gotten response code" + HttpServletResponse.SC_NOT_FOUND + ", actual code: " + m_responseStatus;
    }

    @Test(groups = { UNIT })
    public void getDataForBadURL() throws Exception {
        HttpServletRequest garbage = createMockObjectAdapter(HttpServletRequest.class, new Object() {
            @SuppressWarnings("unused")
            public String getPathInfo() {
                return "/";
            }
        });
        m_servlet.doGet(garbage, m_response);
        assert m_responseStatus == HttpServletResponse.SC_BAD_REQUEST : "We should have gotten response code " + HttpServletResponse.SC_NOT_FOUND + ", actual code: " + m_responseStatus;
    }


    @Test(groups = { UNIT })
    public void getVersionsExistingGateway() throws Exception {
        m_requestPathInfo = "/GW1/versions";
        m_providerVersions = new ArrayList<String>();
        m_providerVersions.add("2.0.0");
        m_servlet.doGet(m_request, m_response);
        assert "2.0.0\n".equals(m_responseOutputStream.toString()) : "Expected to get version 2.0.0 in the response";
    }

    @Test(groups = { UNIT })
    public void getVersionsNonExistingGateway() throws Exception {
        m_requestPathInfo = "/GW1/versions";
        m_servlet.doGet(m_request, m_response);
        assert "".equals(m_responseOutputStream.toString()) : "Expected to get an empty response";
    }

}
