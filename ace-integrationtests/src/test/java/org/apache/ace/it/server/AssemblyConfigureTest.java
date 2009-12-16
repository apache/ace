/*
 * Copyright 2009 Toni Menzel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ace.it.server;

import org.junit.Test;
import org.ops4j.pax.exam.Option;

/**
 * @author Toni Menzel
 * @since Dec 16, 2009
 */
public class AssemblyConfigureTest
{

    @Test
    public void foo()
    {
        System.setProperty( "java.protocol.handler.pkgs", "org.ops4j.pax.url" );
        Option[] options = AssemblyConfigure.get( "ace-gateway","mvn:org.apache.ace/ace-target-devgateway/0.8.0-SNAPSHOT/zip/distribution" );
    }
}
