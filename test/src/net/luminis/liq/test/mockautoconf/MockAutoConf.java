package net.luminis.liq.test.mockautoconf;

import java.io.IOException;
import java.io.InputStream;

import org.osgi.service.deploymentadmin.spi.DeploymentSession;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;
import org.osgi.service.deploymentadmin.spi.ResourceProcessorException;

public class MockAutoConf implements ResourceProcessor{

    public void start() {
        System.err.println("MockAutoConf started.");
    }

    public void stop() {
        System.err.println("MockAutoConf stopped.");
    }

    public void begin(DeploymentSession session) {
        System.err.println("Called begin(...)");
        // TODO Auto-generated method stub

    }

    public void cancel() {
        System.err.println("Called cancel(...)");
        // TODO Auto-generated method stub

    }

    public void commit() {
        System.err.println("Called commit()");
        // TODO Auto-generated method stub

    }

    public void dropAllResources() throws ResourceProcessorException {
        System.err.println("Called dropAllResources()");
        // TODO Auto-generated method stub

    }

    public void dropped(String resource) throws ResourceProcessorException {
        System.err.println("Called dropped(" + resource + ")");
        // TODO Auto-generated method stub

    }

    public void prepare() throws ResourceProcessorException {
        System.err.println("Called prepare()");
        // TODO Auto-generated method stub

    }

    public void process(String name, InputStream stream)
            throws ResourceProcessorException {
        System.err.println("Called process(...).\nAnd here's the resource, named '" + name + "':");
        byte[] buf = new byte[1024];
        try {
            while (stream.read(buf) > 0) {
                System.err.write(buf);
            }
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.err.println("That's it!");
    }

    public void rollback() {
        System.err.println("Called rollback()");
    }

}
