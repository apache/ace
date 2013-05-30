package org.apache.ace.http.redirector;

import java.io.IOException;
import java.util.Dictionary;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;

/**
 * Servlet that will redirect from a source URL to a target URL. If you append ?show to the source URL,
 * it will instead show you the redirect (and you can still click on the target URL).
 */
public class RedirectServlet extends HttpServlet {
    public static final String REDIRECT_URL_KEY = "org.apache.ace.webui.vaadin.redirect";
    private final Object LOCK = new Object();
    private volatile ServiceRegistration m_registration;
    private String m_redirectURL;
    private String m_sourceURL;
    
    public RedirectServlet(Dictionary properties) throws ConfigurationException {
        setup(properties);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String src;
        String url;
        synchronized (LOCK) {
            src = m_sourceURL;
            url = m_redirectURL;
        }
        if (req.getParameter("show") != null) {
            resp.setContentType("text/html");
            resp.getWriter().println("<h1>Http Redirector</h1><p>Redirect configured from " + src + " to <a href=\"" + url + "\">" + url + "</a></p>");
        }
        else {
            resp.sendRedirect(url);
        }
    }
    
    public void update(Dictionary properties) throws ConfigurationException {
        setup(properties);
        m_registration.setProperties(properties);
    }

    private void setup(Dictionary properties) throws ConfigurationException {
        synchronized (LOCK) {
            m_redirectURL = (String) properties.get(REDIRECT_URL_KEY);
            m_sourceURL = (String) properties.get(HttpConstants.ENDPOINT);
            if (m_sourceURL == null) {
                throw new ConfigurationException(HttpConstants.ENDPOINT, "needs to be specified");
            }
            if (m_redirectURL == null) {
                throw new ConfigurationException(REDIRECT_URL_KEY, "needs to be specified");
            }
        }
    }
}
