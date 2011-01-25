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

import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

/**
 * This class contains a set of Pax Exam options, intended for typo-free provisioning of bundles.
 */
public class Options {
    public static class Osgi {
        public static MavenArtifactProvisionOption compendium() {
            return maven("org.osgi.compendium");
        }

        private static MavenArtifactProvisionOption maven(String artifactId) {
            return Options.maven("org.osgi", artifactId);
        }
    }

    public static class Felix {
        public static MavenArtifactProvisionOption preferences() {
            return maven("org.apache.felix.prefs");
        }

        public static MavenArtifactProvisionOption dependencyManager() {
            return maven("org.apache.felix.dependencymanager");
        }

        public static MavenArtifactProvisionOption configAdmin() {
            return maven("org.apache.felix.configadmin");
        }

        private static MavenArtifactProvisionOption maven(String artifactId) {
            return Options.maven("org.apache.felix", artifactId);
        }
    }

    public static class Ace {
        public static MavenArtifactProvisionOption rangeApi() {
            return maven("ace-range-api");
        }

        public static MavenArtifactProvisionOption scheduler() {
            return maven("ace-scheduler");
        }

        public static MavenArtifactProvisionOption httplistener() {
            return maven("ace-httplistener");
        }

        public static MavenArtifactProvisionOption repositoryApi() {
            return maven("ace-repository-api");
        }

        public static MavenArtifactProvisionOption repositoryExt() {
            return maven("ace-repository-ext");
        }

        public static MavenArtifactProvisionOption repositoryImpl() {
            return maven("ace-repository-impl");
        }

        public static MavenArtifactProvisionOption repositoryServlet() {
            return maven("ace-repository-servlet");
        }

        public static MavenArtifactProvisionOption repositoryTask() {
            return maven("ace-repository-task");
        }

        public static MavenArtifactProvisionOption resourceprocessorUseradmin() {
            return maven("ace-resourceprocessor-useradmin");
        }

        public static MavenArtifactProvisionOption configuratorServeruseradmin() {
            return maven("ace-configurator-serveruseradmin");
        }

        public static MavenArtifactProvisionOption configuratorUseradminTask() {
            return maven("ace-configurator-useradmin-task");
        }

        public static MavenArtifactProvisionOption deploymentProviderApi() {
            return maven("ace-deployment-provider-api");
        }

        private static MavenArtifactProvisionOption maven(String artifactId) {
            return Options.maven("org.apache.ace", artifactId);
        }

    }

    public static class Knopflerfish {
        public static MavenArtifactProvisionOption useradmin() {
            return maven("org.knopflerfish.bundle.useradmin", "useradmin_all");
        }
        public static MavenArtifactProvisionOption log() {
            return maven("org.knopflerfish.bundle.log", "log_all");
        }
    }

    public static MavenArtifactProvisionOption jetty() {
        return maven("org.ops4j.pax.web", "pax-web-jetty-bundle");
    }

    private static MavenArtifactProvisionOption maven(String groupId, String artifactId) {
        return mavenBundle().groupId(groupId).artifactId(artifactId).versionAsInProject();
    }
}
