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
package org.apache.ace.client.automation;

import java.io.IOException;
import java.net.URL;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Automatic gateway operator, when configured will automatically register, approve, auto-approve
 * and commit gateways to the repository. An LDAP filter can be used to filter for the correct gateways
 *
 */
public class AutoGatewayOperator implements ManagedService {

    public static final String PID = "org.apache.ace.client.automation";
    public static final String SCHEDULER_NAME = "org.apache.ace.client.processauditlog";

    private volatile StatefulGatewayRepository m_statefulGatewayRepos;
    private volatile RepositoryAdmin m_reposAdmin;
    private volatile UserAdmin m_userAdmin;
    private volatile BundleContext m_bundleContext;
    private volatile LogService m_log;
    @SuppressWarnings("unchecked")
    private volatile Dictionary m_settings;

    private static String username = "serverUser";

    // used for processing the auditlog (tell the repository about that)
    private final AuditLogProcessTask m_task = new AuditLogProcessTask();
    private Object m_serviceReg = null;

    public void start() {
        // get user
        User user = m_userAdmin.getUser("username",username);

        // login at Repository admin
        try {
            URL url =  new URL(getConfigValue(ConfigItem.HOSTNAME) + getConfigValue(ConfigItem.ENDPOINT));
            String customerName = getConfigValue(ConfigItem.CUSTOMER_NAME);

            RepositoryAdminLoginContext loginContext = m_reposAdmin.createLoginContext(user);
            loginContext.addShopRepository(url, customerName, getConfigValue(ConfigItem.STORE_REPOSITORY), false)
            .addGatewayRepository(url, customerName, getConfigValue(ConfigItem.GATEWAY_REPOSITORY), true)
            .addDeploymentRepository(url, customerName, getConfigValue(ConfigItem.DEPLOYMENT_REPOSITORY), true);
            m_reposAdmin.login(loginContext);

            // start refresh task
            Properties props = new Properties();
            props.put(SchedulerConstants.SCHEDULER_NAME_KEY, SCHEDULER_NAME);
            m_serviceReg = m_bundleContext.registerService(Runnable.class.getName(), m_task, props);
        }
        catch (IOException ioe) {
            m_log.log(LogService.LOG_ERROR, "Unable to login at repository admin.", ioe);
        }
    }

    public void stop() {
        // service present, pull it
        if (m_serviceReg != null) {
            ((ServiceRegistration) m_serviceReg).unregister();
        }

        m_serviceReg = null;

        //logout
        try {
            m_reposAdmin.logout(true);
        }
        catch (IOException ioe) {
            // not a lot we can
            System.err.println(ioe.getMessage());
        }
    }

    /**
     * Runnable that will synchronize audit log data with the server and tell the repository about the changes if applicable.
     */
    private final class AuditLogProcessTask implements Runnable {

        private final Object m_lock = new Object();

        public void process() {
            // perform synchronous model actions
            synchronized(m_lock) {
                m_statefulGatewayRepos.refresh();
                boolean changed = false;
                try {
                    checkoutModel();
                    changed |=registerGateways();
                    changed |=approveGateways();
                    changed |=setAutoApprove();
                }
                catch (IOException ioe) {
                    m_log.log(LogService.LOG_WARNING, "Checkout of model failed.", ioe);
                }
                catch (InvalidSyntaxException ise) {
                    m_log.log(LogService.LOG_WARNING, "Illegal register gateway filter.", ise);
                }

                // Commit any changes
                try {
                    if (changed) {
                        m_reposAdmin.commit();
                    }
                }
                catch (IOException ioe) {
                    m_log.log(LogService.LOG_WARNING, "Commit of model failed", ioe);
                }
            }
        }

        public void run() {
                process();
        }
    }

    private void checkoutModel() throws IOException {
        // Do a checkout
        if (!m_reposAdmin.isCurrent()) {
               m_reposAdmin.checkout();
        }
    }

