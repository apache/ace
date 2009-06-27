package net.luminis.liq.obr.metadata;

import java.io.File;
import java.io.IOException;

public interface MetadataGenerator {

    /**
     * Generates the repository.xml based upon the new set of Bundles in the given directory. The xml is created
     * as result of this method in the given directory in a file called repository.xml.
     * This methods creates the file in an atomic fashion (this includes retrying to overwrite an existing file until success).
     *
     * @param directory the location where to store the newly created repository.xml
     *
     * @throws IOException If I/O problems occur when generating the new meta data index file.
     */
    public void generateMetadata(File directory) throws IOException;
}
