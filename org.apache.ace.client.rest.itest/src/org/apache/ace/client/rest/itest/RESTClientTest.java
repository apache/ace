package org.apache.ace.client.rest.itest;

import java.io.IOException;
import java.net.URI;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.felix.dm.Component;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;

public class RESTClientTest extends IntegrationTestBase {
    private volatile UserAdmin m_user;
    
    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent().setImplementation(this).add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
        };
    }
    
    /**
     * Creates a new workspace, ensures it works correctly by asking for a list of entity types, then
     * deletes the workspace again and ensures it's no longer available.
     */
    public void testCreateAndDestroyRESTSession() throws Exception {
        configureServer();
        createServerUser();
        
        Client c = Client.create();
        c.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, false);
        WebResource r = c.resource("http://localhost:8080/client/work");
        try {
            r.post(String.class, "");
            fail("We should have been redirected to a new workspace.");
        }
        catch (UniformInterfaceException e) {
            ClientResponse response = e.getResponse();
            URI location = response.getLocation();
            assertEquals("http://localhost:8080/client/work/rest-1", location.toString());
            WebResource r2 = c.resource(location);
            r2.get(String.class);            
            r2.delete();
            try {
                r2.get(String.class);
            }
            catch (UniformInterfaceException e2) {
                assertEquals(404, e2.getResponse().getStatus());
            }
        }
    }
    
    private void configureServer() throws IOException {
        configure("org.apache.ace.client.rest",
            "org.apache.ace.server.servlet.endpoint", "/client",
            "authentication.enabled", "false");
        
        configure("org.apache.ace.deployment.servlet",
            "org.apache.ace.server.servlet.endpoint", "/deployment",
            "authentication.enabled", "false");

        configure("org.apache.ace.repository.servlet.RepositoryServlet",
            "org.apache.ace.server.servlet.endpoint", "/repository",
            "authentication.enabled", "false");

        configure("org.apache.ace.obr.servlet",
            "org.apache.ace.server.servlet.endpoint", "/obr",
            "authentication.enabled", "false");

        configure("org.apache.ace.obr.storage.file",
            "fileLocation", "store");

        configure("org.apache.ace.deployment.provider.repositorybased",
            "url", "http://localhost:8080/repository",
            "name", "deployment",
            "customer", "apache");

        configure("org.apache.ace.discovery.property",
            "serverURL", "http://localhost:8080");
        
        configure("org.apache.ace.identification.property",
            "targetID", "target-test");
        
        configureFactory("org.apache.ace.server.log.servlet.factory",
            "name", "auditlog",
            HttpConstants.ENDPOINT, "/auditlog",
            "authentication.enabled", "false");
    
        configureFactory("org.apache.ace.server.log.store.factory",
            "name", "auditlog");
        
        configureFactory("org.apache.ace.server.repository.factory",
            "name", "user",
            "customer", "apache",
            "master", "true"
            );
        
        configureFactory("org.apache.ace.server.repository.factory",
            "name", "shop",
            "customer", "apache",
            "master", "true");

        configureFactory("org.apache.ace.server.repository.factory",
            "name", "deployment",
            "customer", "apache",
            "master", "true");

        configureFactory("org.apache.ace.server.repository.factory",
            "name", "target",
            "customer", "apache",
            "master", "true");

        configureFactory("org.apache.ace.server.repository.factory",
            "name", "users",
            "customer", "apache",
            "master", "true");
        
        configure("org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask",
            "repositoryLocation", "http://localhost:8080/repository",
            "repositoryCustomer", "apache",
            "repositoryName", "user");
    }
    private void createServerUser() {
        User user = (User) m_user.createRole("d", Role.USER);
        user.getProperties().put("username", "d");
        user.getCredentials().put("password", "f");
    }
    
    public static void main(String[] args) throws Exception {
        new RESTClientTest().testCreateAndDestroyRESTSession();
    }
}
