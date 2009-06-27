package net.luminis.liq.deployment.streamgenerator;

import java.io.IOException;
import java.io.InputStream;

public interface StreamGenerator {

    /**
     * Returns an input stream with the requested deployment package.
     *
     * @param id the ID of the package
     * @param version the version of the package
     * @return an input stream
     * @throws IOException when the stream could not be generated
     */
    public InputStream getDeploymentPackage(String id, String version) throws IOException;

    /**
     * Returns an input stream with the requested deployment fix package.
     *
     * @param id the ID of the package.
     * @param fromVersion the version of the target.
     * @param toVersion the version the target should be in after applying the package.
     * @return an input stream.
     * @throws IOException when the stream could not be generated.
     */
    public InputStream getDeploymentPackage(String id, String fromVersion, String toVersion) throws IOException;
}
