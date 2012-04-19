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
package org.apache.ace.processlauncher.test.util;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.apache.ace.processlauncher.test.impl.TestUtil.sleep;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.ace.processlauncher.util.InputStreamRedirector;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test cases for {@link InputStreamRedirector}.
 */
public class InputStreamRedirectorTest {

    /**
     * Tests that when an EOF is read on the input stream, the output stream is closed as well.
     * 
     * @throws IOException not part of this test case.
     */
    @Test
    public void testInputStreamEOFCausesOutputStreamToBeClosedOk() throws IOException {
        InputStream myIS = new ByteArrayInputStream("hello world!".getBytes());
        OutputStream mockOS = mock(OutputStream.class);

        InputStreamRedirector redirector = new InputStreamRedirector(myIS, mockOS);
        redirector.run();

        verify(mockOS).close();
    }

    /**
     * Tests that the input stream is 1:1 copied to the given output stream.
     * 
     * @throws IOException not part of this test case.
     */
    @Test
    public void testInputStreamIsVerbatimelyCopiedToOutputStreamOk() throws IOException {
        String input = "hello world!";

        InputStream myIS = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream myOS = new ByteArrayOutputStream();

        InputStreamRedirector redirector = new InputStreamRedirector(myIS, myOS);
        redirector.run();

        assertEquals(input, myOS.toString());
    }

    /**
     * Tests that we can interrupt a redirector and that it ceases its work.
     * 
     * @throws Exception not part of this test case.
     */
    @Test
    public void testInterruptRedirectorOk() throws Exception {
        InputStream myIS = createBlockingInputStream();
        OutputStream myOS = mock(OutputStream.class);

        InputStreamRedirector redirector = new InputStreamRedirector(myIS, myOS);

        Thread redirectorThread = new Thread(redirector);
        redirectorThread.start();

        // Sleep for a little while to ensure everything is up and running...
        sleep(100);

        redirectorThread.interrupt();

        // Wait until the thread is really finished...
        redirectorThread.join(1000);

        verify(myIS, atLeast(1)).read(Mockito.<byte[]>any(), anyInt(), anyInt());
        verify(myOS).close();
    }

    /**
     * Tests that we can recover when the input stream throws an I/O exception.
     * 
     * @throws Exception not part of this test case.
     */
    @Test
    public void testRecoverFromExceptionInInputStreamWithoutOutputStreamOk() throws Exception {
        InputStream myIS = createExceptionThrowingInputStream();
        ByteArrayOutputStream myOS = new ByteArrayOutputStream();

        InputStreamRedirector redirector = new InputStreamRedirector(myIS, myOS);

        redirector.run();

        verify(myIS, atLeast(1)).read(Mockito.<byte[]>any(), anyInt(), anyInt());

        // Verify that the exception is indeed logged...
        String stdout = myOS.toString();

        assertTrue(stdout.contains("IGNORE ME! TEST EXCEPTION!"));
    }

    /**
     * Tests that we can recover when the input stream throws an I/O exception.
     * 
     * @throws Exception not part of this test case.
     */
    @Test
    public void testRecoverFromExceptionInInputStreamWithOutputStreamOk() throws Exception {
        InputStream myIS = createExceptionThrowingInputStream();
        OutputStream myOS = mock(OutputStream.class);

        InputStreamRedirector redirector = new InputStreamRedirector(myIS, myOS);

        redirector.run();

        verify(myIS, atLeast(1)).read(Mockito.<byte[]>any(), anyInt(), anyInt());
        verify(myOS).close();
    }

    /**
     * Tests that we can recover when the input stream throws an I/O exception.
     * 
     * @throws Exception not part of this test case.
     */
    @Test
    public void testWithoutOutputStreamOk() throws Exception {
        InputStream myIS = new ByteArrayInputStream("hello world!".getBytes());

        InputStreamRedirector redirector = new InputStreamRedirector(myIS);

        redirector.run();
    }

    /**
     * Creates an input stream that keeps pretending its returning data when its
     * {@link InputStream#read(byte[])} method is called.
     * 
     * @return a mocked {@link InputStream} instance, never <code>null</code>.
     */
    private InputStream createBlockingInputStream() throws IOException {
        InputStream is = mock(InputStream.class);
        when(is.read(Mockito.<byte[]>any(), anyInt(), anyInt())).thenReturn(Integer.valueOf(10));
        return is;
    }

    /**
     * Creates an input stream that keeps pretending its returning data when its
     * {@link InputStream#read(byte[])} method is called.
     * 
     * @return a mocked {@link InputStream} instance, never <code>null</code>.
     */
    private InputStream createExceptionThrowingInputStream() throws IOException {
        InputStream is = mock(InputStream.class);
        when(is.read(Mockito.<byte[]>any(), anyInt(), anyInt())).thenThrow(
            new IOException("IGNORE ME! TEST EXCEPTION!"));
        return is;
    }
}
