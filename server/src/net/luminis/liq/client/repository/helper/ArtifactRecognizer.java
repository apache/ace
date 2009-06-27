package net.luminis.liq.client.repository.helper;

import java.net.URL;
import java.util.Map;

/**
 * Service interface for services that can recognize the type of an artifact, given a URL
 * to that artifact.
 */
public interface ArtifactRecognizer {
    /**
     * Tries to determine the type of the artifact. If this recognizer cannot determine the type, it
     * should return <code>null</code>.
     * @param artifact A URL to a 'physical' artifact.
     * @return The mimetype of the artifact, or <code>null</code> if the artifact is not recognized.
     */
    public String recognize(URL artifact);

    /**
     * Gets the relevant metadata for this artifact.
     * @param artifact A URL to a 'physical' artifact.
     * @return A map of strings, representing the relevant metadata specific for this artifact. The
     * keys are best defined in the corresponding <code>ArtifactHelper</code> interface for this type of artifact.
     * This function should also set the <code>ArtifactObject.KEY_PROCESSOR_PID</code> attribute.<br>
     * Optionally, <code>ArtifactObject.KEY_ARTIFACT_NAME</code> and <code>ArtifactObject.KEY_ARTIFACT_DESCRIPTION</code>
     * can be set. 
     * @throws IllegalArgumentException when the metadata cannot be retrieved from the <code>artifact</code>.
     */
    public Map<String, String> extractMetaData(URL artifact) throws IllegalArgumentException;
    
    /**
     * Indicates whether this recognizer can handle (i.e., extract metadata) from an artifact of
     * a given mime type.
     * @param mimetype The mimetype of an artifact.
     * @return <code>true</code> when this type should be able to be handled by this recognizer;
     * <code>false</code> otherwise.
     */
    public boolean canHandle(String mimetype);
    
}
