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
package org.apache.ace.gogo.repo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.osgi.framework.Version;

import aQute.bnd.differ.DiffPluginImpl;
import aQute.bnd.osgi.Jar;
import aQute.bnd.service.diff.Delta;
import aQute.bnd.service.diff.Diff;
import aQute.bnd.service.diff.Differ;
import aQute.bnd.service.diff.Tree;

public final class DeployerUtil {

    public static final String QUALIFIER_PREFIX = "CDS";

    private static final Pattern QUALIFIER_PATTERN = Pattern.compile(QUALIFIER_PREFIX + "([\\d]{3})$|(.*)(_" + QUALIFIER_PREFIX + "([\\d]{3})?$)");

    /**
     * Given ad version, creates the 'next' snapshot version. If the version has no snapshot qualifier a new one will be
     * added. If it does, it will be incremented.
     * 
     * @param version
     *            The version
     * @return The next snapshot Version
     * @throws Exception
     *             On Failure
     */
    public static Version getNextSnapshotVersion(Version version) throws Exception {

        if (version.getQualifier() == null || version.getQualifier().equals("")) {
            return new Version(version.getMajor(), version.getMinor(), version.getMicro(), getSnapshotQualifier("", 0));
        }
        Matcher qualifierMatcher = QUALIFIER_PATTERN.matcher(version.getQualifier());
        if (!qualifierMatcher.matches()) {
            return new Version(version.getMajor(), version.getMinor(), version.getMicro(), getSnapshotQualifier(version.getQualifier(), 0));
        }

        String qualifierMatch = qualifierMatcher.group(1);
        if (qualifierMatch != null) {
            int sequence = Integer.parseInt(qualifierMatch);
            return new Version(version.getMajor(), version.getMinor(), version.getMicro(), getSnapshotQualifier("", ++sequence));
        }

        String qualifierPrefix = qualifierMatcher.group(2);
        qualifierMatch = qualifierMatcher.group(4);
        int sequence = Integer.parseInt(qualifierMatch);
        return new Version(version.getMajor(), version.getMinor(), version.getMicro(), getSnapshotQualifier(qualifierPrefix, ++sequence));
    }

    /**
     * Check if there is a diff between two jar files.
     * 
     * @param first
     *            The first Jar
     * @param second
     *            The second Jar
     * @return <code>true</code> if there is a difference, otherwise <code>false</code>
     * @throws Exception
     *             On failure
     */
    public static boolean jarsDiffer(File first, File second) throws Exception {
        Differ di = new DiffPluginImpl();
        Tree n = di.tree(new Jar(second));
        Tree o = di.tree(new Jar(first));
        Diff diff = n.diff(o);
        for (Diff child : diff.getChildren()) {
            for (Diff childc : child.getChildren()) {
                if (childc.getDelta() == Delta.UNCHANGED || childc.getDelta() == Delta.IGNORED) {
                    continue;
                }
                System.out.println(childc);
//                if(childc.getChildren() != null)
//                    for(Diff qq : childc.getChildren()){
//                        System.out.println(" " + qq);
//                    }
                
                return true;
            }
        }
        return false;
    }

    /**
     * Check if there is a diff between two arbitrary files.
     * 
     * @param first
     *            The first file
     * @param second
     *            The second file
     * @return <code>true</code> if there is a difference, otherwise <code>false</code>
     * @throws Exception
     *             On failure
     */
    public static boolean filesDiffer(File first, File second) throws Exception {

        if (first.length() != second.length()) {
            return true;
        }

        DigestInputStream stream = null;
        byte[] firstHash = null;
        byte[] secondHash = null;

        try {
            stream = new DigestInputStream(new FileInputStream(first), MessageDigest.getInstance("MD5"));
            while (stream.read() != -1) {
            }
            firstHash = stream.getMessageDigest().digest();
        }
        finally {
            stream.close();
        }

        try {
            stream = new DigestInputStream(new FileInputStream(second), MessageDigest.getInstance("MD5"));
            while (stream.read() != -1) {
            }
            secondHash = stream.getMessageDigest().digest();
        }
        finally {
            stream.close();
        }
        return !Arrays.equals(firstHash, secondHash);
    }

    /**
     * Clones a bundle file while replacing the Bundle-Version in the manifest with the specified value.
     * 
     * @param sourceJar
     *            The existing jar
     * @param version
     *            The new version
     * @return The new Jar
     * @throws IOException
     *             On failure
     */
    public static File getBundleWithNewVersion(File sourceJar, String version) throws IOException {

        File targetFile = File.createTempFile("bundle", ".jar");
        byte[] buf = new byte[1024];
        ZipInputStream zin = new ZipInputStream(new FileInputStream(sourceJar));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(targetFile));
        ZipEntry entry = zin.getNextEntry();
        while (entry != null) {
            String name = entry.getName();
            out.putNextEntry(new ZipEntry(name));

            if (name.equals("META-INF/MANIFEST.MF")) {
                // FIXME quick abort
                ByteBuffer bb = ByteBuffer.allocate(100 * 1024);
                int len;
                while ((len = zin.read(buf)) > 0) {
                    bb.put(buf, 0, len);
                }

                BufferedReader r = new BufferedReader(new StringReader(new String(bb.array(), 0, bb.position())));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("Bundle-Version:")) {
                        out.write(("Bundle-Version: " + version + "\r\n").getBytes());
                    }
                    else {
                        out.write((line + "\r\n").getBytes());
                    }
                }
            }
            else {
                int len;
                while ((len = zin.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            entry = zin.getNextEntry();
        }
        zin.close();
        out.close();
        return targetFile;
    }

    public static boolean isSameBaseVersion(Version left, Version right) {
        return left.getMajor() == right.getMajor() && left.getMinor() == right.getMinor() && left.getMicro() == right.getMicro();
    }

    public static boolean isSnapshotVersion(Version version) {
        if (version.getQualifier() == null || version.getQualifier().equals("")) {
            return false;
        }
        Matcher qualifierMatcher = QUALIFIER_PATTERN.matcher(version.getQualifier());
        return qualifierMatcher.matches();
    }

    private static String getSnapshotQualifier(String prefix, int i) {
        if (!isEmpty(prefix)) {
            prefix = prefix + "_";
        }
        else {
            prefix = "";
        }
        if (i < 10) {
            return prefix + QUALIFIER_PREFIX + "00" + i;
        }
        else if (i < 100) {
            return prefix + QUALIFIER_PREFIX + "0" + i;
        }
        else if (i < 1000) {
            return prefix + QUALIFIER_PREFIX + i;
        }
        else {
            throw new IllegalArgumentException("Can not qualifiers above 999");
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.equals("");
    }

    private DeployerUtil() {
    }
}
