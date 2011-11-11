package org.apache.ace.webui.vaadin;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.Button.ClickEvent;

@SuppressWarnings("serial")
public class GenericAddWindow extends Window {

    public interface AddFunction {
        void create(String name, String description);
    };

    private final Window m_main;
    private final TextField m_name;
    private AddFunction m_addFunction;

    public GenericAddWindow(final Window main, String caption) {
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
                (getParent()).removeWindow(GenericAddWindow.this);
                // create the feature
                if (m_addFunction != null) {
                    m_addFunction.create((String) m_name.getValue(),
                        (String) description.getValue());
                }
            }
        });
        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.addComponent(close);
        layout.setComponentAlignment(close, Alignment.BOTTOM_RIGHT);
    }

    public void setOkListeren(AddFunction addFunction) {
        m_addFunction = addFunction;
    }

    public void show() {
        if (getParent() != null) {
            // window is already showing
            m_main.getWindow().showNotification("Window is already open");
        }
        else {
            // Open the subwindow by adding it to the parent
            // window
            m_main.getWindow().addWindow(this);
        }
        setRelevantFocus();
    }

    private void setRelevantFocus() {
        m_name.focus();
    }
}