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
package org.apache.ace.log.server.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.feedback.LowestID;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.range.SortedRangeSet;
import org.osgi.service.log.LogService;

/**
 * This class acts as a servlet and handles the log protocol. This means a number of requests will be handled:
 *
 * The endpoint is configured externally, 'auditlog' is used as an example here.
 *
 * Querying existing audit log event id's:
 * http://host:port/auditlog/query - Return all known event ranges
 * http://host:port/auditlog/query?tid=myid&logid=123712636323 - Return the event range belonging to the specified target and log id
 *
 * Accepting new audit log events:
 * http://host:port/auditlog/send - Gets a new log event and puts it in the store, the event is inside the request and should be a formatted as done in <code>Event.toRepresentation()</code>.
 *
 * Querying existing audit log events:
 * http://host:port/auditlog/receive - Return all known events
 * http://host:port/auditlog/receive?tid=myid - Return all known events belonging to the specified target ID
 * http://host:port/auditlog/receive?tid=myid&logid=2374623874 - Return all known events belonging to the specified target ID
 * 
 * Similarly, you can also send/receive lowest IDs for the logs:
 * http://host:port/auditlog/sendids
 * http://host:port/auditlog/receiveids
 *
 * If the request is not correctly formatted or other problems arise error code <code>HttpServletResponse.SC_NOT_FOUND</code> will be sent in the response.
 */
