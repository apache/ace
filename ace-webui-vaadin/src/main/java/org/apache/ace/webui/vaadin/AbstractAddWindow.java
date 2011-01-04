package org.apache.ace.webui.vaadin;

import com.vaadin.ui.Button;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.Button.ClickEvent;

public abstract class AbstractAddWindow extends Window {
    private final Window m_main;
    private final TextField m_name;

    public AbstractAddWindow(final Window main, String caption) {
        m_main = main;
        setModal(true);
        setWidth("15em");
        setCaption(caption);

        // Configure the windws layout; by default a VerticalLayout
        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);

        m_name = new TextField("name");
        final TextField description = new TextField("description");

        layout.addComponent(m_name);
        layout.addComponent(description);

        Button close = new Button("Ok", new Button.ClickListener() {
            // inline click-listener
            public void buttonClick(ClickEvent event) {
                // close the window by removing it from the parent window
                (getParent()).removeWindow(AbstractAddWindow.this);
                // create the feature
                create((String) m_name.getValue(), (String) description.getValue());
            }
        });
        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.addComponent(close);
        layout.setComponentAlignment(close, "right");
    }

    public void show() {
        if (getParent() != null) {
            // window is already showing
            m_main.getWindow().showNotification(
                    "Window is already open");
        } else {
            // Open the subwindow by adding it to the parent
            // window
            m_main.getWindow().addWindow(this);
        }
        setRelevantFocus();
    }

    private void setRelevantFocus() {
        m_name.focus();
    }

    protected abstract void create(String name, String description);
}