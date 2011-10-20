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
package org.apache.ace.ant.deploymentpackage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.MatchingTask;

/**
 * Ant task that creates a deployment package out of a set of bundles.
 */
public class DeploymentPackageTask extends MatchingTask {
    /** The destination deployment package file. */
    private File m_destination;
    /** The name of the deployment package. */
    private String m_name;
    /** The version of the deployment package. */
    private String m_version;
    /** The base directory to scan for files. */
    private File m_dir;

    public void setDir (File dir) {
        m_dir = dir;
    }

    public void setName(String name) {
        m_name = name;
    }

    public void setVersion(String version) {
        m_version = version;
    }

    public void setDestination(File destination) {
        m_destination = destination;
    }
    
    List<OrderedInclude> orderedIncludes = new ArrayList<OrderedInclude>();
    
    public OrderedInclude createOrderedInclude() {
        OrderedInclude i = new OrderedInclude();
        orderedIncludes.add(i);
        return i;
    }
    
    public static class OrderedInclude {
        private String m_name;
        public OrderedInclude() {}
        public void setName(String name) {
            m_name = name;
        }
        public String getName() {
            return m_name;
        }
    }
    
    private String[] findFiles(File dir, String pattern) {
        List<String> found = new ArrayList<String>();
        findFiles(dir, "", pattern, found);
        return found.toArray(new String[found.size()]);
    }
    
    private void findFiles(File base, String dir, String pattern, List<String> files) {
        File[] entries = new File(base, dir).listFiles();
        for (File f : entries) {
            if (f.isDirectory()) {
                String newDir = dir.equals("") ? f.getName() : dir + File.separator + f.getName();
                findFiles(base, newDir, pattern, files);
            }
            else if (f.isFile()) {
                File df = new File(dir, f.getName());
                if (df.toString().matches(pattern)) {
                    files.add(df.toString());
                }
            }
        }
    }

    public void execute() throws BuildException {
        if (m_dir == null) {
            throw new BuildException("dir must be specified");
        }
        if (m_name == null) {
            throw new BuildException("name must be specified");
        }
        if (m_version == null) {
            throw new BuildException("version must be specified");
        }
        if (m_destination == null) {
            throw new BuildException("destination must be specified");
        }
        log("dir = " + m_dir, Project.MSG_DEBUG);
        
        DirectoryScanner ds = getDirectoryScanner(m_dir);
        String[] unorderedIncludes = ds.getIncludedFiles();
        List<String> files = new ArrayList<String>();
        for (int i = 0; i < orderedIncludes.size(); i++) {
            String pattern = orderedIncludes.get(i).getName();
            String[] filez2 = findFiles(m_dir, pattern);
            for (String f : filez2) {
                files.add(f);
            }
        }
        for (int i = 0; i < unorderedIncludes.length; i++) {
            if (!files.contains(unorderedIncludes[i])) {
                files.add(unorderedIncludes[i]);
            }
        }

        for (String file : files) {
            log("Found file: " + file);
        }

        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue("Manifest-Version", "1.0");
        main.putValue("DeploymentPackage-SymbolicName", m_name);
        main.putValue("DeploymentPackage-Version", m_version);

        for (String file : files) {
            try {
                log("Parsing manifest of: " + file);
                JarInputStream jis = new JarInputStream(new FileInputStream(new File(m_dir, file)));
                Manifest bundleManifest = jis.getManifest();
                String bsn = "INVALID";
                String version = "INVALID";
                if (bundleManifest == null) {
                    throw new BuildException("Not a valid manifest in: " + file);
                }
                else {
                    bsn = bundleManifest.getMainAttributes().getValue("Bundle-SymbolicName");
                    version = bundleManifest.getMainAttributes().getValue("Bundle-Version");
                    jis.close();
                }
                Attributes a = new Attributes();
                a.putValue("Bundle-SymbolicName", bsn);
                a.putValue("Bundle-Version", version);
                manifest.getEntries().put(file, a);
            }
            catch (IOException e) {
                throw new BuildException("Could not read bundle " + file + ".", e);
            }
        }

        JarOutputStream output = null;
        FileInputStream fis = null;
        try {
            output = new JarOutputStream(new FileOutputStream(m_destination), manifest);
            byte[] buffer = new byte[4096];
            for (String file : files) {
                log("Writing to deployment package: " + file);
                output.putNextEntry(new ZipEntry(file));
                fis = new FileInputStream(new File(m_dir, file));
                int bytes = fis.read(buffer);
                while (bytes != -1) {
                    output.write(buffer, 0, bytes);
                    bytes = fis.read(buffer);
                }
                output.closeEntry();
                fis.close();
                fis = null;
            }
        }
        catch (Exception e) {
            throw new BuildException("Could not create deployment package " + m_destination + ".", e);
        }
        finally {
            if (output != null) {
                try {
                    output.close();
                }
                catch (IOException e) {
                    throw new BuildException("Could not close deployment package " + m_destination + ".", e);
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                }
                catch (IOException e) {
                    throw new BuildException("Could not close file " + fis + ".", e);
                }
            }
        }
    }
}

