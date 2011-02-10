package org.apache.ace.webui.vaadin;

import org.apache.ace.client.repository.RepositoryObject;

public interface NamedObject {
    String getName();
    String getDescription();
    void setDescription(String description);
    RepositoryObject getObject();
}