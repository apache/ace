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
package org.apache.ace.processlauncher;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides a listener interface for interacting with a process' input/output stream.
 */
public interface ProcessStreamListener {

    /**
     * Returns the process' input, as input stream to allow interaction with the process itself.
     * <p>
     * NOTE: if the given output stream is <strong>closed</strong> by the implementing code, the
     * process will probably terminate. This also means that for some processes, you need to
     * explicitly close the given stream in order to let the process terminate properly.
     * </p>
     * 
     * @param launchConfiguration the launch configuration for which a stdin output stream is set,
     *        cannot be <code>null</code>;
     * @param outputStream the stdin {@link OutputStream} that can be used to write to the process'
     *        stdin.
     * @see #wantsStdin()
     */
    void setStdin(LaunchConfiguration launchConfiguration, OutputStream outputStream);

    /**
     * Called with the process' stdout {@link InputStream}.
     * <p>
     * NOTE: when not interacting with the given stream, strange and unpredictable behavior might
     * occur, as the native streams that are used underneath the I/O streams in Java might
     * overflow/block!<br/>
     * Hence, <strong>always</strong> consume all bytes from this stream when implementing this
     * method!
     * </p>
     * 
     * @param launchConfiguration the launch configuration for which a stdout input stream is set,
     *        cannot be <code>null</code>;
     * @param inputStream the stdout {@link InputStream} that can be used to read from the process'
     *        stdout and stderr.
     * @see #wantsStdout()
     */
    void setStdout(LaunchConfiguration launchConfiguration, InputStream inputStream);

    /**
     * Returns whether or not the standard input of the process is desired by this listener.
     * 
     * @return <code>true</code> if the stdin {@link OutputStream} is to be set on this listener
     *         (see {@link #setStdin(LaunchConfiguration, OutputStream)}), <code>false</code> if the
     *         stdin {@link OutputStream} should be ignored.
     */
    boolean wantsStdin();

    /**
     * Returns whether or not the standard output of the process is desired by this listener.
     * 
     * @return <code>true</code> if the stdout {@link InputStream} is to be set on this listener
     *         (see {@link #setStdout(LaunchConfiguration, InputStream)}), <code>false</code> if the
     *         stdout {@link InputStream} should be redirected to '/dev/null'.
     */
    boolean wantsStdout();
}
