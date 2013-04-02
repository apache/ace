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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.connectionfactory.ConnectionFactory;

/**
 * This class can be used as a base class for artifact preprocessors. It comes with its own upload() method, which will
 * be used by all artifact preprocessors anyway.
 */
public abstract class ArtifactPreprocessorBase implements ArtifactPreprocessor {

    /** 64k buffers should be enough for everybody... */
    protected static final int BUFFER_SIZE = 64 * 1024;

    protected final ConnectionFactory m_connectionFactory;
    private final ExecutorService m_executor;

    /**
     * Creates a new {@link ArtifactPreprocessorBase} instance.
     * 
     * @param connectionFactory
     *            the connection factory to use, cannot be <code>null</code>.
     */
    protected ArtifactPreprocessorBase(ConnectionFactory connectionFactory) {
        m_connectionFactory = connectionFactory;
        m_executor = Executors.newCachedThreadPool();
    }

    /**
     * Creates a new URL for given (file) name and OBR base URL.
     * 
     * @param name
     *            the name of the file to create the URL for;
     * @param obrBase
     *            the OBR base URL to use.
     * @return a new URL for the file, never <code>null</code>.
     * @throws MalformedURLException
     *             in case of invalid characters in the given name.
     */
    protected URL determineNewUrl(String name, URL obrBase) throws MalformedURLException {
        return new URL(obrBase, name);
    }

    /**
     * Silently closes the given {@link Closeable} instance.
     * 
     * @param closable
     *            the closeable to close, may be <code>null</code>.
     */
    protected final void silentlyClose(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            }
            catch (IOException e) {
                // Ignore; nothing we can/will do about here...
            }
        }
    }

    /**
     * Gets a stream to write an artifact to, which will be uploaded asynchronously to the OBR.
     * 
     * @param name
     *            The name of the artifact.
     * @param obrBase
     *            The base URL of the obr to which this artifact should be written.
     * @param inputStream
     *            the input stream with data to upload.
     */
    protected final Future<URL> uploadAsynchronously(final String name, final URL obrBase, final InputStream inputStream) {
        return m_executor.submit(new Callable<URL>() {
            public URL call() throws IOException {
                return upload(inputStream, name, obrBase);
            }
        });
    }

    /**
     * Converts a given URL to a {@link File} object.
     * 
     * @param url
     *            the URL to convert, cannot be <code>null</code>.
     * @return a {@link File} object, never <code>null</code>.
     */
    protected final File urlToFile(URL url) {
        File file;
        try {
            file = new File(url.toURI());
        }
        catch (URISyntaxException e) {
            file = new File(url.getPath());
        }
        return file;
    }

    /**
     * Uploads an artifact synchronously to an OBR.
     * 
     * @param input
     *            A inputstream from which the artifact can be read.
     * @param name
     *            The name of the artifact. If the name is not unique, an IOException will be thrown.
     * @param obrBase
     *            The base URL of the obr to which this artifact should be written.
     * @return A URL to the uploaded artifact; this is identical to calling <code>determineNewUrl(name, obrBase)</code>
     * @throws IOException
     *             If there was an error reading from <code>input</code>, or if there was a problem communicating with
     *             the OBR.
     */
    private URL upload(InputStream input, String name, URL obrBase) throws IOException {
        if (obrBase == null) {
            throw new IOException("There is no storage available for this artifact.");
        }
        if ((name == null) || (input == null)) {
            throw new IllegalArgumentException("None of the parameters can be null.");
        }

        URL url = null;
        try {
            url = determineNewUrl(name, obrBase);

            if (!urlPointsToExistingFile(url)) {
                if ("file".equals(url.getProtocol())) {
                    uploadToFile(input, url);
                }
                else {
                    uploadToRemote(input, url);
                }
            }
        }
        catch (IOException ioe) {
            throw new IOException("Error uploading " + name + ": " + ioe.getMessage());
        }
        finally {
            silentlyClose(input);
        }

        return url;
    }

    /**
     * Uploads an artifact to a local file location.
     * 
     * @param input
     *            the input stream of the (local) artifact to upload.
     * @param url
     *            the URL of the (file) artifact to upload to.
     * @throws IOException
     *             in case of I/O problems.
     */
    private void uploadToFile(InputStream input, URL url) throws IOException {
        File file = urlToFile(url);

        OutputStream output = null;

        try {
            output = new FileOutputStream(file);

            byte[] buffer = new byte[BUFFER_SIZE];
            for (int count = input.read(buffer); count != -1; count = input.read(buffer)) {
                output.write(buffer, 0, count);
            }
        }
        finally {
            silentlyClose(output);
        }
    }

    /**
     * Uploads an artifact to a remote location.
     * 
     * @param input
     *            the input stream of the (local) artifact to upload.
     * @param url
     *            the URL of the (remote) artifact to upload to.
     * @throws IOException
     *             in case of I/O problems, or when the upload was refused by the remote.
     */
    private void uploadToRemote(InputStream input, URL url) throws IOException {
        OutputStream output = null;

        try {
            URLConnection connection = m_connectionFactory.createConnection(url);
            if (connection instanceof HttpURLConnection) {
                // ACE-294: enable streaming mode causing only small amounts of memory to be
                // used for this commit. Otherwise, the entire input stream is cached into
                // memory prior to sending it to the server...
                ((HttpURLConnection) connection).setChunkedStreamingMode(8192);
            }
            connection.setDoOutput(true);

            output = connection.getOutputStream();

            byte[] buffer = new byte[BUFFER_SIZE];
            for (int count = input.read(buffer); count != -1; count = input.read(buffer)) {
                output.write(buffer, 0, count);
            }
            output.close();

            if (connection instanceof HttpURLConnection) {
                int responseCode = ((HttpURLConnection) connection).getResponseCode();
                switch (responseCode) {
                    case HttpURLConnection.HTTP_OK:
                        break;
                    case HttpURLConnection.HTTP_CONFLICT:
                        throw new IOException("Artifact already exists in storage.");
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:
                        throw new IOException("The storage server returned an internal server error.");
                    default:
                        throw new IOException("The storage server returned code " + responseCode + " writing to "
                            + url.toString());
                }
            }
        }
        finally {
            silentlyClose(output);
        }
    }

    /**
     * Determines whether the given URL points to an existing file.
     * 
     * @param url
     *            the URL to test, cannot be <code>null</code>.
     * @return <code>true</code> if the given URL points to an existing file, <code>false</code> otherwise.
     */
    private boolean urlPointsToExistingFile(URL url) {
        boolean result = false;

        if ("file".equals(url.getProtocol())) {
            result = urlToFile(url).exists();
        }
        else {
            try {
                URLConnection connection = m_connectionFactory.createConnection(url);

                if (connection instanceof HttpURLConnection) {
                    HttpURLConnection hc = (HttpURLConnection) connection;

                    // Perform a HEAD on the file, to see whether it exists...
                    hc.setRequestMethod("HEAD");
                    try {
                        int responseCode = hc.getResponseCode();
                        result = (responseCode == HttpURLConnection.HTTP_OK);
                    }
                    finally {
                        hc.disconnect();
                    }
                }
                else {
                    // In all other scenario's: try to read a single byte from the input
                    // stream, if this succeeds, we can assume the file exists...
                    InputStream is = connection.getInputStream();
                    try {
                        is.read();
                    }
                    finally {
                        silentlyClose(is);
                    }
                }
            }
            catch (IOException e) {
                // Ignore; assume file does not exist...
            }
        }

        return result;
    }
}
