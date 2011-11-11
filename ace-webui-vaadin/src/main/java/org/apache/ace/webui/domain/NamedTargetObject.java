package org.apache.ace.webui.domain;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.webui.NamedObject;

public class NamedTargetObject implements NamedObject {
    private final RepositoryObject m_target;

    public NamedTargetObject(StatefulGatewayObject target) {
        m_target = target;
    }

    public NamedTargetObject(GatewayObject target) {
        m_target = target;
    }

    public String getName() {
        if (m_target instanceof StatefulGatewayObject) {
            return ((StatefulGatewayObject) m_target).getID();
        }
        else if (m_target instanceof GatewayObject) {
            return ((GatewayObject) m_target).getID();
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