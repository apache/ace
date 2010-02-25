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
package org.apache.ace.location.upnp;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Dictionary;
import java.util.Properties;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.http.HttpService;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPIcon;
import org.osgi.service.upnp.UPnPService;

// TODO refactor this to use the servlet whiteboard pattern
public class ProvisioningDevice extends HttpServlet implements UPnPDevice {
	private final String DEVICE_ID = "uuid:" + UUID.randomUUID();
	static final String BASE_URL = "/upnp/provisioningdevice";
	private Properties m_properties;
	private volatile HttpService m_http;
    private final String m_host;
    private final int m_port;

	private final UPnPLocationServiceWrapper m_wrapper;

	public ProvisioningDevice(String host, int port) throws Exception {
	    m_host = host;
	    m_port = port;
		m_wrapper = new UPnPLocationServiceWrapper();

		setupDeviceProperties();
	}

	public Object[] getComposition() {
	    return new Object[]{this, m_wrapper};
	}

	private void start() throws Exception {
		m_http.registerServlet(BASE_URL, this, null, null);
	}

	private void stop() {
		m_http.unregister(BASE_URL);
	}

	private void setupDeviceProperties() throws MalformedURLException {

		m_properties = new Properties();
		m_properties.put(UPnPDevice.UPNP_EXPORT,"");

		//this is odd, we have to have this
		// property here, otherwise the device will not be exported
		//although the docs say otherwise
		m_properties.put(
		        org.osgi.service.device.Constants.DEVICE_CATEGORY,
	        	new String[]{UPnPDevice.DEVICE_CATEGORY}
	        );

		m_properties.put(UPnPDevice.FRIENDLY_NAME,"UPnP Provisioning Device");
		m_properties.put(UPnPDevice.MANUFACTURER,"Apache ACE");
		m_properties.put(UPnPDevice.MANUFACTURER_URL,"http://incubator.apache.org/ace/");
		m_properties.put(UPnPDevice.MODEL_DESCRIPTION,"A device that is automagically locatable by targets.");
		m_properties.put(UPnPDevice.MODEL_NAME,"Apache ACE Device");
		m_properties.put(UPnPDevice.MODEL_NUMBER,"1.0");
		m_properties.put(UPnPDevice.MODEL_URL,"http://incubator.apache/org/ace/upnp-models/");

        m_properties.put(UPnPDevice.PRESENTATION_URL, "http://" + m_host + ":" + m_port + BASE_URL);
		m_properties.put(UPnPDevice.SERIAL_NUMBER,"123456789");
		m_properties.put(UPnPDevice.TYPE, UPnPConstants.PROVISIONING_DEVICE_TYPE);
		m_properties.put(UPnPDevice.UDN, DEVICE_ID);
		m_properties.put(UPnPDevice.UPC,"123456789");


		m_properties.put(UPnPService.ID, m_wrapper.getId());
		m_properties.put(UPnPService.TYPE, m_wrapper.getType());
	}

	public Dictionary getDescriptions(String name) {
		return m_properties;
	}

	public UPnPIcon[] getIcons(String name) {
	    //sorry, no icons yet
		return new UPnPIcon[0];
	}

	public UPnPService getService(String id) {
		if (m_wrapper.getId().equals(id)) {
			return m_wrapper;
		}
		return null;
	}

	public UPnPService[] getServices() {
		return new UPnPService[]{m_wrapper};
	}

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
	    response.setContentType("text/html");
	    PrintWriter out = response.getWriter();
	    out.println("<html>");
	    out.println("<head><title>Apache ACE Device</title></head>");
	    out.println("<body>");
	    out.println("  <center>");
	    out.println("  <h1><font face='Arial' color='#808080'>Apache ACE Device</font></h1>");

		out.println("  <p><strong>Location:&nbsp;<i>"  + m_wrapper.getLocation()   + "</i></strong</p>");
		out.println("  <p><strong>Server Type:&nbsp;[" + m_wrapper.getServerType() + "]</strong</p>");
		out.println("  <p><strong>Server Load:&nbsp;[" + m_wrapper.getServerLoad() + "%]</strong</p>");

	    out.println("  <p><a href=" + BASE_URL + "/>Refresh</a></p>");
	    out.println("  </center>");
	    out.println("  </body>");
	    out.println("</html>");
	    out.flush();
	}

	@Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)  throws IOException, ServletException {
	    doGet(request, response);
	}
}
