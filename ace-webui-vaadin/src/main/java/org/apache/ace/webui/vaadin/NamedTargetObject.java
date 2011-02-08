package org.apache.ace.webui.vaadin;

import org.apache.ace.client.repository.stateful.StatefulGatewayObject;

class NamedTargetObject implements NamedObject {
    private final StatefulGatewayObject m_target;

    public NamedTargetObject(StatefulGatewayObject target) {
        m_target = target;
    }

    public String getName() {
        return m_target.getID();
    }

    public String getDescription() {
        return "";
    }

    public void setDescription(String description) {
        throw new IllegalArgumentException();
    }
}