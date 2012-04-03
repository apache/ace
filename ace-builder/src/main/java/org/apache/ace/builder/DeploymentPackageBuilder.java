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
package org.apache.ace.builder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Builder for deployment packages. Can handle bundles, resource processors and artifacts. Uses
 * the builder pattern:
 * <pre>
 * OutputStream out = new FileOutputStream("first.dp");
 * DeploymentPackageBuilder.createDeploymentPackage("mydp", "1.0")
 *     .addBundle(new URL("http://repository/api-1.1.0.jar"))
 *     .addBundle(new URL("http://repository/impl-1.1.3.jar"))
 *     .addResourceProcessor(new URL("http://repository/rp-1.0.2.jar"))
 *     .addArtifact(new URL("http://artifacts/config/v1.jar"), "rp.pid")
 *     .addArtifact(new URL("http://artifacts/data/v3.jar"), "rp.pid")
 *     .generate(out);
 * </pre>
 * For bundles and resource processors, you can simply point to a valid URL and it will
 * be queried for all required metadata. For artifacts, you need to specify both the URL
 * and the PID of the resource processor. The builder will use the order you specify for
 * bundles, resource processors and artifacts, but you don't have to specify all bundles
 * and resource processors first and then all artifacts.
 */
public class DeploymentPackageBuilder {
	private static final int BUFFER_SIZE = 32 * 1024;
	private static final String PREFIX_BUNDLE = "bundle-";
	private static final String PREFIX_ARTIFACT = "artifact-";
	private final String m_name;
	private final String m_version;
	private int m_id = 1;
	private final List<ArtifactData> m_bundles = new ArrayList<ArtifactData>();
	private final List<ArtifactData> m_processors = new ArrayList<ArtifactData>();
	private final List<ArtifactData> m_artifacts = new ArrayList<ArtifactData>();
	
	private DeploymentPackageBuilder(String name, String version) {
		m_name = name;
		m_version = version;
	}
	
	/**
	 * Creates a new deployment package.
	 * 
	 * @param name the name of the deployment package
	 * @param version the version of the deployment package
	 * @return a builder to further add data to the deployment package
	 */
	public static DeploymentPackageBuilder createDeploymentPackage(String name, String version) {
		return new DeploymentPackageBuilder(name, version);
	}
	
	/**
	 * Adds a bundle to the deployment package.
	 * 
	 * @param url a url that refers to the bundle
	 * @return a builder to further add data to the deployment package
	 * @throws Exception if something goes wrong while building
	 */
	public DeploymentPackageBuilder addBundle(URL url) throws Exception {
		return addBundleArtifact(url, false);
	}
	
	/**
	 * Adds a resource processor to the deployment package. A resource processor is a special
	 * type of bundle.
	 * 
	 * @param url a url that refers to the resource processor
	 * @return a builder to further add data to the deployment package
	 * @throws Exception if something goes wrong while building
	 */
	public DeploymentPackageBuilder addResourceProcessor(URL url) throws Exception {
		return addBundleArtifact(url, true);
	}
	
	/**
	 * Adds an artifact to the deployment package.
	 * 
	 * @param url a url that refers to the artifact
	 * @param processorPID the PID of the processor for this artifact
	 * @return a builder to further add data to the deployment package
	 * @throws Exception if something goes wrong while building
	 */
	public DeploymentPackageBuilder addArtifact(URL url, String processorPID) throws Exception {
		String path = url.getPath();
		int i = path.lastIndexOf('/');
		if (i > 0 && i < (path.length() - 1)) {
			path = path.substring(i + 1);
		}
		String name = PREFIX_ARTIFACT + getUniqueID() + "-" + path;
    	m_artifacts.add(ArtifactData.createArtifact(url, name, processorPID));
		return this;
	}

	/**
	 * Generates a deployment package and streams it to the output stream you provide. Before
	 * it starts generating, it will first validate that you have actually specified a
	 * resource processor for each type of artifact you provided.
	 * 
	 * @param output the output stream to write to
	 * @throws Exception if something goes wrong while validating or generating
	 */
	public void generate(OutputStream output) throws Exception {
		validateArtifacts();
		List<ArtifactData> artifacts = new ArrayList<ArtifactData>();
		artifacts.addAll(m_bundles);
		artifacts.addAll(m_processors);
		artifacts.addAll(m_artifacts);
		Manifest m = createManifest(artifacts);
		writeStream(artifacts, m, output);
	}
	
