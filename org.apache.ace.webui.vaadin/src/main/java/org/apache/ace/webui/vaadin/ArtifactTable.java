package org.apache.ace.webui.vaadin;

import com.vaadin.ui.Table;
import com.vaadin.ui.Window;

public class ArtifactTable extends Table {
    public ArtifactTable(final Window main) {
        super("Artifacts");
        addContainerProperty("symbolic name", String.class, null);
        addContainerProperty("version", String.class, null);
        setSizeFull();
        setSelectable(true);
        setMultiSelect(true);
        setImmediate(true);
        setHeight("15em");
    }
}