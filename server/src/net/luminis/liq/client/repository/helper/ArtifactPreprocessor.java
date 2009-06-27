package net.luminis.liq.client.repository.helper;

import java.io.IOException;
import java.net.URL;

/**
 *  An ArtifactPreprocessor processes an artifact before it is deployed.
 */
public interface ArtifactPreprocessor {

    /**
     * Preprocesses a single artifact, uploads it to the obr, and returns the new URL as a string.
     * @param url A string representing a URL to the original artifact.
     * @param props A PropertyResolver which can be used to fill in 'holes' in the template.
     * @param gatewayID The gatewayID of the gateway for which this artifact is being processed.
     * @param version The deployment version for which this artifact is being processed.
     * @param obrBase A base OBR to upload the new artifact to.
     * @return A URL to the new object (or the old one, if no replacing was necessary), as a string.
     * @throws IOException Thrown if reading the original artifact goes wrong, or storing the processed one.
     */
    public String preprocess(String url, PropertyResolver props, String gatewayID, String version, URL obrBase) throws IOException;

    /**
     * Indicates whether the template should be processed again, given the properties, and the version to which it
     * should be compared. 
     * @param url A string representing a URL to the original artifact.
     * @param props A PropertyResolver which can be used to fill in 'holes' in the template.
     * @param gatewayID The gatewayID of the gateway for which this artifact is being processed.
     * @param version The deployment version for which this artifact is being processed.
     * @param fromVersion The deployment version to which the current one should be compared.
     * @return <code>false</code> if the version of the processed artifact identified by <code>fromVersion</code>
     * is identical to what would be created using the new <code>props</code>; <code>true</code> otherwise.
     * @throws IOException 
     */
    public boolean needsNewVersion(String url, PropertyResolver props, String gatewayID, String fromVersion);

}
