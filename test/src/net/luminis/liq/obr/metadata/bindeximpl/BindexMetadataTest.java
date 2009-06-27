package net.luminis.liq.obr.metadata.bindeximpl;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import net.luminis.liq.deployment.provider.ArtifactData;
import net.luminis.liq.deployment.provider.impl.ArtifactDataImpl;
import net.luminis.liq.obr.metadata.MetadataGenerator;
import net.luminis.liq.obr.metadata.bindex.BIndexMetadataGenerator;
import net.luminis.liq.test.utils.deployment.BundleStreamGenerator;

import org.testng.annotations.Test;

public class BindexMetadataTest {

    private ArtifactData generateBundle(File file, String symbolicName, String version) throws Exception {
        ArtifactData bundle = new ArtifactDataImpl(file.getName(), symbolicName, version, file.toURI().toURL(), false);
        BundleStreamGenerator.generateBundle(bundle);
        return bundle;
    }

    /**
     * Generate metadata index, verify contents
     */
    @Test(groups = { UNIT })
    public void generateMetaData() throws Exception {
        File dir = File.createTempFile("meta", "");
        dir.delete();
        dir.mkdir();
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.1", "1.0.0");
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.2", "1.0.0");
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.3", "1.0.0");
        MetadataGenerator meta = new BIndexMetadataGenerator();
        meta.generateMetadata(dir);
        File index = new File(dir, "repository.xml");
        assert index.exists() : "No repository index was generated";
        assert index.length() > 0 : "Repository index can not be size 0";
        int count = 0;
        String line;
        BufferedReader in = new BufferedReader(new FileReader(index));
        while ((line = in.readLine()) != null) {
            if (line.contains("<resource")) {
                count++;
            }
        }
        in.close();
        assert count == 3 : "Expected 3 resources in the repositoty index, found " + count + ".";
    }

    /**
     * Generate a metadata index, remove a bundle, regenerate metadata, verify.
     */
    @Test(groups = { UNIT })
    public void updateMetaData() throws Exception {
        File dir = File.createTempFile("meta", "");
        dir.delete();
        dir.mkdir();
        File bundle = File.createTempFile("bundle", ".jar", dir);
        generateBundle(bundle, "bundle.symbolicname.1", "1.0.0");
        MetadataGenerator meta = new BIndexMetadataGenerator();
        meta.generateMetadata(dir);
        bundle.delete();
        meta.generateMetadata(dir);
        File index = new File(dir, "repository.xml");
        assert index.exists() : "No repository index was generated";
        assert index.length() > 0 : "Repository index can not be size 0";
        int count = 0;
        String line;
        BufferedReader in = new BufferedReader(new FileReader(index));
        while ((line = in.readLine()) != null) {
            if (line.contains("<resource")) {
                count++;
            }
        }
        in.close();
        assert count == 0 : "Expected 0 resources in the repositoty index, found " + count + ".";
    }
}
