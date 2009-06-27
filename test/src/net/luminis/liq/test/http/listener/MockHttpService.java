package net.luminis.liq.test.http.listener;

import java.util.Dictionary;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class MockHttpService implements HttpService {

    private boolean m_registerCalled = false;
    private boolean m_unregisterCalled = false;

    public HttpContext createDefaultHttpContext() {
        // TODO Auto-generated method stub
        return null;
    }

    public void registerResources(String arg0, String arg1, HttpContext arg2) throws NamespaceException {
        // TODO Auto-generated method stub

    }

    @SuppressWarnings("unchecked")
    public void registerServlet(String arg0, Servlet arg1, Dictionary arg2, HttpContext arg3) throws ServletException, NamespaceException {
        m_registerCalled = true;
    }

    public void unregister(String arg0) {
        m_unregisterCalled = true;
    }

    public boolean isRegisterCalled() {
        return m_registerCalled;
    }

    public boolean isUnregisterCalled() {
        return m_unregisterCalled;
    }
}
