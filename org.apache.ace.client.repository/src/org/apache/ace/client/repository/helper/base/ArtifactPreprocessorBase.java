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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.connectionfactory.ConnectionFactory;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * This class can be used as a base class for artifact preprocessors. It comes with its own upload() method, which will
 * be used by all artifact preprocessors anyway.
 */
@ConsumerType
public abstract class ArtifactPreprocessorBase implements ArtifactPreprocessor {

    /** 64k buffers should be enough for everybody... */
    protected static final int BUFFER_SIZE = 64 * 1024;

    protected final ConnectionFactory m_connectionFactory;

    /**
     * Creates a new {@link ArtifactPreprocessorBase} instance.
     * 
     * @param connectionFactory
     *            the connection factory to use, cannot be <code>null</code>.
     */
    protected ArtifactPreprocessorBase(ConnectionFactory connectionFactory) {
        m_connectionFactory = connectionFactory;
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
    String upload(InputStream input, String name, String mimeType, URL obrBase) throws IOException {
        if (obrBase == null) {
            throw new IOException("There is no storage available for this artifact.");
        }
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null.");
        }
        if (input == null) {
            throw new IllegalArgumentException("Input stream cannot be null.");
        }

        OutputStream output = null;
        String location = null;
        try {
            URL url = new URL(obrBase, "?filename=" + name + "&replace=true");
            URLConnection connection = m_connectionFactory.createConnection(url);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);

            connection.setRequestProperty("Content-Type", mimeType);
            if (connection instanceof HttpURLConnection) {
                // ACE-294: enable streaming mode causing only small amounts of memory to be
                // used for this commit. Otherwise, the entire input stream is cached into
                // memory prior to sending it to the server...
                ((HttpURLConnection) connection).setChunkedStreamingMode(8192);
            }

            output = connection.getOutputStream();

            byte[] buffer = new byte[BUFFER_SIZE];
            for (int count = input.read(buffer); count != -1; count = input.read(buffer)) {
                output.write(buffer, 0, count);
            }
            output.close();

            if (connection instanceof HttpURLConnection) {
                int responseCode = ((HttpURLConnection) connection).getResponseCode();
                switch (responseCode) {
                    case HttpURLConnection.HTTP_CREATED:
                        location = connection.getHeaderField("Location");
                        break;
                    case HttpURLConnection.HTTP_CONFLICT:
                        throw new IOException("Artifact already exists in storage: " + name);
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:
                        throw new IOException("The storage server returned an internal server error while trying to upload " + name);
                    default:
                        throw new IOException("The storage server returned code " + responseCode + " writing to " + url.toString());
                }
            }
        }
        finally {
            silentlyClose(output);
        }
        return location;
    }
}
