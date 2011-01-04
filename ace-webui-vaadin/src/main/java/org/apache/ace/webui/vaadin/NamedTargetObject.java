package org.apache.ace.webui.vaadin;

import org.apache.ace.client.repository.object.GatewayObject;

class NamedTargetObject implements NamedObject {
    private final GatewayObject m_target;

    public NamedTargetObject(GatewayObject target) {
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