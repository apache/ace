package net.luminis.liq.client.repository.helper.user.impl;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import net.luminis.liq.client.repository.helper.ArtifactPreprocessor;
import net.luminis.liq.client.repository.helper.ArtifactRecognizer;
import net.luminis.liq.client.repository.helper.base.VelocityArtifactPreprocessor;
import net.luminis.liq.client.repository.helper.user.UserAdminHelper;
import net.luminis.liq.client.repository.object.ArtifactObject;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class UserHelperImpl implements ArtifactRecognizer, UserAdminHelper {

    public boolean canHandle(String mimetype) {
        return MIMETYPE.equals(mimetype);
    }

    public Map<String, String> extractMetaData(URL artifact) throws IllegalArgumentException {
        Map<String, String> result = new HashMap<String, String>();
        result.put(ArtifactObject.KEY_PROCESSOR_PID, PROCESSOR);
        result.put(ArtifactObject.KEY_MIMETYPE, MIMETYPE);
        result.put(ArtifactObject.KEY_ARTIFACT_NAME, new File(artifact.getFile()).getName());
        return result;
    }

    public String recognize(URL artifact) {
        try {
            InputStream in = artifact.openStream();
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            Node root = doc.getFirstChild();
            if (!root.getNodeName().equals("roles")) {
                return null;
            }
            for (Node node = root.getFirstChild(); root != null; root = root.getNextSibling()) {
                if (!node.getNodeName().equals("group") && !node.getNodeName().equals("user") && !node.getNodeName().equals("#text")) {
                    return null;
                }
            }
            return MIMETYPE;
        }
        catch (Exception e) {
            // Does not matter.
        }

        return null;
    }

    public boolean canUse(ArtifactObject object) {
        return MIMETYPE.equals(object.getMimetype());
    }

    public Map<String, String> checkAttributes(Map<String, String> attributes) {
        // All necessary checks will be done by the constructor using getMandatoryAttributes.
        return attributes;
    }

    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
        return "(" + ArtifactObject.KEY_ARTIFACT_NAME + "=" + obj.getAttribute(ArtifactObject.KEY_ARTIFACT_NAME) + ")";
    }

    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
        return Integer.MAX_VALUE;
    }

    public Comparator<ArtifactObject> getComparator() {
        return null;
    }

    public String[] getDefiningKeys() {
        return new String[] {ArtifactObject.KEY_ARTIFACT_NAME};
    }

    public String[] getMandatoryAttributes() {
        return new String[] {ArtifactObject.KEY_ARTIFACT_NAME};
    }

    private final static VelocityArtifactPreprocessor VELOCITY_ARTIFACT_PREPROCESSOR = new VelocityArtifactPreprocessor();
    public ArtifactPreprocessor getPreprocessor() {
        return VELOCITY_ARTIFACT_PREPROCESSOR;
    }

}
