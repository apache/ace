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
package org.apache.ace.gogo;

import java.util.Properties;

import org.apache.ace.gogo.math.MathCommands;
import org.apache.ace.gogo.misc.MiscCommands;
import org.apache.ace.gogo.repo.RepoCommands;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.BundleContext;

public class Activator extends DependencyActivatorBase {

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {

        Properties repoProps = new Properties();
        repoProps.put(CommandProcessor.COMMAND_SCOPE, RepoCommands.SCOPE);
        repoProps.put(CommandProcessor.COMMAND_FUNCTION, RepoCommands.FUNCTIONS);
        manager.add(createComponent()
            .setInterface(Object.class.getName(), repoProps)
            .setImplementation(RepoCommands.class)
            );

        Properties mathProps = new Properties();
        mathProps.put(CommandProcessor.COMMAND_SCOPE, MathCommands.SCOPE);
        mathProps.put(CommandProcessor.COMMAND_FUNCTION, MathCommands.FUNCTIONS);
        manager.add(createComponent()
            .setInterface(Object.class.getName(), mathProps)
            .setImplementation(MathCommands.class)
            );

        Properties miscProps = new Properties();
        miscProps.put(CommandProcessor.COMMAND_SCOPE, MiscCommands.SCOPE);
        miscProps.put(CommandProcessor.COMMAND_FUNCTION, MiscCommands.FUNCTIONS);
        manager.add(createComponent()
            .setInterface(Object.class.getName(), miscProps)
            .setImplementation(MiscCommands.class)
            );

        if (System.getProperty("ace.gogo.script") != null) {
            String script = System.getProperty("ace.gogo.script");
            long delay = 300;
            if (System.getProperty("ace.gogo.script.delay") != null) {
                delay = Long.parseLong(System.getProperty("ace.gogo.script.delay"));
            }
            manager.add(createComponent()
                .setImplementation(new ScriptExecutor(script, delay))
                .add(createServiceDependency()
                    .setService(CommandProcessor.class)
                    .setRequired(true)));
        }
    }

    @Override
    public void destroy(BundleContext arg0, DependencyManager arg1) throws Exception {
    }
}