	private DeploymentPackageBuilder addBundleArtifact(URL url, boolean isResourceProcessor) throws Exception {
		JarInputStream jis = null;
		try {
			jis = new JarInputStream(url.openStream());
	        Manifest bundleManifest = jis.getManifest();
	        if (bundleManifest == null) {
	        	throw new Exception("Not a valid manifest in: " + url);
	        }
	        Attributes attributes = bundleManifest.getMainAttributes();
			String bundleSymbolicName = getRequiredHeader(attributes, "Bundle-SymbolicName");
	        String bundleVersion = getRequiredHeader(attributes, "Bundle-Version");
	        String name = PREFIX_BUNDLE + bundleSymbolicName + "-" + bundleVersion;
	        int i = name.lastIndexOf('/');
	        if (i > 0 && i < (name.length() - 1)) {
	        	name = name.substring(i + 1);
	        }
			if (isResourceProcessor) {
	        	if (!"true".equals(getRequiredHeader(attributes, "DeploymentPackage-Customizer"))) {
	        		throw new IOException("Invalid DeploymentPackage-Customizer header in: " + url);
	        	}
	        	String processorPID = getRequiredHeader(attributes, "Deployment-ProvidesResourceProcessor");
	        	m_processors.add(ArtifactData.createResourceProcessor(url, name, bundleSymbolicName, bundleVersion, processorPID));
	        }
	        else {
	        	m_bundles.add(ArtifactData.createBundle(url, name, bundleSymbolicName, bundleVersion));
	        }
		}
		finally {
			if (jis != null) {
				jis.close();
			}
		}
		return this;
	}

	private void validateArtifacts() throws Exception {
		for (ArtifactData data : m_artifacts) {
			String pid = data.getProcessorPid();
			boolean found = false;
			for (ArtifactData processor : m_processors) {
				if (pid.equals(processor.getProcessorPid())) {
					found = true;
					break;
				}
			}
			if (!found) {
				throw new Exception("No resource processor found for artifact " + data.getURL() + " with processor PID " + pid);
			}
		}
	}
	
	private String getRequiredHeader(Attributes mainAttributes, String headerName) throws Exception {
		String value = mainAttributes.getValue(headerName);
		if (value == null || value.equals("")) {
			throw new Exception("Missing or invalid " + headerName + " header.");
		}
		return value;
	}
	
	private Manifest createManifest(List<ArtifactData> files) throws Exception {
		Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue("Manifest-Version", "1.0");
        main.putValue("DeploymentPackage-SymbolicName", m_name);
        main.putValue("DeploymentPackage-Version", m_version);

        for (ArtifactData file : files) {
        	if (file.isBundle()) {
                Attributes a = new Attributes();
                a.putValue("Bundle-SymbolicName", file.getSymbolicName());
                a.putValue("Bundle-Version", file.getVersion());
                if (file.isCustomizer()) {
                    a.putValue("DeploymentPackage-Customizer", "true");
                    a.putValue("Deployment-ProvidesResourceProcessor", file.getProcessorPid());
                }
                manifest.getEntries().put(file.getFilename(), a);
        	}
        	else {
                Attributes a = new Attributes();
                a.putValue("Resource-Processor", file.getProcessorPid());
                manifest.getEntries().put(file.getFilename(), a);
        	}
        }
		return manifest;
	}
	
	private void writeStream(List<ArtifactData> files, Manifest manifest, OutputStream outputStream) throws Exception {
        JarOutputStream output = null;
        InputStream fis = null;
        try {
			output = new JarOutputStream(outputStream, manifest);
            byte[] buffer = new byte[BUFFER_SIZE];
            for (ArtifactData file : files) {
                output.putNextEntry(new ZipEntry(file.getFilename()));
                fis = file.getURL().openStream();
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
        finally {
            if (output != null) {
            	output.close();
            }
            if (fis != null) {
                fis.close();
            }
        }
    }
	
	private synchronized int getUniqueID() {
		return m_id++;
	}
}
