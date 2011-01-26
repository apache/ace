package org.apache.ace.webui.vaadin;

import org.apache.ace.client.repository.object.ArtifactObject;

public class NamedArtifactObject implements NamedObject {
    private final ArtifactObject m_target;

    public NamedArtifactObject(ArtifactObject target) {
        m_target = target;
    }

    public String getName() {
        return m_target.getName();
    }

    public String getDescription() {
        return m_target.getDescription();
    }

    public void setDescription(String description) {
        m_target.setDescription(description);
    }
}