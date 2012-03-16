package org.apache.ace.webui.domain;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.webui.NamedObject;

public class NamedTargetObject implements NamedObject {
    private final RepositoryObject m_target;

    public NamedTargetObject(StatefulTargetObject target) {
        m_target = target;
    }

    public NamedTargetObject(TargetObject target) {
        m_target = target;
    }

    public String getName() {
        if (m_target instanceof StatefulTargetObject) {
            return ((StatefulTargetObject) m_target).getID();
        }
        else if (m_target instanceof TargetObject) {
            return ((TargetObject) m_target).getID();
        }
        return null;
    }

    public String getDescription() {
        return "";
    }

    public void setDescription(String description) {
        throw new IllegalArgumentException();
    }

    public RepositoryObject getObject() {
        return m_target;
    }

    public String getDefinition() {
        return m_target.getDefinition();
    }
}