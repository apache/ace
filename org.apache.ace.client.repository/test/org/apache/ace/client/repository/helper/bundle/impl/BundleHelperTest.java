package org.apache.ace.client.repository.helper.bundle.impl;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.ace.client.repository.helper.ArtifactResource;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class BundleHelperTest {
    private BundleHelperImpl m_helper;
    
    @BeforeTest
    public void setUp() throws Exception {
    	m_helper = new BundleHelperImpl();
    }

    @Test(groups = { UNIT })
    public void testMimetype() {
    	assert m_helper.canHandle("application/vnd.osgi.bundle") : "Should be able to handle bundle mimetype.";
    	assert !m_helper.canHandle("somecrazy/mimetype") : "Should not be able to handle crazy mimetype.";
    }
    
    @Test(groups = { UNIT })
    public void testManifestExtraction() {
    	ArtifactResource artifact = new ArtifactResource() {
			
			@Override
			public InputStream openStream() throws IOException {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				Manifest manifest = new Manifest();
				Attributes attrs = manifest.getMainAttributes();
		        attrs.putValue("Manifest-Version", "1");
				attrs.putValue("Bundle-SymbolicName", "mybundle");
				attrs.putValue("Bundle-Version", "1.0.0");
				attrs.putValue("Bundle-Name", "My Cool Bundle");
				JarOutputStream jos = new JarOutputStream(baos, manifest);
				jos.close();
				return new ByteArrayInputStream(baos.toByteArray());
			}
			
			@Override
			public URL getURL() {
				return null;
			}
		};
    	Map<String, String> map = m_helper.extractMetaData(artifact);
    	assert "mybundle".equals(map.get("Bundle-SymbolicName")) : "Symbolic name should have been 'mybundle', was " + map.get("Bundle-SymbolicName");
    	assert "1.0.0".equals(map.get("Bundle-Version")) : "Version should have been '1.0.0', was " + map.get("Bundle-Version");
    	assert "My Cool Bundle-1.0.0".equals(map.get(ArtifactObject.KEY_ARTIFACT_NAME)) : "Artifact name should have been 'My Cool Bundle-1.0.0', was " + map.get(ArtifactObject.KEY_ARTIFACT_NAME);
    }
    
    @Test(groups = { UNIT })
    public void testLocalizedManifestExtraction() {
    	ArtifactResource artifact = new ArtifactResource() {
			
			@Override
			public InputStream openStream() throws IOException {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				Manifest manifest = new Manifest();
				Attributes attrs = manifest.getMainAttributes();
		        attrs.putValue("Manifest-Version", "1");
				attrs.putValue("Bundle-SymbolicName", "mybundle");
				attrs.putValue("Bundle-Version", "1.0.0");
				attrs.putValue("Bundle-Name", "%bundleName");
				attrs.putValue("Bundle-Localization", "locale");
				JarOutputStream jos = new JarOutputStream(baos, manifest);
				jos.putNextEntry(new ZipEntry("locale.properties"));
				String content = "bundleName=The Coolest Bundle";
				jos.write(content.getBytes(), 0, content.getBytes().length);
                jos.closeEntry();
				jos.close();

//				// if you want to validate that the bundle is okay
//				FileOutputStream fos = new FileOutputStream(new File("/Users/marceloffermans/unittest.jar"));
//				fos.write(baos.toByteArray(), 0, baos.size());
//				fos.close();
				
				return new ByteArrayInputStream(baos.toByteArray());
			}
			
			@Override
			public URL getURL() {
				return null;
			}
		};
    	Map<String, String> map = m_helper.extractMetaData(artifact);
    	assert "mybundle".equals(map.get("Bundle-SymbolicName")) : "Symbolic name should have been 'mybundle', was " + map.get("Bundle-SymbolicName");
    	assert "1.0.0".equals(map.get("Bundle-Version")) : "Version should have been '1.0.0', was " + map.get("Bundle-Version");
    	assert "The Coolest Bundle-1.0.0".equals(map.get(ArtifactObject.KEY_ARTIFACT_NAME)) : "Artifact name should have been 'The Coolest Bundle-1.0.0', was " + map.get(ArtifactObject.KEY_ARTIFACT_NAME);
    }
}
