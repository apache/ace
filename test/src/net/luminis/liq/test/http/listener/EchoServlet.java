package net.luminis.liq.test.http.listener;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EchoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        ServletOutputStream output = null;
        try {
            output = response.getOutputStream();
            output.println(request.getQueryString());
        }
        catch (IOException e) {
            // not much we can do, the test will fail anyway
        } finally {
            if (output != null) {
                try {
                    output.close();
                }
                catch (IOException e) {
                    // not much we can do, the test will fail anyway
                }
            }
        }
    }
}
