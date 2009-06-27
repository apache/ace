package net.luminis.liq.test.utils.deployment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import net.luminis.liq.deployment.provider.ArtifactData;

import org.osgi.framework.Constants;

public class BundleStreamGenerator {

    public static Manifest getBundleManifest(String symbolicname, String version, Map<String, String> additionalHeaders) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1");
        manifest.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        manifest.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, symbolicname);
        manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, version.toString());
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            manifest.getMainAttributes().putValue(entry.getKey(), entry.getValue());
        }
        return manifest;
    }

    public static void generateBundle(ArtifactData data, Map<String, String> additionalHeaders) throws IOException {
        OutputStream bundleStream = null;
        try {
            File dataFile = new File(data.getUrl().toURI());
            OutputStream fileStream = new FileOutputStream(dataFile);
            bundleStream = new JarOutputStream(fileStream, getBundleManifest(data.getSymbolicName(), data.getVersion(), additionalHeaders));
            bundleStream.flush();
        } catch (URISyntaxException e) {
            throw new IOException();
        } finally {
            if (bundleStream != null) {
                bundleStream.close();
            }
        }
    }

    public static void generateBundle(ArtifactData data) throws IOException {
        generateBundle(data, new HashMap<String, String>());
    }

}
