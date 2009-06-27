package net.luminis.liq.gateway.log.task;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Helper class that abstracts handling of a URLConnection somewhat.
 *
 */
public class Connection {
    private URLConnection m_connection;

    public Connection(URL url) throws IOException {
        m_connection = url.openConnection();
    }

    /**
     * Enables the retrieving of input using this connection and returns an inputstream
     * to the connection.
     *
     * @return Inputstream to the connection.
     * @throws IOException If I/O problems occur.
     */
    public InputStream getInputStream() throws IOException {
        m_connection.setDoInput(true);
        return m_connection.getInputStream();
    }

    /**
     * Enables the sending of output using this connection and returns an outputstream
     * to the connection.
     *
     * @return Outputstream to the connection.
     * @throws IOException If I/O problems occur.
     */
    public OutputStream getOutputStream() throws IOException {
        m_connection.setDoOutput(true);
        return m_connection.getOutputStream();
    }

    /**
     * Should be called when a <code>Connection</code> is used to do a POST (write to it's outputstream)
     * without reading it's inputstream (the response). Calling this will make sure the POST request is sent.
     * If no data was written to the connection nothing is done.
     */
    public void close() {
        if (m_connection.getDoOutput()) {
            try {
                m_connection.getOutputStream().close();
            }
            catch (IOException e) {
                // not much we can do
            }
            try {
                m_connection.getContent();
            }
            catch (IOException e) {
                // not much we can do
            }
        }
        if (m_connection.getDoInput()) {
            try {
                m_connection.getInputStream().close();
            }
            catch (IOException e) {
                // not much we can do
            }
        }
    }

}