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
import java.util.Hashtable;
import java.util.List;

import org.amdatu.scheduling.Job;
import org.amdatu.scheduling.constants.Constants;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;


/**
 * Automatic target operator, when configured will automatically register, approve, auto-approve and commit targets to
 * the repository. An LDAP filter can be used to filter for the correct targets.
 */
public class AutoTargetOperator implements ManagedService {

    public static final String PID = "org.apache.ace.client.automation";
    public static final String SCHEDULER_NAME = "org.apache.ace.client.processauditlog";

    private volatile StatefulTargetRepository m_statefulTargetRepos;
    private volatile RepositoryAdmin m_reposAdmin;
    private volatile UserAdmin m_userAdmin;
    private volatile BundleContext m_bundleContext;
    private volatile LogService m_log;
    private volatile Dictionary<String, ?> m_settings;
    private volatile ServiceRegistration<Job> m_serviceReg;

    // used for processing the auditlog (tell the repository about that)
    private final AuditLogProcessTask m_task = new AuditLogProcessTask();

    public void start() {
        // get user
        User user = m_userAdmin.getUser("username", getConfigValue(ConfigItem.USERNAME));

        // login at Repository admin
        try {
            URL url = new URL(getConfigValue(ConfigItem.HOSTNAME) + getConfigValue(ConfigItem.ENDPOINT));
            String customerName = getConfigValue(ConfigItem.CUSTOMER_NAME);

            RepositoryAdminLoginContext loginContext = m_reposAdmin.createLoginContext(user);
            loginContext
                .add(loginContext.createShopRepositoryContext()
                    .setLocation(url).setCustomer(customerName).setName(getConfigValue(ConfigItem.STORE_REPOSITORY)))
                .add(loginContext.createTargetRepositoryContext()
                    .setLocation(url).setCustomer(customerName).setName(getConfigValue(ConfigItem.TARGET_REPOSITORY)).setWriteable())
                .add(loginContext.createDeploymentRepositoryContext()
                    .setLocation(url).setCustomer(customerName).setName(getConfigValue(ConfigItem.DEPLOYMENT_REPOSITORY)).setWriteable());

            m_reposAdmin.login(loginContext);

            // start refresh task
            Dictionary<String, Object> props = new Hashtable<>();
            props.put("name", SCHEDULER_NAME);
            m_serviceReg = m_bundleContext.registerService(Job.class, m_task, props);
        }
        catch (IOException ioe) {
            m_log.log(LogService.LOG_ERROR, "Unable to login at repository admin.", ioe);
        }
    }

    public void stop() {
        // service present, pull it
        if (m_serviceReg != null) {
            ((ServiceRegistration<?>) m_serviceReg).unregister();
        }

        m_serviceReg = null;

        // logout
        try {
            m_reposAdmin.logout(true);
        }
        catch (IOException ioe) {
            // not a lot we can
            System.err.println(ioe.getMessage());
        }
    }

    /**
     * Runnable that will synchronize audit log data with the server and tell the repository about the changes if
     * applicable.
     */
    private final class AuditLogProcessTask implements Job {

        private final Object m_lock = new Object();
        
