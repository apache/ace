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
package org.apache.ace.it;


/**
 * This class contains a set of Pax Exam options, intended for typo-free provisioning of bundles.
 */
public class Options {
//    public static class Osgi {
//        public static MavenArtifactProvisionOption compendium() {
//            return maven("org.osgi.compendium");
//        }
//
//        private static MavenArtifactProvisionOption maven(String artifactId) {
//            return Options.maven("org.osgi", artifactId);
//        }
//    }
//
//    public static class Felix {
//        public static MavenArtifactProvisionOption preferences() {
//            return maven("org.apache.felix.prefs");
//        }
//
//        public static MavenArtifactProvisionOption dependencyManager() {
//            return maven("org.apache.felix.dependencymanager");
//        }
//
//        public static MavenArtifactProvisionOption configAdmin() {
//            return maven("org.apache.felix.configadmin");
//        }
//
//        public static MavenArtifactProvisionOption eventAdmin() {
//            return maven("org.apache.felix.eventadmin");
//        }
//
//        public static MavenArtifactProvisionOption deploymentAdmin() {
//            return maven("org.apache.felix.deploymentadmin");
//        }
//
//        private static MavenArtifactProvisionOption maven(String artifactId) {
//            return Options.maven("org.apache.felix", artifactId);
//        }
//    }
//
//    public static class Ace {
//        public static WrappedUrlProvisionOption util() {
//            // we do this because we need access to some test classes that aren't exported
//            return wrappedBundle(mavenBundle("org.apache.ace", "org.apache.ace.util")).overwriteManifest(WrappedUrlProvisionOption.OverwriteMode.FULL);
//        }
//        
//        public static Option enableDebugger() {
//            return enableDebugger(true, 8787);
//        }
//        
//        public static Option enableDebugger(boolean suspend, int port) {
//            return new VMOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=" + (suspend ? "y" : "n") + ",address=" + port);
//        }
//
//        public static MavenArtifactProvisionOption authenticationApi() {
//            return maven("org.apache.ace.authentication.api");
//        }
//
//        public static MavenArtifactProvisionOption authentication() {
//            return maven("org.apache.ace.authentication");
//        }
//
//        public static MavenArtifactProvisionOption authenticationProcessorBasicAuth() {
//            return maven("org.apache.ace.authenticationprocessor.basicauth");
//        }
//
//        public static MavenArtifactProvisionOption authenticationProcessorClientCert() {
//            return maven("org.apache.ace.authenticationprocessor.clientcert");
//        }
//
//        public static MavenArtifactProvisionOption authenticationProcessorPassword() {
//            return maven("org.apache.ace.authenticationprocessor.password");
//        }
//
//        public static MavenArtifactProvisionOption connectionFactory() {
//            return maven("org.apache.ace.connectionfactory");
//        }
//
//        public static MavenArtifactProvisionOption rangeApi() {
//            return maven("org.apache.ace.range.api");
//        }
//
//        public static MavenArtifactProvisionOption discoveryApi() {
//            return maven("org.apache.ace.discovery.api");
//        }
//
//        public static MavenArtifactProvisionOption discoveryProperty() {
//            return maven("org.apache.ace.discovery.property");
//        }
//
//        public static MavenArtifactProvisionOption identificationApi() {
//            return maven("org.apache.ace.identification.api");
//        }
//
//        public static MavenArtifactProvisionOption identificationProperty() {
//            return maven("org.apache.ace.identification.property");
//        }
//
//        public static MavenArtifactProvisionOption scheduler() {
//            return maven("org.apache.ace.scheduler");
//        }
//
//        public static MavenArtifactProvisionOption httplistener() {
//            return maven("org.apache.ace.httplistener");
//        }
//
//        public static MavenArtifactProvisionOption repositoryApi() {
//            return maven("org.apache.ace.repository.api");
//        }
//
//        public static MavenArtifactProvisionOption repositoryExt() {
//            return maven("org.apache.ace.repository.ext");
//        }
//
//        public static MavenArtifactProvisionOption repositoryImpl() {
//            return maven("org.apache.ace.repository.impl");
//        }
//
//        public static MavenArtifactProvisionOption repositoryServlet() {
//            return maven("org.apache.ace.repository.servlet");
//        }
//
//        public static MavenArtifactProvisionOption repositoryTask() {
//            return maven("org.apache.ace.repository.task");
//        }
//
//        public static MavenArtifactProvisionOption resourceprocessorUseradmin() {
//            return maven("org.apache.ace.resourceprocessor.useradmin");
//        }
//
//        public static MavenArtifactProvisionOption configuratorServeruseradmin() {
//            return maven("org.apache.ace.configurator.serveruseradmin");
//        }
//
//        public static MavenArtifactProvisionOption configuratorUseradminTask() {
//            return maven("org.apache.ace.configurator.useradmin.task");
//        }
//
//        public static MavenArtifactProvisionOption deploymentApi() {
//            return maven("org.apache.ace.deployment.api");
//        }
//
//        public static MavenArtifactProvisionOption deploymentDeploymentAdmin() {
//            return maven("org.apache.ace.deployment.deploymentadmin");
//        }
//
//        public static MavenArtifactProvisionOption deploymentServlet() {
//            return maven("org.apache.ace.deployment.servlet");
//        }
//
//        public static MavenArtifactProvisionOption deploymentTaskBase() {
//            return maven("org.apache.ace.deployment.task.base");
//        }
//
//        public static MavenArtifactProvisionOption deploymentTask() {
//            return maven("org.apache.ace.deployment.task");
//        }
//
//        public static MavenArtifactProvisionOption deploymentStreamgenerator() {
//            return maven("org.apache.ace.deployment.streamgenerator");
//        }
//
//        public static MavenArtifactProvisionOption deploymentProviderApi() {
//            return maven("org.apache.ace.deployment.provider.api");
//        }
//
//        public static MavenArtifactProvisionOption deploymentProviderFilebased() {
//            return maven("org.apache.ace.deployment.provider.filebased");
//        }
//
//        public static MavenArtifactProvisionOption log() {
//            return maven("org.apache.ace.log");
//        }
//
//        public static MavenArtifactProvisionOption logListener() {
//            return maven("org.apache.ace.log.listener");
//        }
//
//        public static MavenArtifactProvisionOption logServlet() {
//            return maven("org.apache.ace.log.servlet");
//        }
//
//        public static MavenArtifactProvisionOption serverLogStore() {
//            return maven("org.apache.ace.server.log.store");
//        }
//
//        public static MavenArtifactProvisionOption logTask() {
//            return maven("org.apache.ace.log.task");
//        }
//
//        public static MavenArtifactProvisionOption targetLog() {
//            return maven("org.apache.ace.gateway.log"); // TODO rename!
//        }
//
//        public static MavenArtifactProvisionOption targetLogStore() {
//            return maven("org.apache.ace.gateway.log.store"); // TODO rename!
//        }
//
//        public static MavenArtifactProvisionOption obrMetadata() {
//            return maven("org.apache.ace.obr.metadata");
//        }
//
//        public static MavenArtifactProvisionOption obrServlet() {
//            return maven("org.apache.ace.obr.servlet");
//        }
//
//        public static MavenArtifactProvisionOption obrStorage() {
//            return maven("org.apache.ace.obr.storage");
//        }
//
//        public static MavenArtifactProvisionOption clientRepositoryApi() {
//            return maven("org.apache.ace.client.repository.api");
//        }
//
//        public static MavenArtifactProvisionOption clientRepositoryImpl() {
//            return maven("org.apache.ace.client.repository.impl");
//        }
//
//        public static MavenArtifactProvisionOption clientRepositoryHelperBase() {
//            return maven("org.apache.ace.client.repository.helper.base");
//        }
//
//        public static MavenArtifactProvisionOption clientRepositoryHelperBundle() {
//            return maven("org.apache.ace.client.repository.helper.bundle");
//        }
//
//        public static MavenArtifactProvisionOption clientRepositoryHelperConfiguration() {
//            return maven("org.apache.ace.client.repository.helper.configuration");
//        }
//
//        public static MavenArtifactProvisionOption clientAutomation() {
//            return maven("org.apache.ace.client.automation");
//        }
//
//        public static MavenArtifactProvisionOption maven(String artifactId) {
//            return Options.maven("org.apache.ace", artifactId);
//        }
//    }
//
//    public static class Knopflerfish {
//        public static MavenArtifactProvisionOption useradmin() {
//            return maven("org.knopflerfish.bundle", "useradmin");
//        }
//        public static MavenArtifactProvisionOption log() {
//            return maven("org.knopflerfish", "log");
//        }
//    }
//
//    public static MavenArtifactProvisionOption jetty() {
//        return maven("org.ops4j.pax.web", "pax-web-jetty-bundle");
//    }
//
//    private static MavenArtifactProvisionOption maven(String groupId, String artifactId) {
//        return mavenBundle().groupId(groupId).artifactId(artifactId).versionAsInProject();
//    }
}