public class LogServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // response mime type
    private static final String TEXT_MIMETYPE = "text/plain";

    // url path names available on the endpoint
    private static final String QUERY = "/query";
    private static final String SEND = "/send";
    private static final String RECEIVE = "/receive";
    private static final String SEND_IDS = "/sendids";
    private static final String RECEIVE_IDS = "/receiveids";

    // url parameter keys
    private static final String TARGETID_KEY = "tid";
    private static final String FILTER_KEY = "filter";
    private static final String LOGID_KEY = "logid";
    private static final String RANGE_KEY = "range";
    
    // injected by Dependency Manager
    private volatile LogService m_log;
    private volatile LogStore m_store;

    private final String m_name;

    public LogServlet(String name) {
        m_name = name;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        // 'send' calls are POST calls
        String path = request.getPathInfo();
        response.setContentType(TEXT_MIMETYPE);
        try {
            if (SEND.equals(path) && !handleSend(request.getInputStream())) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Could not construct a log event for all events received");
            }
            else if (SEND_IDS.equals(path) && !handleSendIDs(request.getInputStream())) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Could not set lowest IDs for all logs received");
            }
        }
        catch (IOException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing post request");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getPathInfo();
        String targetID = request.getParameter(TARGETID_KEY);
        String logID = request.getParameter(LOGID_KEY);
        String filter = request.getParameter(FILTER_KEY);
        String range = request.getParameter(RANGE_KEY);

        m_log.log(LogService.LOG_DEBUG, "Log servlet called: path(" + path + ") targetID(" + targetID + ") logID(" + logID + ") range( " + range + ") filter(" + filter +")");
        response.setContentType(TEXT_MIMETYPE);

        ServletOutputStream output = null;
        try {
            output = response.getOutputStream();
            if (QUERY.equals(path) && !handleQuery(targetID, logID, filter, output)) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret query");
            }
            else if (RECEIVE.equals(path) && !handleReceive(targetID, logID, range, filter, output)) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret receive request");
            }
            else if (RECEIVE_IDS.equals(path) && !handleReceiveIDs(targetID, logID, filter, output)) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret receiveids request");
            }
        }
        catch (IOException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to process request", e);
        }
        finally {
            try {
                if (output != null) {
                    output.close();
                }
            }
            catch (Exception ex) {
                m_log.log(LogService.LOG_WARNING, "Exception trying to close stream after request: " + request.getRequestURL(), ex);
            }
        }
    }

    // Handle a call to the query 'command'
    protected boolean handleQuery(String targetID, String logID, String filter, ServletOutputStream output) throws IOException {
        if ((targetID != null) && (logID != null)) {
            // target and log id are specified, return only the range that matches these id's
            Descriptor range = m_store.getDescriptor(targetID, Long.parseLong(logID));
            output.print(range.toRepresentation());
            return true;
        }
        else if (targetID != null) {
            // target id is specified, return only the ranges that match this target
            List<Descriptor> ranges = m_store.getDescriptors(targetID);
            for (Descriptor range : ranges) {
                output.print(range.toRepresentation() + "\n");
            }
            return true;
        }
        else if ((targetID == null) && (logID == null)) {
            // no target or log id has been specified, return all ranges
            List<Descriptor> ranges = m_store.getDescriptors();
            for (Descriptor range : ranges) {
                output.print(range.toRepresentation() + "\n");
            }
            return true;
        }
        return false;
    }

    // Handle a call to the receive 'command'
    protected boolean handleReceive(String targetID, String logID, String range, String filter, ServletOutputStream output) throws IOException {
        if ((targetID != null) && (logID != null)) {
            // target and log id are specified, return only the events that are in the range that matches these id's
            if (range != null) {
                Descriptor storeDescriptor = m_store.getDescriptor(targetID, Long.parseLong(logID));
                outputRange(output, new Descriptor(storeDescriptor.getTargetID(), storeDescriptor.getStoreID(), new SortedRangeSet(range)));
            }
            else {
                outputRange(output, m_store.getDescriptor(targetID, Long.parseLong(logID)));
            }
            return true;
        }
        else if ((targetID != null) && (logID == null)) {
            // target id is specified, log id is not, return all events that belong to the specified target id
            List<Descriptor> descriptors = m_store.getDescriptors(targetID);
            for (Descriptor descriptor : descriptors) {
                outputRange(output, descriptor);
            }
            return true;
        }
        else if ((targetID == null) && (logID == null)) {
            // no target or log id has been specified, return all events
            List<Descriptor> descriptors = m_store.getDescriptors();
            for (Descriptor descriptor : descriptors) {
                outputRange(output, descriptor);
            }
            return true;
        }
        return false;
    }

    // Handle a call to the send 'command'
    protected boolean handleSend(ServletInputStream input) throws IOException {
        List<Event> events = new ArrayList<>();
        boolean success = true;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input));

            String eventString;
            while ((eventString = reader.readLine()) != null) {
                try {
                    m_log.log(LogService.LOG_DEBUG, "Log event received: '" + eventString +"'");
                    Event event = new Event(eventString);
                    events.add(event);
                }
                catch (IllegalArgumentException iae) {
                    success = false;
                    m_log.log(LogService.LOG_WARNING, "Could not construct Event from string: '" + eventString + "'");
                }
            }
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Exception ex) {
                    // not much we can do
                }
            }
        }
        m_store.put(events);
        return success;
    }
    
    // Handle a call to the send IDs 'command'
    protected boolean handleSendIDs(ServletInputStream input) throws IOException {
        boolean success = true;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input));
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                	LowestID lid = new LowestID(line);
                	m_log.log(LogService.LOG_DEBUG, "Lowest ID event received: '" + line +"'");
                	m_store.setLowestID(lid.getTargetID(), lid.getStoreID(), lid.getLowestID());
                }
                catch (IllegalArgumentException iae) {
                    success = false;
                    m_log.log(LogService.LOG_WARNING, "Could not construct lowest ID from string: '" + line + "'");
                }
            }
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Exception ex) {
                    // not much we can do
                }
            }
        }
        return success;
    }
    
    // Handle a call to the receive 'command'
    protected boolean handleReceiveIDs(String targetID, String logID, String filter, ServletOutputStream output) throws IOException {
        if ((targetID != null) && (logID != null)) {
            // target and log id are specified, return only the lowest ID that matches these id's
    		long logid = Long.parseLong(logID);
        	outputLowestID(targetID, logid, output);
            return true;
        }
        else if ((targetID != null) && (logID == null)) {
            // target id is specified, log id is not, return all events that belong to the specified target id
            List<Descriptor> descriptors = m_store.getDescriptors(targetID);
            for (Descriptor descriptor : descriptors) {
                outputLowestID(targetID, descriptor.getStoreID(), output);
            }
            return true;
        }
        else if ((targetID == null) && (logID == null)) {
            // no target or log id has been specified, return all events
            List<Descriptor> descriptors = m_store.getDescriptors();
            for (Descriptor descriptor : descriptors) {
                outputLowestID(descriptor.getTargetID(), descriptor.getStoreID(), output);
            }
            return true;
        }
        return false;
    }

	private void outputLowestID(String targetID, long logID, ServletOutputStream output) throws IOException {
		long lowestID = m_store.getLowestID(targetID, logID);
		if (lowestID > 0) {
			LowestID lid = new LowestID(targetID, logID, lowestID);
			output.print(lid.toRepresentation() + "\n");
		}
	}

    // print string representations of all events in the specified range to the specified output
    private void outputRange(ServletOutputStream output, Descriptor range) throws IOException {
        List<Event> events = m_store.get(range);
        for (Event event : events) {
            output.print(event.toRepresentation() + "\n");
        }
    }

    // send an error response
    private void sendError(HttpServletResponse response, int statusCode, String description) {
    	sendError(response, statusCode, description, null);
    }
    
    private void sendError(HttpServletResponse response, int statusCode, String description, Throwable t) {
    	if (t == null) {
    		m_log.log(LogService.LOG_WARNING, "Log request failed: " + description);
    	}
    	else {
    		m_log.log(LogService.LOG_WARNING, "Log request failed: " + description, t);
    	}
        try {
            response.sendError(statusCode, description);
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_WARNING, "Unable to send error response", e);
        }
    }

    @Override
    public String getServletInfo() {
        return "Log Endpoint (channel=" + m_name + ")";
    }
}