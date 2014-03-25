package org.apache.ace.webui;

import org.apache.ace.client.repository.RepositoryObject;

import aQute.bnd.annotation.ProviderType;

@ProviderType
public interface NamedObject {
    String getName();
    String getDescription();
    void setDescription(String description);
    RepositoryObject getObject();
    public String getDefinition();
}