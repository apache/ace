package org.apache.ace.webui.vaadin;

import org.osgi.service.log.LogService;

import com.vaadin.terminal.UserError;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.Button.ClickEvent;

@SuppressWarnings("serial")
public class LoginWindow extends Window {

    public interface LoginFunction {
        boolean login(String name, String password);
    };

    private volatile LogService m_log;
    private TextField m_name;
    private PasswordField m_password;
    private Button m_loginButton;
    private LoginFunction m_loginFunction;

    public LoginWindow(final LogService log, final LoginFunction loginFunction) {
        super("Apache ACE Login");
        m_log = log;
        m_loginFunction = loginFunction;
        setResizable(false);
        setModal(true);
        setWidth("15em");

        LoginPanel p = new LoginPanel();
        setContent(p);
    }

    public void closeWindow() {
        getParent().removeWindow(this);
    }

    public class LoginPanel extends VerticalLayout {
        public LoginPanel() {
            setSpacing(true);
            setMargin(true);
            setClosable(false);
            setSizeFull();
            m_name = new TextField("Name", "");
            m_password = new PasswordField("Password", "");
            m_loginButton = new Button("Login");
            addComponent(m_name);
            addComponent(m_password);
            addComponent(m_loginButton);
            setComponentAlignment(m_loginButton, Alignment.BOTTOM_CENTER);
            m_name.focus();
            m_name.selectAll();
            m_loginButton.addListener(new Button.ClickListener() {
                public void buttonClick(ClickEvent event) {
                    if (m_loginFunction.login((String) m_name.getValue(),
                        (String) m_password.getValue())) {
                        m_log.log(LogService.LOG_INFO,
                            "Apache Ace WebUI succesfull login by user: " + (String) m_name.getValue());
                        closeWindow();
                    }
                    else {
                        // TODO provide some feedback, login failed, for now
                        // don't close the login window
                        m_log.log(LogService.LOG_WARNING, "Apache Ace WebUI invalid username or password entered.");
                        m_loginButton.setComponentError(new UserError(
                            "Invalid username or password."));
                    }
                }
            });
        }
    }

}