/*
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
package org.apache.ace.discovery.property;

import static org.apache.ace.test.utils.TestUtils.UNIT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.discovery.property.constants.DiscoveryConstants;
import org.osgi.service.cm.ConfigurationException;
import org.testng.annotations.Test;

public class PropertyBasedDiscoveryTest {
    @Test(groups = { UNIT })
    public void discoverWithoutPropertyUpdate() {
        PropertyBasedDiscovery discovery = new PropertyBasedDiscovery();
        URL url = discovery.discover();
        assertThat(url, is(nullValue()));
    }

    @Test(groups = { UNIT })
    public void discoverWithPropertyUpdate() throws ConfigurationException, URISyntaxException {
        PropertyBasedDiscovery discovery = new PropertyBasedDiscovery();
        Dictionary dict = new Hashtable();
        dict.put(DiscoveryConstants.DISCOVERY_URL_KEY, "file://local");
        discovery.updated(dict);
        URL url = discovery.discover();
        assertThat(url.toURI(), is(equalTo(new URI("file://local"))));
    }
}
