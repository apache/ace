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

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.gogo.collection.CollectionCommands;
import org.apache.ace.gogo.execute.ExecuteCommands;
import org.apache.ace.gogo.execute.ScriptExecutor;
import org.apache.ace.gogo.log.LogCommands;
import org.apache.ace.gogo.math.MathCommands;
import org.apache.ace.gogo.misc.MiscCommands;
import org.apache.ace.gogo.queue.QueueCommands;
import org.apache.ace.gogo.repo.RepoCommands;
import org.apache.ace.log.server.store.LogStore;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.BundleContext;

public class Activator extends DependencyActivatorBase {

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nop
    }

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setInterface(Object.class.getName(), createProps(RepoCommands.SCOPE, RepoCommands.FUNCTIONS))
            .setImplementation(RepoCommands.class)
            .add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true)));

        manager.add(createComponent()
            .setInterface(Object.class.getName(), createProps(MathCommands.SCOPE, MathCommands.FUNCTIONS))
            .setImplementation(MathCommands.class));

        manager.add(createComponent()
            .setInterface(Object.class.getName(), createProps(MiscCommands.SCOPE, MiscCommands.FUNCTIONS))
            .setImplementation(MiscCommands.class));

        manager.add(createComponent()
            .setInterface(Object.class.getName(), createProps(QueueCommands.SCOPE, QueueCommands.FUNCTIONS))
            .setImplementation(QueueCommands.class));

        manager.add(createComponent()
            .setInterface(Object.class.getName(), createProps(ExecuteCommands.SCOPE, ExecuteCommands.FUNCTIONS))
            .setImplementation(ExecuteCommands.class)
            .add(createServiceDependency()
                .setService(CommandProcessor.class)
                .setRequired(true)));

        manager.add(createComponent()
            .setInterface(Object.class.getName(), createProps(LogCommands.SCOPE, LogCommands.FUNCTIONS))
            .setImplementation(LogCommands.class)
            .add(createServiceDependency()
                .setService(LogStore.class)
                .setRequired(true)));

        manager.add(createComponent()
            .setInterface(Object.class.getName(), createProps(CollectionCommands.SCOPE, CollectionCommands.FUNCTIONS))
            .setImplementation(CollectionCommands.class));

        String script = System.getProperty("ace.gogo.script");
        if (script != null) {
            long delay = Long.getLong("ace.gogo.script.delay", 300L);

            manager.add(createComponent()
                .setImplementation(new ScriptExecutor(script, delay))
                .setComposition("getInstances")
                .add(createServiceDependency()
                    .setService(CommandProcessor.class)
                    .setRequired(true)));
        }
    }

    private Properties createProps(String scope, String[] functions) {
        Properties props = new Properties();
        props.put(CommandProcessor.COMMAND_SCOPE, scope);
        props.put(CommandProcessor.COMMAND_FUNCTION, functions);
        return props;
    }
}
