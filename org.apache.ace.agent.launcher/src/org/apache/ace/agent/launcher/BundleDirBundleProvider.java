package org.apache.ace.agent.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link BundleProvider} that loads bundles from a directory.
 * 
 * @see META-INF/services/org.apache.ace.agent.launcher.BundleProvider
 * 
 */
public class BundleDirBundleProvider implements BundleProvider {

    public static final String BUNDLE_DIR_PROPERTY = "agent.bundles.dir";
    public static final String BUNDLE_DIR_DEFAULT = "bundles";

    @Override
    public String[] getBundleNames() {
        File dir = getDir();
        if (!dir.exists() || !dir.canRead()) {
            return new String[] {};
        }

        File[] bundles = dir.listFiles();
        List<String> names = new ArrayList<String>();
        for (File bundle : bundles) {
            names.add(bundle.getName());
        }
        return names.toArray(new String[names.size()]);
    }

    @Override
    public InputStream getInputStream(String bundleName) throws IOException {
        File dir = getDir();
        if (!dir.exists() || !dir.canRead()) {
            throw new IOException("No such bundle in dir " + dir.getAbsolutePath());
        }
        File bundle = new File(dir, bundleName);
        if (!bundle.exists() || !bundle.canRead()) {
            throw new IOException("No such bundle in dir " + dir.getAbsolutePath());
        }
        return new FileInputStream(bundle);
    }

    private File getDir() {
        String dir = System.getProperty(BUNDLE_DIR_PROPERTY);
        if (dir != null)
            return new File(dir);
        return new File(BUNDLE_DIR_DEFAULT);
    }
}
