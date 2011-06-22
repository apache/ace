package org.apache.ace.webui.vaadin;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.webui.NamedObject;

public class NamedFeatureObject implements NamedObject {
    private final GroupObject m_target;

    public NamedFeatureObject(GroupObject target) {
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
    
    public RepositoryObject getObject() {
        return m_target;
    }
    
    public String getDefinition() {
        return m_target.getDefinition();
    }
}