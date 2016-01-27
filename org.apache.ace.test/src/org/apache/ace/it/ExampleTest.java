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

import java.io.IOException;

import junit.framework.Assert;

import org.apache.felix.dm.Component;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * This class serves as a minimal example of our integration tests. Also, if this test fails, something is likely
 * wrong with the environment
 */
@SuppressWarnings("deprecation")
public class ExampleTest extends IntegrationTestBase {

//    @Configuration
//    public Option[] configuration() {
//        return options(
//            // you can add additional directives, e.g. systemProperty or VMOptions here
//            junitBundles(),
//            provision(
//                Osgi.compendium(),
//                Felix.dependencyManager()
//                // add additional bundles here
//            )
//        );
//    }

    protected void configureProvisionedServices() throws IOException {
        // configure the services you need; you cannot use the injected members yet
    }

    protected Component[] getDependencies() {
        return new Component[] {
                // create Dependency Manager components that should be started before the
                // test starts.
                createComponent()
                    .setImplementation(this)
                    .add(createServiceDependency()
                        .setService(PackageAdmin.class)
                        .setRequired(true))
        };
    }           

    // You can inject services as usual.
    private volatile PackageAdmin m_packageAdmin;

    public void testExample() throws Exception {
        Assert.assertEquals("Hey, who stole my package!",
                0,
                m_packageAdmin.getExportedPackage("org.osgi.framework").getExportingBundle().getBundleId());
    }
}
