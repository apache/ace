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
package org.apache.ace.processlauncher.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Redirects an {@link InputStream} by reading all of its contents and writing this to a given
 * {@link OutputStream}.
 */
public class InputStreamRedirector extends Thread {

    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
        "org.apache.ace.processlauncher.debug", "false"));

    private static final int BUF_SIZE = 32;

    private final InputStream m_inputStream;
    private final OutputStream m_outputStream;

    /**
     * Creates a new {@link InputStreamRedirector} instance that redirects to /dev/null. Essentially
     * this means that all read data is immediately thrown away.
     * 
     * @param inputStream the {@link InputStream} that is to be read, cannot be <code>null</code>.
     */
    public InputStreamRedirector(final InputStream inputStream) {
        this(inputStream, null);
    }

    /**
     * Creates a new {@link InputStreamRedirector} instance that redirects to a given output stream.
     * 
     * @param inputStream the {@link InputStream} that is to be redirected, cannot be
     *        <code>null</code>;
     * @param outputStream the {@link OutputStream} that is to be redirected to, may be
     *        <code>null</code> in which case the input stream is redirected to nothing (/dev/null).
     */
    public InputStreamRedirector(final InputStream inputStream, final OutputStream outputStream) {
        this.m_inputStream = inputStream;
        this.m_outputStream = outputStream;

        setName("InputStreamRedirector-" + getId());
    }

    /**
     * Reads all bytes from the contained input stream and (optionally) writes it to the contained
     * output stream.
     */
    @Override
    public void run() {
        try {
            final byte[] buf = new byte[BUF_SIZE];

            while (!Thread.currentThread().isInterrupted()) {
                int read = m_inputStream.read(buf, 0, buf.length);
                if (read < 0) {
                    // EOF; break out of our main loop...
                    break;
                }

                if (DEBUG) {
                    System.out.write(buf, 0, read);
                }

                if (m_outputStream != null) {
                    m_outputStream.write(buf, 0, read);
                }
            }
        }
        catch (IOException ioe) {
            handleException(ioe);
        }
        finally {
            cleanUp();
        }
    }

    /**
     * Clean up of the contained output stream by closing it properly.
     */
    private void cleanUp() {
        try {
            if (this.m_outputStream != null) {
                // Ensure the last few bits are written...
                this.m_outputStream.flush();
                // Close the output stream and we're done...
                this.m_outputStream.close();
            }
        }
        catch (IOException e) {
            // Ignore; we'll assume it is being handled elsewhere...
        }
    }

    /**
     * Handles the given I/O exception by either printing it to the contained output stream,
     * otherwise to the stderr stream.
     * 
     * @param exception the exception to handle, cannot be <code>null</code>.
     */
    private void handleException(final IOException exception) {
        if (this.m_outputStream != null) {
            exception.printStackTrace(new PrintStream(this.m_outputStream));
        }
        else {
            exception.printStackTrace(System.out);
        }
    }
}
