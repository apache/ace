package org.apache.ace.client;

import org.apache.ace.client.services.CheckoutService;
import org.apache.ace.client.services.CheckoutServiceAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;

public class CheckoutPanel extends HorizontalPanel {
    private CheckoutServiceAsync m_checkoutService = GWT.create(CheckoutService.class);
    
    public CheckoutPanel(final Main main) {
        Button retrieve = new Button("Retrieve");
        retrieve.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_checkoutService.checkout(new AsyncCallback<Void>() {
                    public void onFailure(Throwable caught) {
                        Window.alert("Error retrieving latest version from server.");
                    }
                    public void onSuccess(Void result) {
                        main.updateUI();
                    }
                });
            }
        });
        
        Button store = new Button("Store");
        store.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_checkoutService.commit(new AsyncCallback<Void>() {
                    public void onFailure(Throwable caught) {
                        Window.alert("Error storing version on the server.");
                    }
                    public void onSuccess(Void result) {
                        main.updateUI();
                    }
                });
            }
        });
        Button revert = new Button("Revert");
        revert.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_checkoutService.revert(new AsyncCallback<Void>() {
                    public void onFailure(Throwable caught) {
                        Window.alert("Error reverting to latest version from server.");
                    }
                    public void onSuccess(Void result) {
                        main.updateUI();
                    }
                });
            }
        });
        
        add(retrieve);
        add(store);
        add(revert);
    }
}