    private boolean registerGateways() throws InvalidSyntaxException {
        boolean changed = false;
        String filter = "(&" + getConfigValue(ConfigItem.REGISTER_GW_FILTER) +
        "(" + StatefulGatewayObject.KEY_REGISTRATION_STATE + "=" + StatefulGatewayObject.RegistrationState.Unregistered + "))";
        List<StatefulGatewayObject> sgos =  m_statefulGatewayRepos.get(m_bundleContext.createFilter(filter));
        for (StatefulGatewayObject sgo : sgos) {
            sgo.register();
            changed = true;
        }
        return changed;
    }

    private boolean setAutoApprove() throws InvalidSyntaxException {
        boolean changed = false;
        String filter = "(&" + getConfigValue(ConfigItem.AUTO_APPROVE_GW_FILTER) +
        "(" + StatefulGatewayObject.KEY_REGISTRATION_STATE + "=" + StatefulGatewayObject.RegistrationState.Registered + ")" +
        "(!(" + GatewayObject.KEY_AUTO_APPROVE + "=true)))";

        List<StatefulGatewayObject> sgos =  m_statefulGatewayRepos.get(m_bundleContext.createFilter(filter));
        for (StatefulGatewayObject sgo : sgos) {
                sgo.setAutoApprove(true);
                changed = true;
            }
        return changed;
    }

    private boolean approveGateways() throws InvalidSyntaxException {
        boolean changed = false;
        String filter = "(&" + getConfigValue(ConfigItem.APPROVE_GW_FILTER) +
        "(" + StatefulGatewayObject.KEY_STORE_STATE + "=" + StatefulGatewayObject.StoreState.Unapproved + "))";

        List<StatefulGatewayObject> sgos =  m_statefulGatewayRepos.get(m_bundleContext.createFilter(filter));
            for (StatefulGatewayObject sgo : sgos) {
                sgo.approve();
                changed = true;
            }
        return changed;
    }

    @SuppressWarnings("unchecked")
    public void updated(Dictionary settings) throws ConfigurationException {
        if (settings != null) {
            for (ConfigItem item : ConfigItem.values()) {
                String value = (String) settings.get(item.toString());
                if ((value == null) || value.equals("")) {
                    throw new ConfigurationException(item.toString(), item.getErrorText());
                }
            }
            // store configuration
            m_settings = settings;
        }
    }

    /**
     * @param item The configuration item (enum)
     * @return The value stored in the configuration dictionary.
     */
    private String getConfigValue(ConfigItem item) {
        return (String) m_settings.get(item.toString());
    }

    /**
     *  Helper class used for gateway automation client configuration.
     *  ENUM (itemname, errormessage, filter true/false)
     *
     */
    private enum ConfigItem {
        REGISTER_GW_FILTER ("registerGatewayFilter", "Register gateway filter missing", true),
        APPROVE_GW_FILTER ("approveGatewayFilter", "Approve gateway filter missing", true),
        AUTO_APPROVE_GW_FILTER ("autoApproveGatewayFilter", "Auto approve config value missing", true),
        COMMIT_REPO ("commitRepositories", "Commit value missing.", false),
        GATEWAY_REPOSITORY ("gatewayRepository", "GatewayRepository id missing.", false),
        DEPLOYMENT_REPOSITORY ("deploymentRepository", "DeploymentRepository id missing.", false),
        STORE_REPOSITORY ("storeRepository", "Store Repository id missing.", false),
        CUSTOMER_NAME ("customerName", "Customer name missing", false),
        HOSTNAME ("hostName", "Hostname missing.", false),
        ENDPOINT ("endpoint", "Endpoint missing in config.", false);

        private final String m_name;
        private final String m_errorText;
        private final boolean m_isFilter;

        private ConfigItem(String name, String errorText, boolean isFilter) {
            m_name = name;
            m_errorText = errorText;
            m_isFilter = isFilter;
        }

        @Override
        public String toString() {
            return m_name;
        }

        public String getErrorText() {
            return m_errorText;
        }

        public boolean isFilter() {
            return m_isFilter;
        }
    }
}
