/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.webui.vaadin.component;

import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.Reindeer;

/**
 * Provides a confirmation dialog, based on code found on <a
 * href="https://vaadin.com/forum/-/message_boards/view_message/17883">this forum posting</a>.
 */
public class ConfirmationDialog extends Window implements ClickListener {

    /**
     * Callback class for a {@link ConfirmationDialog}.
     */
    public static interface Callback {
        /**
         * Called upon pressing a button.
         * 
         * @param buttonName
         *            the name of the button that was clicked, never <code>null</code>.
         */
        void onDialogResult(String buttonName);
    }

    public static final String YES = "Yes";
    public static final String NO = "No";
    public static final String CANCEL = "Cancel";

    private final Callback m_callback;

    /**
     * Provides a Yes/No confirmation dialog.
     * 
     * @param caption
     *            the caption of this dialog, cannot be <code>null</code>;
     * @param message
     *            the message to display, may be <code>null</code> to omit the message;
     * @param callback
     *            the callback to call for each pressed button.
     */
    public ConfirmationDialog(String caption, String message, Callback callback) {
        this(caption, message, callback, YES, YES, NO);
    }

    /**
     * Provides a confirmation dialog with a custom set of buttons.
     * 
     * @param caption
     *            the caption of this dialog, cannot be <code>null</code>;
     * @param message
     *            the message to display, may be <code>null</code> to omit the message;
     * @param callback
     *            the callback to call for each pressed button;
     * @param buttonNames
     *            the names of the buttons to display.
     */
    public ConfirmationDialog(String caption, String message, Callback callback, String defaultButton, String... buttonNames) {
        super(caption);

        if (buttonNames == null || buttonNames.length <= 1) {
            throw new IllegalArgumentException("Need at least one button name!");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Need a callback!");
        }

        m_callback = callback;

        setWidth("30em");
        setModal(true);

        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);

        addComponents(message, defaultButton, buttonNames);
    }

    /**
     * @see com.vaadin.ui.Button.ClickListener#buttonClick(com.vaadin.ui.Button.ClickEvent)
     */
    public void buttonClick(ClickEvent event) {
        Window parent = getParent();
        if (parent != null) {
            parent.removeWindow(this);
            parent.focus();
        }

        AbstractComponent comp = (AbstractComponent) event.getComponent();
        m_callback.onDialogResult((String) comp.getData());
    }

    /**
     * Adds all components to this dialog.
     * 
     * @param message
     *            the optional message to display, can be <code>null</code>;
     * @param buttonNames
     *            the names of the buttons to add, never <code>null</code> or empty.
     */
    protected void addComponents(String message, String defaultButton, String... buttonNames) {
        if (message != null) {
            addComponent(new Label(message));
        }

        GridLayout gl = new GridLayout(buttonNames.length + 1, 1);
        gl.setSpacing(true);
        gl.setWidth("100%");

        gl.addComponent(new Label(" "));
        gl.setColumnExpandRatio(0, 1.0f);

        for (String buttonName : buttonNames) {
            Button button = new Button(buttonName, this);
            button.setData(buttonName);
            if (defaultButton != null && defaultButton.equals(buttonName)) {
                button.setStyleName(Reindeer.BUTTON_DEFAULT);
                button.setClickShortcut(KeyCode.ENTER);
                // Request focus in this window...
                button.focus();
            }
            gl.addComponent(button);
        }

        addComponent(gl);
    }
}
