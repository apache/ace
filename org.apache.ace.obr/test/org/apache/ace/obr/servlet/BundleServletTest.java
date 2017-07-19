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
package org.apache.ace.obr.servlet;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.obr.storage.BundleStore;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BundleServletTest {
    private HttpServletRequest m_request;
    private HttpServletResponse m_response;
    private ByteArrayOutputStream m_byteStream = new ByteArrayOutputStream();
    protected int m_status;
    private MockBundleStore m_store;
    private BundleServlet m_bundleServlet;
    private File m_testFile;
    private String m_requestFile;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws IOException {
        m_testFile = createRandomFileWithContent();
        m_store = new MockBundleStore(new FileInputStream(m_testFile));
        m_bundleServlet = new BundleServlet();

        TestUtils.configureObject(m_bundleServlet, LogService.class);
        TestUtils.configureObject(m_bundleServlet, BundleStore.class, m_store);

        m_request = TestUtils.createMockObjectAdapter(HttpServletRequest.class, new Object() {

            @SuppressWarnings("unused")
            public String getScheme() {
                return "http";
            }

            @SuppressWarnings("unused")
            public String getServerName() {
                return "localhost";
            }

            @SuppressWarnings("unused")
            public int getServerPort() {
                return 9999;
            }

            @SuppressWarnings("unused")
            public String getParameter(String param) {
                return m_requestFile;
            }

            @SuppressWarnings("unused")
            public String getPathInfo() {
                return "/" + m_requestFile;
            }

            @SuppressWarnings("unused")
            public StringBuffer getRequestURL() {
                return new StringBuffer("http://localhost:" + TestConstants.PORT + "/obr/" + m_requestFile);
            }

            @SuppressWarnings("unused")
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    int i = 0;

                    @Override
                    public int read() throws IOException {
                        if (i == 0) {
                            i++;
                            return 'a';
                        }
                        else {
                            return -1;
                        }
                    }

                    @Override
                    public boolean isFinished() {
                        return i > 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener l) {
                        // nop
                    }
                };
            }
        });

        // create a HttpServletResponse mock object
        m_response = TestUtils.createMockObjectAdapter(HttpServletResponse.class, new Object() {
            @SuppressWarnings("unused")
            public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        m_byteStream.write(b);
                    }

                    @Override
                    public void println(String s) throws IOException {
                        for (int i = 0; i < s.length(); i++) {
                            m_byteStream.write(s.charAt(i));
                        }
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

            @SuppressWarnings("unused")
            public void setStatus(int status) {
                m_status = status;
            }

            @SuppressWarnings("unused")
            public void sendError(int status) {
                m_status = status;
            }

            @SuppressWarnings("unused")
            public void sendError(int status, String description) {
                m_status = status;
            }
        });
        m_status = HttpServletResponse.SC_OK;
    }

    private File createRandomFileWithContent() throws IOException {
        OutputStream fileOut = null;
        File file = null;
        try {
            file = FileUtils.createTempFile(null);
            fileOut = new FileOutputStream(file);
            byte[] byteArray = new byte[12345];
            Random randomContentCreator = new Random();
            randomContentCreator.nextBytes(byteArray);
            fileOut.write(byteArray);

            return file;
        }
        finally {
            try {
                if (fileOut != null) {
                    fileOut.close();
                }
            }
            catch (IOException e) {
                throw e;
            }
        }
    }

    @Test()
    public void testGetValidResource() throws Exception {
        m_requestFile = m_testFile.getName();
        m_bundleServlet.doGet(m_request, m_response);

        assert m_status == HttpServletResponse.SC_OK : "We should have got response code " + HttpServletResponse.SC_OK + " and we got " + m_status;

        boolean checkStream = checkOutputStreamForFile();
        assert checkStream : "One stream stopped before the other one did.";
    }

    @Test()
    public void testExistsInvalidResource() throws Exception {
        m_requestFile = "UnknownFile";
        m_bundleServlet.doHead(m_request, m_response);

        assert m_status == HttpServletResponse.SC_NOT_FOUND : "We should have got response code " + HttpServletResponse.SC_NOT_FOUND + " and we got " + m_status;
    }

    @Test()
    public void testExistsValidResource() throws Exception {
        m_requestFile = "KnownFile";
        m_bundleServlet.doHead(m_request, m_response);

        assert m_status == HttpServletResponse.SC_OK : "We should have got response code " + HttpServletResponse.SC_NOT_FOUND + " and we got " + m_status;
    }

    @Test()
    public void testGetInValidResource() throws Exception {
        m_requestFile = "UnknownFile";
        m_bundleServlet.doGet(m_request, m_response);

        assert m_status == HttpServletResponse.SC_NOT_FOUND : "We should have got response code " + HttpServletResponse.SC_NOT_FOUND + " and we got " + m_status;
    }

    @Test()
    public void testPostResource() throws Exception {
        m_requestFile = "NewFile";
        m_bundleServlet.doPost(m_request, m_response);
        assert m_status == HttpServletResponse.SC_CREATED;
        m_requestFile = "ExistingFile";
        m_bundleServlet.doPost(m_request, m_response);
        assert m_status == HttpServletResponse.SC_CONFLICT;
    }

    @Test()
    public void testRemoveResource() throws Exception {
        m_requestFile = "RemoveMe";
        m_bundleServlet.doDelete(m_request, m_response);
        assert m_status == HttpServletResponse.SC_OK;
        m_requestFile = "NonExistingFile";
        m_bundleServlet.doDelete(m_request, m_response);
        assert m_status == HttpServletResponse.SC_NOT_FOUND;
    }

    @Test()
    public void testRemoveResourceInPath() throws Exception {
        m_requestFile = "path/to/file";
        m_bundleServlet.doDelete(m_request, m_response);
        assert m_status == HttpServletResponse.SC_OK;
        m_requestFile = "path/to/NonExistingFile";
        m_bundleServlet.doDelete(m_request, m_response);
        assert m_status == HttpServletResponse.SC_NOT_FOUND;
    }

    /**
     * Check if the output from the server is the configured file
     */
    private boolean checkOutputStreamForFile() throws Exception {
        assert m_testFile.length() == m_byteStream.size() : "Different filesize";

        InputStream inStream = null;
        InputStream outStream = null;
        try {
            // ok, the length is the same, now compare the content.
            inStream = new BufferedInputStream(m_testFile.toURI().toURL().openStream());
            outStream = new BufferedInputStream(new ByteArrayInputStream(m_byteStream.toByteArray()));
            int inByte = inStream.read();
            int outByte = outStream.read();
            while ((inByte != -1) && (outByte != -1)) {
                assert inByte == outByte : "Unexpected Stream content found";
                inByte = inStream.read();
                outByte = outStream.read();
            }
            return inByte == outByte;
        }
        finally {
            try {
                if (inStream != null) {
                    inStream.close();
                }
            }
            finally {
                if (outStream != null) {
                    outStream.close();
                }
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    protected void tearDown() {
        m_byteStream = new ByteArrayOutputStream();
        m_testFile.delete();
    }
}