        @Override
        public void execute() {
            // perform synchronous model actions
            synchronized (m_lock) {
                m_statefulTargetRepos.refresh();
                boolean changed = false;
                try {
                    checkoutModel();
                    changed |= registerTargets();
                    changed |= approveTargets();
                    changed |= setAutoApprove();
                }
                catch (IOException ioe) {
                    m_log.log(LogService.LOG_WARNING, "Checkout of model failed.", ioe);
                }
                catch (InvalidSyntaxException ise) {
                    m_log.log(LogService.LOG_WARNING, "Illegal register target filter.", ise);
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
    }

    private void checkoutModel() throws IOException {
        // Do a checkout
        if (!m_reposAdmin.isCurrent()) {
            m_reposAdmin.checkout();
        }
    }

    private boolean registerTargets() throws InvalidSyntaxException {
        boolean changed = false;
        String filter = "(&" + getConfigValue(ConfigItem.REGISTER_TARGET_FILTER) +
            "(" + StatefulTargetObject.KEY_REGISTRATION_STATE + "=" + StatefulTargetObject.RegistrationState.Unregistered + "))";
        List<StatefulTargetObject> stos = m_statefulTargetRepos.get(m_bundleContext.createFilter(filter));
        for (StatefulTargetObject sto : stos) {
            sto.register();
            changed = true;
        }
        return changed;
    }

    private boolean setAutoApprove() throws InvalidSyntaxException {
        boolean changed = false;
        String filter = "(&" + getConfigValue(ConfigItem.AUTO_APPROVE_TARGET_FILTER) +
            "(" + StatefulTargetObject.KEY_REGISTRATION_STATE + "=" + StatefulTargetObject.RegistrationState.Registered + ")" +
            "(!(" + TargetObject.KEY_AUTO_APPROVE + "=true)))";

        List<StatefulTargetObject> stos = m_statefulTargetRepos.get(m_bundleContext.createFilter(filter));
        for (StatefulTargetObject sto : stos) {
            sto.setAutoApprove(true);
            changed = true;
        }
        return changed;
    }

    private boolean approveTargets() throws InvalidSyntaxException {
        boolean changed = false;
        String filter = "(&" + getConfigValue(ConfigItem.APPROVE_TARGET_FILTER) +
            "(" + StatefulTargetObject.KEY_STORE_STATE + "=" + StatefulTargetObject.StoreState.Unapproved + "))";

        List<StatefulTargetObject> stos = m_statefulTargetRepos.get(m_bundleContext.createFilter(filter));
        for (StatefulTargetObject sto : stos) {
            sto.approve();
            changed = true;
        }
        return changed;
    }

    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        if (settings != null) {
            for (ConfigItem item : ConfigItem.values()) {
                String value = (String) settings.get(item.toString());
                if ((value == null) || "".equals(value.trim())) {
                    throw new ConfigurationException(item.toString(), item.getErrorText());
                }
            }
            // store configuration
            m_settings = settings;
            
            Long interval = null;
            
            Object value = settings.get("interval");
            if (value != null) {
                try {
                    interval = Long.valueOf(value.toString());
                }catch (NumberFormatException e) {
                    throw new ConfigurationException("interval", "Interval must be a valid Long value", e);
                }
            
                Dictionary<String, Object> serviceProps = new Hashtable<>();
                serviceProps.put("name", SCHEDULER_NAME);
                if (interval == null) {
                    serviceProps.remove(Constants.REPEAT_FOREVER);
                    serviceProps.remove(Constants.REPEAT_INTERVAL_PERIOD);
                    serviceProps.remove(Constants.REPEAT_INTERVAL_VALUE);
                } else {
                    serviceProps.put(Constants.REPEAT_FOREVER, true);
                    serviceProps.put(Constants.REPEAT_INTERVAL_PERIOD, "milisecond");
                    serviceProps.put(Constants.REPEAT_INTERVAL_VALUE, interval);
                }
                m_serviceReg.setProperties(serviceProps);
            }
        }
    }

    /**
     * @param item
     *            The configuration item (enum)
     * @return The value stored in the configuration dictionary.
     */
    private String getConfigValue(ConfigItem item) {
        return (String) m_settings.get(item.toString());
    }

    /**
     * Helper class used for target automation client configuration. ENUM (itemname, errormessage, filter true/false)
     *
     */
    private enum ConfigItem {
        REGISTER_TARGET_FILTER("registerTargetFilter", "Register target filter missing", true),
        APPROVE_TARGET_FILTER("approveTargetFilter", "Approve target filter missing", true),
        AUTO_APPROVE_TARGET_FILTER("autoApproveTargetFilter", "Auto approve config value missing", true),
        COMMIT_REPO("commitRepositories", "Commit value missing.", false),
        TARGET_REPOSITORY("targetRepository", "TargetRepository id missing.", false),
        DEPLOYMENT_REPOSITORY("deploymentRepository", "DeploymentRepository id missing.", false),
        STORE_REPOSITORY("storeRepository", "Store Repository id missing.", false),
        CUSTOMER_NAME("customerName", "Customer name missing", false),
        HOSTNAME("hostName", "Hostname missing.", false),
        ENDPOINT("endpoint", "Endpoint missing in config.", false),
        USERNAME("userName", "UserName missing.", false);

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

        @SuppressWarnings("unused")
        public boolean isFilter() {
            return m_isFilter;
        }
    }
}
