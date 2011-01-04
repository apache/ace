package org.apache.ace.webui.vaadin;

import org.apache.ace.client.repository.object.LicenseObject;

public class NamedDistributionObject implements NamedObject {
    private final LicenseObject m_target;

    public NamedDistributionObject(LicenseObject target) {
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